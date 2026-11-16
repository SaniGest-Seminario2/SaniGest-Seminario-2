#!/bin/sh

set -eu

if [ "$#" -lt 2 ] || [ "$#" -gt 3 ]; then
  printf 'Uso: %s <jar> <artifact-id> [nombre-servicio]\n' "$0" >&2
  exit 2
fi

JAR_PATH=$1
EXPECTED_ARTIFACT=$2
SERVICE_NAME=${3:-$EXPECTED_ARTIFACT}
SMOKE_PORT=${SMOKE_PORT:-18080}
LOG_FILE=$(mktemp "${TMPDIR:-/tmp}/sanigest-service-smoke.XXXXXX")
APP_PID=""

cleanup() {
  if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
  fi
  rm -f "$LOG_FILE"
}

trap cleanup EXIT INT TERM

if [ ! -f "$JAR_PATH" ]; then
  printf 'No se encontró el artefacto ejecutable: %s\n' "$JAR_PATH" >&2
  printf 'Ejecute primero: mvn -B verify\n' >&2
  exit 1
fi

java -jar "$JAR_PATH" --server.port="$SMOKE_PORT" >"$LOG_FILE" 2>&1 &
APP_PID=$!

attempt=1
while [ "$attempt" -le 30 ]; do
  if ! kill -0 "$APP_PID" 2>/dev/null; then
    printf '%s terminó antes de estar listo.\n' "$SERVICE_NAME" >&2
    sed -n '1,200p' "$LOG_FILE" >&2
    exit 1
  fi
  if HEALTH_RESPONSE=$(curl -fsS "http://127.0.0.1:$SMOKE_PORT/actuator/health" 2>/dev/null); then
    break
  fi
  attempt=$((attempt + 1))
  sleep 1
done

if [ "$attempt" -gt 30 ]; then
  printf '%s no estuvo listo dentro de 30 segundos.\n' "$SERVICE_NAME" >&2
  sed -n '1,200p' "$LOG_FILE" >&2
  exit 1
fi

printf '%s' "$HEALTH_RESPONSE" | grep -Eq '"status":"UP"'
if printf '%s' "$HEALTH_RESPONSE" | grep -q '"components"'; then
  printf 'El health público de %s expuso detalles de componentes.\n' "$SERVICE_NAME" >&2
  exit 1
fi

INFO_RESPONSE=$(curl -fsS "http://127.0.0.1:$SMOKE_PORT/actuator/info")
printf '%s' "$INFO_RESPONSE" | grep -Fq "\"artifact\":\"$EXPECTED_ARTIFACT\""

if printf '%s' "$HEALTH_RESPONSE$INFO_RESPONSE" | grep -Eiq 'jdbc:|password|token|secret'; then
  printf 'Los endpoints operativos de %s expusieron información sensible.\n' "$SERVICE_NAME" >&2
  exit 1
fi

if curl -fsS "http://127.0.0.1:$SMOKE_PORT/actuator/env" >/dev/null 2>&1; then
  printf 'El endpoint sensible /actuator/env está expuesto en %s.\n' "$SERVICE_NAME" >&2
  exit 1
fi

printf 'Smoke test de %s completado correctamente.\n' "$SERVICE_NAME"

#!/bin/sh

set -eu

PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
JAR_PATH=${1:-"$PROJECT_ROOT/services/company-service/target/company-service-0.1.0-SNAPSHOT.jar"}
SMOKE_PORT=${SMOKE_PORT:-18080}
LOG_FILE=$(mktemp "${TMPDIR:-/tmp}/sanigest-company-smoke.XXXXXX")
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
    printf 'Company Service terminó antes de estar listo.\n' >&2
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
  printf 'Company Service no estuvo listo dentro de 30 segundos.\n' >&2
  sed -n '1,200p' "$LOG_FILE" >&2
  exit 1
fi

printf '%s' "$HEALTH_RESPONSE" | grep -Eq '"status":"UP"'
if printf '%s' "$HEALTH_RESPONSE" | grep -q '"components"'; then
  printf 'El health público expuso detalles de componentes.\n' >&2
  exit 1
fi

INFO_RESPONSE=$(curl -fsS "http://127.0.0.1:$SMOKE_PORT/actuator/info")
printf '%s' "$INFO_RESPONSE" | grep -q '"artifact":"company-service"'

if printf '%s' "$HEALTH_RESPONSE$INFO_RESPONSE" | grep -Eiq 'jdbc:|password|token|secret'; then
  printf 'Los endpoints operativos expusieron información sensible.\n' >&2
  exit 1
fi

if curl -fsS "http://127.0.0.1:$SMOKE_PORT/actuator/env" >/dev/null 2>&1; then
  printf 'El endpoint sensible /actuator/env está expuesto.\n' >&2
  exit 1
fi

printf 'Smoke test de Company Service completado correctamente.\n'

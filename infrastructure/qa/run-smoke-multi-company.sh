#!/bin/sh
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$root"

keys=$(mktemp -d)
project="sanigest-tenant-qa-$$"
export SANIGEST_NETWORK_NAME="$project-network"
export SANIGEST_NETWORK_INTERNAL=false
export SANIGEST_IMAGE_TAG="$project"
export COMPANY_ACCESS_KEYS_DIR="$keys"
export COMPANY_MULTIPLE_MEMBERSHIPS_ENABLED=true
export JWT_PUBLIC_KEY_FILE="$keys/jwt-public.pem"
export JWT_PRIVATE_KEY_FILE="$keys/jwt-private.pem"
export POSTGRES_PORT=16432
export SPRING_DATA_REDIS_PORT=16379
export KAFKA_PORT=19092
export COMPANY_SERVICE_PORT=18080
export IDENTITY_SERVICE_PORT=18081
export CATALOG_SERVICE_PORT=18083
export SALES_SERVICE_PORT=18084
export GATEWAY_PORT=18085
export INVENTORY_SERVICE_PORT=18086
export SMOKE_GATEWAY_URL="http://127.0.0.1:$GATEWAY_PORT"
export SMOKE_CATALOG_URL="http://127.0.0.1:$CATALOG_SERVICE_PORT"
export SMOKE_INVENTORY_URL="http://127.0.0.1:$INVENTORY_SERVICE_PORT"

qa_compose() {
  docker compose --project-name "$project" --env-file .env.example \
    -f docker-compose.yml -f infrastructure/qa/compose-multi-company.yml --profile apps "$@"
}

cleanup() {
  result=$?
  trap - EXIT
  if [ "$result" -ne 0 ]; then
    qa_compose ps || :
  fi
  cleanup_result=0
  qa_compose down --volumes --remove-orphans --rmi local || cleanup_result=$?
  rm -rf -- "$keys"
  [ "$result" -eq 0 ] || exit "$result"
  exit "$cleanup_result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

for pair in jwt catalog-company sales-company inventory-company; do
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$keys/$pair-private.pem" 2>/dev/null
  openssl pkey -in "$keys/$pair-private.pem" -pubout -out "$keys/$pair-public.pem"
  # The host directory remains mode 0700. Compose mounts individual files for UID 10001.
  chmod 644 "$keys/$pair-private.pem"
done

qa_compose up -d --build --wait --wait-timeout 120
curl -fsS --retry 20 --retry-connrefused --retry-delay 1 --max-time 4 \
  "$SMOKE_GATEWAY_URL/actuator/health" >/dev/null
python3 infrastructure/qa/smoke-multi-company.py

#!/usr/bin/env sh
set -eu

project_name="${COMPOSE_PROJECT_NAME:-sanigest-qa}"
postgres_db="${POSTGRES_DB:-sanigest_qa}"
postgres_user="${POSTGRES_USER:-sanigest_qa}"
postgres_password="${POSTGRES_PASSWORD:-sanigest-qa-postgres}"
postgres_port="${POSTGRES_PORT:-55432}"
valkey_password="${SPRING_DATA_REDIS_PASSWORD:-sanigest-qa-valkey}"
valkey_port="${SPRING_DATA_REDIS_PORT:-56379}"
network_name="${SANIGEST_NETWORK_NAME:-${project_name}-network}"

export COMPOSE_PROJECT_NAME="$project_name"
export POSTGRES_DB="$postgres_db"
export POSTGRES_USER="$postgres_user"
export POSTGRES_PASSWORD="$postgres_password"
export POSTGRES_PORT="$postgres_port"
export SPRING_DATA_REDIS_PASSWORD="$valkey_password"
export SPRING_DATA_REDIS_PORT="$valkey_port"
export SANIGEST_NETWORK_NAME="$network_name"

cleanup() {
  docker compose down --volumes --remove-orphans
}
trap cleanup EXIT INT TERM

fail() {
  printf 'QA smoke failed: %s\n' "$1" >&2
  docker compose ps >&2 || true
  exit 1
}

wait_for_health() {
  service="$1"
  attempts=0

  while [ "$attempts" -lt 30 ]; do
    container_id="$(docker compose ps -q "$service")"
    if [ -n "$container_id" ]; then
      health="$(docker inspect --format '{{.State.Health.Status}}' "$container_id")"
      [ "$health" = "healthy" ] && return 0
      [ "$health" = "unhealthy" ] && fail "$service became unhealthy"
    fi
    attempts=$((attempts + 1))
    sleep 2
  done

  fail "$service did not become healthy"
}

docker compose config --quiet
docker compose up -d company-postgres valkey
wait_for_health company-postgres
wait_for_health valkey

unauthenticated_response="$(
  docker compose exec -T valkey valkey-cli ping 2>&1 || true
)"
case "$unauthenticated_response" in
  *NOAUTH*) ;;
  *) fail "Valkey accepted a command without authentication" ;;
esac

authenticated_response="$(
  docker compose exec -T valkey \
    valkey-cli --no-auth-warning -a "$valkey_password" ping
)"
[ "$authenticated_response" = "PONG" ] || fail "Valkey did not return PONG"

docker compose exec -T company-postgres \
  psql -v ON_ERROR_STOP=1 -U "$postgres_user" -d "$postgres_db" \
  -c 'CREATE TABLE qa_business_record (id integer PRIMARY KEY); INSERT INTO qa_business_record VALUES (1);' \
  >/dev/null

docker compose exec -T valkey \
  valkey-cli --no-auth-warning -a "$valkey_password" SET qa:disposable-cache present \
  >/dev/null
docker compose restart valkey >/dev/null
wait_for_health valkey

cache_value="$(
  docker compose exec -T valkey \
    valkey-cli --no-auth-warning -a "$valkey_password" GET qa:disposable-cache
)"
[ -z "$cache_value" ] || fail "Valkey retained disposable cache after restart"

docker compose restart company-postgres >/dev/null
wait_for_health company-postgres

business_rows="$(
  docker compose exec -T company-postgres \
    psql -At -U "$postgres_user" -d "$postgres_db" \
    -c 'SELECT COUNT(*) FROM qa_business_record WHERE id = 1;'
)"
[ "$business_rows" = "1" ] || fail "restarting Valkey affected PostgreSQL data"

printf 'QA smoke passed: services are healthy, Valkey requires auth, PostgreSQL persists, and cache restart preserves business data.\n'

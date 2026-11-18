#!/usr/bin/env sh
set -eu

project_name="${COMPOSE_PROJECT_NAME:-sanigest-qa}"
postgres_admin_user="${POSTGRES_ADMIN_USER:-sanigest_admin}"
postgres_admin_password="${POSTGRES_ADMIN_PASSWORD:-sanigest-qa-admin}"
postgres_port="${POSTGRES_PORT:-55432}"
valkey_password="${SPRING_DATA_REDIS_PASSWORD:-sanigest-qa-valkey}"
valkey_port="${SPRING_DATA_REDIS_PORT:-56379}"
kafka_port="${KAFKA_PORT:-59092}"
network_name="${SANIGEST_NETWORK_NAME:-${project_name}-network}"

export COMPOSE_PROJECT_NAME="$project_name"
export POSTGRES_ADMIN_USER="$postgres_admin_user"
export POSTGRES_ADMIN_PASSWORD="$postgres_admin_password"
export POSTGRES_PORT="$postgres_port"
export IDENTITY_DB_PASSWORD="${IDENTITY_DB_PASSWORD:-qa-identity-password}"
export COMPANY_DB_PASSWORD="${COMPANY_DB_PASSWORD:-qa-company-password}"
export CATALOG_DB_PASSWORD="${CATALOG_DB_PASSWORD:-qa-catalog-password}"
export SALES_DB_PASSWORD="${SALES_DB_PASSWORD:-qa-sales-password}"
export EXPENSE_DB_PASSWORD="${EXPENSE_DB_PASSWORD:-qa-expense-password}"
export FINANCE_DB_PASSWORD="${FINANCE_DB_PASSWORD:-qa-finance-password}"
export SPRING_DATA_REDIS_PASSWORD="$valkey_password"
export SPRING_DATA_REDIS_PORT="$valkey_port"
export KAFKA_PORT="$kafka_port"
export SANIGEST_NETWORK_NAME="$network_name"

cleanup() { docker compose --profile infra down --volumes --remove-orphans; }
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

admin_psql() {
  docker compose exec -T postgres \
    psql -X -v ON_ERROR_STOP=1 -U "$postgres_admin_user" -d postgres "$@"
}

service_psql() {
  service="$1"
  password="$2"
  shift 2
  docker compose exec -T -e PGPASSWORD="$password" postgres \
    psql -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -U "sanigest_${service}" \
    -d "sanigest_${service}" "$@"
}

docker compose --profile infra config --quiet
docker compose --profile infra up -d postgres valkey kafka
wait_for_health postgres
wait_for_health valkey
wait_for_health kafka

kafka_broker_count="$(docker compose exec -T kafka \
  /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server kafka:29092 2>/dev/null | \
  grep -c 'id: 1')"
[ "$kafka_broker_count" -ge 1 ] || fail "Kafka did not expose its internal listener"

docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server kafka:29092 --create --if-not-exists \
  --topic qa.smoke.v1 --partitions 1 --replication-factor 1 >/dev/null
kafka_topic="$(docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server kafka:29092 --list | grep -Fx 'qa.smoke.v1' || true)"
[ "$kafka_topic" = "qa.smoke.v1" ] || fail "Kafka could not create and list a topic"

# Re-running the bootstrap must converge without duplicate roles or databases.
docker compose exec -T postgres \
  /docker-entrypoint-initdb.d/10-create-service-databases.sh >/dev/null

role_count="$(admin_psql -Atc \
  "SELECT count(*) FROM pg_roles WHERE rolname ~ '^sanigest_(identity|company|catalog|sales|expense|finance)$';")"
[ "$role_count" = "6" ] || fail "expected 6 service roles, found $role_count"

database_count="$(admin_psql -Atc \
  "SELECT count(*) FROM pg_database WHERE datname ~ '^sanigest_(identity|company|catalog|sales|expense|finance)$';")"
[ "$database_count" = "6" ] || fail "expected 6 service databases, found $database_count"

for service in identity company catalog sales expense finance; do
  upper_service="$(printf '%s' "$service" | tr '[:lower:]' '[:upper:]')"
  eval "password=\${${upper_service}_DB_PASSWORD}"
  service_psql "$service" "$password" \
    -c 'CREATE TABLE IF NOT EXISTS qa_own_record (id integer PRIMARY KEY);' >/dev/null &
done
wait

service_psql company "$COMPANY_DB_PASSWORD" \
  -c 'INSERT INTO qa_own_record VALUES (1) ON CONFLICT DO NOTHING;' >/dev/null
cross_database_response="$(
  docker compose exec -T -e PGPASSWORD="$COMPANY_DB_PASSWORD" postgres \
    psql -X -h 127.0.0.1 -U sanigest_company -d sanigest_sales -c 'SELECT 1;' 2>&1 || true
)"
case "$cross_database_response" in
  *'does not have CONNECT privilege'*) ;;
  *)
    printf '%s\n' "$cross_database_response" >&2
    fail "Company role connected to the Sales database"
    ;;
esac

unauthenticated_response="$(docker compose exec -T valkey valkey-cli ping 2>&1 || true)"
case "$unauthenticated_response" in
  *NOAUTH*) ;;
  *) fail "Valkey accepted a command without authentication" ;;
esac

authenticated_response="$(
  docker compose exec -T valkey valkey-cli --no-auth-warning -a "$valkey_password" ping
)"
[ "$authenticated_response" = "PONG" ] || fail "Valkey did not return PONG"

docker compose exec -T valkey \
  valkey-cli --no-auth-warning -a "$valkey_password" SET qa:disposable-cache present >/dev/null
docker compose restart valkey >/dev/null
wait_for_health valkey
cache_value="$(docker compose exec -T valkey \
  valkey-cli --no-auth-warning -a "$valkey_password" GET qa:disposable-cache)"
[ -z "$cache_value" ] || fail "Valkey retained disposable cache after restart"

docker compose restart postgres >/dev/null
wait_for_health postgres
business_rows="$(service_psql company "$COMPANY_DB_PASSWORD" \
  -Atc 'SELECT count(*) FROM qa_own_record WHERE id = 1;')"
[ "$business_rows" = "1" ] || fail "PostgreSQL did not retain Company data"

printf '%s\n' \
  'QA smoke passed: PostgreSQL is isolated and persistent, Valkey is authenticated and disposable, and Kafka accepts topic operations.'

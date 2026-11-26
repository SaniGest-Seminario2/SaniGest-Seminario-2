#!/usr/bin/env sh
set -eu

PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$PROJECT_ROOT"

project_name="${COMPOSE_PROJECT_NAME:-sanigest-qa-$$}"
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
export INVENTORY_DB_PASSWORD="${INVENTORY_DB_PASSWORD:-qa-inventory-password}"
export SALES_DB_PASSWORD="${SALES_DB_PASSWORD:-qa-sales-password}"
export EXPENSE_DB_PASSWORD="${EXPENSE_DB_PASSWORD:-qa-expense-password}"
export FINANCE_DB_PASSWORD="${FINANCE_DB_PASSWORD:-qa-finance-password}"
export SPRING_DATA_REDIS_PASSWORD="$valkey_password"
export SPRING_DATA_REDIS_PORT="$valkey_port"
export KAFKA_PORT="$kafka_port"
export SANIGEST_NETWORK_NAME="$network_name"

stage="Compose configuration and startup"
stage_started=$(date +%s)

report_stage() {
  outcome=$1
  duration=$(( $(date +%s) - stage_started ))
  printf '%s — %s (%ss)\n' "$outcome" "$stage" "$duration"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    printf '| %s | %s | %s s |\n' "$stage" "$outcome" "$duration" >>"$GITHUB_STEP_SUMMARY" ||
      printf '::warning::Could not write the infrastructure stage summary.\n'
  fi
}

next_stage() {
  report_stage Passed
  stage=$1
  stage_started=$(date +%s)
}

log_failure_indicators() {
  # Inspect a bounded tail and print only fixed descriptions. Container output can contain credentials.
  docker compose logs --no-color --tail 30 "$1" 2>/dev/null | awk '
    /[Aa]ddress already in use/ { port_conflict = 1 }
    /[Pp]ermission denied/ { file_permission = 1 }
    /[Nn]o space left on device/ { disk_full = 1 }
    /FATAL:|\[ERROR\]|[[:space:]]ERROR[[:space:]]/ { errors++ }
    END {
      if (port_conflict) print "Recent container logs indicate an address conflict."
      if (file_permission) print "Recent container logs indicate a permissions error."
      if (disk_full) print "Recent container logs indicate exhausted disk space."
      if (errors) printf "Recent container logs contain %d error-level entries.\n", errors
    }
  '
}

finish() {
  result=$?
  trap - EXIT
  if [ "$result" -eq 0 ]; then
    report_stage Passed
  else
    report_stage Failed
    printf '::error::Infrastructure / %s failed (exit %s). See the step log.\n' "$stage" "$result"
    # Emit container states and health exit codes. Health output and raw logs may contain credentials.
    for name in postgres valkey kafka; do
      container_id=$(docker compose ps -aq "$name" 2>/dev/null) || continue
      [ -n "$container_id" ] || continue
      printf '%s: ' "$name"
      docker inspect --format '{{.State.Status}}{{with .State.Health}} health={{.Status}} failing_streak={{.FailingStreak}}{{range .Log}} probe_exit={{.ExitCode}}{{end}}{{end}}' "$container_id" ||
        printf 'state unavailable\n'
      log_failure_indicators "$name" || printf 'log indicators unavailable\n'
    done
  fi
  stage="Cleanup of temporary containers and volumes"
  stage_started=$(date +%s)
  cleanup_result=0
  docker compose --profile infra down --volumes --remove-orphans || cleanup_result=$?
  if [ "$cleanup_result" -eq 0 ]; then
    report_stage Passed
  else
    report_stage Failed
    printf '::error::Infrastructure cleanup failed (exit %s).\n' "$cleanup_result"
    [ "$result" -ne 0 ] || result=$cleanup_result
  fi
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    printf '\n' >>"$GITHUB_STEP_SUMMARY" || :
  fi
  exit "$result"
}

trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  printf '### Infrastructure runtime checks\n\n| Check | Result | Duration |\n|---|---|---:|\n' >>"$GITHUB_STEP_SUMMARY" ||
    printf '::warning::Could not write the infrastructure summary header.\n'
fi

fail() {
  printf 'QA smoke failed: %s\n' "$1" >&2
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
next_stage "PostgreSQL health"
wait_for_health postgres
next_stage "Valkey health"
wait_for_health valkey
next_stage "Kafka health"
wait_for_health kafka
next_stage "Kafka listener and topic operations"

kafka_brokers="$(docker compose exec -T kafka \
  /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server kafka:29092)"
printf '%s\n' "$kafka_brokers" | grep -q 'id: 1' || fail "Kafka did not expose its internal listener"

docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server kafka:29092 --create --if-not-exists \
  --topic qa.smoke.v1 --partitions 1 --replication-factor 1 >/dev/null
kafka_topics="$(docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server kafka:29092 --list)"
printf '%s\n' "$kafka_topics" | grep -Fxq 'qa.smoke.v1' || fail "Kafka could not create and list a topic"

next_stage "PostgreSQL bootstrap and database isolation"
# Re-running the bootstrap must converge without duplicate roles or databases.
docker compose exec -T postgres \
  /docker-entrypoint-initdb.d/10-create-service-databases.sh >/dev/null

role_count="$(admin_psql -Atc \
  "SELECT count(*) FROM pg_roles WHERE rolname ~ '^sanigest_(identity|company|catalog|sales|inventory|expense|finance)$';")"
[ "$role_count" = "7" ] || fail "expected 7 service roles, found $role_count"

database_count="$(admin_psql -Atc \
  "SELECT count(*) FROM pg_database WHERE datname ~ '^sanigest_(identity|company|catalog|sales|inventory|expense|finance)$';")"
[ "$database_count" = "7" ] || fail "expected 7 service databases, found $database_count"

for service in identity company catalog sales inventory expense finance; do
  upper_service="$(printf '%s' "$service" | tr '[:lower:]' '[:upper:]')"
  eval "password=\${${upper_service}_DB_PASSWORD}"
  service_psql "$service" "$password" \
    -c 'CREATE TABLE IF NOT EXISTS qa_own_record (id integer PRIMARY KEY);' >/dev/null
done

service_psql company "$COMPANY_DB_PASSWORD" \
  -c 'INSERT INTO qa_own_record VALUES (1) ON CONFLICT DO NOTHING;' >/dev/null
cross_database_response="$(
  docker compose exec -T -e PGPASSWORD="$COMPANY_DB_PASSWORD" postgres \
    psql -X -h 127.0.0.1 -U sanigest_company -d sanigest_sales -c 'SELECT 1;' 2>&1 || true
)"
case "$cross_database_response" in
  *'does not have CONNECT privilege'*) ;;
  *)
    fail "Expected a CONNECT privilege rejection for Company on the Sales database"
    ;;
esac

# Exercise the targeted bootstrap against an existing volume with a missing Inventory database.
admin_psql -c 'DROP DATABASE sanigest_inventory;' >/dev/null
docker compose exec -T postgres /docker-entrypoint-initdb.d/10-create-service-databases.sh inventory >/dev/null
[ "$(service_psql company "$COMPANY_DB_PASSWORD" -Atc 'SELECT count(*) FROM qa_own_record;')" = "1" ] ||
  fail "Inventory bootstrap modified existing Company data"
for other in identity company catalog sales expense finance; do
  denied="$(docker compose exec -T -e PGPASSWORD="$INVENTORY_DB_PASSWORD" postgres \
    psql -X -h 127.0.0.1 -U sanigest_inventory -d "sanigest_$other" -c 'SELECT 1;' 2>&1 || true)"
  case "$denied" in
    *'does not have CONNECT privilege'*) ;;
    *) fail "Inventory unexpectedly connected to $other" ;;
  esac
done

next_stage "Valkey authentication"
unauthenticated_response="$(docker compose exec -T valkey valkey-cli ping 2>&1 || true)"
case "$unauthenticated_response" in
  *NOAUTH*) ;;
  *) fail "Valkey accepted a command without authentication" ;;
esac

authenticated_response="$(
  docker compose exec -T valkey valkey-cli --no-auth-warning -a "$valkey_password" ping
)"
[ "$authenticated_response" = "PONG" ] || fail "Valkey did not return PONG"

next_stage "Valkey cache is disposable after restart"
docker compose exec -T valkey \
  valkey-cli --no-auth-warning -a "$valkey_password" SET qa:disposable-cache present >/dev/null
docker compose restart valkey >/dev/null
wait_for_health valkey
cache_value="$(docker compose exec -T valkey \
  valkey-cli --no-auth-warning -a "$valkey_password" GET qa:disposable-cache)"
[ -z "$cache_value" ] || fail "Valkey retained disposable cache after restart"

next_stage "PostgreSQL data survives restart"
docker compose restart postgres >/dev/null
wait_for_health postgres
business_rows="$(service_psql company "$COMPANY_DB_PASSWORD" \
  -Atc 'SELECT count(*) FROM qa_own_record WHERE id = 1;')"
[ "$business_rows" = "1" ] || fail "PostgreSQL did not retain Company data"

printf '%s\n' \
  'QA smoke passed: PostgreSQL is isolated and persistent, Valkey is authenticated and disposable, and Kafka accepts topic operations.'

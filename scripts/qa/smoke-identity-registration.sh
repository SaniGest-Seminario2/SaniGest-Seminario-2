#!/usr/bin/env sh

set -eu

PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
POSTGRES_PORT=${POSTGRES_PORT:-55433}
IDENTITY_SERVICE_PORT=${IDENTITY_SERVICE_PORT:-58081}
PROJECT_NAME=${COMPOSE_PROJECT_NAME:-sanigest-identity-registration-qa}
POSTGRES_ADMIN_USER=${POSTGRES_ADMIN_USER:-sanigest_admin}
POSTGRES_ADMIN_PASSWORD=${POSTGRES_ADMIN_PASSWORD:-sanigest-qa-admin}
IDENTITY_DB_PASSWORD=${IDENTITY_DB_PASSWORD:-qa-identity-password}
LOG_FILE=$PROJECT_ROOT/services/identity-service/target/identity-registration-smoke.log
TEMP_DIR=$(mktemp -d "${TMPDIR:-/tmp}/sanigest-identity-registration.XXXXXX")
CURL_IMAGE=${CURL_IMAGE:-curlimages/curl:8.17.0}

export COMPOSE_PROJECT_NAME=$PROJECT_NAME
export POSTGRES_PORT
export IDENTITY_SERVICE_PORT
export POSTGRES_ADMIN_USER
export POSTGRES_ADMIN_PASSWORD
export IDENTITY_DB_PASSWORD
export COMPANY_DB_PASSWORD=${COMPANY_DB_PASSWORD:-qa-company-password}
export CATALOG_DB_PASSWORD=${CATALOG_DB_PASSWORD:-qa-catalog-password}
export SALES_DB_PASSWORD=${SALES_DB_PASSWORD:-qa-sales-password}
export EXPENSE_DB_PASSWORD=${EXPENSE_DB_PASSWORD:-qa-expense-password}
export FINANCE_DB_PASSWORD=${FINANCE_DB_PASSWORD:-qa-finance-password}
export SPRING_DATA_REDIS_PASSWORD=${SPRING_DATA_REDIS_PASSWORD:-qa-valkey-password}
export SANIGEST_NETWORK_NAME=${SANIGEST_NETWORK_NAME:-$PROJECT_NAME-network}

cleanup() {
  docker compose --project-directory "$PROJECT_ROOT" --profile apps \
    down --volumes --remove-orphans >/dev/null 2>&1 || true
  rm -rf "$TEMP_DIR"
}

trap cleanup EXIT INT TERM

fail() {
  printf 'Identity registration smoke failed: %s\n' "$1" >&2
  docker compose --project-directory "$PROJECT_ROOT" logs --no-color identity-service \
    >"$LOG_FILE" 2>&1 || true
  if [ -f "$LOG_FILE" ]; then
    tail -n 100 "$LOG_FILE" >&2
  fi
  exit 1
}

request() {
  name=$1
  payload=$2
  docker run --rm --network "$SANIGEST_NETWORK_NAME" \
    "$CURL_IMAGE" -sS -w '\n%{http_code}' \
    -H 'Content-Type: application/json' \
    --data "$payload" \
    "http://identity-service:8080/api/v1/auth/register" >"$TEMP_DIR/$name.response"
  tail -n 1 "$TEMP_DIR/$name.response" >"$TEMP_DIR/$name.status"
  sed '$d' "$TEMP_DIR/$name.response" >"$TEMP_DIR/$name.body"
}

assert_status() {
  name=$1
  expected=$2
  actual=$(cat "$TEMP_DIR/$name.status")
  if [ "$actual" != "$expected" ]; then
    printf 'Unexpected response for %s (expected %s, got %s):\n' \
      "$name" "$expected" "$actual" >&2
    cat "$TEMP_DIR/$name.body" >&2
    fail "unexpected HTTP status for $name"
  fi
}

cd "$PROJECT_ROOT"
docker pull "$CURL_IMAGE" >/dev/null
docker compose --profile apps up -d --build --wait identity-service >/dev/null

request success \
  '{"email":" Smoke.User@Example.COM ","password":"correct-horse-battery-staple","name":" Smoke User "}'
assert_status success 201
grep -Eq '"userId":"[0-9a-fA-F-]{36}"' "$TEMP_DIR/success.body" || \
  fail "success response did not contain a userId"
grep -Fq '"email":"smoke.user@example.com"' "$TEMP_DIR/success.body" || \
  fail "success response did not contain the normalized email"
if grep -Eiq 'password|hash|correct-horse-battery-staple' "$TEMP_DIR/success.body"; then
  fail "success response exposed password data"
fi

request duplicate \
  '{"email":"SMOKE.USER@EXAMPLE.COM","password":"another-secure-password","name":"Duplicate"}'
assert_status duplicate 409
grep -Fq 'urn:sanigest:problem:duplicate-email' "$TEMP_DIR/duplicate.body" || \
  fail "duplicate response did not use the duplicate-email problem type"

request invalid_email \
  '{"email":"not-an-email","password":"correct-horse-battery-staple","name":"Invalid"}'
assert_status invalid_email 400
grep -Fq '"email"' "$TEMP_DIR/invalid_email.body" || \
  fail "invalid email response did not identify the email field"

request weak_password \
  '{"email":"weak@example.com","password":"short","name":"Weak"}'
assert_status weak_password 400
grep -Fq '"password"' "$TEMP_DIR/weak_password.body" || \
  fail "weak password response did not identify the password field"

request concurrent_one \
  '{"email":"race@example.com","password":"first-secure-password","name":"Race One"}' &
first_pid=$!
request concurrent_two \
  '{"email":"RACE@EXAMPLE.COM","password":"second-secure-password","name":"Race Two"}' &
second_pid=$!
wait "$first_pid"
wait "$second_pid"

first_status=$(cat "$TEMP_DIR/concurrent_one.status")
second_status=$(cat "$TEMP_DIR/concurrent_two.status")
if ! { [ "$first_status" = "201" ] && [ "$second_status" = "409" ]; } && \
  ! { [ "$first_status" = "409" ] && [ "$second_status" = "201" ]; }; then
  fail "concurrent registration returned [$first_status, $second_status], expected [201, 409]"
fi

stored_rows=$(docker compose exec -T -e PGPASSWORD="$IDENTITY_DB_PASSWORD" postgres \
  psql -X -At -v ON_ERROR_STOP=1 -h 127.0.0.1 -U sanigest_identity \
  -d sanigest_identity -c "SELECT count(*) FROM users;")
[ "$stored_rows" = "2" ] || fail "expected exactly two persisted users, found $stored_rows"

stored_hash=$(docker compose exec -T -e PGPASSWORD="$IDENTITY_DB_PASSWORD" postgres \
  psql -X -At -v ON_ERROR_STOP=1 -h 127.0.0.1 -U sanigest_identity \
  -d sanigest_identity -c \
  "SELECT password_hash FROM users WHERE email = 'smoke.user@example.com';")
case "$stored_hash" in
  \$2a\$*|\$2b\$*|\$2y\$*) ;;
  *) fail "stored credential was not a BCrypt hash" ;;
esac
[ "$stored_hash" != "correct-horse-battery-staple" ] || \
  fail "plain-text password was persisted"

printf 'Identity registration functional smoke completed successfully.\n'

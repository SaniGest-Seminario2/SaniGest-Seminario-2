#!/bin/sh
set -eu

# The official PostgreSQL image runs this file on a new data volume. It is also
# safe to execute again manually, which makes upgrades and QA deterministic.
case "${1:-}" in
  "") services="identity company catalog sales inventory expense finance" ;;
  inventory) services="inventory" ;;
  *) printf '%s\n' 'Usage: 10-create-service-databases.sh [inventory]' >&2; exit 2 ;;
esac

for service in $services; do
  upper_service="$(printf '%s' "$service" | tr '[:lower:]' '[:upper:]')"
  eval "password=\${${upper_service}_DB_PASSWORD:-}"

  if [ -z "$password" ]; then
    printf 'Missing required variable %s_DB_PASSWORD\n' "$upper_service" >&2
    exit 1
  fi

  role="sanigest_${service}"
  database="sanigest_${service}"

  psql --username "$POSTGRES_USER" --dbname postgres --set ON_ERROR_STOP=1 \
    --set role="$role" --set password="$password" <<'SQL'
SELECT format(
  'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION',
  :'role',
  :'password'
)
WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = :'role') \gexec

SELECT format(
  'ALTER ROLE %I WITH LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION',
  :'role',
  :'password'
) \gexec
SQL

  psql --username "$POSTGRES_USER" --dbname postgres --set ON_ERROR_STOP=1 \
    --set role="$role" --set database="$database" <<'SQL'
SELECT format('CREATE DATABASE %I OWNER %I', :'database', :'role')
WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_database WHERE datname = :'database') \gexec
SELECT format('ALTER DATABASE %I OWNER TO %I', :'database', :'role') \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'database') \gexec
SELECT format('GRANT CONNECT, TEMPORARY ON DATABASE %I TO %I', :'database', :'role') \gexec
SQL

  psql --username "$POSTGRES_USER" --dbname "$database" --set ON_ERROR_STOP=1 \
    --set role="$role" <<'SQL'
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
SELECT format('ALTER SCHEMA public OWNER TO %I', :'role') \gexec
SQL
done

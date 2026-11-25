#!/usr/bin/env sh
set -eu

PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
if ! command -v jq >/dev/null 2>&1; then
  printf '%s\n' 'jq is required to validate the Docker Compose contract.' >&2
  exit 1
fi

CONFIG_FILE=$(mktemp "${TMPDIR:-/tmp}/sanigest-compose.XXXXXX")
trap 'rm -f "$CONFIG_FILE"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

cd "$PROJECT_ROOT"
docker compose --env-file .env.example --profile infra --profile apps \
  config --format json >"$CONFIG_FILE"

jq -er '
  def require($condition; $message):
    if $condition then . else error($message) end;

  def healthy_dependencies($expected):
    ((. // {}) | keys) == $expected and all(.[]; .condition == "service_healthy");

  . as $compose
  | {
    "postgres": ["infra", "apps"],
    "valkey": ["infra", "apps"],
    "kafka": ["infra", "apps"],
    "company-service": ["apps"],
    "identity-service": ["apps"],
    "catalog-service": ["apps"],
    "sales-service": ["apps"],
    "inventory-service": ["apps"]
  } as $profiles
  | reduce ($profiles | keys[]) as $name (.;
      .services[$name] as $service
      | require($service.profiles == $profiles[$name]; "\($name) has unexpected profiles")
      | require(($service.healthcheck | type) == "object" and ($service.healthcheck | length) > 0;
          "\($name) has no health check")
      | require(all($service.ports[]?; .host_ip == "127.0.0.1");
          "\($name) exposes a port beyond loopback"))
  | require((.services["company-service"].depends_on | healthy_dependencies(["kafka", "postgres", "valkey"]));
      "Company requires healthy Kafka, PostgreSQL and Valkey dependencies")
  | require((.services["identity-service"].depends_on | healthy_dependencies(["postgres"]));
      "Identity requires a healthy PostgreSQL dependency")
  | require((.services["catalog-service"].depends_on | healthy_dependencies(["postgres"]));
      "Catalog requires a healthy PostgreSQL dependency")
  | require((.services["sales-service"].depends_on | healthy_dependencies(["postgres"]));
      "Sales requires a healthy PostgreSQL dependency")
  | require((.services["inventory-service"].depends_on | healthy_dependencies(["catalog-service", "company-service", "postgres"]));
      "Inventory requires healthy Catalog, Company and PostgreSQL dependencies")
  | require((["company-service", "identity-service", "catalog-service", "sales-service", "inventory-service"] | all(. as $name |
      $compose.services[$name].build.dockerfile == "infrastructure/docker/service.Dockerfile"
      and $compose.services[$name].build.args.SERVICE == $name));
      "Application builds must select their module through the shared Dockerfile")
  | require(.networks["sanigest-local"].internal == true; "sanigest-local must be internal")
  | require((.volumes | keys) == ["kafka-data", "postgres-data"]; "Unexpected persistent volumes")
  | require(.services.valkey.volumes == null; "Valkey cache must remain disposable")
  | "Compose contract is valid: profiles, health gates, network and persistence policy."
' "$CONFIG_FILE"

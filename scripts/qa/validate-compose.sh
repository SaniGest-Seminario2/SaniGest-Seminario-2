#!/usr/bin/env sh
set -eu

PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
CONFIG_FILE=$(mktemp "${TMPDIR:-/tmp}/sanigest-compose.XXXXXX")
trap 'rm -f "$CONFIG_FILE"' EXIT INT TERM

cd "$PROJECT_ROOT"
docker compose --env-file .env.example --profile infra --profile apps \
  config --format json >"$CONFIG_FILE"

python3 - "$CONFIG_FILE" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    compose = json.load(stream)

services = compose["services"]
expected_profiles = {
    "postgres": ["infra", "apps"],
    "valkey": ["infra", "apps"],
    "kafka": ["infra", "apps"],
    "company-service": ["apps"],
}
for service, profiles in expected_profiles.items():
    assert services[service]["profiles"] == profiles, (service, services[service]["profiles"])
    assert services[service].get("healthcheck"), f"{service} has no health check"
    for port in services[service].get("ports", []):
        assert port["host_ip"] == "127.0.0.1", f"{service} exposes a port beyond loopback"

dependencies = services["company-service"]["depends_on"]
assert set(dependencies) == {"postgres", "valkey", "kafka"}
assert all(value["condition"] == "service_healthy" for value in dependencies.values())
assert compose["networks"]["sanigest-local"]["internal"] is True
assert set(compose["volumes"]) == {"postgres-data", "kafka-data"}
assert services["valkey"].get("volumes") is None, "Valkey cache must remain disposable"

print("Compose contract is valid: profiles, health gates, network and persistence policy.")
PY

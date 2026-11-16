#!/bin/sh

set -eu

PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)

exec sh "$PROJECT_ROOT/scripts/qa/smoke-spring-boot-service.sh" \
  "$PROJECT_ROOT/services/company-service/target/company-service-0.1.0-SNAPSHOT.jar" \
  company-service \
  "Company Service"

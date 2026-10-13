#!/usr/bin/env bash
# Validate interpolation using explicitly test-only values, without printing secrets.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
export DOMAIN=staging.sportgate.pro ACME_EMAIL=test@example.invalid
export POSTGRES_PASSWORD=test-only-postgres-password JWT_SECRET=test-only-jwt-secret-at-least-32-characters
for file in infra/compose.prod.yaml infra/compose.staging.yaml infra/compose.monitoring.yaml; do
  docker compose -f "$file" config --quiet
done

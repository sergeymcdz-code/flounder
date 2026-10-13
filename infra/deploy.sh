#!/usr/bin/env bash
# Deploy / update the Flounder demo stand.
# Usage (from any directory):  ./infra/deploy.sh [--no-pull]
#   --no-pull   skip `git pull` (deploy the checked-out working tree as is)
#
# What it does: pull the latest main, build images, (re)start containers, wait for API health.
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$REPO_DIR/infra/compose.prod.yaml"
ENV_FILE="$REPO_DIR/infra/.env.prod"
BRANCH="${DEPLOY_BRANCH:-main}"
PULL=1
[[ "${1:-}" == "--no-pull" ]] && PULL=0

log() { printf '\n[deploy] %s\n' "$*"; }

cd "$REPO_DIR"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "ERROR: $ENV_FILE not found. Copy infra/.env.prod.example to infra/.env.prod and fill it in." >&2
  exit 1
fi

# Fail early on missing required variables (compose would fail later with a less clear message).
for var in DOMAIN ACME_EMAIL POSTGRES_PASSWORD JWT_SECRET; do
  if ! grep -Eq "^${var}=.+" "$ENV_FILE"; then
    echo "ERROR: $var is not set in $ENV_FILE" >&2
    exit 1
  fi
done

if [[ "$PULL" -eq 1 ]]; then
  log "Pulling latest $BRANCH"
  git fetch origin "$BRANCH"
  git checkout -q "$BRANCH"
  git pull --ff-only origin "$BRANCH"
fi
log "Deploying commit $(git rev-parse --short HEAD)"

compose() { docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"; }

log "Building images and starting containers"
compose up -d --build --remove-orphans

log "Waiting for API health (up to 3 minutes)"
for _ in $(seq 1 36); do
  status="$(docker inspect --format '{{.State.Health.Status}}' flounder-api 2>/dev/null || echo unknown)"
  if [[ "$status" == "healthy" ]]; then
    log "API is healthy"
    compose ps
    log "Removing dangling images from previous builds"
    docker image prune -f >/dev/null || true
    DOMAIN_VALUE="$(grep -E '^DOMAIN=' "$ENV_FILE" | cut -d= -f2-)"
    log "Done. Check: https://${DOMAIN_VALUE}/actuator/health"
    exit 0
  fi
  sleep 5
done

echo "ERROR: API did not become healthy in time. Last status: $status" >&2
echo "Logs: docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod logs --tail=200 api" >&2
compose ps
exit 1

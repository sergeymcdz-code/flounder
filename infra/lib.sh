#!/usr/bin/env bash
# Shared trusted environment and local maintenance logging.
INFRA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ENV_FILE:-$INFRA_DIR/.env.prod}"
# shellcheck disable=SC1090
if [[ -f "$ENV_FILE" ]]; then set -a; source "$ENV_FILE"; set +a; fi
BACKUP_DIR="${BACKUP_DIR:-/home/deploy/backups}"
# Shared values are consumed by sourcing scripts.
# shellcheck disable=SC2034
CONTAINER="${POSTGRES_CONTAINER:-flounder-postgres}"
# shellcheck disable=SC2034
DB="${POSTGRES_DB:-sport_platform}"
# shellcheck disable=SC2034
USER_NAME="${POSTGRES_USER:-sport_user}"
umask 077
log_start() {
  mkdir -p "$BACKUP_DIR"
  exec > >(tee -a "$BACKUP_DIR/backup.log") 2>&1
}
alert() { "$INFRA_DIR/alert.sh" "$*" || echo '[alert] delivery failed' >&2; }

#!/usr/bin/env bash
# Restore the Flounder PostgreSQL database from a backup made by infra/backup.sh.
# Usage:  ./infra/restore.sh backups/2026-10-14.sql.gz
#
# WARNING: the current database is DROPPED and replaced by the backup contents.
# The API container is stopped during the restore and started again afterwards.
set -euo pipefail

if [[ $# -ne 1 || ! -f "$1" ]]; then
  echo "Usage: $0 <path-to-backup.sql.gz>" >&2
  exit 1
fi
BACKUP_FILE="$(realpath "$1")"

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$REPO_DIR/infra/compose.prod.yaml"
ENV_FILE="$REPO_DIR/infra/.env.prod"
CONTAINER="flounder-postgres"

# shellcheck disable=SC1090
set -a; source "$ENV_FILE"; set +a
DB="${POSTGRES_DB:-sport_platform}"
USER_NAME="${POSTGRES_USER:-sport_user}"

compose() { docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"; }
psql_admin() { docker exec -i "$CONTAINER" psql -U "$USER_NAME" -d postgres -v ON_ERROR_STOP=1 "$@"; }

echo "This will DROP database '$DB' and restore it from:"
echo "  $BACKUP_FILE"
read -r -p "Type 'yes' to continue: " answer
[[ "$answer" == "yes" ]] || { echo "Aborted."; exit 1; }

echo "[restore] stopping api"
compose stop api

echo "[restore] recreating database $DB"
psql_admin -c "DROP DATABASE IF EXISTS \"$DB\" WITH (FORCE);"
psql_admin -c "CREATE DATABASE \"$DB\" OWNER \"$USER_NAME\";"

echo "[restore] loading dump"
gunzip -c "$BACKUP_FILE" | docker exec -i "$CONTAINER" psql -U "$USER_NAME" -d "$DB" -v ON_ERROR_STOP=1 -q

echo "[restore] starting api"
compose start api
echo "[restore] done. Check: docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod logs -f api"

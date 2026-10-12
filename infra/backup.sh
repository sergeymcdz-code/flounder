#!/usr/bin/env bash
# Dump the Flounder PostgreSQL database into backups/YYYY-MM-DD.sql.gz.
# Usage:  ./infra/backup.sh
# Keeps the last BACKUP_KEEP_DAYS files (default 14). Runs pg_dump inside the postgres container.
#
# Daily cron at 03:00 (crontab -e):
#   0 3 * * * /opt/flounder/infra/backup.sh >> /opt/flounder/backups/backup.log 2>&1
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$REPO_DIR/infra/.env.prod"
BACKUP_DIR="${BACKUP_DIR:-$REPO_DIR/backups}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"
CONTAINER="flounder-postgres"

# shellcheck disable=SC1090
set -a; source "$ENV_FILE"; set +a
DB="${POSTGRES_DB:-sport_platform}"
USER_NAME="${POSTGRES_USER:-sport_user}"

mkdir -p "$BACKUP_DIR"
STAMP="$(date +%F)"
FILE="$BACKUP_DIR/$STAMP.sql.gz"
# Several backups on the same day get a time suffix instead of overwriting.
[[ -e "$FILE" ]] && FILE="$BACKUP_DIR/${STAMP}_$(date +%H%M%S).sql.gz"

echo "[backup] $(date '+%F %T') dumping $DB -> $FILE"
docker exec "$CONTAINER" pg_dump -U "$USER_NAME" -d "$DB" --no-owner --no-privileges \
  | gzip -9 > "$FILE.tmp"
mv "$FILE.tmp" "$FILE"
echo "[backup] done, size $(du -h "$FILE" | cut -f1)"

# Rotate old backups.
find "$BACKUP_DIR" -name '*.sql.gz' -type f -mtime "+$KEEP_DAYS" -print -delete | sed 's/^/[backup] removed old: /' || true

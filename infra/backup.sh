#!/usr/bin/env bash
# Atomic PostgreSQL custom-format backup, optional files volume, 30-day retention.
set -Eeuo pipefail
# shellcheck source=infra/lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
log_start
KEEP_DAYS="${BACKUP_KEEP_DAYS:-30}"
[[ "$KEEP_DAYS" =~ ^[1-9][0-9]*$ ]] || exit 2
exec 9>"$BACKUP_DIR/.backup.lock"
flock -n 9 || { echo '[backup] already running'; exit 1; }
STAMP="$(TZ=Europe/Moscow date +%Y%m%d-%H%M)"
FILE="$BACKUP_DIR/flounder-$STAMP.dump"
FILES="$BACKUP_DIR/flounder-$STAMP.files.tar.gz"
cleanup() {
  local status=$?
  trap - EXIT
  rm -f "$FILE.tmp" "$FILES.tmp"
  if (( status != 0 )); then alert "Flounder: backup failed ($status), see backup.log"; fi
  exit "$status"
}
trap cleanup EXIT
[[ ! -e "$FILE" && ! -e "$FILES" ]] || { echo '[backup] timestamp already exists; keeping existing backup'; exit 0; }
printf '[backup] %s dumping %s\n' "$(date -Is)" "$DB"
docker exec "$CONTAINER" pg_dump -U "$USER_NAME" -d "$DB" -Fc --no-owner --no-privileges > "$FILE.tmp"
docker exec -i "$CONTAINER" pg_restore --list < "$FILE.tmp" >/dev/null
# M04 may add this volume. Inspection errors must not be treated as absence.
VOLUME="${FILES_VOLUME:-flounder_files}"
volumes="$(docker volume ls --format '{{.Name}}')"
if printf '%s\n' "$volumes" | grep -Fxq "$VOLUME"; then
  docker run --rm --network none --mount "type=volume,src=$VOLUME,dst=/files,readonly" \
    postgres:16 tar -czf - -C /files . > "$FILES.tmp"
  gzip -t "$FILES.tmp"
  mv "$FILES.tmp" "$FILES"
fi
mv "$FILE.tmp" "$FILE"
if [[ -n "${BACKUP_S3_REMOTE:-}" ]]; then
  rclone copyto "$FILE" "$BACKUP_S3_REMOTE/$(basename "$FILE")"
  if [[ -f "$FILES" ]]; then rclone copyto "$FILES" "$BACKUP_S3_REMOTE/$(basename "$FILES")"; fi
fi
find "$BACKUP_DIR" -maxdepth 1 -type f \( -name 'flounder-*.dump' -o -name 'flounder-*.files.tar.gz' -o -name '*.sql.gz' \) \
  -mmin "+$((KEEP_DAYS * 1440))" -print -delete
printf '[backup] %s complete: %s (%s bytes)\n' "$(date -Is)" "$FILE" "$(stat -c %s "$FILE")"

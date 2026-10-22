#!/usr/bin/env bash
# Atomic PostgreSQL custom-format backup, encrypted with an owner-held age identity.
set -Eeuo pipefail
# shellcheck source=infra/lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
log_start
KEEP_DAYS="${BACKUP_KEEP_DAYS:-30}"
[[ "$KEEP_DAYS" =~ ^[1-9][0-9]*$ ]] || exit 2
exec 9>"$BACKUP_DIR/.backup.lock"
flock -n 9 || { echo '[backup] Задание уже выполняется.'; exit 1; }
STAMP="$(TZ=Europe/Moscow date +%Y%m%d-%H%M)"
BASE_FILE="$BACKUP_DIR/flounder-$STAMP.dump"
FILE="$BASE_FILE"
BASE_FILES="$BACKUP_DIR/flounder-$STAMP.files.tar.gz"
FILES="$BASE_FILES"
cleanup() {
  local status=$?
  trap - EXIT
  rm -f "$BASE_FILE.tmp" "$BASE_FILES.tmp" "$BASE_FILE.age.tmp" "$BASE_FILES.age.tmp"
  if (( status != 0 )); then alert "Flounder: ошибка резервного копирования ($status), см. backup.log"; fi
  exit "$status"
}
trap cleanup EXIT
# Keep legacy local backups working, but never silently upload plaintext remotely.
if [[ -n "${BACKUP_AGE_RECIPIENT:-}" ]]; then
  command -v age >/dev/null || { echo '[backup] Установите age.'; exit 2; }
  # Validate the recipient before creating any plaintext temporary data.
  age -r "$BACKUP_AGE_RECIPIENT" </dev/null >/dev/null
else
  alert 'Flounder: BACKUP_AGE_RECIPIENT не задан; локальная копия не зашифрована, внешняя выгрузка запрещена.'
  [[ -z "${BACKUP_S3_REMOTE:-}" ]] || exit 2
fi
[[ ! -e "$FILE" && ! -e "$FILES" && ! -e "$FILE.age" && ! -e "$FILES.age" ]] || {
  echo '[backup] Копия за эту минуту уже существует; она сохранена.'; exit 0;
}
printf '[backup] %s: резервное копирование\n' "$(date -Is)"
docker exec "$CONTAINER" pg_dump -U "$USER_NAME" -d "$DB" -Fc --no-owner --no-privileges > "$FILE.tmp"
docker exec -i "$CONTAINER" pg_restore --list < "$FILE.tmp" >/dev/null
VOLUME="${FILES_VOLUME:-flounder_files}"
volumes="$(docker volume ls --format '{{.Name}}')"
if printf '%s\n' "$volumes" | grep -Fxq "$VOLUME"; then
  docker run --rm --network none --security-opt no-new-privileges:true \
    --mount "type=volume,src=$VOLUME,dst=/files,readonly" \
    postgres:16.14 tar -czf - -C /files . > "$FILES.tmp"
  gzip -t "$FILES.tmp"
fi
if [[ -n "${BACKUP_AGE_RECIPIENT:-}" ]]; then
  age -r "$BACKUP_AGE_RECIPIENT" -o "$FILE.age.tmp" "$FILE.tmp"
  if [[ -f "$FILES.tmp" ]]; then age -r "$BACKUP_AGE_RECIPIENT" -o "$FILES.age.tmp" "$FILES.tmp"; fi
  rm -f "$FILE.tmp" "$FILES.tmp"
  FILE="$FILE.age"
  FILES="$FILES.age"
fi
if [[ -f "$FILES.tmp" ]]; then mv "$FILES.tmp" "$FILES"; fi
mv "$FILE.tmp" "$FILE"
if [[ -n "${BACKUP_S3_REMOTE:-}" ]]; then
  # Avoid destination reads, bucket probes and post-upload HEAD with a write-only S3 credential.
  rclone copyto --no-check-dest --s3-no-check-bucket --s3-no-head "$FILE" "$BACKUP_S3_REMOTE/$(basename "$FILE")"
  if [[ -f "$FILES" ]]; then rclone copyto --no-check-dest --s3-no-check-bucket --s3-no-head "$FILES" "$BACKUP_S3_REMOTE/$(basename "$FILES")"; fi
fi
find "$BACKUP_DIR" -maxdepth 1 -type f \( -name 'flounder-*.dump' -o -name 'flounder-*.dump.age' \
  -o -name 'flounder-*.files.tar.gz' -o -name 'flounder-*.files.tar.gz.age' -o -name '*.sql.gz' \) \
  -mmin "+$((KEEP_DAYS * 1440))" -print -delete
printf '[backup] %s: готово (%s байт)\n' "$(date -Is)" "$(stat -c %s "$FILE")"

#!/usr/bin/env bash
# Verify a trusted backup in an isolated database. Never stop the production API.
set -Eeuo pipefail
# shellcheck source=infra/lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
log_start
[[ ( $# -eq 1 || $# -eq 2 ) && -r "$1" ]] || {
  echo 'Использование: restore.sh <backup.dump|backup.sql.gz|backup.dump.age> [файл-ключ-age]'; exit 2;
}
FILE="$(realpath "$1")"
TEST_DB=sport_platform_restore_test
[[ "$DB" != "$TEST_DB" ]] || { echo '[restore] Тестовая БД совпадает с исходной.'; exit 2; }
[[ "$USER_NAME" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || exit 2
exec 9>"$BACKUP_DIR/.restore.lock"
flock -n 9 || { echo '[restore] Проверка уже выполняется.'; exit 1; }
created=0
decrypted=''
cleanup() {
  local status=$?
  trap - EXIT
  if [[ -n "$decrypted" ]]; then rm -f "$decrypted"; fi
  if (( created )); then
    docker exec "$CONTAINER" dropdb -U "$USER_NAME" --force "$TEST_DB" || status=1
  fi
  if (( status == 0 )); then
    alert 'Flounder: проверка восстановления успешна; тестовая БД удалена'
  else
    alert "Flounder: ошибка проверки восстановления ($status), см. backup.log"
  fi
  exit "$status"
}
trap cleanup EXIT
if [[ "$FILE" == *.age ]]; then
  [[ $# -eq 2 && -r "$2" ]] || { echo '[restore] Укажите читаемый файл ключа age вторым аргументом.'; exit 2; }
  decrypted="$(mktemp "${TMPDIR:-/tmp}/flounder-restore-XXXXXX.dump")"
  age --decrypt -i "$2" "$FILE" > "$decrypted"
  FILE="$decrypted"
elif [[ $# -eq 2 ]]; then
  echo '[restore] Файл ключа допустим только для .age.'; exit 2
fi
case "$FILE" in
  *.dump) docker exec -i "$CONTAINER" pg_restore --list < "$FILE" >/dev/null ;;
  *.sql.gz) gzip -t "$FILE" ;;
  *) echo '[restore] Неподдерживаемый формат.'; exit 2 ;;
esac
# If the name already exists, fail without touching it; only delete our own DB.
docker exec "$CONTAINER" createdb -U "$USER_NAME" -T template0 -O "$USER_NAME" "$TEST_DB"
created=1
case "$FILE" in
  *.dump) docker exec -i "$CONTAINER" pg_restore -U "$USER_NAME" -d "$TEST_DB" \
    --no-owner --no-privileges --exit-on-error --single-transaction < "$FILE" ;;
  *.sql.gz) gzip -dc "$FILE" | docker exec -i "$CONTAINER" psql -X -U "$USER_NAME" -d "$TEST_DB" \
    -v ON_ERROR_STOP=1 --single-transaction -q ;;
esac
count="$(docker exec "$CONTAINER" psql -X -U "$USER_NAME" -d "$TEST_DB" -v ON_ERROR_STOP=1 -Atc 'select count(*) from events')"
[[ "$count" =~ ^[0-9]+$ ]] || exit 1
printf '[restore] %s OK: events=%s\n' "$(date -Is)" "$count"

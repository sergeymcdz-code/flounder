#!/usr/bin/env bash
# Explicit production rollback. Verification alone belongs in restore.sh.
set -Eeuo pipefail
# shellcheck source=infra/lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
log_start
[[ $# -eq 1 && -r "$1" ]] || { echo 'Usage: restore-production.sh <backup.dump|backup.sql.gz>'; exit 2; }
FILE="$(realpath "$1")"
case "$FILE" in *.dump|*.sql.gz) ;; *) echo '[restore-production] unsupported format'; exit 2 ;; esac
[[ "$DB" =~ ^[A-Za-z_][A-Za-z0-9_]*$ && "$USER_NAME" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || exit 2
case "$DB" in postgres|template0|template1|sport_platform_restore_test) echo '[restore-production] unsafe target DB'; exit 2 ;; esac
COMPOSE_FILE="${COMPOSE_FILE:-$INFRA_DIR/compose.prod.yaml}"
HEALTH_URL="${RESTORE_HEALTH_URL:-https://${DOMAIN:?DOMAIN is required}/actuator/health}"
compose() { docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"; }
exec 8>"$BACKUP_DIR/.restore-production.lock"
flock -n 8 || { echo '[restore-production] already running'; exit 1; }
printf 'Replace database %s in %s from %s\n' "$DB" "$CONTAINER" "$FILE"
read -r -p 'Type restore-production to continue: ' answer
[[ "$answer" == restore-production ]] || { echo '[restore-production] aborted'; exit 1; }
# Validate in the isolated DB before stopping production or touching its data.
"$INFRA_DIR/restore.sh" "$FILE"
stopped=0
drop_attempted=0
fresh_dir=''
cleanup() {
  local status=$?
  trap - EXIT
  if (( status != 0 )); then
    if (( stopped && ! drop_attempted )); then compose start api || true; fi
    if (( stopped && drop_attempted )); then
      echo '[restore-production] API remains stopped; inspect the error and restore the safety copy before restarting.'
    fi
    printf '[restore-production] FAILED; safety backup directory: %s\n' "${fresh_dir:-not created}"
    alert "Flounder: production restore failed ($status); see backup.log"
  fi
  exit "$status"
}
trap cleanup EXIT
compose stop api
stopped=1
# A unique directory guarantees a fresh backup even after a same-minute manual run.
# Keep the safety copy outside ordinary rotation until the operator accepts recovery.
fresh_dir="$(mktemp -d "$BACKUP_DIR/pre-restore-$(TZ=Europe/Moscow date +%Y%m%d-%H%M%S)-XXXXXX")"
# Configuration is already loaded/exported; do not source BACKUP_DIR from .env again.
ENV_FILE=/dev/null BACKUP_DIR="$fresh_dir" POSTGRES_CONTAINER="$CONTAINER" \
  POSTGRES_DB="$DB" POSTGRES_USER="$USER_NAME" "$INFRA_DIR/backup.sh"
safety_dumps=("$fresh_dir"/*.dump)
[[ ${#safety_dumps[@]} -eq 1 && -s "${safety_dumps[0]}" ]] || { echo '[restore-production] missing fresh safety dump'; exit 1; }
printf '[restore-production] fresh safety copy: %s\n' "$fresh_dir"
drop_attempted=1
docker exec "$CONTAINER" dropdb -U "$USER_NAME" --force "$DB"
docker exec "$CONTAINER" createdb -U "$USER_NAME" -T template0 -O "$USER_NAME" "$DB"
case "$FILE" in
  *.dump) docker exec -i "$CONTAINER" pg_restore -U "$USER_NAME" -d "$DB" \
    --no-owner --no-privileges --single-transaction --exit-on-error < "$FILE" ;;
  *.sql.gz) gzip -dc "$FILE" | docker exec -i "$CONTAINER" psql -X -U "$USER_NAME" \
    -d "$DB" -v ON_ERROR_STOP=1 --single-transaction ;;
esac
count="$(docker exec "$CONTAINER" psql -X -U "$USER_NAME" -d "$DB" -v ON_ERROR_STOP=1 -Atc 'select count(*) from events')"
[[ "$count" =~ ^[0-9]+$ ]] || exit 1
printf '[restore-production] events=%s\n' "$count"
compose start api
stopped=0
health="$(curl --fail --silent --show-error --retry 12 --retry-connrefused --retry-all-errors \
  --retry-delay 5 --max-time 10 --retry-max-time 120 "$HEALTH_URL")"
[[ "$health" =~ \"status\"[[:space:]]*:[[:space:]]*\"UP\" ]] || { echo '[restore-production] health is not UP'; exit 1; }
printf '[restore-production] complete; health UP; keep safety copy in %s until acceptance\n' "$fresh_dir"

#!/usr/bin/env bash
# Real local PostgreSQL round trip for both formats and failure cleanup.
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d)"
export POSTGRES_CONTAINER="m09-test-$RANDOM-$$" FILES_VOLUME="m09-files-$RANDOM-$$"
fixture_container="$POSTGRES_CONTAINER"
fixture_volume="$FILES_VOLUME"
cleanup() {
  docker rm -f "$fixture_container" >/dev/null 2>&1 || true
  docker volume rm "$fixture_volume" >/dev/null 2>&1 || true
  rm -rf "$TMP"
}
trap cleanup EXIT
mkdir -p "$TMP/bin" "$TMP/backups"
export ENV_FILE="$TMP/test.env" BACKUP_DIR="$TMP/backups" POSTGRES_DB=sport_platform POSTGRES_USER=sport_user
export TELEGRAM_BOT_TOKEN=123456:test-only-token TELEGRAM_CHAT_ID=123456
: > "$ENV_FILE"
REAL_DATE="$(command -v date)"
export REAL_DATE
cat > "$TMP/bin/date" <<'DATE'
#!/usr/bin/env bash
if [[ "$*" == +%Y%m%d-%H%M ]]; then printf '20301013-0300\n'; else exec "$REAL_DATE" "$@"; fi
DATE
chmod +x "$TMP/bin/date"
cat > "$TMP/bin/curl" <<'CURL'
#!/usr/bin/env bash
cat >> "${TEST_ALERTS:-/dev/null}"
printf '%s\n' "$*" >> "${TEST_ALERTS:-/dev/null}"
case "$*" in *'/actuator/health'*) printf '{"status":"UP"}';; esac
exit 0
CURL
chmod +x "$TMP/bin/curl"
export PATH="$TMP/bin:$PATH"
docker run -d --name "$POSTGRES_CONTAINER" -e POSTGRES_USER -e POSTGRES_DB \
  -e POSTGRES_PASSWORD=test-only-password postgres:16.14 >/dev/null
ready=0
for _ in {1..60}; do
  if docker exec "$POSTGRES_CONTAINER" pg_isready -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null 2>&1; then ready=1; break; fi
  sleep 1
done
(( ready ))
docker exec "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 \
  -c 'create table events(id integer primary key); insert into events values (1);' >/dev/null
docker volume create "$FILES_VOLUME" >/dev/null
docker run --rm --network none -v "$FILES_VOLUME:/files" postgres:16.14 sh -c 'echo document > /files/example.txt'
touch "$BACKUP_DIR/old.sql.gz" "$BACKUP_DIR/unrelated.txt"
touch -d '31 days ago' "$BACKUP_DIR/old.sql.gz" "$BACKUP_DIR/unrelated.txt"
"$REPO/infra/backup.sh"
# A same-minute repeated backup must not raise a false failure alert.
"$REPO/infra/backup.sh"
[[ ! -f "$BACKUP_DIR/old.sql.gz" && -f "$BACKUP_DIR/unrelated.txt" ]]
dumps=("$BACKUP_DIR"/*.dump)
archives=("$BACKUP_DIR"/*.files.tar.gz)
[[ ${#dumps[@]} -eq 1 ]]
tar -tzf "${archives[0]}" | grep -q example.txt
"$REPO/infra/restore.sh" "${dumps[0]}"
docker exec "$POSTGRES_CONTAINER" pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-privileges |
  gzip > "$TMP/legacy.sql.gz"
"$REPO/infra/restore.sh" "$TMP/legacy.sql.gz"
# A valid gzip with invalid SQL must fail and remove the isolated DB.
printf 'invalid SQL;\n' | gzip > "$TMP/broken.sql.gz"
if "$REPO/infra/restore.sh" "$TMP/broken.sql.gz"; then echo 'Expected restore failure'; exit 1; fi
count="$(docker exec "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d postgres -Atc "select count(*) from pg_database where datname='sport_platform_restore_test'")"
[[ "$count" == 0 ]]
# A pre-existing DB must remain untouched when createdb refuses the operation.
docker exec "$POSTGRES_CONTAINER" createdb -U "$POSTGRES_USER" sport_platform_restore_test
if "$REPO/infra/restore.sh" "${dumps[0]}"; then echo 'Expected existing DB refusal'; exit 1; fi
count="$(docker exec "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d postgres -Atc "select count(*) from pg_database where datname='sport_platform_restore_test'")"
[[ "$count" == 1 ]]
docker exec "$POSTGRES_CONTAINER" dropdb -U "$POSTGRES_USER" sport_platform_restore_test
# Production fixture survived every verification, including failures.
[[ "$(docker exec "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc 'select count(*) from events')" == 1 ]]
# Production rollback is exercised only against this disposable local fixture.
# Real DB operations remain real; compose start/stop and health are isolated mocks.
REAL_DOCKER="$(command -v docker)"
printf 'BACKUP_DIR=%q\n' "$BACKUP_DIR" >> "$ENV_FILE"
export REAL_DOCKER TEST_CALLS="$TMP/production-calls" DOMAIN=test.example.invalid
cat > "$TMP/bin/docker" <<'DOCKER'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$TEST_CALLS"
if [[ "$1" == compose ]]; then exit 0; fi
if [[ "${FAIL_SAFETY_BACKUP:-0}" == 1 && "$*" == *' pg_dump '* ]]; then exit 17; fi
if [[ "${FAIL_PROD_LOAD:-0}" == 1 && "$*" == *' pg_restore '* && "$*" == *' -d sport_platform '* ]]; then exit 18; fi
exec "$REAL_DOCKER" "$@"
DOCKER
chmod +x "$TMP/bin/docker"
: > "$TEST_CALLS"
if printf 'no\n' | "$REPO/infra/restore-production.sh" "${dumps[0]}"; then echo 'Expected confirmation refusal'; exit 1; fi
[[ ! -s "$TEST_CALLS" ]]
# A failed validation must not stop API or drop production.
if printf 'restore-production\n' | "$REPO/infra/restore-production.sh" "$TMP/broken.sql.gz"; then exit 1; fi
if grep -q '^compose ' "$TEST_CALLS"; then echo 'Validation failure stopped API'; exit 1; fi
# Prove the fresh safety copy is made even though this minute already has a dump.
docker exec "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c 'insert into events values (2)' >/dev/null
printf 'restore-production\n' | "$REPO/infra/restore-production.sh" "${dumps[0]}"
[[ "$(docker exec "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc 'select count(*) from events')" == 1 ]]
safety=("$BACKUP_DIR"/pre-restore-*/*.dump)
[[ ${#safety[@]} -eq 1 ]]
"$REPO/infra/restore.sh" "${safety[0]}" | grep -q 'events=2'
printf 'restore-production\n' | "$REPO/infra/restore-production.sh" "$TMP/legacy.sql.gz"
# A failed fresh backup must prevent DROP and restart the unchanged API.
: > "$TEST_CALLS"
export FAIL_SAFETY_BACKUP=1
if printf 'restore-production\n' | "$REPO/infra/restore-production.sh" "${dumps[0]}"; then exit 1; fi
unset FAIL_SAFETY_BACKUP
if grep -q 'dropdb.*sport_platform$' "$TEST_CALLS"; then echo 'Backup failure reached DROP'; exit 1; fi
grep -q '^compose .* start api$' "$TEST_CALLS"
# A production load failure after DROP must leave API stopped for manual recovery.
: > "$TEST_CALLS"
export FAIL_PROD_LOAD=1
if printf 'restore-production\n' | "$REPO/infra/restore-production.sh" "${dumps[0]}"; then exit 1; fi
unset FAIL_PROD_LOAD
if grep -q '^compose .* start api$' "$TEST_CALLS"; then echo 'Load failure restarted API'; exit 1; fi
# Restore the disposable fixture and prove the safety copy remains usable.
printf 'restore-production\n' | "$REPO/infra/restore-production.sh" "$TMP/legacy.sql.gz"
# Owner identities exist only inside this disposable test workspace.
export ENV_FILE=/dev/null BACKUP_DIR="$TMP/encrypted" TEST_ALERTS="$TMP/alerts"
mkdir -p "$BACKUP_DIR" "$TMP/plaintext"
age-keygen -o "$TMP/owner.key" 2>/dev/null
age-keygen -o "$TMP/wrong.key" 2>/dev/null
export BACKUP_AGE_RECIPIENT
BACKUP_AGE_RECIPIENT="$(age-keygen -y "$TMP/owner.key")"
"$REPO/infra/backup.sh"
"$REPO/infra/backup.sh"
encrypted=("$BACKUP_DIR"/*.dump.age)
[[ ${#encrypted[@]} -eq 1 && -s "${encrypted[0]}" ]]
[[ -z "$(find "$BACKUP_DIR" -name '*.tmp' -o -name '*.dump' -o -name '*.tar.gz')" ]]
[[ "$(stat -c %a "${encrypted[0]}")" == 600 ]]
archives=("$BACKUP_DIR"/*.files.tar.gz.age)
age -d -i "$TMP/owner.key" "${archives[0]}" | tar -tzf - | grep -q example.txt
TMPDIR="$TMP/plaintext" "$REPO/infra/restore.sh" "${encrypted[0]}" "$TMP/owner.key"
[[ -z "$(ls -A "$TMP/plaintext")" ]]
cp "${encrypted[0]}" "$TMP/renamed.age"
TMPDIR="$TMP/plaintext" "$REPO/infra/restore.sh" "$TMP/renamed.age" "$TMP/owner.key"
for key in "$TMP/wrong.key" "$TMP/missing.key"; do
  if TMPDIR="$TMP/plaintext" "$REPO/infra/restore.sh" "${encrypted[0]}" "$key"; then exit 1; fi
  [[ -z "$(ls -A "$TMP/plaintext")" ]]
done
if "$REPO/infra/restore.sh" "${encrypted[0]}"; then exit 1; fi
: > "$TEST_ALERTS"
"$REPO/infra/verify-latest-backup.sh"
grep -q 'владелец должен проверить' "$TEST_ALERTS"
# Confirm production rollback accepts an encrypted fresh safety copy.
mkdir "$TMP/encrypted-production"
printf 'restore-production\n' | BACKUP_DIR="$TMP/encrypted-production" "$REPO/infra/restore-production.sh" "$TMP/legacy.sql.gz"
safety_age=("$TMP/encrypted-production"/pre-restore-*/*.dump.age)
[[ ${#safety_age[@]} -eq 1 && -s "${safety_age[0]}" ]]
"$REPO/infra/restore.sh" "${safety_age[0]}" "$TMP/owner.key"
# Public recipient errors never leave a final or partial backup.
if BACKUP_AGE_RECIPIENT=invalid BACKUP_DIR="$TMP/bad-recipient" "$REPO/infra/backup.sh"; then exit 1; fi
[[ -z "$(find "$TMP/bad-recipient" -name '*.dump*')" ]]
# Outbound copies must always be encrypted, using a write-only remote mode.
cat > "$TMP/bin/rclone" <<'RCLONE'
#!/usr/bin/env bash
set -euo pipefail
[[ "$1" == copyto && "$2" == --no-check-dest && "$3" == --s3-no-check-bucket && "$4" == --s3-no-head && "$5" == *.age && "$6" == *.age ]]
printf '%s\n' "$*" >> "$TEST_RCLONE_LOG"
RCLONE
chmod +x "$TMP/bin/rclone"
export TEST_RCLONE_LOG="$TMP/rclone.log"
BACKUP_S3_REMOTE=test-only:bucket BACKUP_DIR="$TMP/remote" "$REPO/infra/backup.sh"
[[ "$(wc -l < "$TEST_RCLONE_LOG")" == 2 ]]
# Missing recipient warns locally; a configured remote refuses plaintext entirely.
unset BACKUP_AGE_RECIPIENT
: > "$TEST_ALERTS"
BACKUP_DIR="$TMP/plain-backup" "$REPO/infra/backup.sh"
grep -q 'BACKUP_AGE_RECIPIENT' "$TEST_ALERTS"
if BACKUP_S3_REMOTE=test-only:bucket BACKUP_DIR="$TMP/refused" "$REPO/infra/backup.sh"; then exit 1; fi
[[ -z "$(find "$TMP/refused" -name '*.dump*')" ]]
# Production fixture is still intact after encrypted verification failures.
[[ "$(docker exec "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc 'select count(*) from events')" == 1 ]]
# An unavailable container must produce no final or partial backup.
: > "$ENV_FILE"
export POSTGRES_CONTAINER=nonexistent-m09-test-container BACKUP_DIR="$TMP/failed"
if "$REPO/infra/backup.sh"; then echo 'Expected backup failure'; exit 1; fi
[[ -z "$(find "$BACKUP_DIR" -name '*.dump*' -print -quit)" ]]
echo 'Maintenance tests passed (custom dump, legacy SQL, files, rotation, failure cleanup, production isolation and confirmed rollback)'

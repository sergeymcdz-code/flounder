#!/usr/bin/env bash
# Real local PostgreSQL round trip for both formats and failure cleanup.
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d)"
export POSTGRES_CONTAINER="m09-test-$RANDOM-$$" FILES_VOLUME="m09-files-$RANDOM-$$"
cleanup() {
  docker rm -f "$POSTGRES_CONTAINER" >/dev/null 2>&1 || true
  docker volume rm "$FILES_VOLUME" >/dev/null 2>&1 || true
  rm -rf "$TMP"
}
trap cleanup EXIT
mkdir -p "$TMP/bin" "$TMP/backups"
export ENV_FILE="$TMP/test.env" BACKUP_DIR="$TMP/backups" POSTGRES_DB=sport_platform POSTGRES_USER=sport_user
export TELEGRAM_BOT_TOKEN=123456:test-only-token TELEGRAM_CHAT_ID=123456
: > "$ENV_FILE"
cat > "$TMP/bin/curl" <<'CURL'
#!/usr/bin/env bash
cat >/dev/null
exit 0
CURL
chmod +x "$TMP/bin/curl"
export PATH="$TMP/bin:$PATH"
docker run -d --name "$POSTGRES_CONTAINER" -e POSTGRES_USER -e POSTGRES_DB \
  -e POSTGRES_PASSWORD=test-only-password postgres:16 >/dev/null
ready=0
for _ in {1..60}; do
  if docker exec "$POSTGRES_CONTAINER" pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null 2>&1; then ready=1; break; fi
  sleep 1
done
(( ready ))
docker exec "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 \
  -c 'create table events(id integer primary key); insert into events values (1);' >/dev/null
docker volume create "$FILES_VOLUME" >/dev/null
docker run --rm --network none -v "$FILES_VOLUME:/files" postgres:16 sh -c 'echo document > /files/example.txt'
touch "$BACKUP_DIR/old.sql.gz" "$BACKUP_DIR/unrelated.txt"
touch -d '31 days ago' "$BACKUP_DIR/old.sql.gz" "$BACKUP_DIR/unrelated.txt"
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
# An unavailable container must produce no final or partial backup.
export POSTGRES_CONTAINER=nonexistent-m09-test-container BACKUP_DIR="$TMP/failed"
if "$REPO/infra/backup.sh"; then echo 'Expected backup failure'; exit 1; fi
[[ -z "$(find "$BACKUP_DIR" -name '*.dump*' -print -quit)" ]]
echo 'Maintenance tests passed (custom dump, legacy SQL, files, rotation, failure cleanup, production isolation)'

#!/usr/bin/env bash
# Validate interpolation using explicitly test-only values, without printing secrets.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
export DOMAIN=staging.sportgate.pro ACME_EMAIL=test@example.invalid
export POSTGRES_PASSWORD=test-only-postgres-password JWT_SECRET=test-only-jwt-secret-at-least-32-characters
export APP_FILES_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
for file in infra/compose.prod.yaml infra/compose.staging.yaml infra/compose.monitoring.yaml; do
  docker compose --env-file /dev/null -f "$file" config --quiet
done
# Verify the actual production environment and the volume name consumed by M09 backups.
env -u MAIL_SECURITY docker compose --env-file /dev/null -f infra/compose.prod.yaml config --format json | python3 -c '
import json, sys
config = json.load(sys.stdin)
assert config["services"]["api"]["environment"]["MAIL_SECURITY"] == "auto"
for name, memory in {"api": 1664, "postgres": 640, "web": 128, "caddy": 128}.items():
    service = config["services"][name]
    assert int(service["mem_limit"]) == memory * 1024 * 1024
    assert "no-new-privileges:true" in service["security_opt"]
assert config["services"]["postgres"]["image"] == "postgres:16.14"
assert config["services"]["caddy"]["image"] == "caddy:2.10.2"
assert config["services"]["caddy"]["environment"]["CSP_HEADER"] == "Content-Security-Policy-Report-Only"
assert config["services"]["api"]["environment"]["APP_FILES_ENCRYPTION_KEY"] == "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
assert config["volumes"]["flounder_files"]["name"] == "flounder_files"
assert any(v.get("source") == "flounder_files" and v.get("target") == "/data/files" for v in config["services"]["api"]["volumes"])
'
MAIL_SECURITY=ssl docker compose --env-file /dev/null -f infra/compose.prod.yaml config --format json | python3 -c '
import json, sys
assert json.load(sys.stdin)["services"]["api"]["environment"]["MAIL_SECURITY"] == "ssl"
'
error_file="$(mktemp)"
trap 'rm -f "$error_file"' EXIT
if env -u APP_FILES_ENCRYPTION_KEY docker compose --env-file /dev/null -f infra/compose.prod.yaml config --quiet >"$error_file" 2>&1; then
  echo 'Expected missing encryption key to reject production configuration' >&2
  exit 1
fi
grep -q 'APP_FILES_ENCRYPTION_KEY is required' "$error_file"

# Compose must preserve the literal bcrypt dollars from the protected env file.
cat > "$error_file" <<'ENV'
STAGING_BASIC_AUTH_HASH='$2a$14$test-only-hash'
ENV
# shellcheck disable=SC2016
docker compose --env-file "$error_file" -f infra/compose.prod.yaml config --format json | python3 -c '
import json, sys
value = json.load(sys.stdin)["services"]["caddy"]["environment"]["STAGING_BASIC_AUTH_HASH"]
# Compose versions may escape dollars in reusable config output.
assert value.replace("$$", "$") == "$2a$14$test-only-hash"
'

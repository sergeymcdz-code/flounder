#!/usr/bin/env bash
# Local-only edge for the unchanged QA05 suite and production preview.
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d)"
NAME="flounder-qa05-caddy-$$"
logs_pid=''
wait_pid=''
cleanup() {
  docker rm -f "$NAME" >/dev/null 2>&1 || true
  for child in "$logs_pid" "$wait_pid"; do
    if [[ -n "$child" ]]; then kill "$child" 2>/dev/null || true; wait "$child" 2>/dev/null || true; fi
  done
  rm -rf "$TMP"
}
trap cleanup EXIT
trap 'exit 130' INT TERM
python3 "$REPO/infra/tests/csp-test.py" "$REPO/sport-platform/frontend/dist/index.html"
# Production snippets and routing are retained; only local upstream addresses change.
sed 's/api:8080/127.0.0.1:8080/g; s/web:80/127.0.0.1:5174/g' "$REPO/infra/Caddyfile" > "$TMP/Caddyfile"
mkdir "$TMP/sites"
docker run -d --rm --name "$NAME" --network host --security-opt no-new-privileges:true \
  -e DOMAIN=http://localhost:5173 -e ACME_EMAIL=test@example.invalid \
  -e CSP_HEADER=Content-Security-Policy \
  -v "$TMP/Caddyfile:/etc/caddy/Caddyfile:ro" -v "$TMP/sites:/etc/caddy/sites:ro" caddy:2.10.2 >/dev/null
docker logs -f "$NAME" &
logs_pid=$!
docker wait "$NAME" &
wait_pid=$!
wait "$wait_pid"

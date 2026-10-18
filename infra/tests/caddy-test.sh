#!/usr/bin/env bash
# Exercise the real pinned edge with disposable local upstreams, never the VPS.
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d)"
NAME="flounder-caddy-test-$$"
upstream_pid=''
cleanup() {
  docker rm -f "$NAME" >/dev/null 2>&1 || true
  if [[ -n "$upstream_pid" ]]; then kill "$upstream_pid" 2>/dev/null || true; wait "$upstream_pid" 2>/dev/null || true; fi
  rm -rf "$TMP"
}
trap cleanup EXIT
python3 "$REPO/infra/tests/csp-test.py" "$@"
mkdir "$TMP/sites"
sed 's/api:8080/127.0.0.1:19281/g; s/web:80/127.0.0.1:19281/g' "$REPO/infra/Caddyfile" > "$TMP/Caddyfile"
sed 's/staging.sportgate.pro/http:\/\/127.0.0.1:19282/; s/staging-gateway:80/127.0.0.1:19281/' \
  "$REPO/infra/caddy-sites/staging.example" > "$TMP/sites/staging.caddy"
hash="$(docker run --rm --network none caddy:2.10.2 caddy hash-password --plaintext test-only-password)"
cat > "$TMP/upstream.cjs" <<'NODE'
require('node:http').createServer((req, res) => {
  req.on('data', () => {});
  req.on('end', () => { res.writeHead(200, { 'content-type': 'text/plain' }); res.end('ok'); });
  req.on('error', () => res.destroy());
}).listen(19281, '127.0.0.1');
NODE
# Enabling the optional site without its hash must fail configuration validation.
if docker run --rm --network none -e DOMAIN=http://127.0.0.1:19280 -e ACME_EMAIL=test@example.invalid \
  -v "$TMP/Caddyfile:/etc/caddy/Caddyfile:ro" -v "$TMP/sites:/etc/caddy/sites:ro" \
  caddy:2.10.2 caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile > "$TMP/no-hash.log" 2>&1; then
  echo 'Staging без хеша не должен проходить validate.'; exit 1
fi
node "$TMP/upstream.cjs" &
upstream_pid=$!
for mode in Content-Security-Policy-Report-Only Content-Security-Policy; do
  docker run -d --name "$NAME" --network host --security-opt no-new-privileges:true \
    -e DOMAIN=http://127.0.0.1:19280 -e ACME_EMAIL=test@example.invalid \
    -e "CSP_HEADER=$mode" -e STAGING_BASIC_AUTH_USER=test-only-user -e "STAGING_BASIC_AUTH_HASH=$hash" \
    -v "$TMP/Caddyfile:/etc/caddy/Caddyfile:ro" -v "$TMP/sites:/etc/caddy/sites:ro" \
    caddy:2.10.2 >/dev/null
  for _ in {1..30}; do
    if curl --noproxy '*' -fsS http://127.0.0.1:19280/ >/dev/null 2>&1; then break; fi
    sleep 1
  done
  CSP_TEST_MODE="$mode" python3 - <<'PY'
import base64
import os
import urllib.error
import urllib.request
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
auth = 'Basic ' + base64.b64encode(b'test-only-user:test-only-password').decode()
def request(port, path='/', size=None, authenticated=False):
    headers = {'Authorization': auth} if authenticated else {}
    req = urllib.request.Request(f'http://127.0.0.1:{port}{path}', data=b'x' * size if size is not None else None, headers=headers)
    try: return opener.open(req)
    except urllib.error.HTTPError as error: return error
mode = os.environ['CSP_TEST_MODE']
for port in (19280, 19282):
    response = request(port, authenticated=True)
    assert response.status == 200
    assert "object-src 'none'" in response.headers[mode]
    for header in ('Strict-Transport-Security', 'X-Content-Type-Options', 'X-Frame-Options', 'Referrer-Policy', 'Permissions-Policy'):
        assert response.headers[header], header
    assert response.headers.get('Server') is None
    assert request(port, '/api/v1/documents', 12_000_000, True).status == 200
    assert request(port, '/api/v1/documents', 12_000_001, True).status == 413
    assert request(port, '/api/v1/payments/webhooks/test', 16_384, True).status == 200
    assert request(port, '/api/v1/payments/webhooks/test', 16_385, True).status == 413
assert request(19282).status == 401
assert request(19282, '/actuator/health').status == 401
print(f'Caddy: заголовки, staging auth и лимиты тела проверены ({mode})')
PY
  docker rm -f "$NAME" >/dev/null
done

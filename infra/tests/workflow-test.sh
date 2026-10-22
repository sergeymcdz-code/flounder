#!/usr/bin/env bash
# Execute the workflow scripts locally with trusted test keys and a mocked SSH.
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
export TEST_SSH_DIR="$TMP/ssh" TEST_SSH_LOG="$TMP/ssh.log"
mkdir -p "$TEST_SSH_DIR" "$TMP/bin"
python3 - "$REPO" "$TMP" <<'PY'
from pathlib import Path
import sys
import yaml
root, tmp = map(Path, sys.argv[1:])
workflow = yaml.safe_load((root / '.github/workflows/deploy.yml').read_text())
assert workflow['permissions'] == {'contents': 'read'}
job = workflow['jobs']['deploy']
assert job['environment'] == 'production'
steps = job['steps']
for step, name in zip(steps[:2], ('prepare', 'deploy')):
    script = step['run']
    assert '${{' not in script
    assert 'ssh-keyscan' not in script
    (tmp / f'{name}.sh').write_text(script.replace('~/.ssh', '"$TEST_SSH_DIR"'))
assert steps[1]['env']['APP_DIR'] == '${{ inputs.app_dir }}'
assert steps[1]['env']['BRANCH'] == '${{ inputs.branch }}'
PY
ssh-keygen -q -t ed25519 -N '' -f "$TMP/test-key"
export SSH_HOST=test.example.invalid SSH_USER=testuser APP_DIR=/home/testuser/flounder BRANCH=main
export SSH_HOST_KEY SSH_HOST_KEY_FINGERPRINT SSH_KEY
SSH_HOST_KEY="$(cut -d ' ' -f 1-2 "$TMP/test-key.pub")"
SSH_HOST_KEY_FINGERPRINT="$(ssh-keygen -lf "$TMP/test-key.pub" -E sha256 | awk '{print $2}')"
SSH_KEY="$(cat "$TMP/test-key")"
bash "$TMP/prepare.sh"
[[ "$(stat -c %a "$TEST_SSH_DIR/deploy_key")" == 600 ]]
grep -Fxq "$SSH_HOST $SSH_HOST_KEY" "$TEST_SSH_DIR/known_hosts"
if SSH_HOST_KEY_FINGERPRINT=SHA256:wrong bash "$TMP/prepare.sh"; then exit 1; fi
if SSH_HOST_KEY='' bash "$TMP/prepare.sh"; then exit 1; fi
cat > "$TMP/bin/ssh" <<'SSH'
#!/usr/bin/env bash
printf '%s\n' "$@" >> "$TEST_SSH_LOG"
SSH
chmod +x "$TMP/bin/ssh"
export PATH="$TMP/bin:$PATH"
bash "$TMP/deploy.sh"
grep -qx 'StrictHostKeyChecking=yes' "$TEST_SSH_LOG"
grep -qx 'GlobalKnownHostsFile=/dev/null' "$TEST_SSH_LOG"
grep -qx "cd '$APP_DIR' && DEPLOY_BRANCH='main' ./infra/deploy.sh" "$TEST_SSH_LOG"
rm "$TEST_SSH_LOG"
for variable in BRANCH APP_DIR SSH_USER SSH_HOST; do
  if env "$variable=invalid value" bash "$TMP/deploy.sh"; then exit 1; fi
  [[ ! -e "$TEST_SSH_LOG" ]]
done
echo 'Проверки workflow пройдены: окружение, входные параметры, доверенный ключ и отпечаток.'

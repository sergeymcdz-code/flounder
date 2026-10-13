#!/usr/bin/env bash
# No host changes: map the fixed deploy key path into a fixture and mock sudo.
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
export TEST_KEYS="$TMP/authorized_keys" TEST_CONFIG="$TMP/sshd.conf" TEST_SUDO_LOG="$TMP/sudo.log"
sed "s|/home/deploy/.ssh/authorized_keys|$TEST_KEYS|g" "$REPO/infra/harden.sh" > "$TMP/harden.sh"
mkdir -p "$TMP/bin"
cat > "$TMP/bin/id" <<'ID'
#!/usr/bin/env bash
printf 'deploy\n'
ID
cat > "$TMP/bin/sudo" <<'SUDO'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$TEST_SUDO_LOG"
args=("$@")
for i in "${!args[@]}"; do
  if [[ "${args[i]}" == /etc/ssh/sshd_config.d/00-flounder-hardening.conf ]]; then args[i]="$TEST_CONFIG"; fi
done
case "$1" in
  tee) if [[ "${args[1]}" == "$TEST_CONFIG" ]]; then tee "$TEST_CONFIG"; else cat; fi ;;
  test|cat|cp|rm) "${args[@]}" ;;
  /usr/sbin/sshd)
    if [[ "$2" == -t && "${FAIL_SSHD:-0}" == 1 ]]; then exit 1; fi
    if [[ "$2" == -T ]]; then printf 'permitrootlogin prohibit-password\n'; fi ;;
  apt-get|install|systemctl) exit 0 ;;
  *) echo 'Unexpected sudo call'; exit 1 ;;
esac
SUDO
chmod +x "$TMP/bin/"*
export PATH="$TMP/bin:$PATH"
unset SSH_CONNECTION
# Missing deploy keys refuse the SSH policy change, including under su - deploy.
if bash "$TMP/harden.sh" </dev/null > "$TMP/output"; then exit 1; fi
grep -q 'Add a deploy public key' "$TMP/output"
[[ ! -e "$TEST_CONFIG" ]]
printf 'ssh-ed25519 test-only-key\n' > "$TEST_KEYS"
if printf 'no\n' | bash "$TMP/harden.sh" > "$TMP/output"; then exit 1; fi
[[ ! -e "$TEST_CONFIG" ]]
printf 'deploy-key-verified\n' | bash "$TMP/harden.sh" > "$TMP/output"
grep -qx 'PermitRootLogin prohibit-password' "$TEST_CONFIG"
grep -q 'addr=127.0.0.1' "$TEST_SUDO_LOG"
# Repeat is idempotent; failed validation rolls back the existing file.
printf 'deploy-key-verified\n' | bash "$TMP/harden.sh" > "$TMP/output"
printf 'previous configuration\n' > "$TEST_CONFIG"
export FAIL_SSHD=1
if printf 'deploy-key-verified\n' | bash "$TMP/harden.sh" > "$TMP/output"; then exit 1; fi
grep -qx 'previous configuration' "$TEST_CONFIG"
echo 'Hardening tests passed (su session, key guard, confirmation, root password policy, rollback)'

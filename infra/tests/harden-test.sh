#!/usr/bin/env bash
# Never touches the host: fixed paths and privileged commands map to fixtures.
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
export TEST_KEYS="$TMP/authorized_keys" TEST_CONFIG="$TMP/sshd.conf" TEST_SUDO_LOG="$TMP/sudo.log"
export TEST_JAIL="$TMP/jail.conf"
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
  case "${args[i]}" in
    /etc/ssh/sshd_config.d/00-flounder-hardening.conf) args[i]="$TEST_CONFIG" ;;
    /etc/fail2ban/jail.d/flounder.conf) args[i]="$TEST_JAIL" ;;
  esac
done
case "$1" in
  -n) [[ "$*" == '-n true' && "${FAIL_SUDO:-0}" != 1 ]] ;;
  tee) if [[ "${args[1]}" == "$TEST_CONFIG" || "${args[1]}" == "$TEST_JAIL" ]]; then tee "${args[1]}"; else cat; fi ;;
  test|cat|cp|rm) "${args[@]}" ;;
  /usr/sbin/sshd)
    if [[ "$2" == -t && "${FAIL_SSHD:-0}" == 1 ]]; then exit 1; fi
    if [[ "$2" == -T ]]; then
      tr '[:upper:]' '[:lower:]' < "$TEST_CONFIG"
      if [[ "${FAIL_POLICY:-0}" == 1 ]]; then exit 1; fi
    fi ;;
  systemctl)
    if [[ "$*" == 'systemctl restart ssh' && "${FAIL_RESTART:-0}" == 1 ]]; then exit 1; fi ;;
  apt-get|install|fail2ban-client|ufw) exit 0 ;;
  *) echo 'Неожиданный вызов sudo'; exit 1 ;;
esac
SUDO
chmod +x "$TMP/bin/"*
export PATH="$TMP/bin:$PATH"
unset SSH_CONNECTION
# Missing/empty/comment-only files refuse even the sudo preflight.
for keys in missing empty comments; do
  case "$keys" in
    empty) : > "$TEST_KEYS" ;;
    comments) printf '# ssh-ed25519 test-only-comment\n' > "$TEST_KEYS" ;;
  esac
  if bash "$TMP/harden.sh" </dev/null > "$TMP/output"; then exit 1; fi
  grep -q 'Добавьте ключ' "$TMP/output"
  [[ ! -e "$TEST_CONFIG" && ! -e "$TEST_SUDO_LOG" ]]
done
printf 'ssh-ed25519 test-only-key\n' > "$TEST_KEYS"
# Missing noninteractive sudo access refuses all host mutations.
if printf 'deploy-key-verified\n' | FAIL_SUDO=1 bash "$TMP/harden.sh" > "$TMP/output" 2>&1; then exit 1; fi
grep -q 'требуется sudo' "$TMP/output"
[[ "$(cat "$TEST_SUDO_LOG")" == '-n true' && ! -e "$TEST_CONFIG" ]]
: > "$TEST_SUDO_LOG"
if printf 'no\n' | bash "$TMP/harden.sh" > "$TMP/output"; then exit 1; fi
[[ ! -e "$TEST_CONFIG" && "$(cat "$TEST_SUDO_LOG")" == '-n true' ]]
for _ in 1 2; do
  : > "$TEST_SUDO_LOG"
  printf 'deploy-key-verified\ndeploy-key-verified\n' | bash "$TMP/harden.sh" > "$TMP/output"
  [[ "$(head -1 "$TEST_SUDO_LOG")" == '-n true' ]]
  diff -u <(printf 'PasswordAuthentication no\nKbdInteractiveAuthentication no\nPermitRootLogin no\n') "$TEST_CONFIG"
  diff -u <(printf '[sshd]\nenabled = true\nbackend = systemd\nmaxretry = 5\nfindtime = 10m\nbantime = 1h\n') "$TEST_JAIL"
  grep -q 'user=deploy,host=localhost,addr=127.0.0.1' "$TEST_SUDO_LOG"
  grep -q 'user=root,host=localhost,addr=127.0.0.1' "$TEST_SUDO_LOG"
  [[ "$(tail -1 "$TEST_SUDO_LOG")" == 'ufw --force enable' ]]
  for port in 22 80 443; do grep -qx "ufw allow $port/tcp" "$TEST_SUDO_LOG"; done
  grep -qx 'ufw default deny incoming' "$TEST_SUDO_LOG"
  grep -q 'Проверьте вход по ключу во втором окне' "$TMP/output"
done
# Declining the post-restart check never enables the firewall.
: > "$TEST_SUDO_LOG"
if printf 'deploy-key-verified\nno\n' | bash "$TMP/harden.sh" > "$TMP/output"; then exit 1; fi
if grep -q '^ufw ' "$TEST_SUDO_LOG"; then exit 1; fi
# Validation, effective policy and restart errors restore the previous fragment.
for failure in FAIL_SSHD FAIL_POLICY FAIL_RESTART; do
  printf 'previous configuration\n' > "$TEST_CONFIG"
  : > "$TEST_SUDO_LOG"
  if printf 'deploy-key-verified\n' | env "$failure=1" bash "$TMP/harden.sh" > "$TMP/output" 2>&1; then exit 1; fi
  grep -qx 'previous configuration' "$TEST_CONFIG"
  if grep -q '^ufw ' "$TEST_SUDO_LOG"; then exit 1; fi
done
rm "$TEST_CONFIG"
if printf 'deploy-key-verified\n' | FAIL_SSHD=1 bash "$TMP/harden.sh" > "$TMP/output" 2>&1; then exit 1; fi
[[ ! -e "$TEST_CONFIG" ]]
echo 'Проверки harden.sh пройдены: ключ, подтверждения, политика, идемпотентность, UFW, откат.'

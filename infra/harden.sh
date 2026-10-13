#!/usr/bin/env bash
# Optional Ubuntu hardening. Run as deploy; never run automatically on deployment.
set -euo pipefail
[[ "$(id -un)" == deploy && -n "${SSH_CONNECTION:-}" ]] || {
  echo 'Run from an SSH session as deploy after a separate public-key login test.'; exit 1;
}
sudo apt-get update
sudo apt-get install -y fail2ban unattended-upgrades
sudo install -d -m 0755 /etc/fail2ban/jail.d
printf '[sshd]\nenabled = true\nbackend = systemd\n' | sudo tee /etc/fail2ban/jail.d/flounder.conf >/dev/null
printf 'APT::Periodic::Update-Package-Lists "1";\nAPT::Periodic::Unattended-Upgrade "1";\n' |
  sudo tee /etc/apt/apt.conf.d/20auto-upgrades >/dev/null
sudo systemctl enable --now fail2ban unattended-upgrades
# Packages above can be rerun; SSH policy needs explicit operator confirmation.
echo 'Keep this session open. In a second terminal test: ssh -o PasswordAuthentication=no -o KbdInteractiveAuthentication=no deploy@SERVER_IP'
read -r -p 'After successful key login, type deploy-key-verified to disable root login: ' answer
[[ "$answer" == deploy-key-verified ]] || { echo 'SSH policy unchanged'; exit 1; }
target=/etc/ssh/sshd_config.d/00-flounder-hardening.conf
backup="$(mktemp)"
existed=0
if sudo test -f "$target"; then sudo cat "$target" | tee "$backup" >/dev/null; existed=1; fi
rollback() {
  if (( existed )); then sudo cp "$backup" "$target"; else sudo rm -f "$target"; fi
}
# Ubuntu uses first-value-wins; 00 sorts before cloud-init's fragments.
printf 'PermitRootLogin no\n' | sudo tee "$target" >/dev/null
if ! sudo /usr/sbin/sshd -t || ! sudo /usr/sbin/sshd -T -C "user=root,host=localhost,addr=${SSH_CONNECTION%% *}" | grep -qx 'permitrootlogin no'; then
  rollback; rm -f "$backup"; echo 'SSH validation failed; configuration rolled back'; exit 1
fi
if ! sudo systemctl reload ssh; then rollback; rm -f "$backup"; exit 1; fi
rm -f "$backup"
echo 'Root SSH login disabled. Test a new deploy login before closing this session.'

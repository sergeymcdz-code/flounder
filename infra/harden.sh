#!/usr/bin/env bash
# Optional Ubuntu hardening. Run as deploy; never run automatically on deployment.
set -euo pipefail
[[ "$(id -un)" == deploy ]] || {
  echo 'Run as deploy (SSH or su - deploy) after a separate public-key login test.'; exit 1;
}
sudo apt-get update
sudo apt-get install -y fail2ban unattended-upgrades
sudo install -d -m 0755 /etc/fail2ban/jail.d
printf '[sshd]\nenabled = true\nbackend = systemd\n' | sudo tee /etc/fail2ban/jail.d/flounder.conf >/dev/null
printf 'APT::Periodic::Update-Package-Lists "1";\nAPT::Periodic::Unattended-Upgrade "1";\n' |
  sudo tee /etc/apt/apt.conf.d/20auto-upgrades >/dev/null
sudo systemctl enable --now fail2ban unattended-upgrades
# Packages above can be rerun; SSH policy needs explicit operator confirmation.
[[ -s /home/deploy/.ssh/authorized_keys ]] || {
  echo 'Add a deploy public key to /home/deploy/.ssh/authorized_keys and verify a separate deploy key login first.'; exit 1;
}
client_addr="${SSH_CONNECTION:-127.0.0.1}"
client_addr="${client_addr%% *}"
echo 'Keep this session open. In a second terminal test: ssh -o PasswordAuthentication=no -o KbdInteractiveAuthentication=no deploy@SERVER_IP'
read -r -p 'After successful key login, type deploy-key-verified to disable root password login: ' answer
[[ "$answer" == deploy-key-verified ]] || { echo 'SSH policy unchanged'; exit 1; }
target=/etc/ssh/sshd_config.d/00-flounder-hardening.conf
backup="$(mktemp)"
existed=0
if sudo test -f "$target"; then sudo cat "$target" | tee "$backup" >/dev/null; existed=1; fi
rollback() {
  if (( existed )); then sudo cp "$backup" "$target"; else sudo rm -f "$target"; fi
}
# Ubuntu uses first-value-wins; 00 sorts before cloud-init's fragments.
printf 'PermitRootLogin prohibit-password\n' | sudo tee "$target" >/dev/null
if ! sudo /usr/sbin/sshd -t || ! sudo /usr/sbin/sshd -T -C "user=root,host=localhost,addr=$client_addr" | grep -qx 'permitrootlogin prohibit-password'; then
  rollback; rm -f "$backup"; echo 'SSH validation failed; configuration rolled back'; exit 1
fi
if ! sudo systemctl reload ssh; then rollback; rm -f "$backup"; exit 1; fi
rm -f "$backup"
echo 'Root SSH password login disabled; root key login remains enabled. Test a new deploy login before closing this session.'

#!/usr/bin/env bash
# Optional Ubuntu hardening. Run as deploy; never run automatically on deployment.
set -euo pipefail
[[ "$(id -un)" == deploy ]] || {
  echo 'Запустите от пользователя deploy: su - deploy.'; exit 1;
}
# Refuse all host changes until the fallback account has a public key.
grep -Eq '^(ssh-(ed25519|rsa)|ecdsa-sha2-|sk-)' /home/deploy/.ssh/authorized_keys 2>/dev/null || {
  echo 'Добавьте ключ в /home/deploy/.ssh/authorized_keys и проверьте отдельный вход deploy по ключу.'; exit 1;
}
sudo -n true || {
  echo 'Для deploy требуется sudo без запроса пароля. Настройки не изменены.'; exit 1;
}
echo 'Сохраните эту сессию и доступ к консоли провайдера. Проверьте вход по ключу во втором окне.'
echo 'ssh -o PasswordAuthentication=no -o KbdInteractiveAuthentication=no deploy@SERVER_IP'
read -r -p 'После успешного входа и sudo -v введите deploy-key-verified: ' answer
[[ "$answer" == deploy-key-verified ]] || { echo 'Настройки не изменены.'; exit 1; }

sudo apt-get update
sudo apt-get install -y fail2ban unattended-upgrades ufw
sudo install -d -m 0755 /etc/fail2ban/jail.d /etc/ssh/sshd_config.d
printf '[sshd]\nenabled = true\nbackend = systemd\nmaxretry = 5\nfindtime = 10m\nbantime = 1h\n' |
  sudo tee /etc/fail2ban/jail.d/flounder.conf >/dev/null
printf 'APT::Periodic::Update-Package-Lists "1";\nAPT::Periodic::Unattended-Upgrade "1";\n' |
  sudo tee /etc/apt/apt.conf.d/20auto-upgrades >/dev/null
sudo fail2ban-client -t
sudo systemctl enable --now fail2ban unattended-upgrades
sudo systemctl restart fail2ban

client_addr="${SSH_CONNECTION:-127.0.0.1}"
client_addr="${client_addr%% *}"
target=/etc/ssh/sshd_config.d/00-flounder-hardening.conf
backup="$(mktemp)"
existed=0
changed=0
if sudo test -f "$target"; then sudo cat "$target" | tee "$backup" >/dev/null; existed=1; fi
cleanup() {
  local status=$?
  trap - EXIT
  if (( changed )); then
    if (( existed )); then sudo cp "$backup" "$target"; else sudo rm -f "$target"; fi
    if sudo /usr/sbin/sshd -t; then sudo systemctl restart ssh || true; fi
    echo 'Проверка SSH не пройдена; предыдущий drop-in восстановлен. Проверьте службу через консоль.' >&2
  fi
  rm -f "$backup"
  exit "$status"
}
trap cleanup EXIT
# Ubuntu uses first-value-wins; 00 sorts before cloud-init's fragments.
changed=1
printf 'PasswordAuthentication no\nKbdInteractiveAuthentication no\nPermitRootLogin no\n' |
  sudo tee "$target" >/dev/null
sudo /usr/sbin/sshd -t
for user in deploy root; do
  policy="$(sudo /usr/sbin/sshd -T -C "user=$user,host=localhost,addr=$client_addr")"
  for expected in 'passwordauthentication no' 'kbdinteractiveauthentication no' 'permitrootlogin no'; do
    grep -qx "$expected" <<< "$policy"
  done
done
sudo systemctl restart ssh
changed=0
rm -f "$backup"
trap - EXIT

echo 'Проверьте вход по ключу во втором окне. Root SSH отключён. Не закрывайте текущую сессию.'
read -r -p 'После нового входа deploy и sudo -v введите deploy-key-verified для включения UFW: ' answer
[[ "$answer" == deploy-key-verified ]] || { echo 'SSH настроен; UFW не изменён.'; exit 1; }
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw default deny incoming
# Must be the final host mutation, after a new key login following the SSH restart.
sudo ufw --force enable

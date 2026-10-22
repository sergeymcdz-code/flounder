# Обслуживание инфраструктуры SportGate

Команды выполняет владелец в окно обслуживания. Историю Git не переписывать.
Серверные IP, учётные записи и имена копий ниже — заменяемые обозначения.
`<admin-user>` — административная учётная запись для входа до hardening, например root.
`<ssh-user>` — непривилегированный владелец `~/flounder`, ожидаемый `infra/harden.sh`.
Это разные роли: до hardening входите администратором, затем выполняйте `su - <ssh-user>`.
После hardening входите напрямую через `ssh <ssh-user>@SERVER_IP`: root SSH отключён.
Проверяйте результат каждого блока перед следующим; при ошибке остановитесь.

## Подготовка и сохранение состояния

Откройте консоль провайдера и оставьте её доступной до окончания проверки.
В первой SSH-сессии до hardening:

```bash
ssh root@SERVER_IP
install -d -m 0700 /root/flounder-hardening-before
cp -a /etc/ssh /root/flounder-hardening-before/ssh
cp -a /etc/fail2ban /root/flounder-hardening-before/fail2ban
if test -d /etc/ufw; then cp -a /etc/ufw /root/flounder-hardening-before/ufw; fi
if command -v ufw >/dev/null; then
  ufw status verbose > /root/flounder-hardening-before/ufw-status.txt
else
  printf 'Status: not installed\n' > /root/flounder-hardening-before/ufw-status.txt
fi
su - <ssh-user>
cd ~/flounder
git status --short
git rev-parse HEAD > ~/flounder-before-hardening.commit
mkdir -p ~/.config/flounder/before-hardening
chmod 700 ~/.config/flounder/before-hardening
cp infra/.env.prod ~/.config/flounder/before-hardening/env.prod
printf 'services:\n' > ~/.config/flounder/before-hardening/images.rollback.yaml
for service in api web caddy postgres; do
  rollback_tag="flounder-rollback-$service:before-hardening"
  docker image tag "$(docker inspect -f '{{.Image}}' "flounder-$service")" "$rollback_tag"
  printf '  %s:\n    image: %s\n' "$service" "$rollback_tag" >> ~/.config/flounder/before-hardening/images.rollback.yaml
done
chmod 600 ~/.config/flounder/before-hardening/env.prod
docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod ps
docker exec flounder-postgres postgres --version
```

Проверка: checkout чистый, службы работают, commit сохранён, копии доступны только владельцу.
Сверьте вывод `postgres --version`: если сервер уже работает на патче PostgreSQL 16
новее 16.14, после получения изменений закрепите фактически установленную версию
в `infra/compose.prod.yaml` вместо 16.14 до первого `up -d`; не понижайте её. Изменение закреплённого образа
проверьте через Compose config перед применением.
Существующие локальные изменения сначала разберите вручную. Ничего не публикуйте из env.
Не запускайте `down -v`. Перед изменением образа PostgreSQL сохраните проверенный дамп по
разделу бэкапов. Установленный вручную fail2ban сохраняется в управляемой конфигурации скрипта.

Получите изменения после слияния PR (это ещё не перезапуск контейнеров):

```bash
ssh <admin-user>@SERVER_IP
su - <ssh-user>
cd ~/flounder
git pull --ff-only origin main
git log -1 --oneline
docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod config --quiet
```

Проверка: ожидаемый commit, config завершается с кодом 0. Новых миграций и API нет.
При отказе config проверьте обязательные переменные, не выводите полный config в общий чат.

## SSH и firewall

На ноутбуке **во втором окне**, до запуска скрипта:

```bash
ssh -o PasswordAuthentication=no -o KbdInteractiveAuthentication=no <ssh-user>@SERVER_IP
whoami
sudo -v
```

Проверка: вошли именно непривилегированным пользователем; sudo доступен.
В первой сессии, оставляя вторую открытой:

```bash
ssh <admin-user>@SERVER_IP
su - <ssh-user>
cd ~/flounder
grep -Eq '^(ssh-(ed25519|rsa)|ecdsa-sha2-|sk-)' ~/.ssh/authorized_keys
sudo -n true
./infra/harden.sh
```

Скрипт дважды запрашивает `deploy-key-verified`. Первый раз — после проверки ключа и sudo.
После перезапуска SSH **заново откройте** вход во втором окне теми же командами.
Только после успеха подтвердите второй раз: затем последним действием включается UFW.
Если второй вход не работает, не подтверждайте UFW и используйте исходную сессию/консоль.

Проверка из нового входа по ключу:

```bash
ssh -o PasswordAuthentication=no -o KbdInteractiveAuthentication=no <ssh-user>@SERVER_IP
cd ~/flounder
sudo /usr/sbin/sshd -t
sudo /usr/sbin/sshd -T -C user="$(id -un)",host=localhost,addr=127.0.0.1 | grep -E '^(passwordauthentication|kbdinteractiveauthentication|permitrootlogin) '
sudo systemctl is-active ssh fail2ban unattended-upgrades
sudo fail2ban-client status sshd
sudo ufw status verbose
curl -fsS https://sportgate.pro/actuator/health
```

Ожидается три значения `no`, службы `active`, jail sshd активен, UFW активен с разрешёнными
TCP 22/80/443 и default deny incoming, health содержит `UP`.
Проверка `addr=127.0.0.1` полезна для `su -`; при наличии Match-блоков дополнительно
проверьте `sshd -T -C` с адресом реального клиента. UDP 443 необязателен: HTTP/3 может
не работать, HTTPS по TCP работает. UFW не фильтрует опубликованные Docker-порты:
не добавляйте внешних портов БД/API; gateway/Kuma доступны только на loopback.

Откат SSH при ошибке валидации/рестарта скрипт делает автоматически. Для полного отката
через **консоль провайдера** (не рассчитывайте на новый root SSH):

```bash
if ! test -f /root/flounder-hardening-before/ssh/sshd_config.d/00-flounder-hardening.conf; then
  rm -f /etc/ssh/sshd_config.d/00-flounder-hardening.conf
fi
cp -a /root/flounder-hardening-before/ssh/. /etc/ssh/
/usr/sbin/sshd -t
systemctl restart ssh
cp -a /root/flounder-hardening-before/fail2ban/. /etc/fail2ban/
fail2ban-client -t
systemctl restart fail2ban
ufw disable
if test -d /root/flounder-hardening-before/ufw; then
  cp -a /root/flounder-hardening-before/ufw/. /etc/ufw/
fi
if grep -q '^Status: active' /root/flounder-hardening-before/ufw-status.txt; then
  ufw --force enable
fi
ufw status verbose
```

Проверьте новый вход и sudo. UFW выключайте лишь временно для восстановления; затем
верните исходные правила из сохранённого каталога и исходное состояние из ufw-status.txt.
Автообновления пакетов и установленные пакеты откатом drop-in не удаляются.

## Caddy, CSP и staging

В production env задайте `CSP_HEADER=Content-Security-Policy-Report-Only` для первого
выката. Это выбранный по умолчанию вариант мягкого внедрения; он сообщает о нарушениях
в DevTools, не блокируя ресурсы. После QA05 можно включить `Content-Security-Policy`.
Оба режима проверены локальными тестами. При изменении inline-скрипта CI требует
пересмотреть и обновить хеш в `infra/Caddyfile`; приложение здесь не меняется.

Для staging внешний Caddy читает auth из **production env**, не из staging env.
Сгенерируйте хеш интерактивно (пароль не попадёт в аргументы процесса):

```bash
ssh <ssh-user>@SERVER_IP
cd ~/flounder
docker exec -it flounder-caddy caddy hash-password
nano infra/.env.prod
```

Добавьте `STAGING_BASIC_AUTH_USER=staging` и `STAGING_BASIC_AUTH_HASH='ВЫВОД_КОМАНДЫ'`.
Одинарные кавычки сохраняют `$` bcrypt при чтении Bash/Compose. Не публикуйте пароль/хеш.
Сначала поднимите staging и проверьте loopback health по runbook §13, затем скопируйте
пример сайта. Пустой/неверный хеш не должен использоваться для включения сайта.

Применение Caddy после заполнения env и готовности staging:

```bash
ssh <ssh-user>@SERVER_IP
cd ~/flounder
cp infra/caddy-sites/staging.example infra/caddy-sites/staging.caddy
docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod run --rm --no-deps caddy caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod up -d --no-deps --force-recreate caddy
curl -fsSI https://sportgate.pro/
curl -sS -o /dev/null -w '%{http_code}\n' https://staging.sportgate.pro/actuator/health
curl --user staging -fsS https://staging.sportgate.pro/actuator/health
```

Проверка: validate успешен, prod заголовки содержат CSP Report-Only/HSTS/nosniff,
без auth staging отвечает 401, с паролем (curl запросит его) health UP и те же заголовки.
`reload` не обновляет env контейнера; нужен `up --force-recreate caddy`.
Если staging не нужен, пропустите копирование сайта и не заполняйте auth.
У мониторов внешнего staging тоже должны быть credentials; loopback gateway остаётся
доступен только на самом хосте для сида/проверки. При выключении staging удалите
`staging.caddy`, выполните validate и reload Caddy, затем остановите staging.

В браузере пройдите QA05: обзор/программа, импорт, заявки, посев/печать, live, снятие,
ввод и публикация результатов. DevTools → Console: нет сообщений CSP; проверьте темы,
PDF, PWA и Network для документов. Лимиты `/api/*` и вебхуков проверены локальным
`infra/tests/caddy-test.sh`; не отправляйте тестовые платежи на production.
После проверки поменяйте CSP_HEADER на `Content-Security-Policy`, повторите validate,
пересоздание Caddy и браузерную проверку. Быстрый откат CSP — вернуть Report-Only и
пересоздать Caddy; откат сайта staging — удалить его локальный файл и reload.

Закреплённые образы prod: PostgreSQL 16.14, Caddy 2.10.2. Лимиты памяти S03 сохранены,
для четырёх сервисов включён no-new-privileges. Для их применения после бэкапа:

```bash
ssh <ssh-user>@SERVER_IP
cd ~/flounder
docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod up -d --no-build
docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod ps
docker inspect -f '{{.Name}} memory={{.HostConfig.Memory}} security={{.HostConfig.SecurityOpt}}' flounder-api flounder-web flounder-caddy flounder-postgres
curl -fsS https://sportgate.pro/actuator/health
```

Ожидается api/postgres healthy, остальные Up; память 1664/128/128/640 МиБ соответственно,
security содержит no-new-privileges. Не меняйте JVM/настройки PostgreSQL S03 при этом шаге.

## Бэкапы и внешняя копия

На **ноутбуке/отдельном узле владельца** установите age, затем:

```bash
umask 077
age-keygen -o owner-backup.key
age-keygen -y owner-backup.key
```

Приватный owner-backup.key храните у владельца с резервной офлайн-копией. На сервер
переносится **только публичный** `age1...` в `BACKUP_AGE_RECIPIENT` production env.
Telegram сначала настройте по runbook §12. На сервере:

```bash
ssh <ssh-user>@SERVER_IP
cd ~/flounder
sudo apt-get update
sudo apt-get install -y age
nano infra/.env.prod
chmod 600 infra/.env.prod
./infra/alert.sh 'SportGate: проверка уведомлений'
./infra/backup.sh
find ~/backups -maxdepth 1 -name '*.age' -type f -printf '%f %m %s байт\n'
tail -n 20 ~/backups/backup.log
./infra/verify-latest-backup.sh
```

Проверка: уведомление пришло, `.dump.age` и при наличии volume `.files.tar.gz.age`
имеют права 600; новых `.dump`/`.tar.gz`/`.tmp` за этот запуск нет. Weekly-проверка
присылает **напоминание**, полное восстановление нужно выполнить владельцу отдельно.
Если получатель пуст, приходит предупреждение о незашифрованной локальной копии;
при настроенном remote такой запуск отказывается создавать/отправлять открытый дамп.

С ноутбука скачайте выбранную пару (замените условные имена):

```bash
scp <ssh-user>@SERVER_IP:~/backups/CHOSEN_BACKUP.dump.age .
scp <ssh-user>@SERVER_IP:~/backups/CHOSEN_BACKUP.files.tar.gz.age .
```

На **отдельном узле восстановления** с Docker, age и checkout того же commit:

```bash
cd ~/flounder
docker run -d --name sportgate-restore-check -e POSTGRES_USER=sport_user -e POSTGRES_DB=sport_platform -e POSTGRES_PASSWORD=test-only-restore-password postgres:16.14
docker exec sportgate-restore-check pg_isready -h 127.0.0.1 -U sport_user -d sport_platform
ENV_FILE=/dev/null BACKUP_DIR="$PWD/work/restore-check" POSTGRES_CONTAINER=sportgate-restore-check ./infra/restore.sh /путь/CHOSEN_BACKUP.dump.age /путь/owner-backup.key
age -d -i /путь/owner-backup.key /путь/CHOSEN_BACKUP.files.tar.gz.age | tar -tzf -
docker exec sportgate-restore-check psql -U sport_user -d postgres -Atc "select count(*) from pg_database where datname='sport_platform_restore_test'"
docker rm -f sportgate-restore-check
```

После старта дождитесь pg_isready с кодом 0. Проверка restore завершается 0, число событий
разумно, тестовая БД удалена (count 0), архив читается. Telegram без локальных настроек
может сообщить, что не настроен; это не заменяет проверку вывода/кода возврата.
Никаких подключений к production БД в этом блоке нет. Дамп/архив могут содержать
персональные данные: рабочий каталог владельца должен быть защищён, после проверки
удалите расшифрованные данные; приватный ключ не копируйте в production.

Для S3 используйте отдельный аккаунт/бакет в выбранном регионе РФ. Серверный ключ
ограничьте `PutObject` выбранным префиксом; запретите чтение, listing, delete и доступ
к другим бакетам. Rclone запускается с `--no-check-dest --s3-no-check-bucket --s3-no-head`; включите bucket versioning,
retention/object lock для защиты существующих версий. Конкретную политику провайдера
проверьте тестовым объектом: запись разрешена, чтение/удаление этим ключом запрещены.
Для провайдера, которому нужны дополнительные права multipart, отдельно ограничьте
их этим же префиксом; не выдавайте серверу универсальный S3-ключ.

Настройки в защищённом production env: `RCLONE_CONFIG_RF_TYPE=s3`,
`RCLONE_CONFIG_RF_PROVIDER=Other`, `RCLONE_CONFIG_RF_ENDPOINT`,
`RCLONE_CONFIG_RF_ACCESS_KEY_ID`, `RCLONE_CONFIG_RF_SECRET_ACCESS_KEY`,
`BACKUP_S3_REMOTE=rf:BUCKET/flounder`. Установите rclone, запустите новый бэкап и
проверьте получение **обоих зашифрованных** объектов через отдельный ключ чтения
на узле владельца. Проверьте восстановление скачанной копии тем же порядком.
Lifecycle настраивает владелец отдельной административной учётной записью.
Ошибка выгрузки даёт ненулевой код и алерт; локальные готовые файлы сохраняются.
Откат remote — очистить BACKUP_S3_REMOTE; уже созданные копии не удалять.
Откат шифрования не расшифровывает старые `.age`: сохраняйте ключ и тестируйте их
новым restore.sh на узле владельца даже при возврате старой версии скриптов.

## Ручной workflow GitHub

В GitHub создайте environment `production`, при необходимости задайте reviewers и
ограничение веток. В environment secrets добавьте SSH_HOST, SSH_USER, SSH_KEY,
SSH_HOST_KEY и SSH_HOST_KEY_FINGERPRINT. Ключ для SSH_KEY принадлежит непривилегированной
учётной записи; после hardening root использовать нельзя.

Через доверенную **консоль провайдера** получите публичный ключ сервера и его отпечаток:

```bash
cat /etc/ssh/ssh_host_ed25519_key.pub
ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub -E sha256
```

SSH_HOST_KEY — первые два поля (`ssh-ed25519 BASE64`), SSH_HOST_KEY_FINGERPRINT —
поле `SHA256:...`. Сравнение выполняется до SSH, неизвестный/изменившийся ключ
не принимается автоматически. Секреты не вставляйте в PR и не снимайте скриншоты их значений.
В Actions → Deploy demo stand задайте абсолютный app_dir без пробелов и ветку main,
запустите вручную. Проверка: job успешен, health UP, commit соответствует main.
Откат workflow — не запускать его, использовать ручные команды; при ротации host key
обновить оба доверенных значения через консоль, сохраняя strict checking.

## Возврат конфигурации контейнеров

В новой сессии по непривилегированному ключу:

```bash
ssh <ssh-user>@SERVER_IP
cd ~/flounder
git show "$(cat ~/flounder-before-hardening.commit):infra/compose.prod.yaml" > infra/compose.rollback.yaml
git show "$(cat ~/flounder-before-hardening.commit):infra/Caddyfile" > infra/Caddyfile
cp ~/.config/flounder/before-hardening/env.prod infra/.env.prod
rm -f infra/caddy-sites/staging.caddy
docker compose -f infra/compose.rollback.yaml -f ~/.config/flounder/before-hardening/images.rollback.yaml --env-file infra/.env.prod config --quiet
docker compose -f infra/compose.rollback.yaml -f ~/.config/flounder/before-hardening/images.rollback.yaml --env-file infra/.env.prod up -d --no-build
docker compose -f infra/compose.rollback.yaml -f ~/.config/flounder/before-hardening/images.rollback.yaml --env-file infra/.env.prod ps
curl -fsS https://sportgate.pro/actuator/health
```

Rollback использует теги `flounder-rollback-<service>:before-hardening`, созданные
до изменений и записанные в images.rollback.yaml. Они защищают прежние образы от
dangling-prune в deploy.sh; не переназначайте их до приёмки. Не удаляйте эти образы
до приёмки и не скачивайте произвольный более старый patch поверх нового data volume.
При проблеме совместимости восстановите проверенный дамп в отдельный новый volume;
исходный не удалять. После успешного отката ожидаются healthy/UP; разберите локальные
изменения Caddyfile перед следующим git pull. SSH/UFW откатываются отдельно через консоль.

# Runbook демо-стенда Flounder (T09)

Пошаговая инструкция для владельца: как поднять, проверить, обновить и починить демо-стенд. Технические знания не нужны — копируйте команды по порядку. Каждая команда сопровождается пояснением «зачем».

Стенд: один сервер Timeweb Cloud (Ubuntu 24.04, 2 vCPU / 4 ГБ), всё работает в Docker:
`caddy` (HTTPS и маршрутизация) → `web` (сайт, React) и `api` (бэкенд, Spring Boot) → `postgres` (база данных).

Файлы, о которых идёт речь, лежат в репозитории: `infra/compose.prod.yaml`, `infra/Caddyfile`, `infra/.env.prod.example`, `infra/deploy.sh`, `infra/backup.sh`, `infra/restore.sh`, `infra/crontab.example`, `.github/workflows/deploy.yml`.

Условные обозначения: `DOMAIN` — ваш домен (например `demo.flounder.kz`), `SERVER_IP` — IP сервера из панели Timeweb. Команды с `$` вводятся на сервере; `PS>` — на вашем ноутбуке в PowerShell.

---

## 0. Что нужно до начала

1. **Сервер.** В панели Timeweb Cloud создать сервер: Ubuntu 24.04, 2 vCPU / 4 ГБ, образ «Docker» (если есть — тогда Docker уже установлен) или чистая Ubuntu. При создании добавьте свой SSH-ключ (как его сделать — п. 1). Запишите `SERVER_IP` и пароль root (придёт на почту/в панели).
2. **Домен.** У DNS-провайдера домена создать A-запись: `DOMAIN` → `SERVER_IP`. Проверить с ноутбука: `PS> nslookup DOMAIN` — должен показать `SERVER_IP`. DNS обновляется до 1 часа; без правильной A-записи сертификат не выпустится.
3. **Порты.** Если в панели Timeweb включён firewall — открыть входящие TCP 22, 80, 443 (и UDP 443 для HTTP/3, необязательно).
4. **GitHub.** Репозиторий `flounder` приватный, значит серверу нужен доступ на чтение — п. 3.

---

## 1. Первый вход по SSH

**Зачем:** все действия на сервере делаются через терминал по SSH.

На ноутбуке (Windows 10/11, PowerShell) ключ создаётся один раз:

```powershell
PS> ssh-keygen -t ed25519 -C "sergey-laptop"
# Enter три раза (путь по умолчанию, без пароля на ключ). Публичный ключ: C:\Users\<вы>\.ssh\id_ed25519.pub
PS> Get-Content $env:USERPROFILE\.ssh\id_ed25519.pub
```

Содержимое `.pub` вставьте в панель Timeweb (SSH-ключи) при создании сервера. Если сервер уже создан — войдите по паролю root и добавьте ключ вручную (ниже).

Вход:

```powershell
PS> ssh root@SERVER_IP
```

При первом входе спросит «Are you sure you want to continue connecting?» — ответить `yes`. Если ключ не добавлен — спросит пароль root.

Сразу обновим систему и создадим отдельного пользователя `deploy` (работать под root — плохая практика; при утечке ключа ущерб меньше):

```bash
$ apt-get update && apt-get -y upgrade          # зачем: свежие пакеты безопасности
$ adduser --disabled-password --gecos "" deploy  # зачем: пользователь, под которым живёт стенд
$ mkdir -p /home/deploy/.ssh && cp /root/.ssh/authorized_keys /home/deploy/.ssh/ \
  && chown -R deploy:deploy /home/deploy/.ssh && chmod 700 /home/deploy/.ssh   # зачем: тот же SSH-ключ для deploy
$ echo "deploy ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/deploy                 # зачем: sudo без пароля (пароля у deploy нет)
```

Дальше входите как `ssh deploy@SERVER_IP`.

---

## 2. Установка Docker (Ubuntu 24.04)

**Зачем:** всё приложение запускается контейнерами; нужен Docker Engine и плагин Compose.

Проверить, не стоит ли уже (образ «Docker» в Timeweb ставит его через cloud-init):

```bash
$ docker --version && docker compose version
```

Если команда выдала версии (Docker ≥ 24, Compose ≥ 2.20) — переходите к добавлению пользователя в группу. Если `command not found` — ставим по официальной инструкции:

```bash
$ sudo apt-get update
$ sudo apt-get install -y ca-certificates curl git                       # зачем: инструменты для скачивания и git
$ sudo install -m 0755 -d /etc/apt/keyrings
$ sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc   # зачем: ключ репозитория Docker
$ sudo chmod a+r /etc/apt/keyrings/docker.asc
$ echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null          # зачем: подключить репозиторий Docker
$ sudo apt-get update
$ sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

В любом случае:

```bash
$ sudo usermod -aG docker deploy   # зачем: запускать docker без sudo
$ exit                             # выйти и войти заново, чтобы группа применилась
PS> ssh deploy@SERVER_IP
$ docker ps                        # должно вывести пустую таблицу без ошибки
```

Если места на диске мало (40 ГБ), включим ограничение логов — оно уже задано в `compose.prod.yaml` (50 МБ × 5 файлов на контейнер), ничего делать не надо.

---

## 3. Клонирование репозитория на сервер

Репозиторий приватный. Два способа дать серверу доступ **только на чтение** — выберите один.

### Вариант А (рекомендуется): deploy key

**Зачем:** отдельный ключ, привязанный к одному репозиторию, только чтение. Его утечка не даёт доступа ни к чему другому.

На сервере:

```bash
$ ssh-keygen -t ed25519 -f ~/.ssh/flounder_deploy -N "" -C "flounder-demo-server"   # зачем: ключ сервера для GitHub
$ cat ~/.ssh/flounder_deploy.pub                                                     # показать публичную часть
```

Скопируйте вывод (одна строка `ssh-ed25519 AAAA… flounder-demo-server`). На GitHub: репозиторий → **Settings → Deploy keys → Add deploy key**, название «demo server», вставить ключ, галочку **Allow write access НЕ ставить** → Add key.

Сказать SSH, каким ключом ходить на GitHub:

```bash
$ cat >> ~/.ssh/config <<'EOF'
Host github.com
  IdentityFile ~/.ssh/flounder_deploy
  IdentitiesOnly yes
EOF
$ ssh -T git@github.com     # зачем: проверка; ожидаем "Hi <owner>/flounder! You've successfully authenticated…"
```

Клонируем (путь `/opt/flounder` используется во всех скриптах и в cron):

```bash
$ sudo mkdir -p /opt/flounder && sudo chown deploy:deploy /opt/flounder
$ git clone git@github.com:<owner>/flounder.git /opt/flounder
$ cd /opt/flounder && git log --oneline -1   # зачем: убедиться, что код скачался
```

### Вариант Б: HTTPS с токеном

**Зачем:** проще, но токен привязан к вашему аккаунту — храните как пароль.

На GitHub: аватар → **Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token**: Repository access — Only select repositories → `flounder`; Permissions → Contents: **Read-only**; срок — 90 дней (потом перевыпустить). Скопировать токен (`github_pat_…`).

```bash
$ sudo mkdir -p /opt/flounder && sudo chown deploy:deploy /opt/flounder
$ git clone https://github.com/<owner>/flounder.git /opt/flounder
# Username: <ваш логин GitHub>; Password: <вставить токен>
$ cd /opt/flounder && git config credential.helper store   # зачем: запомнить токен, чтобы deploy.sh не спрашивал его каждый раз
$ git pull                                                  # ещё раз введите логин и токен — теперь они сохранятся в ~/.git-credentials
```

---

## 4. Заполнение `.env.prod`

**Зачем:** в этом файле все секреты и домен. Он не попадает в git (есть в `.gitignore`).

```bash
$ cd /opt/flounder
$ cp infra/.env.prod.example infra/.env.prod
$ chmod 600 infra/.env.prod                     # зачем: читать может только deploy
$ echo "POSTGRES_PASSWORD=$(openssl rand -hex 24)"   # зачем: сгенерировать пароль БД — скопируйте строку
$ echo "JWT_SECRET=$(openssl rand -base64 48)"       # зачем: сгенерировать секрет подписи токенов — скопируйте строку
$ nano infra/.env.prod
```

В редакторе `nano` заполните:

| Переменная | Что вписать |
|---|---|
| `DOMAIN` | ваш домен без `https://`, например `demo.flounder.kz` |
| `ACME_EMAIL` | ваша почта — Let's Encrypt пришлёт письмо, если сертификат не продлится |
| `POSTGRES_PASSWORD` | строка из `openssl rand -hex 24` |
| `JWT_SECRET` | строка из `openssl rand -base64 48` (минимум 32 символа) |
| `APP_CORS_ALLOWED_ORIGINS` | `https://` + домен, например `https://demo.flounder.kz` |
| `APP_PAYMENTS_PROVIDER` | `MANUAL` (оставить) |
| `DEMO_SEED` | `true` (оставить) |

Сохранить: `Ctrl+O`, `Enter`, выйти: `Ctrl+X`.

Важно: `JWT_SECRET` менять после запуска нельзя без последствий — все выданные токены входа перестанут работать (пользователям придётся войти заново). `POSTGRES_PASSWORD` после первого запуска менять нельзя вовсе (база уже создана с этим паролем) — если нужно, см. п. 10.

---

## 5. Первый запуск

**Зачем:** скрипт `deploy.sh` скачивает свежий код, собирает образы (бэкенд ~5–8 минут на 2 vCPU в первый раз, фронт ~2 минуты) и запускает все контейнеры. Повторные запуски быстрее благодаря кэшу.

```bash
$ cd /opt/flounder
$ ./infra/deploy.sh
```

Что произойдёт: `git pull` → `docker compose … up -d --build` → ожидание, пока API ответит `healthy` (до 3 минут) → таблица контейнеров и строка `Done. Check: https://DOMAIN/actuator/health`.

Если первый запуск нужен без `git pull` (например, код скопирован вручную): `./infra/deploy.sh --no-pull`.

Пока собирается — можно смотреть прогресс в соседнем окне: `docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod logs -f`.

Сертификат Let's Encrypt выпускается автоматически при первом обращении по домену (нужны открытые 80/443 и правильная A-запись). Caddy повторяет попытки сам; выпуск обычно занимает 10–60 секунд.

---

## 6. Проверка, что всё работает

```bash
$ docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod ps
```

Ожидаем 4 контейнера `flounder-postgres`, `flounder-api`, `flounder-web`, `flounder-caddy` со статусом `Up` (у postgres и api — `(healthy)`).

```bash
$ curl -s https://DOMAIN/actuator/health      # зачем: проверить бэкенд через HTTPS; ответ {"status":"UP"}
$ curl -sI https://DOMAIN/ | head -5           # зачем: проверить сайт; HTTP/2 200 и заголовок strict-transport-security
$ curl -s https://DOMAIN/api/v1/public/events  # зачем: публичный API; пустой список [] или JSON — нормально
```

С телефона: открыть `https://DOMAIN` — замок в адресной строке, страница загружается. Swagger (появится после T01): `https://DOMAIN/swagger-ui.html`.

Если `curl` к домену выдаёт ошибку сертификата — подождите минуту (идёт выпуск), затем п. 11 «Сертификат».

---

## 7. Сид демо-данных (T10)

**Зачем:** наполнить пустую базу организацией, событием и заявками для демонстрации. Скрипт появится в задаче T10 (`scripts/demo/seed.ts`), работает через HTTP API стенда и идемпотентен (повторный запуск дублей не создаёт).

Запускается с ноутбука или с сервера (нужен Node.js 22):

```bash
$ cd /opt/flounder && git pull                     # зачем: получить скрипт после слияния T10
$ sudo apt-get install -y nodejs npm               # только если Node нет на сервере
$ API_URL=https://DOMAIN ADMIN_EMAIL=<админ> ADMIN_PASSWORD=<пароль> npx tsx scripts/demo/seed.ts
```

Точные параметры — в `tasks/T10_Демо-данные_и_сценарий.md` и в README скрипта. После сида сделайте бэкап (п. 9) — это «чистая» точка, к которой можно откатиться после репетиции.

---

## 8. Логи

**Зачем:** первое, куда смотреть при любой проблеме.

```bash
$ cd /opt/flounder
$ docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod logs --tail=200 api     # последние 200 строк бэкенда
$ docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod logs -f api             # «живой» хвост (выход Ctrl+C)
$ docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod logs --tail=100 caddy   # HTTPS, сертификаты, маршрутизация
$ docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod logs --tail=100 postgres
```

Логи бэкенда в JSON (одна строка — одно событие); искать ошибки: `… logs api | grep -i '"level":"ERROR"'`.

Чтобы передать логи Claude для разбора: `… logs --tail=300 api > ~/api.log` и скопировать файл на ноутбук: `PS> scp deploy@SERVER_IP:~/api.log .`

Удобное сокращение — добавьте в `~/.bashrc` алиас, чтобы не печатать длинную команду:

```bash
$ echo "alias fl='docker compose -f /opt/flounder/infra/compose.prod.yaml --env-file /opt/flounder/infra/.env.prod'" >> ~/.bashrc && source ~/.bashrc
$ fl ps   # теперь так
```

---

## 9. Бэкап и восстановление (M09)

Все команды ниже выполняет оператор как `deploy`; разработчик их на сервере не запускает.
`restore.sh` теперь **проверяет** бэкап в отдельной БД `sport_platform_restore_test`,
считает строки `events`, удаляет созданную тестовую БД и сообщает результат в Telegram.
Рабочая БД и API продолжают работать. Если тестовая БД уже существует, скрипт отказывается
её трогать: сначала выясните, кто её создал. Не запускайте проверки на непроверенных чужих дампах.

### Бэкап вручную и расписание

```bash
cd /opt/flounder
mkdir -p /home/deploy/backups
chmod 700 /home/deploy/backups
./infra/backup.sh
ls -lh /home/deploy/backups
```

Новый формат: `/home/deploy/backups/flounder-YYYYMMDD-HHMM.dump` (`pg_dump -Fc`,
время имени по Москве). Повтор в ту же минуту отказывается перезаписывать файл;
для второй копии дождитесь следующей минуты. Права файлов — только для deploy.
Лог: `/home/deploy/backups/backup.log`, локальная ротация бэкапов — 30 суток.
Неполные временные файлы удаляются, ошибка возвращает ненулевой код и алерт.
Если есть Docker volume `flounder_files`, рядом создаётся `.files.tar.gz`;
другое имя задайте `FILES_VOLUME` в `.env.prod`. БД и файлы копируются последовательно:
для согласованного снимка документов оператор должен приостановить их изменение.
Volume отсутствует на текущем main; восстановление файлов проверяется отдельно по мере появления M04.

Старый боевой `.sql.gz` (~36 КБ, снят 13.10.2026) совместим с новым `restore.sh`:
расширение `.dump` выбирает `pg_restore`, `.sql.gz` — `gzip` → `psql`.
Скопируйте старые файлы из `/opt/flounder/backups` в `/home/deploy/backups`, сохраняя оригиналы;
их размер сам по себе не подтверждает целостность.

```bash
./infra/restore.sh /home/deploy/backups/OLD_BACKUP.sql.gz
./infra/verify-latest-backup.sh
sudo timedatectl set-timezone Europe/Moscow
timedatectl
crontab -e
```

Замените старую строку бэкапа строками `infra/crontab.example`, сохранив прочие задания.
Ubuntu cron считает расписание в timezone **хоста**; одной переменной `TZ` в crontab недостаточно.
Ежедневно 03:00 МСК — бэкап, воскресенье 04:00 — восстановление последней копии,
каждые 15 минут — диск (>85%). Настройте Telegram до установки cron (§12),
проверьте `crontab -l`. Изменение timezone хоста не меняет timezone соревнований/контейнера API.

### Восстановление за 10 минут

Цель для небольшой демо-БД — около 10 минут после выбора проверенного бэкапа;
это план процедуры, время на сервере ещё не измерено. Общие MVP-цели: RPO 24 ч, RTO 2 ч.
Для обычной проверки достаточно `restore.sh`; следующие команды **заменяют рабочие данные**
и выполняются только при согласованном откате в окно обслуживания. Сначала сохраните
текущую БД, проверьте выбранный дамп, убедитесь, что его схема совместима с выбранной версией API.

1. Войти как deploy, выбрать копию абсолютным путём и выполнить проверку:

```bash
cd /opt/flounder
FILE=/home/deploy/backups/CHOSEN_BACKUP.dump  # или старый .sql.gz
./infra/backup.sh
./infra/restore.sh "$FILE"
```

2. Если проверка успешна, закрыть запись пользователей, подтвердить откат и остановить API.
Значения DB/USER ниже — стандартные; при изменённых `.env.prod` сначала подставьте свои.
Не продолжать после любой ошибки (включён `set -e`).

```bash
set -euo pipefail
DB=sport_platform
DB_USER=sport_user
read -r -p "Удалить текущую БД $DB? Введите restore-production: " answer
[[ "$answer" == restore-production ]]
docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod stop api
docker exec flounder-postgres dropdb -U "$DB_USER" --force "$DB"
docker exec flounder-postgres createdb -U "$DB_USER" -T template0 -O "$DB_USER" "$DB"
case "$FILE" in
  *.dump) docker exec -i flounder-postgres pg_restore -U "$DB_USER" -d "$DB" \
    --no-owner --no-privileges --single-transaction --exit-on-error < "$FILE" ;;
  *.sql.gz) gzip -dc "$FILE" | docker exec -i flounder-postgres psql -X -U "$DB_USER" \
    -d "$DB" -v ON_ERROR_STOP=1 --single-transaction ;;
  *) echo "Неизвестный формат"; exit 1 ;;
esac
docker exec flounder-postgres psql -X -U "$DB_USER" -d "$DB" -v ON_ERROR_STOP=1 -c 'select count(*) from events'
docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod start api
curl -fsS https://sportgate.pro/actuator/health
```

3. Открыть публичную страницу существующего события, войти организатором, проверить
число заявок/результаты. При ошибке загрузки оставить API остановленным, изучить `backup.log`
и логи PostgreSQL; использовать сохранённую текущую копию, не выполнять повторный DROP наугад.

Если появились файлы M04: сначала остановить сервисы, пишущие в volume, сохранить его текущую
копию и проверить парный архив (`tar -tzf ...files.tar.gz`). Восстановление в новый volume:
`docker volume create flounder_files_recovered`, затем
`docker run --rm -i --network none -v flounder_files_recovered:/files postgres:16 tar -xzf - -C /files < ...files.tar.gz`.
Подключить новый volume в compose M04, проверить ссылки на документы, только потом открыть запись.
Оригинальный volume сохранять до приёмки; восстановление файлов нельзя считать проверенным лишь по SQL smoke.

### Опционально: копия в S3 в РФ

По умолчанию выгрузка выключена (`BACKUP_S3_REMOTE` пуст). Установить `rclone`, выбрать
российский регион у Selectel/Yandex/VK и передать через `.env.prod` переменные
`RCLONE_CONFIG_RF_TYPE=s3`, `RCLONE_CONFIG_RF_PROVIDER=Other`,
`RCLONE_CONFIG_RF_ENDPOINT`, `RCLONE_CONFIG_RF_ACCESS_KEY_ID`,
`RCLONE_CONFIG_RF_SECRET_ACCESS_KEY`; затем `BACKUP_S3_REMOTE=rf:BUCKET/flounder`.
Скрипт загружает дамп и парный файловый архив; ошибка выгрузки означает ошибку задания,
локальная копия остаётся. Настроить lifecycle bucket на 30 дней, приватный доступ и шифрование.
Для ручной проверки под deploy: загрузить один дамп, скачать в отдельный путь через
`rclone copyto rf:BUCKET/flounder/NAME.dump /home/deploy/backups/downloaded.dump`,
затем `./infra/restore.sh /home/deploy/backups/downloaded.dump`.
Не передавать ключи в CLI, не коммитить `.env`; хотя бы одну копию хранить вне сервера.

---

## 10. Обновление стенда

**Зачем:** выкатить слитые в `main` изменения. Простой — около минуты (пока API перезапускается); сайт в это время отдаёт ошибку 502.

Способ 1 — на сервере:

```bash
$ cd /opt/flounder && ./infra/deploy.sh
```

Способ 2 — из GitHub, без входа на сервер (нужно один раз настроить):

1. На сервере создать ключ для GitHub Actions: `ssh-keygen -t ed25519 -f ~/.ssh/gha_deploy -N "" -C gha` и добавить публичную часть в доверенные: `cat ~/.ssh/gha_deploy.pub >> ~/.ssh/authorized_keys`.
2. В репозитории: **Settings → Secrets and variables → Actions → New repository secret**: `SSH_HOST` = `SERVER_IP`, `SSH_USER` = `deploy`, `SSH_KEY` = содержимое `cat ~/.ssh/gha_deploy` (приватный ключ целиком, со строками BEGIN/END). После этого `rm ~/.ssh/gha_deploy` на сервере — приватный ключ там не нужен.
3. GitHub → **Actions → Deploy demo stand → Run workflow** (ветка `main`, папка `/opt/flounder`) → Run. Лог деплоя виден в самом Action.

Если давать GitHub доступ по SSH не хочется — просто не создавайте секреты и пользуйтесь способом 1; workflow без секретов завершится ошибкой и ничего не сделает.

Откат к предыдущей версии кода: `cd /opt/flounder && git log --oneline -5` → выбрать коммит → `git checkout <hash> && ./infra/deploy.sh --no-pull`. Вернуться на актуальный `main`: `./infra/deploy.sh`.

Смена `POSTGRES_PASSWORD` после первого запуска: `fl exec postgres psql -U sport_user -d sport_platform -c "ALTER USER sport_user PASSWORD 'новый';"`, затем вписать новый пароль в `.env.prod` и `fl up -d` (пересоздаст api с новым паролем).

---

## 11. Что делать, если упало

Сначала — общая картина:

```bash
$ fl ps -a                 # какие контейнеры живы; Exited/Restarting — проблема
$ df -h /                  # свободно ли место на диске (ниже 1 ГБ — чистить, см. ниже)
$ free -m                  # память; если available < 200 МБ — API мог быть убит по OOM
```

### Контейнер `Restarting` или `Exited`

```bash
$ fl logs --tail=200 api   # (или postgres / caddy / web) — последние строки покажут причину
$ fl up -d                 # зачем: пересоздать упавшие контейнеры; остальные не трогает
```

`restart: unless-stopped` означает, что Docker сам перезапускает упавший контейнер и поднимает всё после перезагрузки сервера. Если контейнер падает циклически — смотрите логи, типичные причины ниже.

| Симптом в логах | Причина | Что делать |
|---|---|---|
| `FATAL: password authentication failed` | в `.env.prod` сменили `POSTGRES_PASSWORD` после создания базы | вернуть старый пароль или сменить его в БД (п. 10) |
| `Schema-validation: missing table/column` | код ждёт миграцию, которой нет в базе; или база старее кода | `fl logs postgres`; обычно помогает `./infra/deploy.sh` (Flyway применит миграции). Если миграция упала — восстановить бэкап (п. 9) и сообщить разработчикам |
| `Migration checksum mismatch` / `FlywayValidateException` | в репозитории изменили уже применённую миграцию | не чинить руками — сообщить разработчикам, приложить лог |
| `OutOfMemoryError` или контейнер просто исчез, в `dmesg` есть `Killed process` | мало памяти | `fl restart api`; если повторяется — уменьшить `-Xmx1024m` до `-Xmx768m` в `compose.prod.yaml` или увеличить сервер |
| `JWT_SECRET` / `Could not resolve placeholder` | пустая переменная в `.env.prod` | заполнить и `fl up -d` |
| `port is already allocated` (caddy) | порт 80/443 занят другим сервисом (nginx/apache) | `sudo ss -ltnp | grep ':80\|:443'`, остановить лишнее: `sudo systemctl disable --now nginx` |

### Сайт не открывается, но контейнеры `Up`

```bash
$ fl exec caddy wget -qO- http://api:8080/actuator/health   # зачем: API отвечает изнутри сети Docker?
$ fl logs --tail=50 caddy    # ищите строки с "certificate", "challenge", "error"
$ nslookup DOMAIN            # A-запись ведёт на этот сервер?
```

### Сертификат (ошибка HTTPS в браузере)

Причины по частоте: A-запись ещё не обновилась; закрыт порт 80 (нужен для проверки Let's Encrypt); опечатка в `DOMAIN`. Исправить и перезапустить Caddy: `fl restart caddy`. Caddy пробует выпуск повторно с нарастающей паузой; после 5+ неудачных попыток за час Let's Encrypt ограничивает запросы — подождите час.

### Закончилось место на диске

```bash
$ docker system df           # сколько занимают образы/контейнеры
$ docker image prune -af     # зачем: удалить неиспользуемые образы (текущие не тронет)
$ sudo journalctl --vacuum-size=200M
$ ls -la /home/deploy/backups   # старые бэкапы удалить вручную при необходимости
```

### Полный перезапуск

```bash
$ fl down && fl up -d        # зачем: остановить и поднять заново все контейнеры; данные в volume сохраняются
```

**Опасно:** `fl down -v` удаляет volume с базой данных и сертификатами — не выполнять без свежего бэкапа.

### Сервер перезагрузился

Ничего делать не нужно: Docker стартует автоматически и поднимает контейнеры (`restart: unless-stopped`). Через 2–3 минуты проверьте `curl -s https://DOMAIN/actuator/health`.

### Ничего не помогло

Соберите: `fl ps -a`, `fl logs --tail=300 api > ~/api.log`, `fl logs --tail=100 caddy > ~/caddy.log`, `df -h`, `free -m` — и отправьте Claude/разработчикам. Если демо через час — восстановите последний бэкап (п. 9) и перезапустите (`fl down && fl up -d`).

---

## Шпаргалка

| Действие | Команда |
|---|---|
| Статус | `fl ps` |
| Логи API | `fl logs -f api` |
| Обновить | `cd /opt/flounder && ./infra/deploy.sh` |
| Бэкап | `/opt/flounder/infra/backup.sh` |
| Восстановить | `/opt/flounder/infra/restore.sh /home/deploy/backups/<файл>.dump` (проверка) |
| Перезапуск одного сервиса | `fl restart api` |
| Полный перезапуск | `fl down && fl up -d` |
| Здоровье | `curl -s https://DOMAIN/actuator/health` |

(`fl` — алиас из п. 8; без него: `docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod …` из `/opt/flounder`.)

## 12. Мониторинг и Telegram (M09)

1. В Telegram открыть `@BotFather`, выполнить `/newbot`. Полученный токен хранить только
в `.env.prod`: `TELEGRAM_BOT_TOKEN`, туда же `TELEGRAM_CHAT_ID`. Написать боту `/start`;
для группы добавить бота и написать ему адресованное сообщение. Chat ID взять из ответа
`getUpdates` через Telegram Bot API в доверенном локальном инструменте (не вставлять токен
в адресную строку браузера/логи). Для группы ID отрицательный. `chmod 600 infra/.env.prod`.
Не отправлять в Telegram дампы, ФИО и секреты: алерты содержат лишь статус инфраструктуры.
2. Под deploy запустить Kuma и отправить тестовый алерт:

```bash
cd /opt/flounder
docker compose -f infra/compose.monitoring.yaml up -d
./infra/alert.sh "Flounder: проверка доставки уведомлений"
./infra/check-disk.sh
```

3. С ноутбука открыть туннель `ssh -L 3001:127.0.0.1:3001 deploy@SERVER_IP`,
перейти на `http://localhost:3001`, создать администратора с уникальным паролем.
Порт Kuma открыт только на loopback; не публиковать его через ufw.
4. В **Settings → Notifications → Telegram** внести токен/Chat ID из защищённого окружения,
нажать **Test**, назначить уведомление по умолчанию. Kuma хранит настройки в своём volume;
переменные `TELEGRAM_*` используются `alert.sh`, Kuma не импортирует их автоматически.
5. Создать HTTP(s) монитор раз в 60 с: `https://sportgate.pro/actuator/health`, статус 200
и keyword `"UP"` (не принимать один только HTTP 200). Второй монитор:
`https://sportgate.pro/api/v1/public/events/PUBLIC_SLUG`, статус 200, keyword реального slug;
третий — страница `https://sportgate.pro/e/PUBLIC_SLUG`. У SPA fallback даже неизвестный slug
может вернуть 200, поэтому API-монитор обязателен. Включить проверку TLS/уведомления
об истечении сертификата на 14/7 дней; **Ignore TLS errors** выключено.
6. Временно указать в копии монитора несуществующий API slug, дождаться Telegram DOWN,
вернуть существующий slug и проверить UP. Выполнить `verify-latest-backup.sh`, убедиться
в сообщении об успешной проверке и отсутствии `sport_platform_restore_test`.

Kuma на том же VPS не сможет отправить сообщение при падении всего VPS/сети.
Для алерта о полном отказе нужен внешний монитор на отдельном узле (желательно в РФ).
`alert.sh` возвращает ошибку при отсутствии настроек/HTTP-ошибке; ошибка доставки пишется
в лог cron, но не меняет результат успешной SQL-проверки. Еженедельно просматривать лог.

## 13. Staging на том же сервере (M09)

Staging не меняет checkout `/opt/flounder`, имеет проект `flounder-staging`, отдельные
БД `sport_platform_staging`, postgres volume, JWT/пароль БД и образы. БД не публикуется наружу.
Только staging gateway подключён к общей edge-сети Caddy. Код выбранной ветки собирается
в `/home/deploy/flounder-staging`; повторный деплой отказывается затирать локальные изменения.
Удалять staging volume при смене ветки автоматически нельзя; миграции должны идти вперёд.
Для отката ветки со старой схемой сначала согласовать отдельный сброс staging.

Оценка 2 vCPU/4 ГБ: prod JVM heap 1 ГБ + native ~0,3–0,6 ГБ, PostgreSQL ~0,2–0,5 ГБ,
ОС/Docker ~0,5–0,8 ГБ; staging лимиты 768+384+96+96 МБ, Kuma 384 МБ.
Суммарно ~3,5–4,7 ГБ: для двух постоянно нагруженных стендов запаса нет.
Сборка Maven/Vite добавляет временную нагрузку и не ограничена runtime-лимитами.
На текущем VPS запускать staging кратковременно вне демонстрации/старта, наблюдать
`free -m`, `docker stats`, `df -h`; после проверки останавливать staging.
Рекомендация для постоянной эксплуатации: 4 vCPU/8 ГБ или отдельный staging VPS;
сборки перенести в CI с публикацией образов. Это оценка, нагрузочная проверка не выполнена.

Шаги под deploy после слияния PR:

```bash
cd /opt/flounder
git pull --ff-only origin main
# В окно обслуживания обновить prod compose: подключает Caddy к flounder_edge.
docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod up -d --build
mkdir -p /home/deploy/.config/flounder
cp infra/.env.staging.example /home/deploy/.config/flounder/.env.staging
chmod 700 /home/deploy/.config/flounder
chmod 600 /home/deploy/.config/flounder/.env.staging
nano /home/deploy/.config/flounder/.env.staging
./infra/deploy-staging.sh feat/M01-admin-panel
curl -fsS http://127.0.0.1:8081/actuator/health
```

В `.env.staging` заполнить независимые секреты БД/JWT, актуальные `ADMIN_EMAIL/ADMIN_PASSWORD`
seed-админа выделенной staging-БД и `DEMO_PASSWORD`. На чистой БД seed-админ приходит из
существующих миграций (см. `docs/02_ТЗ_демо.md` §1), затем сменить его пароль через UI.
Скрипт дожидается health и запускает **настоящий HTTP-сид** `scripts/demo/seed.ts`
с `DEMO_SEED_ENABLED=true`. Он не полагается на неработающий флаг `DEMO_SEED` backend.
Требуется Node.js 22+; ветка должна содержать `scripts/demo` и совместимые миграции/API.
Сид обращается к loopback gateway, а не к production. Его профиль процесса `staging`
не меняет серверный prod-профиль, который используется для настроек БД/логов.

У DNS добавить A `staging.sportgate.pro` → этот сервер; после успешной локальной проверки:

```bash
cp infra/caddy-sites/staging.example infra/caddy-sites/staging.caddy
docker exec flounder-caddy caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
docker exec flounder-caddy caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile
curl -fsS https://staging.sportgate.pro/actuator/health
```

Открыть `/e/PUBLIC_SLUG` из вывода staging-сида, проверить вход организатора и импорт.
Для остановки (данные сохраняются):

```bash
docker compose --project-name flounder-staging -f infra/compose.staging.yaml \
  --env-file /home/deploy/.config/flounder/.env.staging stop
```

После остановки staging убрать его сайт `rm infra/caddy-sites/staging.caddy` и перезагрузить
Caddy той же командой, чтобы не оставлять публичный 502. Не использовать `down -v` у prod.

## 14. Correlation-id и опциональный Sentry (M09)

JSON ECS в `application-prod.yml` уже включён; Docker ограничивает журналы 50 МБ × 5
на контейнер (также staging/Kuma). Фильтр до Spring Security принимает безопасный
`X-Request-Id` (буквы/цифры/`._-`, 1–128 знаков), иначе генерирует UUID;
записывает `correlation-id` в MDC и возвращает заголовок. CORS разрешает/открывает этот
заголовок. Проверка под deploy:

```bash
curl -fsS -D - -H 'X-Request-Id: m09-manual-check' https://sportgate.pro/actuator/health
docker logs --tail=100 flounder-api
```

Для проверки MDC отправить запрос, который создаёт запись приложения в логах
(например, существующий сценарий бизнес-ошибки), и найти `correlation-id` в JSON.
`health` может не создавать лог на каждый запрос. Фильтр очищает MDC и при исключении.

Sentry **опционален**, без DSN транспорт выключен. Backend подготовлен через
`sentry-spring-boot-starter-jakarta`, frontend — динамический `@sentry/react` только
при непустом `VITE_SENTRY_DSN`. Чтобы включить: создать отдельные backend/frontend
проекты, внести `SENTRY_DSN` и `VITE_SENTRY_DSN` в окружение нужного стенда и пересобрать
API/web (`VITE_*` встраивается при сборке, runtime-переменной недостаточно).
На staging вызвать контролируемую ошибку и убедиться в получении события правильным
проектом; затем очистить тестовые события. Автоматическая отправка PII выключена,
tracing sampling = 0. Не использовать реальные персональные данные в проверке;
внешний Sentry включать только после выбора размещения и политики обработки данных.
DSN пусты в примерах; доступ к аккаунту Sentry и реальная доставка здесь не проверялись.

## 15. Опциональное усиление SSH (M09)

`harden.sh` не запускается при деплое. Он идемпотентно устанавливает fail2ban/sshd,
unattended-upgrades; ufw остаётся настроенным ранее. Шаг отключения root SSH выполняется
только после явного подтверждения работающего ключа deploy. Не закрывать исходную сессию.

1. В отдельном терминале ноутбука: `ssh -o PasswordAuthentication=no -o KbdInteractiveAuthentication=no deploy@SERVER_IP`.
Проверить `whoami` → deploy и `sudo -v`. Убедиться в доступе к консоли провайдера.
2. В исходной SSH-сессии deploy: `cd /opt/flounder && ./infra/harden.sh`.
3. Только после успеха шага 1 ввести `deploy-key-verified`. Скрипт ставит
`PermitRootLogin no`, проверяет `sshd -t` и эффективную политику `sshd -T`, перезагружает
`ssh` (не обрывает существующую сессию). При ошибке проверки файл откатывается.
4. Проверить ещё один вход deploy по ключу, `sudo fail2ban-client status sshd`,
`systemctl status unattended-upgrades`, `sudo ufw status`. Root SSH должен отказать.

Не принимать подтверждение без фактической проверки второго входа.
Скрипт и пакетная конфигурация проверены статически; их выполнение на сервере — отдельный
опциональный шаг оператора. При потере входа использовать консоль провайдера и убрать
`/etc/ssh/sshd_config.d/00-flounder-hardening.conf`, проверить `sshd -t`, reload ssh.

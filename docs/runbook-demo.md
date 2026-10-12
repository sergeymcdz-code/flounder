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

Если места на диске мало (40 ГБ), включим ограничение логов — оно уже задано в `compose.prod.yaml` (20 МБ × 5 файлов на контейнер), ничего делать не надо.

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

## 9. Бэкап и восстановление

### Бэкап вручную

**Зачем:** перед демо, перед обновлением, после сида.

```bash
$ /opt/flounder/infra/backup.sh
# [backup] ... dumping sport_platform -> /opt/flounder/backups/2026-10-14.sql.gz
```

Файлы лежат в `/opt/flounder/backups/`, старше 14 дней удаляются автоматически. Скопировать бэкап к себе: `PS> scp deploy@SERVER_IP:/opt/flounder/backups/2026-10-14.sql.gz .` — храните хотя бы один бэкап вне сервера.

### Ежедневный бэкап в 03:00

```bash
$ mkdir -p /opt/flounder/backups
$ crontab -e      # зачем: открыть расписание задач; при первом запуске выбрать редактор 1 (nano)
```

Вставить строку из `infra/crontab.example`:

```
0 3 * * * /opt/flounder/infra/backup.sh >> /opt/flounder/backups/backup.log 2>&1
```

Сохранить (`Ctrl+O`, `Enter`, `Ctrl+X`). Проверить: `crontab -l`. Результаты — в `backups/backup.log`.

### Восстановление

**Зачем:** откатить базу к бэкапу (например, после неудачной репетиции). Текущие данные будут **удалены** и заменены содержимым бэкапа; API на время восстановления останавливается (~1 минута).

```bash
$ /opt/flounder/infra/restore.sh /opt/flounder/backups/2026-10-14.sql.gz
# This will DROP database 'sport_platform' ... Type 'yes' to continue: yes
```

Проверка после восстановления: `curl -s https://DOMAIN/actuator/health` → `UP`, открыть сайт.

Проверку «бэкап восстанавливается» сделайте один раз до демо: `backup.sh` → `restore.sh` на только что созданный файл → сайт работает, данные на месте.

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
$ ls -la /opt/flounder/backups   # старые бэкапы удалить вручную при необходимости
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
| Восстановить | `/opt/flounder/infra/restore.sh backups/<файл>.sql.gz` |
| Перезапуск одного сервиса | `fl restart api` |
| Полный перезапуск | `fl down && fl up -d` |
| Здоровье | `curl -s https://DOMAIN/actuator/health` |

(`fl` — алиас из п. 8; без него: `docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod …` из `/opt/flounder`.)

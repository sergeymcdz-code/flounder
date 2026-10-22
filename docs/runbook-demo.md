# Runbook демо-стенда Flounder (T09)

Пошаговая инструкция для владельца: как поднять, проверить, обновить и починить демо-стенд. Технические знания не нужны — копируйте команды по порядку. Каждая команда сопровождается пояснением «зачем».

Стенд: один сервер Timeweb Cloud (Ubuntu 24.04, 2 vCPU / 4 ГБ), всё работает в Docker:
`caddy` (HTTPS и маршрутизация) → `web` (сайт, React) и `api` (бэкенд, Spring Boot) → `postgres` (база данных).

Файлы, о которых идёт речь, лежат в репозитории: `infra/compose.prod.yaml`, `infra/Caddyfile`, `infra/.env.prod.example`, `infra/deploy.sh`, `infra/backup.sh`, `infra/restore.sh`, `infra/crontab.example`, `infra/deploy-staging.sh`, `infra/restore-production.sh`, `infra/alert.sh`, `infra/check-disk.sh`, `infra/verify-latest-backup.sh`, `infra/harden.sh`, `infra/compose.staging.yaml`, `infra/compose.monitoring.yaml`.

Текущий каталог репозитория на сервере — `/home/<ssh-user>/flounder` (`~/flounder` под `<ssh-user>`); проверить `readlink -f ~/flounder`. До hardening возможен вход административной учётной записью и `su - <ssh-user>`; после него входить напрямую непривилегированным ключом. Актуальный порядок обновления — [в инструкции владельца](infra-hardening.md), выполнять в окно без нагрузки.

Условные обозначения: `DOMAIN` — ваш домен (например `demo.flounder.kz`), `SERVER_IP` — IP сервера из панели Timeweb. Команды с `$` вводятся на сервере; `PS>` — на вашем ноутбуке в PowerShell.

---

## 0. Что нужно до начала

1. **Сервер.** В панели Timeweb Cloud создать сервер: Ubuntu 24.04, 2 vCPU / 4 ГБ, образ «Docker» (если есть — тогда Docker уже установлен) или чистая Ubuntu. При создании добавьте свой SSH-ключ (как его сделать — п. 1). Запишите `SERVER_IP` и пароль root (придёт на почту/в панели).
2. **Домен.** У DNS-провайдера домена создать A-запись: `DOMAIN` → `SERVER_IP`. Проверить с ноутбука: `PS> nslookup DOMAIN` — должен показать `SERVER_IP`. DNS обновляется до 1 часа; без правильной A-записи сертификат не выпустится.
3. **Порты.** Если в панели Timeweb включён firewall — открыть входящие TCP 22, 80, 443 (и UDP 443 для HTTP/3, необязательно).
4. **GitHub.** Репозиторий публичный; для чтения достаточно HTTPS без токена — п. 3.

---

## 1. Первый вход по SSH

**Зачем:** все действия на сервере делаются через терминал по SSH.

На ноутбуке (Windows 10/11, PowerShell) ключ создаётся один раз:

```powershell
PS> ssh-keygen -t ed25519 -C "sportgate-laptop"
# Enter три раза (путь по умолчанию, без пароля на ключ). Публичный ключ: C:\Users\<вы>\.ssh\id_ed25519.pub
PS> Get-Content $env:USERPROFILE\.ssh\id_ed25519.pub
```

Содержимое `.pub` вставьте в панель Timeweb (SSH-ключи) при создании сервера. Если сервер уже создан — войдите по паролю root и добавьте ключ вручную (ниже).

Вход:

```powershell
PS> ssh <ssh-user>@SERVER_IP
```

При первом входе спросит «Are you sure you want to continue connecting?» — ответить `yes`. Если ключ не добавлен — спросит пароль root.

Сразу обновим систему и создадим отдельного пользователя `<ssh-user>` (работать под root — плохая практика; при утечке ключа ущерб меньше):

```bash
$ apt-get update && apt-get -y upgrade          # зачем: свежие пакеты безопасности
$ adduser --disabled-password --gecos "" <ssh-user>  # зачем: пользователь, под которым живёт стенд
$ mkdir -p /home/<ssh-user>/.ssh && cp /root/.ssh/authorized_keys /home/<ssh-user>/.ssh/ \
  && chown -R <ssh-user>:<ssh-user> /home/<ssh-user>/.ssh && chmod 700 /home/<ssh-user>/.ssh   # зачем: тот же SSH-ключ для <ssh-user>
$ echo "<ssh-user> ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/<ssh-user>                 # зачем: sudo без пароля (пароля у <ssh-user> нет)
```

Дальше входите как `ssh <ssh-user>@SERVER_IP`.

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
$ sudo usermod -aG docker <ssh-user>   # зачем: запускать docker без sudo
$ exit                             # выйти и войти заново, чтобы группа применилась
PS> ssh <ssh-user>@SERVER_IP
$ docker ps                        # должно вывести пустую таблицу без ошибки
```

Если места на диске мало (40 ГБ), включим ограничение логов — оно уже задано в `compose.prod.yaml` (50 МБ × 5 файлов на контейнер), ничего делать не надо.

---

## 3. Клонирование репозитория на сервер

Репозиторий публичный. Токен и deploy key для `git clone`/`git pull` не нужны.
`<ssh-user>` в примерах — ваш непривилегированный пользователь сервера.

```bash
git clone https://github.com/sergeymcdz-code/flounder.git ~/flounder
cd ~/flounder
git remote set-url origin https://github.com/sergeymcdz-code/flounder.git
git pull --ff-only origin main
git log -1 --oneline
```

Если раньше сохраняли GitHub-токен, удалите настройку credential helper и соответствующую
запись GitHub из локального хранилища credentials; отзовите ненужный токен в GitHub.
Не публикуйте содержимое файла credentials.

---

## 4. Заполнение `.env.prod`

**Зачем:** в этом файле все секреты и домен. Он не попадает в git (есть в `.gitignore`).

```bash
$ cd ~/flounder
$ cp infra/.env.prod.example infra/.env.prod
$ chmod 600 infra/.env.prod                     # зачем: читать может только <ssh-user>
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
| `JWT_SECRET` | строка из `openssl rand -base64 48` (Base64 от ≥32 случайных байт; проверяется при старте) |
| `APP_CORS_ALLOWED_ORIGINS` | `https://` + домен, например `https://demo.flounder.kz` |
| `APP_PAYMENTS_PROVIDER` | `MANUAL` (оставить) |
| `DEMO_SEED` | `true` (оставить) |

Сохранить: `Ctrl+O`, `Enter`, выйти: `Ctrl+X`.

Важно: `JWT_SECRET` менять после запуска нельзя без последствий — все выданные токены входа перестанут работать (пользователям придётся войти заново). `POSTGRES_PASSWORD` после первого запуска менять нельзя вовсе (база уже создана с этим паролем) — если нужно, см. п. 10.

---

## 5. Первый запуск

**Зачем:** скрипт `deploy.sh` скачивает свежий код, собирает образы (бэкенд ~5–8 минут на 2 vCPU в первый раз, фронт ~2 минуты) и запускает все контейнеры. Повторные запуски быстрее благодаря кэшу.

```bash
$ cd ~/flounder
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

## 7. Сид демо-данных (S02)

**Зачем:** наполнить демо-базу организацией и тремя соревнованиями A/B/C с датами относительно дня показа. Скрипт S02 (`scripts/demo/seed.ts`), работает через HTTP API стенда и идемпотентен (повторный запуск дублей не создаёт).

На ноутбуке нужен Node.js 22; на сервере Node устанавливать не требуется — запускать
под `<ssh-user>` в Docker, после настройки `scripts/demo/.env` по README:

```bash
cd ~/flounder
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp \
  -e DEMO_DATE=2026-10-19 -e DEMO_EVENTS=A,B,C \
  -v "$PWD":/repo -w /repo/scripts/demo node:22-alpine sh -eu -c 'npm ci && npx tsx seed.ts'
```

Владелец задаёт DEMO_OWNER_EMAIL отдельного участника в scripts/demo/.env (default `<user-email>`); служебный аккаунт использовать нельзя. До сида удалить прежнее одиночное «Открытый кубок Самары по плаванию (демо)» по инструкции с demo_targets JSON и остановкой API в [README скрипта](../scripts/demo/README.md). Там же — параметры DEMO_DATE/DEMO_EVENTS и сброс выбранных slug. После сида сделайте бэкап (п. 9) — это «чистая» точка, к которой можно откатиться после репетиции.

---

## 8. Логи

**Зачем:** первое, куда смотреть при любой проблеме.

```bash
$ cd ~/flounder
$ docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod logs --tail=200 api     # последние 200 строк бэкенда
$ docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod logs -f api             # «живой» хвост (выход Ctrl+C)
$ docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod logs --tail=100 caddy   # HTTPS, сертификаты, маршрутизация
$ docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod logs --tail=100 postgres
```

Логи бэкенда в JSON (одна строка — одно событие); искать ошибки: `… logs api | grep -i '"level":"ERROR"'`.

Чтобы передать логи Claude для разбора: `… logs --tail=300 api > ~/api.log` и скопировать файл на ноутбук: `PS> scp <ssh-user>@SERVER_IP:~/api.log .`

Удобное сокращение — добавьте в `~/.bashrc` алиас, чтобы не печатать длинную команду:

```bash
$ echo "alias fl='docker compose -f /home/<ssh-user>/flounder/infra/compose.prod.yaml --env-file /home/<ssh-user>/flounder/infra/.env.prod'" >> ~/.bashrc && source ~/.bashrc
$ fl ps   # теперь так
```

---

## 9. Бэкапы и восстановление

Полная инструкция с проверками и откатом: [обслуживание инфраструктуры](infra-hardening.md).
Копии создаются `infra/backup.sh`; `BACKUP_AGE_RECIPIENT` содержит только публичный
ключ age. Приватный ключ остаётся у владельца вне сервера и репозитория.
Шифруются дамп и файловый архив. Без получателя приходит Telegram-предупреждение:
допускается только локальная незашифрованная копия, внешняя отправка запрещена.

```bash
cd ~/flounder
./infra/backup.sh
find ~/backups -maxdepth 1 -type f -name '*.age' -printf '%f\n'
tail -n 20 ~/backups/backup.log
```

Имена файлов в документации условные: `CHOSEN_BACKUP.dump.age` и
`CHOSEN_BACKUP.files.tar.gz.age`. Повтор за ту же минуту сохраняет существующую копию.
Ротация — 30 суток, права 0600, незавершённые временные файлы удаляются при ошибке.
Дамп и файлы снимаются последовательно: для согласованной копии приостановите запись
документов на время бэкапа. Сохраните `APP_FILES_ENCRYPTION_KEY` отдельно у владельца.

`restore.sh CHOSEN_BACKUP.dump.age /путь/к/ключу` проверяет восстановление в отдельной
БД `sport_platform_restore_test` и удаляет её. Выполнять на отдельном узле владельца с
тестовой БД, не переносить приватный ключ на production. Старые `.dump` и `.sql.gz`
поддерживаются без аргумента ключа. Проверка отказывается удалять уже существующую тестовую БД.
Приватные временные файлы расшифровки удаляются при успехе и ошибке.

Расписание `infra/crontab.example`: ежедневно бэкап, еженедельно напоминание владельцу
проверить последнюю зашифрованную копию вне production, ежечасно проверка диска.
Старые открытые дампы проверяются автоматически. Проверка `.age` в cron — это
**напоминание, а не подтверждение SQL-восстановления**. Настройте Telegram по §12;
часовой пояс cron — часовой пояс хоста (`timedatectl`), варианты для UTC/Москвы есть в примере.

`BACKUP_S3_REMOTE` включает внешнюю копию через rclone. Используйте отдельный аккаунт
и ключ только на запись; lifecycle и чтение/восстановление настраивает владелец другим
ключом вне сервера. Подробные команды и права — в [инструкции](infra-hardening.md).

Производственный откат выполняет только владелец в окно обслуживания:
`restore-production.sh CHOSEN_BACKUP.dump`. Зашифрованную копию сначала расшифровать
на узле владельца; на сервер передать только выбранный дамп с правами 0600 и удалить
после приёмки. Скрипт проверяет дамп в отдельной БД, останавливает API, сохраняет свежую
страховочную копию (также `.age`, если задан получатель), заменяет БД и проверяет health.
Если загрузка после удаления БД не удалась, API остаётся остановленным; восстановите
страховочную копию тем же порядком. Для файлов сначала проверить парный архив и
восстановить его в **новый** volume, сохранить исходный до приёмки.

---

## 10. Обновление стенда

**Зачем:** выкатить слитые в `main` изменения. Простой — около минуты (пока API перезапускается); сайт в это время отдаёт ошибку 502.

Способ 1 — на сервере:

```bash
$ cd ~/flounder && ./infra/deploy.sh
```

Ручной workflow развёртывания использует environment `production` и заранее доверенный
ключ сервера с проверкой отпечатка. Подготовка описана в [инструкции владельца](infra-hardening.md).
Для ручного обновления достаточно публичного HTTPS origin без токена. Перед пересозданием
контейнеров сохраните нужные логи; параметры ротации Docker могут потребовать пересоздания.
Обновление выполняйте в согласованное окно обслуживания.

Откат к предыдущей версии кода: `cd ~/flounder && git log --oneline -5` → выбрать коммит → `git checkout <hash> && ./infra/deploy.sh --no-pull`. Вернуться на актуальный `main`: `./infra/deploy.sh`.

Смена `POSTGRES_PASSWORD` после первого запуска: `fl exec postgres psql -U sport_user -d sport_platform -c "ALTER USER sport_user PASSWORD 'новый';"`, затем вписать новый пароль в `.env.prod` и `fl up -d` (пересоздаст api с новым паролем).

---

## 11. Что делать, если упало

Сначала — общая картина:

```bash
$ fl ps -a                 # какие контейнеры живы; Exited/Restarting — проблема
$ df -h /                  # свободно ли место на диске (ниже 1 ГБ — чистить, см. ниже)
$ free -m                  # память; если available < 200 МБ — API мог быть убит по OOM
```

Диагностика CPU и памяти: [аудит S03, §2](perf/2026-10-15_perf_audit.md#2-команды-для-владельца-копировать-по-строке).
После V038 и перезапуска Postgres с `shared_preload_libraries=pg_stat_statements`
медленные запросы видны так:

```bash
$ fl exec postgres psql -U sport_user -d sport_platform -c "select calls, round(total_exec_time::numeric,1) as total_ms, round(mean_exec_time::numeric,2) as mean_ms, left(query,160) as query from pg_stat_statements order by total_exec_time desc limit 10"
```

Продовые лимиты S03: API 1664 МБ (heap 60 %), Postgres 640 МБ, web/Caddy по
128 МБ. Сравнивайте `docker stats` в простое и под нагрузкой с
[локальным отчётом k6](perf/2026-10-16_k6_report.md).

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
| `OutOfMemoryError` или контейнер просто исчез, в `dmesg` есть `Killed process` | мало памяти | `fl restart api`; если повторяется — уменьшить `-XX:MaxRAMPercentage=60` (например, до `45`) в `compose.prod.yaml` или увеличить сервер |
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
$ ls -la /home/<ssh-user>/backups   # старые бэкапы удалить вручную при необходимости
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
| Обновить | `cd ~/flounder && ./infra/deploy.sh` |
| Бэкап | `~/flounder/infra/backup.sh` |
| Восстановить | `~/flounder/infra/restore.sh /home/<ssh-user>/backups/<файл>.dump` (проверка) |
| Перезапуск одного сервиса | `fl restart api` |
| Полный перезапуск | `fl down && fl up -d` |
| Здоровье | `curl -s https://DOMAIN/actuator/health` |

(`fl` — алиас из п. 8; без него: `docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod …` из `~/flounder`.)

## 12. Мониторинг и Telegram (M09)

1. В Telegram открыть `@BotFather`, выполнить `/newbot`. Полученный токен хранить только
в `.env.prod`: `TELEGRAM_BOT_TOKEN`, туда же `TELEGRAM_CHAT_ID`. Написать боту `/start`;
для группы добавить бота и написать ему адресованное сообщение. Chat ID взять из ответа
`getUpdates` через Telegram Bot API в доверенном локальном инструменте (не вставлять токен
в адресную строку браузера/логи). Для группы ID отрицательный. `chmod 600 infra/.env.prod`.
Не отправлять в Telegram дампы, ФИО и секреты: алерты содержат лишь статус инфраструктуры.
2. Под `<ssh-user>` запустить Kuma и отправить тестовый алерт:

```bash
cd ~/flounder
docker compose -f infra/compose.monitoring.yaml up -d
./infra/alert.sh "Flounder: проверка доставки уведомлений"
./infra/check-disk.sh
```

3. С ноутбука открыть туннель `ssh -L 3001:127.0.0.1:3001 <ssh-user>@SERVER_IP`,
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

Staging не меняет checkout `~/flounder`, имеет проект `flounder-staging`, отдельные
БД `sport_platform_staging`, postgres volume, JWT/пароль БД и образы. БД не публикуется наружу.
Только staging gateway подключён к общей edge-сети Caddy. Код выбранной ветки собирается
в `/home/<ssh-user>/flounder-staging`; повторный деплой отказывается затирать локальные изменения.
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

Шаги под `<ssh-user>` после слияния PR **после демо 15.10**, в окно без нагрузки:

```bash
cd ~/flounder
git pull --ff-only origin main
# В окно обслуживания обновить prod compose: подключает Caddy к flounder_edge.
./infra/deploy.sh
mkdir -p /home/<ssh-user>/.config/flounder
cp infra/.env.staging.example /home/<ssh-user>/.config/flounder/.env.staging
chmod 700 /home/<ssh-user>/.config/flounder
chmod 600 /home/<ssh-user>/.config/flounder/.env.staging
nano /home/<ssh-user>/.config/flounder/.env.staging
./infra/deploy-staging.sh feat/M01-admin-panel
curl -fsS http://127.0.0.1:8081/actuator/health
```

В `.env.staging` заполнить независимые секреты БД/JWT, актуальные `ADMIN_EMAIL/ADMIN_PASSWORD`
bootstrap-администратора выделенной staging-БД и `DEMO_PASSWORD`. На чистой БД задайте
`APP_ADMIN_EMAIL`/`APP_ADMIN_PASSWORD` для создания администратора; встроенный аккаунт отключён V050.
Перед обновлением убедиться, что `scripts/demo/.env` на сервере указывает рабочий bootstrap-аккаунт
через `ADMIN_EMAIL`, а не `admin@flounder.demo`; пароли — собственные, не менее 12 символов.
Скрипт дожидается health, проверяет отключение встроенного администратора (ожидается 401), затем запускает **настоящий HTTP-сид** `scripts/demo/seed.ts`
с `DEMO_SEED_ENABLED=true`. Он не полагается на неработающий флаг `DEMO_SEED` backend.
Node/npm на хосте не нужны: сид выполняется в `node:22-alpine` с UID/GID `<ssh-user>`, `HOME=/tmp` и host network. Ветка должна содержать `scripts/demo` и совместимые миграции/API.
Сид обращается к loopback gateway, а не к production. Его профиль процесса `staging`
не меняет серверный prod-профиль, который используется для настроек БД/логов.

У DNS добавить A `staging.sportgate.pro` → этот сервер; после успешной локальной проверки:

```bash
# Сначала подготовить STAGING_BASIC_AUTH_HASH в infra/.env.prod (см. §15).
cp infra/caddy-sites/staging.example infra/caddy-sites/staging.caddy
docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod run --rm --no-deps caddy caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod up -d --no-deps --force-recreate caddy
curl -sS -o /dev/null -w '%{http_code}\n' https://staging.sportgate.pro/actuator/health
# Ожидается 401; затем curl --user staging (пароль вводится интерактивно).
```

Открыть `/e/PUBLIC_SLUG` из вывода staging-сида, проверить вход организатора и импорт.
Для остановки (данные сохраняются):

```bash
docker compose --project-name flounder-staging -f infra/compose.staging.yaml \
  --env-file /home/<ssh-user>/.config/flounder/.env.staging stop
```

После остановки staging убрать его сайт `rm infra/caddy-sites/staging.caddy` и перезагрузить
Caddy той же командой, чтобы не оставлять публичный 502. Не использовать `down -v` у prod.

## 14. Correlation-id и опциональный Sentry (M09)

JSON ECS в `application-prod.yml` уже включён; Docker ограничивает журналы 50 МБ × 5
на контейнер (также staging/Kuma). Фильтр до Spring Security принимает безопасный
`X-Request-Id` (буквы/цифры/`._-`, 1–128 знаков), иначе генерирует UUID;
записывает `correlation-id` в MDC и возвращает заголовок. CORS разрешает/открывает этот
заголовок. Проверка под `<ssh-user>`:

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

## 15. Обслуживание SSH и Caddy

`infra/harden.sh` запускается владельцем отдельно от деплоя. Он проверяет непустой
`authorized_keys`, требует отдельный вход по ключу до изменений и после перезапуска SSH,
затем включает UFW. Root SSH отключается полностью; существующую сессию не закрывать
до проверки нового входа и sudo. Политика fail2ban управляется только скриптом.

Порядок сохранения настроек, выполнения, проверки и отката: [инструкция владельца](infra-hardening.md).
Нужен рабочий ключ непривилегированного пользователя и доступ к консоли провайдера.

CSP изначально работает в `Content-Security-Policy-Report-Only`. После браузерной проверки
QA05 переключить `CSP_HEADER=Content-Security-Policy` в `.env.prod` и пересоздать Caddy.
Хеш inline-скрипта темы проверяется в CI по исходному и собранному `index.html`.
У staging включены те же заголовки и basic auth: добавьте `STAGING_BASIC_AUTH_HASH`
в **production** `.env.prod`, поскольку внешний Caddy обслуживает оба домена.
Хеш генерируется интерактивной командой `docker exec -it flounder-caddy caddy hash-password`;
в env заключите его в одинарные кавычки. Для staging health без credentials ожидается 401.
При добавлении хеша пересоздайте Caddy; одного reload недостаточно для новых env.
Монитор staging должен отправлять basic auth или проверять loopback gateway.

# Деплой партии 1 (14.10): M09 инфра, M02 аутентификация, QA01-правки

Слиты в `main` 13.10 ≈21:40 МСК: #25 (M09), #29 (M02), #39 (QA01 follow-ups). CI зелёный. Миграции: V029 `password_reset_tokens`, V030 `refresh_tokens` (только create table).

## Шаг 0 — переменные окружения (ДО deploy.sh)
```bash
ssh root@82.97.248.229
su - deploy
cd ~/flounder && nano infra/.env.prod
```
Добавить строки (значения без кавычек, если нет пробелов):
```
APP_PUBLIC_URL=https://sportgate.pro
APP_AUTH_LOGIN_MAX_FAILURES=20
```
`APP_AUTH_LOGIN_WINDOW` и `APP_JWT_ACCESS_TTL` не задавать (остаются 15 мин и 2 ч). После демо 19.10 вернуть `APP_AUTH_LOGIN_MAX_FAILURES=5`.

## Шаг 1 — деплой (~1–2 мин недоступности: compose пересоздаёт все контейнеры из-за новых настроек логов)
```bash
./infra/deploy.sh
curl -fsS -D - -H 'X-Request-Id: m09-check' https://sportgate.pro/actuator/health   # ожидается X-Request-Id: m09-check в ответе и {"status":"UP"}
```

## Шаг 2 — проверки M02 (5 минут, в браузере)
1. Вход `org@flounder.demo / Demo2026!` → кабинет открывается (старые сессии в браузере тоже должны остаться живыми).
2. «Выйти» → снова вход — ок. 
3. Одна опечатка в пароле → «Неверная почта или пароль», затем верный пароль → вход (счётчик сброшен).
4. Профиль → «Сменить пароль» у `parent@flounder.demo`: текущий `Demo2026!`, новый `Parent2026x` → сообщение «Пароль изменён…», повторный вход новым паролем. (Потом вернуть `Demo2026!` тем же путём — сид ожидает его.)
5. «Забыли пароль?» → ввести любой email → всегда «Если адрес зарегистрирован, письмо отправлено»; письмо пока не уходит (M05 не слит), ссылка пишется в лог `docker logs flounder-api | grep reset-password` с `https://sportgate.pro/...`.

## Команды для Сергея после деплоя

Вход как обычно: `ssh root@82.97.248.229`, затем `su - deploy`. Всё ниже — под `deploy`, из `~/flounder`, после слияния #25 и `git pull --ff-only origin main` (сами контейнеры от pull не меняются).

**1. Бэкап и каталог (можно до демо, без редеплоя):**
```bash
cd ~/flounder && readlink -f ~/flounder          # ожидается /home/deploy/flounder
mkdir -p /home/deploy/backups && chmod 700 /home/deploy/backups
cp -n ~/flounder/backups/2026-10-13.sql.gz /home/deploy/backups/   # старый дамп, оригинал остаётся
./infra/backup.sh
ls -la /home/deploy/backups/ && tail -n 5 /home/deploy/backups/backup.log
```

**2. Проверка restore на тестовой БД (рабочую БД и API не трогает):**
```bash
./infra/restore.sh /home/deploy/backups/2026-10-13.sql.gz            # старый формат
./infra/restore.sh "$(ls -t /home/deploy/backups/flounder-*.dump | head -1)"   # свежий .dump
docker exec flounder-postgres psql -U sport_user -d postgres -Atc \
  "select count(*) from pg_database where datname='sport_platform_restore_test'"   # ожидается 0
curl -fsS https://sportgate.pro/actuator/health                       # {"status":"UP"}
```
Ожидать строки `[restore] … OK: events=N` в выводе; если Telegram не настроен, будет `[alert] Telegram is not configured` — это нормально.

**3. Cron (реальный путь, timezone хоста не меняем):**
```bash
timedatectl | grep 'Time zone'     # если UTC — брать UTC-вариант ниже, если Europe/Moscow — первый
crontab -l                         # сохранить старые строки, кроме старого backup.sh
crontab -e
```
Вставить **один** вариант (пути уже реальные, `$HOME` = `/home/deploy`):
```cron
SHELL=/bin/bash
PATH=/usr/local/bin:/usr/bin:/bin
# Хост в Europe/Moscow: бэкап 03:00, проверка восстановления вс 04:00, диск раз в час
0 3 * * * "$HOME/flounder/infra/backup.sh"
0 4 * * 0 "$HOME/flounder/infra/verify-latest-backup.sh"
0 * * * * "$HOME/flounder/infra/check-disk.sh" >> "$HOME/backups/backup.log" 2>&1
```
```cron
# Хост в UTC (то же время по Москве):
0 0 * * * "$HOME/flounder/infra/backup.sh"
0 1 * * 0 "$HOME/flounder/infra/verify-latest-backup.sh"
0 * * * * "$HOME/flounder/infra/check-disk.sh" >> "$HOME/backups/backup.log" 2>&1
```
Проверить: `crontab -l`; на следующий день — `ls /home/deploy/backups/` и `tail /home/deploy/backups/backup.log`.

**4. Внешняя копия на ноутбук (PowerShell, с ноутбука):**
```powershell
scp root@82.97.248.229:/home/deploy/backups/flounder-*.dump .
scp root@82.97.248.229:/home/deploy/flounder/backups/2026-10-13.sql.gz .
```

**5. Telegram-алерты (опционально, 5 минут):** создать бота у @BotFather, получить токен; написать боту `/start`; узнать chat_id: `https://api.telegram.org/bot<TOKEN>/getUpdates` → `"chat":{"id":…}`. Затем на сервере:
```bash
nano ~/flounder/infra/.env.prod      # добавить TELEGRAM_BOT_TOKEN=123456:abc…  и TELEGRAM_CHAT_ID=…
chmod 600 ~/flounder/infra/.env.prod
./infra/alert.sh "Flounder: тест уведомления"   # должно прийти сообщение
```
После этого cron сам будет присылать: ошибку бэкапа, результат воскресной проверки, диск >85 %.

**6. Редеплой prod с новым compose — в окно без нагрузки (демо перенесено на 19.10)** (~1 мин недоступности, пересоздаются все контейнеры, история `docker logs` теряется):
```bash
cd ~/flounder && ./infra/deploy.sh
curl -fsS -D - -H 'X-Request-Id: m09-check' https://sportgate.pro/actuator/health   # в ответе X-Request-Id: m09-check
```
Если после этого слит и M04: в `infra/.env.prod` добавить `FILES_VOLUME=flounder_flounder_files` и запустить `./infra/backup.sh` — должен появиться `flounder-….files.tar.gz`.

**7. Staging — отдельным блоком, после демо.**
Сначала у регистратора: A-запись `staging.sportgate.pro` → `82.97.248.229` (HSTS prod уже с `includeSubDomains`, staging будет только HTTPS, сертификат Caddy выпустит сам). Затем:
```bash
mkdir -p ~/.config/flounder && cp ~/flounder/infra/.env.staging.example ~/.config/flounder/.env.staging
chmod 600 ~/.config/flounder/.env.staging
nano ~/.config/flounder/.env.staging   # свои POSTGRES_PASSWORD, JWT_SECRET (≥32 символа), ADMIN_PASSWORD, DEMO_PASSWORD
cd ~/flounder && ./infra/deploy-staging.sh main        # или ветка PR; Node на сервере не нужен
curl -fsS http://127.0.0.1:8081/actuator/health          # staging локально
cp infra/caddy-sites/staging.example infra/caddy-sites/staging.caddy
docker exec flounder-caddy caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
docker exec flounder-caddy caddy reload   --config /etc/caddy/Caddyfile --adapter caddyfile
curl -fsS https://staging.sportgate.pro/actuator/health
```
Остановить staging (данные сохраняются) и убрать публичный сайт:
```bash
docker compose --project-name flounder-staging -f ~/flounder/infra/compose.staging.yaml \
  --env-file ~/.config/flounder/.env.staging stop
rm ~/flounder/infra/caddy-sites/staging.caddy
docker exec flounder-caddy caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile
```
Память: при живом prod сборка staging-образов на 4 ГБ может задеть `flounder-api` OOM-killer'ом — поэтому только в окно без нагрузки; перед prod-деплоем staging лучше останавливать.

**Не запускать `./infra/harden.sh`**, пока на ноутбуке нет рабочего ключа для `deploy@82.97.248.229` (скрипт сам откажет без `authorized_keys`, но проверку второго входа он делать не умеет — это делаете вы во втором терминале).


# M09 — ревью PR #25 (итерация 2)

PR: #25 «chore(infra): M09 verified backups, monitoring and isolated staging», ветка `chore/M09-infra-backups-monitoring`.
Проверяемый коммит: `9288f2a` «fix(infra): address M09 review and script production recovery» (диф `08a48e6..9288f2a`, 12 файлов, +317/−107).
Предыдущее ревью: `docs/reviews/M09-review-1.md` (3 блокера B1–B3, 10 желательных D1–D10).
CI run #74 для `9288f2a`: backend / frontend / infra — success. `bash -n` всех `infra/*.sh` и `infra/tests/*.sh` — чисто.

## Вердикт

**Approve — сливать.** Все три блокера закрыты именно так, как просили, без побочных изменений: `restore.sh`, `deploy.sh`, `compose.prod.yaml`, `RequestIdFilter`, CI не тронуты. Из желательных закрыто 9 из 10; D10 закрыт **неверно по вине ревью 1** (см. ниже, не блокирует). Новых обязательных переменных в `.env.prod` нет, `deploy.sh` после слияния работает как раньше. Конфликтов с `origin/main` (63263d1, включает M01/M10/QA02 r2) и `pr/29` (M02) нет; с `pr/38` (M04) — два тривиальных «оба добавили в одно место» конфликта.

Что остаётся **не закрытым** (не блокирует, но нужно учесть при слиянии M04):
1. **N1 (важно при слиянии M04).** `backup.sh` ищет том `FILES_VOLUME=flounder_files`, а compose-проект называется `flounder` (`name: flounder`), поэтому том M04 на сервере будет `flounder_flounder_files` (проверено `docker compose config` на `pr/38`). После слияния M04 архив файлов **молча не будет создаваться**. Правка: в `.env.prod` задать `FILES_VOLUME=flounder_flounder_files` (или сменить дефолт в `backup.sh:27`). Делает тот PR, который сливается вторым, либо отдельным коммитом.
2. **N2 (моя ошибка в ревью 1, D10).** `.github/workflows/deploy.yml` **существует** в `main` и в PR. Я написал, что его нет; Codex поверил, убрал из runbook «Способ 2 — деплой из GitHub» и написал «Автоматический deploy workflow отсутствует». Текст теперь неверен. Правка: вернуть абзац (или одну строку «есть ручной workflow *Deploy demo stand*, в поле `app_dir` вводить `/home/deploy/flounder`») и поправить дефолт `app_dir: /opt/flounder` → `/home/deploy/flounder` в `deploy.yml`. Можно отдельным docs-коммитом после слияния.

## Проверено по 7 пунктам

| # | Пункт | Статус | Где / что проверено |
|---|---|---|---|
| 1 | **B1** сид staging без Node на хосте | **Закрыт** | `infra/deploy-staging.sh:44-48`: `docker run --rm --network host --user "$(id -u):$(id -g)" -e HOME=/tmp -e API_URL=http://127.0.0.1:8081 … -e ADMIN_EMAIL -e ADMIN_PASSWORD -e DEMO_PASSWORD -v "$STAGING_DIR":/repo -w /repo/scripts/demo node:22-alpine sh -c 'npm ci && npx tsx seed.ts'`. Секреты передаются как `-e NAME` (из окружения после `set -a; source .env.staging`), не в argv. `scripts/demo/package-lock.json` есть, `tsx` в devDependencies, `**/node_modules/` в `.gitignore` → dirty-tree guard при следующем запуске не сработает ложно. Порт `127.0.0.1:8081` опубликован staging-caddy (`compose.staging.yaml:67`), с `--network host` доступен. Тест `staging-test.sh:41-47` теперь проверяет именно вызов `docker run` со всеми 13 аргументами, мок `npm` удалён. |
| 2 | **B2** `harden.sh`: `prohibit-password` + guard | **Закрыт** | `harden.sh:4` guard только `id -un == deploy` (работает под `su - deploy`); `:15-17` отказ при пустом `/home/deploy/.ssh/authorized_keys` **до** запроса подтверждения; `:18-19` `client_addr` из `SSH_CONNECTION` или `127.0.0.1`; `:31-32` пишет `PermitRootLogin prohibit-password`, проверяет `sshd -t` и `sshd -T … | grep -qx 'permitrootlogin prohibit-password'`, rollback при ошибке. Новый `infra/tests/harden-test.sh` с mock `sudo`/`id` (без изменений хоста): нет ключа → отказ; «no» → файл не создан; подтверждение → файл с `prohibit-password`, `addr=127.0.0.1`; повтор идемпотентен; `FAIL_SSHD=1` → откат прежнего файла. Запускается из `staging-test.sh` → в CI infra job. Runbook §15: «до появления ключа deploy на ноутбуке не запускать», «root по ключу продолжает работать». |
| 3 | **B3** пути `/opt/flounder` → `~/flounder` | **Закрыт** | `git grep /opt/flounder pr/25` — остался только `.github/workflows/deploy.yml` (см. N2). `crontab.example:9-11` — `"$HOME/flounder/infra/…"` при `SHELL=/bin/bash` (cron задаёт HOME из passwd, раскрытие работает); лог check-disk → `"$HOME/backups/backup.log"`. Runbook: все `cd`, alias `fl`, таблица команд, §13 staging — на `~/flounder`/`/home/deploy/flounder`; добавлена фраза про `readlink -f ~/flounder`. |
| 4 | **D1** check-disk раз в час | Закрыт | `crontab.example:11` `0 * * * *`; дедупликации нет (допустимо, в ревью 1 предлагалось «или»). Макс. 24 сообщения/сутки. |
| 5 | **D5** `restore-production.sh` вместо `set -e` в интерактиве | **Закрыт, качественно** | Новый `infra/restore-production.sh` (67 строк): usage/`-r`, формат `.dump`/`.sql.gz`, защита от `postgres/template*/sport_platform_restore_test`, `flock`, подтверждение `restore-production` **до** установки trap (отказ не шлёт алерт), затем проверка дампа через неизменённый `restore.sh` **до** остановки API; `compose stop api` → свежий `backup.sh` в уникальный `mktemp -d "$BACKUP_DIR/pre-restore-…-XXXXXX"` (с `ENV_FILE=/dev/null`, чтобы не пересорсить `BACKUP_DIR`; `-f /dev/null` ложно — проверено в `lib.sh:6`) → проверка, что дамп один и непустой → `drop_attempted=1` → DROP/CREATE/load → smoke `count(*) from events` → `compose start api` → `curl` health UP. Cleanup: ошибка до DROP → API запускается; после DROP → остаётся остановленным, печатает путь страховочной копии, алерт. Ротация `find -maxdepth 1 -type f` подкаталог `pre-restore-*` не трогает; `verify-latest-backup.sh` его тоже не видит. Тест `maintenance-test.sh:71-113` на одноразовом PostgreSQL: отказ без подтверждения (0 вызовов docker), битый SQL не останавливает API, свежая копия с `events=2` при уже существующем дампе той же минуты, `FAIL_SAFETY_BACKUP` → нет DROP + API restart, `FAIL_PROD_LOAD` → API не запущен. Runbook §10 переписан на скрипт. |
| 6 | **D3/D7/D2/D6/D8/D10** timezone, TZ staging, deploy.sh, scp, кавычки, список файлов | Закрыт (кроме D10) | D3: `crontab.example:2-6` «проверь `timedatectl`», `restart cron` после смены, вариант UTC закомментирован; runbook §9 «перед демо timezone не менять». D7: `compose.staging.yaml:43` `TZ: ${TZ:-Europe/Samara}`. D2: runbook §13 `./infra/deploy.sh` вместо голого `compose up`. D6: блок `scp root@SERVER_IP:…` для `.dump` и `2026-10-13.sql.gz`, предупреждение о 30-дневной ротации. D8: `.env.prod.example:4-5` про одинарные кавычки. D10: список файлов расширен, но утверждение «deploy workflow отсутствует» неверно — см. N2. |
| 7 | **D4** повтор `backup.sh` в ту же минуту | Закрыт | `backup.sh:22` → `exit 0` с сообщением «keeping existing backup», алерт не шлётся (trap смотрит на status≠0). Тест: мок `date` фиксирует `20301013-0300`, второй вызов успешен. В `restore-production.sh` уникальный каталог гарантирует, что это «exit 0» не подменит свежую копию. |

**Регрессии:** не найдено. `restore.sh`, `alert.sh`, `check-disk.sh`, `lib.sh`, `deploy.sh`, `compose.prod.yaml`, `compose.monitoring.yaml`, CI — байт-в-байт как в `08a48e6`. `compose.staging.yaml` — одна строка TZ. Prod после слияния: обязательные переменные те же (`DOMAIN ACME_EMAIL POSTGRES_PASSWORD JWT_SECRET`), новые — с дефолтами/пустые; первый `deploy.sh` пересоздаст контейнеры (logging options) и будет на 1–3 мин дольше (Sentry) — как и было описано в ревью 1, Codex это внёс в runbook и описание PR.

## Блокирующие

Нет.

## Желательные

- **N1** `FILES_VOLUME` ↔ `flounder_flounder_files` (см. вердикт). Одна строка; важно до того, как M04 начнёт писать файлы.
- **N2** вернуть «Способ 2» в runbook и дефолт `app_dir` в `deploy.yml` (см. вердикт). Ответственность ревью 1.
- **N3** `restore-production.sh:62-63`: `--retry-max-time 120` на health после `compose start api`. `deploy.sh` ждёт до 3 мин; холодный старт API на 4 ГБ VPS бывает 60–90 с. Поднять до `--retry 36 --retry-max-time 300`, чтобы не получить «health is not UP» + алерт при фактически успешном откате (БД уже восстановлена, API запущен — не опасно, но путает).
- **N4** `restore.sh`, вызванный из `restore-production.sh`, шлёт в Telegram «restore verification OK» перед самим откатом — лишнее сообщение. Можно подавить через переменную (`RESTORE_QUIET=1`), не обязательно.
- **N5** Runbook §7 (сид prod в Docker) не передаёт `-e API_URL/ADMIN_*` и рассчитывает на `scripts/demo/.env` в чекауте — работает (`process.loadEnvFile`), но стоит явно сказать «создать `scripts/demo/.env` из `.env.example` с `API_URL=https://sportgate.pro`», иначе сид упадёт с «Задайте API_URL».

## Порядок слияния

1. **#25 (M09) можно сливать сейчас**, после демо 15.10 — как и планировалось (редеплой контейнеров, Kuma, staging, harden — только в окно без нагрузки). Бэкап-скрипты можно использовать на сервере до демо без редеплоя (через `git checkout origin/<ветка> -- infra/`, как в ревью 1).
2. `pr/29` (M02) — независим, конфликтов нет, порядок любой.
3. `pr/38` (M04) — после M09: при merge два конфликта «оба добавили»: `infra/.env.prod.example` (блок TELEGRAM/BACKUP/SENTRY vs блок FILES_*/APP_FILES_*; оставить оба) и `sport-platform/pom.xml` (`io.sentry:sentry-spring-boot-starter-jakarta:8.22.0` vs `software.amazon.awssdk:s3:2.31.68`; оставить обе зависимости). `compose.prod.yaml` сливается чисто (M09 меняет logging/network, M04 — volume/env). Сразу в M04 (или первым коммитом после) добавить N1: `FILES_VOLUME=flounder_flounder_files`.
4. N2/N3 — docs/infra follow-up одним коммитом в `main` после слияния, не держать PR.

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

**6. Редеплой prod с новым compose — только после демо 15.10, в окно без нагрузки** (~1 мин недоступности, пересоздаются все контейнеры, история `docker logs` теряется):
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

# M09 — ревью PR #25 (итерация 1)

PR: #25 «chore(infra): M09 verified backups, monitoring and isolated staging», ветка `chore/M09-infra-backups-monitoring`, коммит `08a48e6`, база `main a37fcb5`.
Задание: `tasks/M09_Инфраструктура_бэкапы_мониторинг_staging.md` + уточнения (совместимость `restore.sh` со старым `.sql.gz`, `npm test` в CI обязателен, Sentry/harden опциональны, ничего не выполнять на сервере).
CI run #57: backend / frontend / **infra (новый job)** — success.

## Вердикт

**Слить после правок.** Ядро (backup/restore/cron/alert, CI, correlation-id, Kuma, compose staging) сделано качественно и безопасно для prod: новых обязательных переменных нет, все новые env имеют default, `deploy.sh` не менялся, `caddy validate`/`compose config` проходят, конфликтов с `main` и открытыми PR (#15, #18, #21, #24, #26) нет. Три находки блокируют в том виде, как PR описывает приёмку на **реальном** сервере (см. `docs/09_HANDOFF…`): staging-деплой падает без Node на хосте, `harden.sh` закрывает root-вход, которым пользуется Сергей, и все пути/крон привязаны к `/opt/flounder`, тогда как стенд живёт в `~/flounder`.

Бэкап-скрипты (`backup.sh`, `restore.sh`, `verify-latest-backup.sh`, `alert.sh`, cron) можно использовать на стенде **до демо 15.10 без редеплоя контейнеров** — они работают через `docker exec flounder-postgres` и не трогают compose. Всё остальное (редеплой prod с новым compose, Kuma, staging, harden) — после демо.

## Проверено

| # | Пункт задания | Статус | Где |
|---|---|---|---|
| 1 | `pg_dump -Fc` → `/home/deploy/backups/flounder-YYYYMMDD-HHMM.dump`, атомарная запись `.tmp`→`mv`, проверка `pg_restore --list`, `flock`, ротация 30 дней, `backup.log`, ненулевой код + алерт при ошибке, условный tar тома `flounder_files`, опциональный rclone | OK | `infra/backup.sh:3-42`, `infra/lib.sh:16-20` |
| 1 | `restore.sh` → только `sport_platform_restore_test`, `select count(*) from events`, `dropdb --force` в trap, отказ если тест-БД уже существует (`created=0` → не удаляется чужая), поддержка `.dump` (pg_restore) и legacy `.sql.gz` (gzip→psql, `ON_ERROR_STOP`, `--single-transaction`) | OK | `infra/restore.sh:14-45` |
| 1 | Совместимость со старым `backups/2026-10-13.sql.gz`: старый `backup.sh` на main делал `pg_dump --no-owner --no-privileges` без `--create/--clean` (`origin/main:infra/backup.sh`), значит plain-дамп грузится в тестовую БД штатно. Regression-тест на эквивалентном формате — `infra/tests/maintenance-test.sh:45-47` | OK | |
| 1 | Cron: 03:00 бэкап, вс 04:00 restore-проверка, диск | OK c оговорками (B3, D1, D3) | `infra/crontab.example:6-8` |
| 1 | Runbook «Восстановление за 10 минут» | есть, но с опасным `set -e` в интерактивной сессии (D5) | `docs/runbook-demo.md:320-347` |
| 2 | Uptime Kuma, порт только на `127.0.0.1:3001`, mem_limit, инструкция по боту, мониторы health/keyword UP, API slug, страница, TLS 14/7 | OK | `infra/compose.monitoring.yaml`, runbook §12 |
| 2 | `alert.sh`: токен из `.env.prod`, не в argv (`curl --config -` через stdin), валидация формата, без вывода ответа | OK, секретов в репо нет (`.env.prod.example:34-35` пустые) | `infra/alert.sh:11-17` |
| 2 | `check-disk.sh` >85 % для `/`, backups, `/var/lib/docker` | OK (спам — D1) | `infra/check-disk.sh:7-11` |
| 3 | JSON-логи: `logging.structured.format.console: ecs` уже был на main (встроенный structured logging Boot 3.5, новых зависимостей не требует); MDC попадает в ECS автоматически | OK | `application-prod.yml:51-54` |
| 3 | `X-Request-Id` → MDC `correlation-id` → заголовок ответа; whitelist `[A-Za-z0-9._-]{1,128}` (защита от log-injection), `HIGHEST_PRECEDENCE` (до Spring Security — 401/403 тоже коррелируются), восстановление MDC в `finally`; CORS allowed+exposed; unit-тест | OK | `config/RequestIdFilter.java:16-47`, `CorsConfig.java:32-38`, `RequestIdFilterTest.java` |
| 3 | Docker json-file 50m×5 (было 20m×5) | OK; смена опций логирования = пересоздание **всех** контейнеров (в т.ч. postgres) при следующем `deploy.sh` → ~20–40 с простоя API, старые `docker logs` теряются | `infra/compose.prod.yaml:8-12` |
| 3 | Sentry: `sentry-spring-boot-starter-jakarta 8.22.0` в pom, `sentry.dsn: ${SENTRY_DSN:}` (пустой DSN = SDK выключен, транспорт не создаётся), `send-default-pii: false`, traces 0; фронт — динамический `import('@sentry/react')` только при `VITE_SENTRY_DSN` | OK | `pom.xml:46-50`, `application-prod.yml:66-73`, `frontend/src/main.tsx:10-17` |
| 4 | `compose.staging.yaml`: project `flounder-staging`, своя БД/пользователь/volume/образы `flounder-staging-*`, порт только `127.0.0.1:8081`, внешняя сеть `flounder_edge` только у gateway, лимиты памяти; `deploy-staging.sh` — отдельный worktree, prod checkout не переключается, guard домена/БД, dirty-tree guard; тесты в CI | OK по изоляции; **падает на сиде без Node (B1)** | `infra/compose.staging.yaml`, `infra/deploy-staging.sh:24-50` |
| 4 | Caddy: `import /etc/caddy/sites/*.caddy` (пустой glob — только warning, не ошибка), `./caddy-sites` в репо (`.gitignore` + `staging.example`), `staging.caddy` включается вручную | OK; **нужна A-запись `staging.sportgate.pro` → 82.97.248.229 (Сергей)** | `infra/Caddyfile:40`, `infra/compose.prod.yaml:92-97,108-110` |
| 4 | Оценка 2 vCPU/4 ГБ: prod ~1.8–2.4 ГБ + staging лимиты 1.3 ГБ + Kuma 0.4 + Maven-сборка в Docker (не лимитирована) → запаса нет; рекомендация 4/8 или отдельный VPS | Согласен | runbook §13 |
| 5 | CI: `npm ci` → `npm test` (`node --test tests/ && vitest run`) → lint → build; кеши maven (setup-java) и npm (setup-node) сохранены; `mvn -B test` не тронут; бейдж в README; новый job `infra` (shellcheck + 3 regression-теста с реальным postgres в Docker) | OK | `.github/workflows/ci.yml:21-48` |
| 6 | `harden.sh`: fail2ban (backend systemd — верно для Ubuntu 24.04 без rsyslog), unattended-upgrades, идемпотентен; root-шаг только после ввода `deploy-key-verified`, `sshd -t` + `sshd -T` + откат | идемпотентно — да; **политика `PermitRootLogin no` неверна для этого стенда (B2)** | `infra/harden.sh:7-13, 26` |
| 7 | `git merge-tree --write-tree origin/main pr/25` — чисто; с `origin/{feat/M01-admin-panel, qa/QA02, qa/QA03, qa/QA06, fix/M10}` — чисто | OK | |
| — | `bash -n` всех `infra/*.sh`, `infra/tests/*.sh` — OK; `docker compose config --quiet` prod/staging — OK локально; shellcheck — в CI (локально не установлен) | OK | |
| — | Миграции не добавлены; эндпоинтов нет; `qa04-endpoints.tsv` менять не нужно | OK | |

## Блокирующие

**B1. `deploy-staging.sh` требует Node/npm на хосте — на сервере Node нет.**
`infra/deploy-staging.sh:45-50` делает `cd scripts/demo && npm ci && npm exec -- tsx seed.ts`. По `docs/09_HANDOFF…` («на сервере нет Node — запускать через `docker run … node:22-alpine`») команда упадёт с `npm: command not found` **после** того, как `compose up --build --wait` уже поднял staging; `set -e` завершит скрипт кодом ≠0, сид не выполнится, публичного slug не будет. Runbook (`docs/runbook-demo.md:558`) перекладывает установку Node 22+ на оператора — это лишняя зависимость на боевом хосте. Нужно запускать сид так же, как в handoff: контейнер `node:22-alpine` с `--network host` (чтобы `API_URL=http://127.0.0.1:8081` был доступен), `--user "$(id -u):$(id -g)" -e HOME=/tmp`, монтируя `$STAGING_DIR`, и прокидывая `ADMIN_EMAIL/ADMIN_PASSWORD/DEMO_PASSWORD/DEMO_SEED_ENABLED/NODE_ENV/SPRING_PROFILES_ACTIVE` через `-e`. Тест `infra/tests/staging-test.sh:34-37,45` подменяет `npm` и поэтому не ловит проблему — переписать проверку на вызов `docker run … node:22-alpine`.

**B2. `harden.sh` ставит `PermitRootLogin no` — это отрежет единственный рабочий вход Сергея.**
`infra/harden.sh:26`: `printf 'PermitRootLogin no\n'`; runbook `:620,623` («Root SSH должен отказать»). По handoff Сергей заходит `ssh root@82.97.248.229` и делает `su - deploy`; ключ deploy у него на ноутбуке может отсутствовать. Задание просит отключить **вход root по паролю** после проверки ключа deploy, т.е. `PermitRootLogin prohibit-password` (ключ root продолжает работать) и/или `PasswordAuthentication no`. Дополнительно: guard `infra/harden.sh:4` (`SSH_CONNECTION` непустой и `id -un == deploy`) при `su - deploy` не выполняется (`su -` сбрасывает окружение) — скрипт откажет с невнятным сообщением; `SSH_CONNECTION%% *` в `sshd -T -C addr=` тогда пуст. Исправить: `prohibit-password`; проверять `sshd -T … | grep -qx 'permitrootlogin prohibit-password'`; перед подтверждением проверять, что `/home/deploy/.ssh/authorized_keys` непустой; в runbook §15 — явное предупреждение, что до появления ключа deploy на ноутбуке скрипт запускать нельзя. Скрипт опциональный и до демо не выполняется, но в текущем виде его нельзя оставлять в main как инструкцию.

**B3. Все пути привязаны к `/opt/flounder`, а стенд живёт в `~/flounder` (`/home/deploy/flounder`).**
`infra/crontab.example:6-8`, runbook `:473`, `:541`, описание PR (шаги 1–5). По handoff деплой — `su - deploy -c 'cd ~/flounder && ./infra/deploy.sh'`, старый бэкап — `~/flounder/backups/2026-10-13.sql.gz` (старый `backup.sh` писал в `$REPO_DIR/backups`, каталог в `.gitignore`). Если `/opt/flounder` не существует (не symlink), cron молча не будет делать бэкапы (cron пишет ошибку только в mail). Правка: в `crontab.example` использовать `$HOME/flounder/infra/...` (SHELL=/bin/bash уже задан, `$HOME` раскрывается) или явный комментарий «подставьте `readlink -f ~/flounder`», в runbook один раз зафиксировать реальный путь. (Несоответствие унаследовано от T09-runbook, но M09 делает его критичным — cron.)

## Желательные

**D1.** `infra/crontab.example:8` — `check-disk.sh` каждые 15 минут при диске >85 % шлёт 96 сообщений в сутки. Сделать `0 * * * *` или дедупликацию через state-файл в `$BACKUP_DIR` (алерт раз в 6–12 ч, пока состояние не изменилось).

**D2.** Описание PR шаг 4 и runbook `:543` предлагают голый `docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod up -d --build` вместо `./infra/deploy.sh` (который проверяет `.env.prod`, делает `--remove-orphans`, ждёт health и чистит dangling-образы). Указать `./infra/deploy.sh` (или `--no-pull`).

**D3.** Timezone cron. `infra/crontab.example:2` и runbook `:295` требуют `timedatectl set-timezone Europe/Moscow`, но не говорят, что Debian/Ubuntu cron читает TZ при старте — нужен `sudo systemctl restart cron` после смены. Проще и безопаснее перед демо не трогать TZ хоста, а дать второй вариант строк в UTC (`0 0 * * *` = 03:00 МСК, `0 1 * * 0` = 04:00 МСК) с инструкцией «сначала `timedatectl | grep 'Time zone'`».

**D4.** `infra/backup.sh:11,22` — имя с точностью до минуты; второй запуск в ту же минуту завершается кодом 1 **и шлёт алерт «backup failed»** (ложная тревога при ручном повторе). Либо `%H%M%S`, либо суффикс, либо выход 0 с сообщением «already exists».

**D5.** Runbook «Восстановление за 10 минут» `:328-347`: блок начинается с `set -euo pipefail` и рассчитан на вставку в **интерактивную** SSH-сессию. В интерактивном bash `set -e` при первой же ошибке (например, опечатка в `$FILE`) завершает сам shell → SSH-сессия закрывается, API остаётся остановленным, `set -u` ломает дальнейшую работу в сессии. Вынести в `infra/restore-production.sh` с подтверждением `restore-production`, снятием свежего бэкапа и теми же шагами (фактически это старый `restore.sh` с `-Fc`) — тогда и «10 минут» измеримы. Также в блоке используется `$FILE` из шага 1 — если шаги выполняются в разных сессиях, переменная пуста.

**D6.** `infra/backup.sh:40-41` — ротация удаляет и `*.sql.gz` старше 30 дней, т.е. единственный доисторический бэкап `2026-10-13.sql.gz` исчезнет 12.11. Приемлемо, но в runbook явно сказать «хотя бы один дамп скопировать с сервера» и дать `scp`-команду (как было в старом §9).

**D7.** `infra/compose.staging.yaml:43` — `TZ: Europe/Moscow`, в prod `Europe/Samara` (`compose.prod.yaml:55`). Staging должен воспроизводить prod-поведение времени (T11 — время площадки); выровнять на `${TZ:-Europe/Samara}`.

**D8.** `infra/lib.sh:6` — `.env.prod` сорсится bash'ем (`set -a; source`). Унаследовано от старого `backup.sh`, но стоит оставить комментарий в `.env.prod.example`: значения с пробелами/`$`/`#` нужно брать в одинарные кавычки, иначе `backup.sh`/`alert.sh` упадут, хотя compose их примет.

**D9.** `sentry-spring-boot-starter-jakarta` добавлен безусловно → первый `deploy.sh` после слияния инвалидирует слой `dependency:go-offline` и соберётся дольше (+1–3 мин). Не дефект, предупредить Сергея.

**D10.** `docs/runbook-demo.md:8` всё ещё перечисляет `.github/workflows/deploy.yml`, которого нет; заодно обновить список файлов (`deploy-staging.sh`, `alert.sh`, `check-disk.sh`, `harden.sh`).

## Текст правок для Codex

```
Внеси правки по ревью M09 (docs/reviews/M09-review-1.md), тот же PR #25:

1. infra/deploy-staging.sh: на сервере нет Node. Сид запускать через Docker, как в docs/09_HANDOFF…:
   docker run --rm --network host --user "$(id -u):$(id -g)" -e HOME=/tmp \
     -e API_URL=http://127.0.0.1:8081 -e DEMO_SEED_ENABLED=true -e NODE_ENV=development \
     -e SPRING_PROFILES_ACTIVE=staging -e ADMIN_EMAIL -e ADMIN_PASSWORD -e DEMO_PASSWORD \
     -v "$STAGING_DIR":/repo -w /repo/scripts/demo node:22-alpine sh -c 'npm ci && npx tsx seed.ts'
   Убрать требование «Node.js 22+» из runbook §13. В infra/tests/staging-test.sh проверять вызов
   `docker run … node:22-alpine` с api=http://127.0.0.1:8081 и DEMO_SEED_ENABLED=true вместо подмены npm.

2. infra/harden.sh: вместо `PermitRootLogin no` писать `PermitRootLogin prohibit-password` (задание: отключить
   вход root ПО ПАРОЛЮ; владелец заходит как root по ключу и делает `su - deploy`). Проверку `sshd -T`
   заменить на `permitrootlogin prohibit-password`. Перед запросом подтверждения проверять, что
   /home/deploy/.ssh/authorized_keys существует и непуст, иначе выходить с подсказкой. Guard в строке 4:
   допускать запуск без SSH_CONNECTION (через `su - deploy`), для `sshd -T -C` брать addr=127.0.0.1,
   если SSH_CONNECTION пуст. Runbook §15: «root по ключу продолжает работать, по паролю — нет».

3. Пути: стенд развёрнут в /home/deploy/flounder (~/flounder), не в /opt/flounder. В infra/crontab.example
   использовать $HOME/flounder/infra/... и комментарий «проверьте `readlink -f ~/flounder`»; в runbook §9/§11/§13
   и в описании PR заменить /opt/flounder на ~/flounder (или написать «каталог репозитория, см. §3»).
   Старый бэкап лежит в ~/flounder/backups/2026-10-13.sql.gz.

4. infra/crontab.example: check-disk.sh — раз в час (`0 * * * *`) или дедупликация алерта через state-файл.
   Добавить вариант строк в UTC (0 0 * * * и 0 1 * * 0) и примечание: после `timedatectl set-timezone`
   выполнить `sudo systemctl restart cron`.

5. Вынести блок «Восстановление за 10 минут» из runbook в скрипт infra/restore-production.sh
   (set -Eeuo pipefail внутри скрипта, подтверждение словом restore-production, автоматический свежий
   backup.sh перед DROP, поддержка .dump и .sql.gz, stop/start api, smoke events, curl health).
   В runbook оставить вызов скрипта и проверки после него. Не использовать `set -e` в интерактивных блоках.

6. infra/backup.sh: при существующем файле той же минуты не слать алерт «backup failed» — либо
   %H%M%S в имени, либо выход с кодом 0 и сообщением.

7. В описании PR и runbook §13 шаг обновления prod — `./infra/deploy.sh` (не голый `docker compose up`).
   infra/compose.staging.yaml: TZ: ${TZ:-Europe/Samara} как в prod. Runbook §9: вернуть команду `scp`
   копии дампа на ноутбук. docs/runbook-demo.md:8 — убрать несуществующий deploy.yml, добавить новые скрипты.
   .env.prod.example: примечание про кавычки для значений с пробелами/$/# (файл сорсится bash).

Не менять: формат дампа, логику restore.sh, RequestIdFilter, CI. Прогнать shellcheck и infra/tests.
```

## Порядок слияния и риски для стенда

- **Конфликтов нет** ни с `main`, ни с открытыми #15 (M01), #18, #21, #24, #26. M09 трогает `pom.xml`, `package.json/lock`, `main.tsx`, `CorsConfig` — ни один открытый PR эти файлы не меняет. Порядок относительно других PR не важен; логично сливать после правок B1–B3 (Codex, ~1 ч) и после T11/M01, чтобы не смешивать с демо-деплоем.
- **Что произойдёт при первом `deploy.sh` после слияния** (все дефолты на месте, `deploy.sh` не менялся):
  1. Maven-слой зависимостей пересобирается (Sentry) — сборка на 1–3 мин дольше, при 4 ГБ памяти — как обычно, в окно без нагрузки.
  2. Из-за смены `max-size` логов compose **пересоздаст все 4 контейнера, включая postgres** (данные в volume, не теряются) → ~20–40 с недоступности API + обычное ожидание healthy; старая история `docker logs` пропадает.
  3. Создастся сеть `flounder_edge`, caddy подключится к ней; `import /etc/caddy/sites/*.caddy` при пустом каталоге — только warning. Поведение `sportgate.pro` не меняется.
  4. Новых обязательных переменных в `.env.prod` нет (`SENTRY_DSN`, `VITE_SENTRY_DSN`, `TELEGRAM_*` — опциональные/пустые). `deploy.sh` проверяет только `DOMAIN ACME_EMAIL POSTGRES_PASSWORD JWT_SECRET` — как раньше.
  5. API начнёт отдавать заголовок `X-Request-Id`; Sentry выключен (пустой DSN).
- **Staging на том же VPS**: порты/сети/volumes/БД/образы изолированы; единственная точка соприкосновения — сеть `flounder_edge` и общий Caddy. Память: при живом prod и сборке staging-образов (Maven+Vite внутри Docker без лимитов) реальна нехватка 4 ГБ → OOM-killer может задеть `flounder-api` (у него `-XX:+ExitOnOutOfMemoryError`, но системный OOM — это SIGKILL). Поэтому staging — только после демо, в окно без нагрузки, и первое время лучше с остановкой staging перед prod-деплоем. Долгосрочно — 4 vCPU/8 ГБ или отдельный VPS (согласен с PR).
- **DNS**: `staging.sportgate.pro` A → `82.97.248.229` — делает Сергей у регистратора; HSTS `includeSubDomains` на prod уже есть, поэтому staging только по HTTPS (Caddy выпустит сертификат сам после включения `staging.caddy`).
- **Бэкапы до демо не требуют редеплоя**: `backup.sh`/`restore.sh` работают через `docker exec flounder-postgres` на текущих контейнерах. `git pull` на сервере не меняет запущенные контейнеры.

## Команды для Сергея

Подключение как обычно: `ssh root@82.97.248.229`, затем `su - deploy`. Все команды ниже — под `deploy`, если не сказано иное. Предполагается, что PR #25 слит (после правок) — либо можно выполнить то же из ветки PR: `git fetch origin chore/M09-infra-backups-monitoring && git checkout -q origin/chore/M09-infra-backups-monitoring -- infra/` (только скрипты, контейнеры не трогаются; после демо `git checkout -- infra/`).

### До демо 15.10 (минимум: бэкап + проверка восстановления + cron, ~10 минут)

```bash
su - deploy
cd ~/flounder && git pull --ff-only origin main        # только файлы; контейнеры не перезапускаются
ls -ld ~/flounder /opt/flounder 2>&1                   # запомнить реальный путь (ожидается /home/deploy/flounder)

mkdir -p /home/deploy/backups && chmod 700 /home/deploy/backups
cp -n ~/flounder/backups/*.sql.gz /home/deploy/backups/ 2>/dev/null; ls -l /home/deploy/backups

./infra/backup.sh                                       # новый дамп flounder-YYYYMMDD-HHMM.dump
./infra/restore.sh "$(ls -t /home/deploy/backups/*.dump | head -1)"      # проверка нового дампа в отдельной БД
./infra/restore.sh /home/deploy/backups/2026-10-13.sql.gz                # проверка старого формата
docker exec flounder-postgres psql -U sport_user -d postgres -Atc \
  "select count(*) from pg_database where datname='sport_platform_restore_test'"   # должно быть 0
tail -n 20 /home/deploy/backups/backup.log
curl -fsS https://sportgate.pro/actuator/health; echo    # {"status":"UP"} — prod не пострадал
```
Ожидаемо в выводе `restore.sh`: строка `[restore] … OK: events=N` (N ≥ 1) и `[alert] Telegram is not configured` — это нормально, пока бот не настроен; код возврата 0.

Копия вне сервера (с ноутбука, PowerShell, под root — у него есть доступ к каталогу):
```powershell
PS> scp root@82.97.248.229:/home/deploy/backups/flounder-*.dump .
```

Cron (сначала узнать часовой пояс хоста):
```bash
timedatectl | grep 'Time zone'
crontab -l                                              # посмотреть, что есть сейчас
crontab -e                                              # заменить старую строку backup.sh на блок ниже
```
Если зона `Europe/Moscow`:
```
SHELL=/bin/bash
PATH=/usr/local/bin:/usr/bin:/bin
0 3 * * * /home/deploy/flounder/infra/backup.sh
0 4 * * 0 /home/deploy/flounder/infra/verify-latest-backup.sh
0 * * * * /home/deploy/flounder/infra/check-disk.sh >> /home/deploy/backups/backup.log 2>&1
```
Если зона `UTC` (ничего не менять в TZ перед демо, просто сдвинуть часы):
```
SHELL=/bin/bash
PATH=/usr/local/bin:/usr/bin:/bin
0 0 * * * /home/deploy/flounder/infra/backup.sh
0 1 * * 0 /home/deploy/flounder/infra/verify-latest-backup.sh
0 * * * * /home/deploy/flounder/infra/check-disk.sh >> /home/deploy/backups/backup.log 2>&1
```
Проверка: `crontab -l`. (Если путь репозитория другой — подставить вывод `ls -ld` выше.)

Опционально, если есть 10 минут, — Telegram-алерты: `@BotFather` → `/newbot` → токен; написать боту `/start`; chat id получить через `getUpdates` (не вставлять токен в адресную строку браузера с историей). Затем:
```bash
nano ~/flounder/infra/.env.prod      # дописать TELEGRAM_BOT_TOKEN=... и TELEGRAM_CHAT_ID=...
chmod 600 ~/flounder/infra/.env.prod
./infra/alert.sh "Flounder: тест уведомления"
```

**Не делать до демо:** `deploy.sh` ради M09, Kuma, staging, `harden.sh`, смену TZ хоста.

### После демо (16.10 и позже, в окно без нагрузки)

1. Редеплой prod с новым compose (логи 50m, сеть edge, X-Request-Id; ~5–8 мин, 20–40 с простоя):
```bash
su - deploy
cd ~/flounder && ./infra/deploy.sh
curl -fsS -D - -o /dev/null -H 'X-Request-Id: m09-check' https://sportgate.pro/actuator/health | grep -i x-request-id
docker ps --format '{{.Names}} {{.Status}}'
```
2. Мониторинг:
```bash
docker compose -f infra/compose.monitoring.yaml up -d
./infra/check-disk.sh
```
С ноутбука: `ssh -L 3001:127.0.0.1:3001 root@82.97.248.229` → `http://localhost:3001` → админ, Telegram-уведомление, мониторы по runbook §12 (health keyword `UP`, `/api/v1/public/events/otkrytyy-kubok-samary-po-plavaniyu-demo`, страница `/e/…`, TLS 14/7 дней).

3. Staging (только после правки B1 в PR; нужна A-запись `staging.sportgate.pro → 82.97.248.229`):
```bash
mkdir -p /home/deploy/.config/flounder && chmod 700 /home/deploy/.config/flounder
cp ~/flounder/infra/.env.staging.example /home/deploy/.config/flounder/.env.staging
chmod 600 /home/deploy/.config/flounder/.env.staging
nano /home/deploy/.config/flounder/.env.staging        # свои пароль БД, JWT, ADMIN_PASSWORD (Demo2026! на чистой БД), DEMO_PASSWORD
free -m; df -h /                                        # убедиться, что ≥1.5 ГБ свободно
./infra/deploy-staging.sh main                          # или имя ветки
curl -fsS http://127.0.0.1:8081/actuator/health; echo
cp infra/caddy-sites/staging.example infra/caddy-sites/staging.caddy
docker exec flounder-caddy caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
docker exec flounder-caddy caddy reload  --config /etc/caddy/Caddyfile --adapter caddyfile
curl -fsS https://staging.sportgate.pro/actuator/health; echo
```
Остановить staging после проверки: `docker compose --project-name flounder-staging -f infra/compose.staging.yaml --env-file /home/deploy/.config/flounder/.env.staging stop` и `rm infra/caddy-sites/staging.caddy` + `caddy reload`.

4. Hardening — только после правки B2 и после того, как на ноутбуке появится ключ для `deploy` (`ssh deploy@82.97.248.229` работает): `cd ~/flounder && ./infra/harden.sh`, держать вторую сессию открытой.

5. Sentry — по желанию, после выбора размещения данных (DSN в `.env.prod`, затем `deploy.sh`).

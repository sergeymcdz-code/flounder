# Деплой партии 5 — этап 2 + аудит безопасности + календарь S10

Подготовлено 21.10.2026. Выполнять **только после** слияния всей цепочки `#75 → #60 → #72 → #73 → #74 → #59 → #61 → #63 → #58 → #68 → #77 → #78` **и** `#64` (S05, миграция V035). Без S05 в main деплой не выполнять: Flyway на стенде применит V036–V050, а V035 позже не примет.

Текущее состояние стенда (ожидание): main `57cea5d`, Flyway `038`, 3 события A/B/C. Простой ~2 мин (пересоздаются все 4 контейнера). Все команды — по одной, каждый блок с самого начала.

## Блок 0 — до деплоя (владелец, один раз)

1. `scripts/demo/.env` на сервере: `ADMIN_EMAIL` = рабочий bootstrap-администратор (не `admin@flounder.demo`), `ADMIN_PASSWORD` — его пароль, `DEMO_PASSWORD` — новый, ≥ 12 символов. Значения не показывать.
2. `infra/.env.prod`: добавить из `infra/.env.prod.example` новые переменные (значения по умолчанию допустимы): `APP_AUTH_LOGIN_MAX_FAILURES=5`, `APP_AUTH_LOGIN_MAX_FAILURES_PER_IP=30`, `CSP_HEADER=Content-Security-Policy-Report-Only`, `BACKUP_AGE_RECIPIENT=` (пусто до настройки бэкапов). Проверка полноты: `bash infra/tests/compose-config-test.sh` после `git pull`.

## Блок 1 — бэкап и деплой (~10 мин)

```
ssh root@82.97.248.229
```
```
su - deploy
```
```
cd ~/flounder && ./infra/backup.sh
```
```
cd ~/flounder && git pull
```
```
cd ~/flounder && bash infra/tests/compose-config-test.sh
```
```
cd ~/flounder && ./infra/deploy.sh
```
```
cd ~/flounder && docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod ps
```
```
curl -s https://sportgate.pro/actuator/health
```
```
docker exec flounder-postgres psql -U sport_user -d sport_platform -c "select version from flyway_schema_history order by installed_rank desc limit 3"
```

Ожидание: health `UP`, версии `051` (если SEC04 слит) или `050`, ниже `040`, `039`. Откат: `cd ~/flounder && git checkout 57cea5d && ./infra/deploy.sh --no-pull` (миграции не откатываются — восстановить дамп из `~/backups`).

## Блок 2 — проверки безопасности (3 мин)

```
curl -s -o /dev/null -w '%{http_code}\n' https://sportgate.pro/v3/api-docs
```
Ожидание: `404` (SEC04) или `401/403` без SEC04 — не `200`.
```
curl -s -o /dev/null -w '%{http_code}\n' -X POST https://sportgate.pro/api/v1/payments/webhooks/yookassa -H 'Content-Type: application/json' -d '{}'
```
Ожидание: `404` (провайдер MANUAL — SEC03).
```
curl -s -o /dev/null -w '%{http_code}\n' -X POST https://sportgate.pro/api/v1/auth/login -H 'Content-Type: application/json' -d '{"email":"admin@flounder.demo","password":"x"}'
```
Ожидание: `401` — старая служебная учётка отключена (V050).

## Блок 3 — пароли (после деплоя)

1. `org@`, `sec@`, `judge@`, `parent@flounder.demo` — задаются новым `DEMO_PASSWORD` при повторном сиде: `cd ~/flounder/scripts/demo && npm run seed` (или через контейнер node:22-alpine, см. `docs/qa/demo-calendar.md`).
2. `infra/.env.prod`: новый `APP_ADMIN_PASSWORD`; новый `JWT_SECRET` (`openssl rand -base64 48`) — все сессии сбросятся. Затем `cd ~/flounder && ./infra/deploy.sh --no-pull`.
3. `passwd` под root. Вход только по ключу — отдельно по `docs/infra-hardening.md` (SEC06), после проверки ключа.
4. `APP_FILES_ENCRYPTION_KEY` — не ротируется (бэклог).

## Блок 4 — календарь S10 (первый сид, ~2–10 мин)

```
cd ~/flounder && ./infra/backup.sh
```
```
cd ~/flounder && mkdir -p ~/backups && flock -n ~/backups/demo-calendar.lock docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -e DEMO_DATE=2026-10-19 -e CALENDAR_WEEKS=25 -v "$PWD":/repo -w /repo/scripts/demo node:22-alpine sh -eu -c 'npm ci && npx tsx calendar.ts' 2>&1 | tee -a ~/backups/demo-calendar.log
```
Ожидание: последняя строка `S10 {"mode":"seed","weeks":25,…}` — 29 событий: 14 COMPLETED, 1 REGISTRATION_CLOSED, 4 REGISTRATION_OPEN, 9 PUBLISHED, 1 DRAFT; в каталоге 28 карточек.
```
curl -fsS https://sportgate.pro/api/v1/public/events | python3 -c 'import json,sys; d=json.load(sys.stdin); items=d if isinstance(d,list) else d.get("items",d.get("content",[])); print(len(items))'
```
Еженедельное обновление (понедельник): та же команда с `calendar.ts --advance`; cron — после первого ручного прогона (`docs/qa/demo-calendar.md`).

## После деплоя

- Старые заявки без взноса — «Обновление S05» в `docs/manual/Инструкция_администратора.md`.
- SEC04 (V051): перед слиянием проверить дубли email: `docker exec flounder-postgres psql -U sport_user -d sport_platform -c "select lower(email), count(*) from users group by 1 having count(*)>1"` — должно быть 0 строк.
- SEC06 на сервере (SSH-ключ, UFW, fail2ban, CSP) — отдельным окном по `docs/infra-hardening.md`, CSP сначала Report-Only.

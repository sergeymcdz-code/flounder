# Демо SportGate: три соревнования S02

Требуется выделенная демо-БД с миграциями main, backend API, Node ≥22. Миграций S02 нет. Секреты задаются окружением или `scripts/demo/.env` (см. `.env.example`, в Git не добавлять).

```bash
cd scripts/demo
npm ci
# Set ADMIN_EMAIL/ADMIN_PASSWORD for the bootstrap admin and DEMO_PASSWORD (or account-specific passwords).
DEMO_SEED_ENABLED=true API_URL=http://localhost:8080 DEMO_DATE=2026-10-19 DEMO_EVENTS=A,B,C npx tsx seed.ts
```

### Сервер без Node

Выполняет deploy из корня checkout; предварительно владелец задаёт `DEMO_OWNER_EMAIL` в `scripts/demo/.env` вместе с API_URL=https://sportgate.pro, ADMIN_EMAIL/ADMIN_PASSWORD, DEMO_PASSWORD и DEMO_SEED_ENABLED=true. Адрес должен принадлежать отдельному участнику; по умолчанию `sergeymcdzkz@gmail.com`. Адрес администратора `sergeymcdz@yandex.ru` для участника не использовать. `.env` не выводить на экран.

```bash
cd /home/deploy/flounder
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp \
  -e DEMO_DATE=2026-10-19 -e DEMO_EVENTS=A,B,C \
  -v "$PWD":/repo -w /repo/scripts/demo node:22-alpine \
  sh -eu -c 'npm ci && npx tsx seed.ts'
```

### До сида: удалить старое одиночное событие

После резервной копии БД/файлов проверить UUID, название и slug прежнего события через публичный API. При 404 подтвердить, что оно уже удалено, и пропустить этот шаг. Новый `reset.ts` также может выбирать старое событие по `DEMO_SLUGS`, сохраняя проверку демо-организации/площадки. Прямой сброс тем же SQL через контейнерный psql:

```bash
cd /home/deploy/flounder
./infra/backup.sh
curl -fsS https://sportgate.pro/api/v1/public/events/otkrytyy-kubok-samary-po-plavaniyu-demo \
  -o /tmp/s02-legacy-event.json
python3 - <<'PYJSON' > /tmp/s02-legacy-targets.json
import json
with open('/tmp/s02-legacy-event.json') as source:
    event = json.load(source)
assert event['title'] == 'Открытый кубок Самары по плаванию (демо)'
assert event['publicSlug'] == 'otkrytyy-kubok-samary-po-plavaniyu-demo'
print(json.dumps([{'id': event['id'], 'title': event['title'], 'slug': event['publicSlug']}], ensure_ascii=False))
PYJSON
cat /tmp/s02-legacy-targets.json
# Verify the UUID/title/slug JSON before stopping the API.
LEGACY_DEMO_TARGETS="$(cat /tmp/s02-legacy-targets.json)"
docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod stop api
docker exec -i flounder-postgres psql -X -U sport_user -d sport_platform \
  -v ON_ERROR_STOP=1 -v "demo_targets=$LEGACY_DEMO_TARGETS" < scripts/demo/reset.sql
docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod start api
# Wait for healthy in compose ps; then check the external endpoint:
curl -fsS https://sportgate.pro/actuator/health
```

Формат `demo_targets`: `[{"id":"UUID из API","title":"Открытый кубок Самары по плаванию (демо)","slug":"otkrytyy-kubok-samary-po-plavaniyu-demo"}]`. SQL повторно сверяет эти значения и демо-организацию/площадку; при несовпадении откатывает всю транзакцию. Если psql завершился ошибкой, сохранить вывод и проверить откат до продолжения; API запустить обратно и проверить health. До удаления перенастроить keyword-монитор Uptime Kuma со старого slug на B либо согласовать окно обслуживания.

DEMO_DATE — день показа, формат YYYY-MM-DD, любой день недели; по умолчанию 2026-10-19. DEMO_EVENTS — непустой список A/B/C без повторов, по умолчанию все три. Даты не зависят от текущего времени: начало события/сессии 1 — 10:00 Europe/Samara, сессии 2 — 15:00; регистрация открывается за 30 дней до демо, закрывается A за 9 дней, B за 1 день, C через 4 дня в 18:00 Самары. Запуск C после его окна регистрации завершится ошибкой, даты автоматически не подменяются.

| Событие / slug на чистой БД | Дата / статус | Данные |
|---|---|---|
| A `kubok-sportgate-osen` | демо −8 / COMPLETED | 96 пловцов, 160 заявок/результатов, все заплывы FINISHED, 2 CORRECTED, 2 DNS + 1 DNF |
| B `otkrytyy-kubok-samary-po-plavaniyu` | демо +1 / REGISTRATION_CLOSED | исходный set-M.xlsx: 120 пловцов, 200 заявок; первые 2 заплыва сессии 1 FINISHED, прочие PUBLISHED |
| C `pervenstvo-oblasti-sredi-yunoshey` | демо +5 / REGISTRATION_OPEN | 124 пловца/заявки: 114 CONFIRMED + 6 NEEDS_CORRECTION + 4 WAITLISTED; 2 PDF документа |

Всего 300 уникальных пловцов (без служебных аккаунтов), 40 общих B/C профилей, 12 клубов, годы рождения 2010–2016. Пулы генератора содержат 48 мужских/48 женских имён и 120 фамилий с согласованием по полу; в A представлены 48 имён каждого пола. B использует исходный набор M без изменения файлов. A/C детерминированно генерируются в памяти. У C восемь видов (50/100/200/400 вольным стилем, М/Ж), лимит 150 на событие и 16 на каждый вид. 120 заявок вне очереди включают 6 NEEDS_CORRECTION. Очередь создаётся обычной подачей сверх заполненного M-50; шесть на исправлении — подачей и модерацией. Импорт для этих состояний не используется. Для одного из шести аккаунтов владелец задаёт `DEMO_OWNER_EMAIL` (default `sergeymcdzkz@gmail.com`), пароль PARENT_PASSWORD/DEMO_PASSWORD; остальные дополнительные аккаунты `@flounder.demo`. Существующий служебный аккаунт по этому адресу, в том числе platform_admin вместе с participant, отклоняется до создания демо-данных; роли и профиль не меняются. Для существующего отдельного участника пароль должен совпадать с PARENT_PASSWORD/DEMO_PASSWORD; иначе понятная ошибка предлагает проверить пароль или другой DEMO_OWNER_EMAIL. Изменение адреса/генератора имён для уже заполненных A/C требует reset соответствующих событий. Импортированные спортсмены без аккаунтов/e-mail. PDF в fixtures содержит только пометку DEMO, не медицинские сведения.

Сид использует только HTTP API: создание, импорт, посев, расписание, публикация, судейский ввод/финиш, две правки секретарём, завершение A. При отсутствующем API или неожиданном состоянии останавливается; SQL-сидинга нет. Бюджет HTTP-клиента — 600 с, тайм-аут одного запроса — 15 с. В конце печатаются время и JSON-сводки `S02 A/B/C` с UUID, slug, статусами и количеством записей.

Повторный прогон находит события по точному названию, сверяет даты/настройки/состав и не пересоздаёт заплывы или документы. Частичный импорт и недостающие заплывы продолжаются через API. Расхождение даты/состава требует reset. `EVENT_TITLE_SUFFIX=" · резерв"` создаёт отдельную копию выбранных событий; брать фактический slug из вывода (API может разрешить коллизию суффиксом). `--skip-import` оставлен для изолированных тестов: создаёт только настройки/открывает регистрацию, не формирует полный сценарий S02 и не очищает существующие данные.

Аккаунты org/sec/judge/parent создаются и получают роли/членство через API. ADMIN_EMAIL должен быть platform_admin; пароль существующего пользователя сид не меняет. REQUIRE_VENUE_META=true проверяет бассейн 25 м/8 дорожек строго; площадку без метаданных необходимо пересоздать через API после сброса демо. Почта в локальном backend по умолчанию выключена; для показа письма настроить SMTP/M05 на демо-стенде заранее.

## Сброс выбранных событий

```bash
# Read-only API validation and exact SQL command for all three events:
npx tsx reset.ts
# One event by its actual slug (or a comma-separated list):
DEMO_SLUGS=pervenstvo-oblasti-sredi-yunoshey npx tsx reset.ts
# Alternative selection using the same A/B/C keys as seed:
DEMO_EVENTS=B,C npx tsx reset.ts
```

Сначала сделать копию БД/файлов. Без --apply сброс только проверяет UUID/название/slug/организацию/площадку и печатает команду psql с `demo_targets` JSON. Для ручного применения остановить API/фоновые записи и выполнить **точную выведенную команду** с `reset.sql`, затем запустить API. --apply требует psql и DEMO_DATABASE_URL; API должен оставаться доступен для проверки, а демо-стенд — в режиме обслуживания без пользовательских/фоновых записей.

```bash
# Only on the selected demo DB after backup; DSN is supplied through the environment.
DEMO_SLUGS=pervenstvo-oblasti-sredi-yunoshey npx tsx reset.ts --apply
# Backups use the same title suffix:
EVENT_TITLE_SUFFIX=" · резерв" DEMO_EVENTS=B npx tsx reset.ts
```

SQL выполняется одной транзакцией, повторно проверяет **все** тройки UUID/название/slug и демо-организацию/площадку. Изменившаяся цель откатывает всю транзакцию. Удаляются выбранные события и зависимости, документы этих событий, осиротевшие импортированные профили; общие профили B/C с оставшимися заявками и аккаунты сохраняются. Отсутствующие события при выборе DEMO_EVENTS — no-op; неизвестный/чужой DEMO_SLUGS останавливает сброс. Физические файлы документов сохраняются в хранилище для штатной очистки; SQL не удаляет файлы напрямую. Сброс одного C сохраняет B и его 40 общих профилей.

## Проверки

```bash
npm test
npm run typecheck
# With a running local API and its bootstrap admin:
cd ../../sport-platform/frontend
PLAYWRIGHT_CHROMIUM_EXECUTABLE=/usr/bin/chromium npm run test:demo
```

При MAIL_ENABLED=true сид создаёт уведомления и для вымышленных адресов @flounder.demo; это может приводить к возвратам SMTP. Для e2e после 18.10 требуется актуальный DEMO_DATE, чтобы окно регистрации отдельного QA05-события B оставалось открытым.

Unit-тесты проверяют даты/границы календаря, состав, повторы, выбор событий, роли, ошибки и сброс без сети. E2e проверяет три жизненных цикла через настоящий API/браузер, общие profile ID и документы; прежний семишаговый тест использует отдельный пустой B с суффиксом QA05. Для удалённого API требуется явный QA05_ALLOW_REMOTE=1; рабочие данные для e2e не использовать. Подробный сценарий: [docs/demo-script.md](../../docs/demo-script.md), чек-лист: [репетиция 17.10](../../docs/qa/rehearsal-2026-10-17.md).

Перед обновлением стенда владелец должен задать в `scripts/demo/.env` рабочий `ADMIN_EMAIL` bootstrap-администратора, а не `admin@flounder.demo`. `ADMIN_PASSWORD`, `DEMO_PASSWORD` и все переопределения `*_PASSWORD` должны содержать собственные пароли длиной не менее 12 символов. Пароль — в менеджере паролей владельца, в репозитории не хранится. Исторический встроенный администратор отключается миграцией V050.

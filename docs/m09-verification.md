# M09 — проверка инфраструктуры

База ветки: `main` после `git fetch origin main`, `a37fcb5` (13.10.2026).
На боевом сервере ничего не запускалось. Секреты, аккаунты сервисов и реальные дампы
оператора не использовались. Пошаговая приёмка под `deploy`: `docs/runbook-demo.md` §§9, 12–15.

## Исправленные дефекты в области M09

| ID | Severity | Было | Стало / проверка |
|---|---|---|---|
| M09-01 | major | `restore.sh` удалял рабочую БД и останавливал API для проверки бэкапа | Только отдельная `sport_platform_restore_test`; тест обоих форматов, ошибки SQL, существующей тестовой БД и неизменности исходной БД в `infra/tests/maintenance-test.sh` |
| M09-02 | minor | Бэкапы plain SQL, ротация 14 дней, cron зависит от timezone хоста | Custom dump, 30 суток, явная инструкция Europe/Moscow; regression ротации и ошибки pg_dump |
| M09-03 | minor | CI frontend не запускал `npm test` | Отдельный обязательный шаг перед lint/build; backend Testcontainers сохранён |

Флаг `DEMO_SEED` не обрабатывается текущим backend: staging запускает существующий
HTTP-сид с `DEMO_SEED_ENABLED=true`. Адрес сида и сохранность production checkout
проверяются в `infra/tests/staging-test.sh` без реального деплоя приложения.

## Что проверено

- Shellcheck 0.11: `infra/*.sh`, `infra/tests/*.sh`.
- `docker compose config --quiet`: production, staging, monitoring;
  фиктивные значения окружения задаёт `infra/tests/compose-config-test.sh`.
- Локальный реальный PostgreSQL 16: `.dump` и старый `.sql.gz` восстанавливаются,
  `events` содержит ожидаемую строку, тестовая БД после успеха и ошибки удаляется;
  ранее существующая тестовая БД сохраняется. Исходная БД не меняется.
- Файловый volume включается в `.files.tar.gz`; старые SQL-бэкапы удаляются по 30-дневной
  ротации, посторонние файлы сохраняются; при недоступном контейнере нет финального/частичного дампа.
- Staging: выбор remote-ветки, повторный деплой, HTTP-сид только на loopback staging,
  отказ для dirty tree и подкаталога production. Docker в этом тесте подменён; проверяются аргументы node:22-alpine/host network/UID/GID/env;
  полноценный деплой должен пройти операторскую приёмку.
- `caddy validate`: production и staging в локальных контейнерах без сети.
- Frontend: `npm test` (Node test runner + 42 Vitest-теста), `npm run build`, `npm run lint`.
  Lint завершился успешно с существующими предупреждениями. Сборка также прошла
  с тестовым `VITE_SENTRY_DSN`; событий во внешний сервис не отправляли.
- Backend: полный `mvn -q test` с Java 21 и локальным Docker/Testcontainers;
  код возврата 0, 42 test suites / 1026 tests, failures/errors/skipped = 0.
  После тестов Surefire предупредил о принудительном завершении fork JVM: shutdown
  Spring/Modulith ожидает соединение Hikari с уже остановленным PostgreSQL. Такой же
  стек есть в исходных отчётах среды от 12.10; тесты/CI не ослаблялись.
  Добавлен `RequestIdFilterTest`:
  безопасный заголовок, генерация UUID, очистка/восстановление MDC при исключении.

CI дополнен infra job с shellcheck, проверкой compose и этими regression-тестами.
GitHub-hosted Ubuntu предоставляет Docker; отдельный DinD service не добавлен,
`mvn -B test` и существующие кеши Maven/npm сохранены, добавлена диагностика `docker info`.

## Не проверено и ограничения

- Фактический сегодняшний боевой `.sql.gz` 36 КБ: остаётся у оператора; старый формат
  проверен на локальном дампе такого же формата, не на реальных данных.
- Cron/timezone, Telegram-бот/Kuma-мониторы/TLS, реальный staging/DNS/ресурсы VPS —
  требуют операторских действий; никакого SSH на сервер не выполнялось.
- Полное восстановление production за 10 минут: дана процедура, время не измерялось.
  MVP RPO 24 ч / RTO 2 ч остаются целями, не результатами этой проверки.
- M04 volume сейчас отсутствует на main: условное копирование проверено на тестовом
  volume. Согласованность БД/документов и восстановление реальных файлов требуют
  приостановки записи и отдельной приёмки M04.
- Sentry и hardening сделаны как **опциональные** шаги. DSN пусты; реальная доставка
  Sentry не проверена. `harden.sh` проверен shellcheck, установки пакетов/reload SSH не было.
- S3/rclone — опциональная выгрузка через окружение; реальный bucket не подключён.
- Kuma на одном VPS не обнаружит его полный отказ: нужен внешний монитор.
- Два нагруженных стенда на 2 vCPU/4 ГБ не имеют надёжного запаса; предложены
  4 vCPU/8 ГБ либо отдельный staging, runtime лимиты и остановка staging после приёмки.

Миграции не добавлены/не изменены. Новых/изменённых API-эндпоинтов нет.
Технические дополнения вне исходного списка M09 необходимы для явно запрошенных
correlation-id и Sentry: Java filter/CORS/тест, Maven/npm зависимости и frontend bootstrap/
Docker build arg. README содержит требуемый CI-бейдж; `.gitignore` разрешает только
новый несекретный `.env.staging.example`, настоящий `.env.staging` остаётся игнорируемым.

## Правки по M09-review-1 (PR #25)

- Сид staging переведён на `docker run … node:22-alpine`, без Node/npm на сервере;
  пути исправлены на `~/flounder`, timezone API staging выровнен с prod (Europe/Samara).
- Cron: раз в час проверка диска, отдельные варианты Moscow/UTC, restart cron при
  смене timezone; повтор бэкапа в ту же минуту успешен без ложного алерта.
- Hardening сохраняет root по ключу (`prohibit-password`), проверяет deploy authorized_keys
  до подтверждения, допускает `su - deploy` без SSH_CONNECTION. Regression с mock sudo
  проверяет отсутствие ключа, подтверждение, fallback addr и rollback; хост не менялся.
- `restore-production.sh`: отдельное подтверждение, неизменённый restore.sh для проверки,
  stop API → свежий бэкап в уникальном каталоге → DROP/load/smoke → start API/health.
  Локальные regression-тесты проверили оба формата, сохранность свежего снимка, отказ
  без подтверждения/при битом SQL, запрет DROP при сбое бэкапа и остановленный API при
  ошибке загрузки. Compose start/stop и HTTP health подменены, SQL реально выполнялся
  в одноразовом локальном PostgreSQL. Реальный production не восстанавливался.
- Shellcheck и все infra/tests после правок прошли. CI, restore.sh, RequestIdFilter
  и формат дампа не изменены. Слияние и редеплой — после демо 15.10.2026.

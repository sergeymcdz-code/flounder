# S03: локальная нагрузочная проверка

Только выделенная локальная БД. Скрипты разрешают `BASE_URL` с хостом `localhost`,
`127.0.0.1` или `api` (локальная сеть Docker). Не запускать против sportgate.pro.
Требуются Docker и готовый API, устанавливать k6 на хост не нужно.

## Подготовка

1. Из корня репозитория скопировать `infra/.env.prod.example` во временный
   `work/s03/.env.perf`, заполнить тестовые значения. Для `JWT_SECRET` использовать
   `openssl rand -base64 48`, для `APP_FILES_ENCRYPTION_KEY` — `openssl rand -base64 32`.
   Задать `DEMO_SEED=true`, `APP_ADMIN_EMAIL` и `APP_ADMIN_PASSWORD` для локального админа.
   Значения остаются только в окружении или игнорируемом `.env`.
2. Добавить локальный override `work/s03/local.yaml`:

   ```yaml
   services:
     api:
       ports: ["127.0.0.1:8080:8080"]
   ```

   Запустить из корня:

   ```bash
   docker compose -f infra/compose.prod.yaml -f work/s03/local.yaml --env-file work/s03/.env.perf up -d --build postgres api
   curl -f http://localhost:8080/actuator/health/readiness
   ```

3. Выполнить [штатный HTTP-сид](../../../scripts/demo/README.md) с
   `DEMO_SEED_ENABLED=true`, локальным `API_URL`, тестовыми паролями и
   `DEMO_DATE=2026-10-17`. Скрипт запускается вне профиля `prod`; сервер при этом
   использует prod-настройки на выделенной локальной БД. Ожидаются 200 заявок.
4. Организатором сформировать заплывы, пересчитать расписание и опубликовать
   стартовый протокол; ввести и опубликовать результаты. Из ответа сида взять
   `SLUG`, `EVENT_ID`, из списка дисциплин — `DISCIPLINE_ID`. Один и тот же набор
   данных использовать до и после изменений. Пустые заплывы не измеряют N+1.

## Прогон

Из этого каталога (каждый сценарий — 2 минуты; один запрос на итерацию, без sleep):

```bash
docker run --rm -i --network host -e BASE_URL=http://localhost:8080 -e SLUG="$SLUG" \
  grafana/k6:0.54.0 run --summary-export=/dev/stdout - < public.js

docker run --rm -i --network host -e BASE_URL=http://localhost:8080 \
  -e EVENT_ID -e DISCIPLINE_ID -e ORGANIZER_EMAIL -e ORGANIZER_PASSWORD \
  grafana/k6:0.54.0 run --summary-export=/dev/stdout - < organizer.js
```

`ORGANIZER_EMAIL` / `ORGANIZER_PASSWORD` предварительно экспортировать из локального
окружения; пароли и JWT не сохраняются в summary. Организатор входит один раз в
`setup()`, затем выполняются только GET. `ATHLETE_QUERY` опционален (по умолчанию
`Ива`). URL распределяются равномерно по `iterationInTest`.

Пороги: публичный API 50 RPS / p95 < 300 мс; организатор 10 RPS / p95 < 500 мс;
ошибки < 0,5 %, пропущенных итераций нет. Дополнительные пороги по тегу `name`
сохраняют p50 (`med`), p95, p99, число запросов и ошибки каждого URL в summary.
Код k6 `99` означает нарушение порога; HTTP 200 проверяется отдельно.

Для чистого JSON сохранить summary в примонтированный каталог:

```bash
mkdir -p ../../../work/s03/k6
docker run --rm -i --network host --user "$(id -u):$(id -g)" -v "$PWD/../../../work/s03/k6:/out" \
  -e BASE_URL=http://localhost:8080 -e SLUG \
  grafana/k6:0.54.0 run --summary-export=/out/public.json - < public.js
```

Не запускать сборки и тесты одновременно с финальными замерами. Для сравнения
снять прогон на main, затем на ветке S03 с той же БД. Первые пробные прогоны
используются для прогрева; финальные пары выполнять при одинаковых условиях.
`--network host` рассчитан на Linux; в Docker Desktop нужна поддержка host networking.

## Диагностика

Во время прогона: `docker stats flounder-api flounder-postgres`. Отдельно снять
несколько отсчётов после минуты простоя. Зафиксировать CPU/память машины, image ID,
версии Java/Postgres/k6, число заявок/заплывов и условия прогрева.

```bash
docker exec flounder-postgres psql -U sport_user -d sport_platform -c 'show shared_buffers;'
docker exec flounder-postgres psql -U sport_user -d sport_platform -c '\di'
docker exec flounder-postgres psql -U sport_user -d sport_platform -c "select calls, round(total_exec_time::numeric,1) as total_ms, round(mean_exec_time::numeric,2) as mean_ms, left(query,160) as query from pg_stat_statements order by total_exec_time desc limit 10"
```

Для N+1 временно включить `LOGGING_LEVEL_ORG_HIBERNATE_SQL=DEBUG` в локальном
API. В отдельном окне без нагрузки посчитать строки логгера `org.hibernate.SQL`
на HTTP-потоке для одного GET результатов вида и сохранения результатов заплыва
(оно включает `rankDiscipline`). Сохранить одинаковые JSON-запросы до/после;
затем вернуть уровень WARN перед нагрузочными замерами.

Проверка рестарта: отметить время, `docker restart flounder-api`, опрашивать
`/api/v1/public/events/$SLUG` до первого 200 (цель ≤ 90 с). Caddy проверить
отдельно: при недоступном API после исчерпания повторов ожидается HTML 503 с
`Retry-After: 30`. HTTP-кэш браузера здесь не участвует: k6 запрашивает полные
ответы без условных заголовков. ETag/304 проверяются интеграционным тестом.

Отчёт: [2026-10-16_k6_report.md](../../../docs/perf/2026-10-16_k6_report.md).

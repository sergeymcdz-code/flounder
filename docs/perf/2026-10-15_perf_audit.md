# Аудит производительности и отказоустойчивости стенда sportgate.pro (15.10.2026)

Автор: агент DBA/перф (волна 5). Основание: код и конфиги в `main` на 15.10.2026 (коммит `a9338d1`). Доступа к серверу у агента нет — все выводы по коду; что именно происходит на VPS, покажут команды из §2. Код не менялся; задание для Codex — `tasks/S03_Производительность_и_БД.md`.

Сервер: Timeweb VPS, 2 vCPU / 4 ГБ, Ubuntu, Docker Compose (`infra/compose.prod.yaml`): `postgres:16`, `flounder-api` (Spring Boot 3.5, Java 21, `-Xmx1024m`), `flounder-web` (Caddy со статикой), `flounder-caddy` (edge, TLS, HTTP/3). Staging и Uptime Kuma по плану ещё не развёрнуты (runbook §13, deploy-batch1 п.7).

## 1. Почему в панели 20–30 % при «нуле пользователей» — гипотезы по убыванию вероятности

Для масштаба: на 2 vCPU 20–30 % панели = 0,4–0,6 ядра постоянно. Один простаивающий Spring Boot на Java 21 с G1 даёт 1–3 % одного ядра, Postgres без запросов — <1 %, Caddy — ~0. То есть код приложения сам по себе такой нагрузки не объясняет, искать надо «вокруг».

| # | Гипотеза | Почему вероятно | Как проверить (одной командой, см. §2) |
|---|----------|-----------------|------------------------------------------|
| 1 | **Фоновый интернет-шум на публичном IP: перебор SSH (sshd + fail2ban) и сканеры HTTPS (TLS-рукопожатия в Caddy).** | Любой VPS с открытыми 22/443 получает сотни попыток входа и тысячи запросов сканеров в час. Каждая попытка SSH — форк sshd и обмен ключами (криптография), `fail2ban` с `backend = systemd` (из `infra/harden.sh`) постоянно читает journal. Каждый TLS-handshake на 443 — несколько миллисекунд CPU. Вместе это легко даёт 10–30 % на 2 vCPU. | `journalctl -u ssh --since -1h | grep -c -E "Invalid user|Failed password"`; `docker logs flounder-caddy --since 1h 2>&1 | wc -l`; `top` → процессы `sshd`, `fail2ban-server`, `caddy`. |
| 2 | **Панель Timeweb показывает нагрузку гипервизора, включая steal и соседей.** | У Timeweb график в панели — метрика виртуальной машины снаружи, в неё попадает «steal» (время, отнятое соседями). Внутри `top` покажет `st` > 0 и реальную картину. | `top -bn1 | head -5` (столбец `st`), `vmstat 1 5` (колонка `st`), `cat /proc/pressure/cpu`. |
| 3 | **«Ноль пользователей» — не ноль: открытые вкладки публичных страниц опрашивают API каждые 5 с.** | `frontend/src/features/public/usePublicData.ts`: `LIVE_POLL_MS = 5000`, `RESULTS_POLL_MS = 5000`, `SCHEDULE_POLL_MS = 30000`. Одна открытая вкладка табло/результатов = 12–24 запроса в минуту; плюс Playwright-прогоны QA, поисковые боты (Яндекс/Google индексируют публичные страницы), чужие uptime-мониторы. Кэш публичного API 10 с (`PublicationCacheConfig`) снижает SQL, но JSON-сериализация и JWT/Security-фильтры на каждый запрос остаются. | `docker logs flounder-caddy --since 10m 2>&1 | grep -oE '"uri":"[^"?]+' | sort | uniq -c | sort -rn | head -20` — кто и что дергает. |
| 4 | **Перезапуски контейнера `flounder-api` по healthcheck (restart-loop) или недавний деплой/сборка.** | Старт JVM — 100 % CPU на 40–90 с. Если healthcheck (`curl /actuator/health` каждые 15 с, 10 попыток) иногда падает, Docker перезапускает API, и усреднённый график «висит» на 20–30 %. Сборка `deploy.sh --build` (Maven + npm) на том же сервере тоже даёт пики на 5–10 минут. | `docker inspect --format '{{.Name}} restarts={{.RestartCount}} started={{.State.StartedAt}}' flounder-api flounder-postgres flounder-caddy flounder-web`; `docker ps -a` (нет ли зависших build-контейнеров). |
| 5 | **Нехватка памяти → swap → kswapd.** | 4 ГБ: ОС+Docker ~0,5 ГБ, JVM = heap 1 ГБ + ~0,4 ГБ вне heap, Postgres ~0,3 ГБ, Caddy×2 ~0,1 ГБ — норма ~2,5 ГБ, но во время сборки образов (`mvn package` ~1–1,5 ГБ) памяти не хватает; если есть swap — ядро начинает гонять страницы, это CPU. Лимитов памяти у prod-контейнеров нет. | `free -m`; `vmstat 1 5` (колонки `si`/`so` должны быть 0); `dmesg -T | grep -i -E "oom|killed process" | tail`. |
| 6 | **Системные службы Ubuntu: unattended-upgrades/apt, snapd, fwupd, qemu-guest-agent, агент Timeweb.** | В плане есть «System restart required» — апдейты ходят. `apt` + `unattended-upgrade` после обновлений могут держать CPU минутами; `snapd` периодически обновляет снапы. Это пики, но при усреднении по графику выглядят как фон. | `ps -eo pid,etimes,pcpu,comm --sort=-pcpu | head -15`; `systemctl list-timers --all | head -20`. |
| 7 | **Шедулеры приложения.** | `EventLifecycleScheduler` — раз в минуту запрос по `events` без индекса по `status` (таблица крошечная, стоимость ≈ 0). `NotificationScheduler` — каждые 10 с `select … from notification_outbox where status='PENDING' and next_attempt_at<=? …` (индекс `(status, created_at)` есть). Оба запроса — микросекунды; если бы транзакция падала с ошибкой каждую минуту, это было бы видно в логе. **Низкая вероятность**, но именно это владелец «чувствует» как фоновую активность. | `docker logs flounder-api --since 30m 2>&1 | grep -c -E '"log.level":"(ERROR|WARN)"'`; `docker logs flounder-api --since 30m 2>&1 | grep -i -E "lifecycle|outbox|notification" | tail -5`. |
| 8 | **JVM: G1 + JIT на 2 vCPU без лимитов, `management.tracing.enabled: true` (моста трассировки в pom нет → no-op), `micrometer-registry-prometheus` в classpath (сбор метрик, эндпоинт закрыт).** | Фон 1–3 %, не 20 %. Но без `mem_limit` и `MaxRAMPercentage` JVM не знает про контейнер и считает ресурсы всего хоста. | `docker stats --no-stream`; `ps -L … -p $(docker inspect -f '{{.State.Pid}}' flounder-api)` из §2 (какие потоки JVM жгут CPU: `G1 Conc`, `C2 CompilerThre`, `http-nio-*`). |
| 9 | **Mail health-indicator / SMTP-проверки на каждый healthcheck.** | В `NotificationConfiguration` объявлен `JavaMailSender`; Spring Boot создаёт `MailHealthIndicator` только для бина типа `JavaMailSenderImpl` — здесь тип бина `JavaMailSender`, поэтому индикатор, скорее всего, не создаётся. Но проверить дёшево. | `docker logs flounder-api --since 1h 2>&1 | grep -ci smtp` (должно быть 0 при отсутствии писем); `docker exec flounder-api cat /proc/net/tcp | grep -ci ':024B '` (0x24B = 587; должно быть 0). |
| 10 | **Postgres: autovacuum, checkpointer, `event_publication` (Spring Modulith) и `notification_outbox` растут без очистки.** | Строки завершённых событий Modulith никогда не удаляются (`spring.modulith.events.completion-mode` не задан, по умолчанию `update`), `notification_outbox` хранит SENT навсегда. На демо-объёме это мегабайты, а не нагрузка. | `docker exec flounder-postgres psql -U sport_user -d sport_platform -c "select relname, n_live_tup, n_dead_tup, last_autovacuum from pg_stat_user_tables order by n_live_tup desc limit 15"`. |

Итог: сначала смотрим §2 (10 минут владельца). Ожидаю, что картина сложится из п.1–3 и п.4/5 (если были рестарты/сборки). Приложение надо оптимизировать не ради «простоя 20 %», а ради p95 на демо и устойчивости — это §3–4.

## 2. Команды для владельца (копировать по строке)

Вход: `ssh root@82.97.248.229`, затем `su - deploy`, затем `cd ~/flounder`. Все команды ниже — от пользователя `deploy` (если команда начинается с `sudo`, при запросе пароля вернуться в root и выполнить без `sudo`). Результат каждой — скопировать в чат целиком.

Общее состояние хоста:
```bash
uptime; nproc; free -m; df -h / /var/lib/docker
top -bn1 | head -15
vmstat 1 5
cat /proc/pressure/cpu; cat /proc/pressure/memory
ps -eo pid,etimes,pcpu,pmem,comm --sort=-pcpu | head -15
systemctl list-timers --all --no-pager | head -20
```

Докер и контейнеры:
```bash
docker stats --no-stream
docker ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.RunningFor}}'
docker inspect --format '{{.Name}} restarts={{.RestartCount}} started={{.State.StartedAt}} health={{.State.Health.Status}}' flounder-api flounder-postgres flounder-caddy flounder-web
ps -L -o pid,tid,pcpu,pmem,comm -p "$(docker inspect -f '{{.State.Pid}}' flounder-api)" --sort=-pcpu | head -20
docker exec flounder-api sh -c 'cat /sys/fs/cgroup/memory.max; cat /sys/fs/cgroup/cpu.max'
```

Кто ходит на сервер (шум из интернета и реальные запросы):
```bash
sudo journalctl -u ssh --since -1h --no-pager | grep -c -E 'Invalid user|Failed password|Connection closed by'
sudo fail2ban-client status sshd 2>/dev/null || echo 'fail2ban не установлен'
docker logs flounder-caddy --since 1h 2>&1 | wc -l
docker logs flounder-caddy --since 1h 2>&1 | grep -oE '"uri":"[^"?]+' | sort | uniq -c | sort -rn | head -25
docker logs flounder-caddy --since 1h 2>&1 | grep -oE '"remote_ip":"[^"]+' | sort | uniq -c | sort -rn | head -10
docker logs flounder-caddy --since 1h 2>&1 | grep -oE '"User-Agent":\["[^"]{0,60}' | sort | uniq -c | sort -rn | head -10
```

Приложение (ошибки, шедулеры, SMTP):
```bash
docker logs flounder-api --since 1h 2>&1 | grep -c -E '"log.level":"ERROR"'
docker logs flounder-api --since 1h 2>&1 | grep -E '"log.level":"(ERROR|WARN)"' | tail -20
docker logs flounder-api --since 1h 2>&1 | grep -i -E 'lifecycle|outbox|smtp|mail' | tail -10
docker logs flounder-api 2>&1 | grep -E 'Started SportPlatformApplication' | tail -5
curl -s -o /dev/null -w 'health=%{http_code} time=%{time_total}s\n' http://127.0.0.1:8080/actuator/health 2>/dev/null || docker exec flounder-api curl -s -o /dev/null -w 'health=%{http_code} time=%{time_total}s\n' http://localhost:8080/actuator/health
```

Postgres (активность, медленные запросы, размеры):
```bash
docker exec flounder-postgres psql -U sport_user -d sport_platform -c "select pid, state, now()-query_start as age, left(query,90) as query from pg_stat_activity where datname='sport_platform' and pid<>pg_backend_pid() order by query_start"
docker exec flounder-postgres psql -U sport_user -d sport_platform -c "select relname, n_live_tup, n_dead_tup, seq_scan, idx_scan, last_autovacuum from pg_stat_user_tables order by seq_scan desc limit 20"
docker exec flounder-postgres psql -U sport_user -d sport_platform -c "select pg_size_pretty(pg_database_size('sport_platform')) as db_size"
docker exec flounder-postgres psql -U sport_user -d sport_platform -c "show shared_buffers; show work_mem; show max_connections; show shared_preload_libraries"
docker exec flounder-postgres psql -U sport_user -d sport_platform -c "select calls, round(total_exec_time::numeric,1) as total_ms, round(mean_exec_time::numeric,2) as mean_ms, left(query,100) as query from pg_stat_statements order by total_exec_time desc limit 15" 2>&1 | head -25
```
Последняя команда сейчас ответит `relation "pg_stat_statements" does not exist` — расширение включается в S03 (параметр `shared_preload_libraries` + `create extension`). После S03 её можно запускать как есть.

Быстрый замер отклика публичного API снаружи (с ноутбука, не с сервера; подставить slug демо-события):
```bash
for p in "" /start-list /schedule /live /results; do curl -s -o /dev/null -w "$p %{http_code} %{time_total}s\n" "https://sportgate.pro/api/v1/public/events/SLUG$p"; done
```

## 3. Что нашлось в коде и конфигах

### 3.1 Контейнеры, JVM, healthcheck (`infra/compose.prod.yaml`, `sport-platform/Dockerfile`)
- У prod-сервисов нет `mem_limit` (в staging есть). JVM получает `-Xmx1024m` жёстко; `-XX:MaxRAMPercentage` не используется; нет `-Xss`, `-XX:MaxMetaspaceSize`, `ReservedCodeCacheSize`. При OOM за пределами heap (metaspace, threads, PDF-рендер openhtmltopdf/PDFBox, POI при импорте Excel) контейнер может съесть память хоста целиком, и тогда OOM-killer убьёт что попало (бывает Postgres).
- `healthcheck` API: `curl` каждые 15 с (`retries: 10`, `start_period: 90s`); Postgres — `pg_isready` каждые 10 с. Нормально, но интервал 30 с снизит шум, а `/actuator/health/readiness` точнее отражает готовность.
- Нет `stop_grace_period` (по умолчанию 10 с), при `server.shutdown: graceful` запросы в полёте (PDF, импорт) могут не успеть завершиться при деплое.
- Caddy: `encode gzip zstd` и HTTP/3 (443/udp опубликован) есть. Нет `reverse_proxy` retry (`lb_try_duration`) — пока API перезапускается (60–90 с), публика видит 502 вместо ожидания. Нет `handle_errors` с понятной страницей «технические работы».
- Образ: нет CDS/AppCDS и layered-jar — старт JVM ~40–60 с на 2 vCPU; с AppCDS можно сократить на 30–40 % (важно для времени восстановления после рестарта).
- `deploy.sh` собирает образы на самом сервере (`up -d --build`) — пики CPU/памяти, риск OOM для работающего API.

### 3.2 Spring/JPA/Hikari (`application.yml`, `application-prod.yml`)
- Хорошо: `open-in-view: false`, `ddl-auto: validate`, `show-sql: false`, `org.hibernate.SQL: WARN`, Redis/Kafka исключены, JSON-логи ECS.
- Hikari: только `maximum-pool-size: 10`; нет `minimum-idle`, `connection-timeout`, `max-lifetime`, `keepalive-time`, `leak-detection-threshold`.
- Нет `hibernate.jdbc.batch_size` / `order_inserts` / `order_updates` — импорт Excel (200 заявок) и генерация заплывов делают по INSERT на строку.
- Tomcat по умолчанию: 200 потоков, на 2 vCPU это избыточно; `server.tomcat.threads.max` не задан.
- `management.tracing.enabled: true, sampling 1.0` — моста трассировки нет, по факту не работает; лучше явно выключить, чтобы не включилось случайно при добавлении зависимости.
- Spring Modulith: `event_publication` не чистится (нет `spring.modulith.events.completion-mode: delete`).
- Аутентификация: на каждый запрос с JWT — 1 запрос в БД (`users.findAuthenticationById`), далее `EventAccessService.assertCanManage` — ещё `events.findById` + членство в организации. 3 запроса до бизнес-логики на каждый организаторский вызов. Сознательное решение (мгновенная блокировка), для демо приемлемо; кэш 5–10 с — после демо.

### 3.3 Шедулеры
- `EventLifecycleScheduler` — cron `0 * * * * *` (раз в минуту): `findDueLifecycleEvents` по `events.status` + даты — без индекса (таблица маленькая). Обрабатывает по событию в своей транзакции с `PESSIMISTIC_WRITE`.
- `NotificationScheduler` — `fixedDelay = 10000`: `dueBatch` → `lockPending … for update skip locked`. Индекс `(status, created_at)` есть; лучше частичный по `next_attempt_at where status='PENDING'`. Условие `app.mail.worker-enabled` есть (по умолчанию `true`).
- Оба без jitter — некритично (один инстанс). Оба синхронные в общем `TaskScheduler` (1 поток по умолчанию): долгая отправка писем (SMTP таймаут 5 с × 50 писем) заблокирует lifecycle-переходы на минуты. Нужен отдельный пул или ограничение батча по времени.

### 3.4 Публичные чтения (`publication/infrastructure/PublicationReadRepository.java`)
- Кэш Caffeine 10 с на 6 кэшей (`publicEvent`, `publicStartList`, `publicSchedule`, `publicLive`, `publicResults`, `publicAthletes`), `maximumSize(500)`. Хорошо. Но HTTP-заголовков кэширования (`Cache-Control`, `ETag`) на публичных JSON нет — каждый опрос раз в 5 с гоняет полный JSON (стартовый протокол на 200 участников — 100–200 КБ до gzip) даже если ничего не изменилось. `ShallowEtagHeaderFilter` + `Cache-Control: public, max-age=5, stale-while-revalidate=30` дадут 304 и экономию трафика на телефонах у бассейна.
- `UNIT_QUERY`: коррелированный подзапрос `select max(l.executed_at) from rule_execution_logs l where l.event_id=d.event_id and l.kind='SCHEDULE_SHIFT'` выполняется на каждую строку заплыва; индекса `(event_id, kind, executed_at)` нет. Для 60 заплывов и журнала из сотен строк — миллисекунды, но на 50 соревнований журнал вырастет. Вынести в CTE один раз на событие + индекс.
- `entries(eventId)` — один большой JOIN на 6 таблиц, индексы по всем FK есть (`idx_cue_competition_unit`, `idx_cue_registration`, `idx_results_entry`, `idx_registrations_participant_profile_id`). ОК.
- `capacity` — `count(distinct …)` по `registrations(event_id, status)`: составного индекса нет (есть отдельные `event_id` и `status`).
- `PublicDocumentService` — PDF кэшируется 60 с. ОК. PDF-рендер — самая тяжёлая операция (1–3 с CPU), защищена кэшем только для публичных; организаторские PDF без кэша — нормально.
- Фронт опрашивает `/live` и `/results` каждые 5 с при TTL кэша 10 с: половина запросов заведомо получает те же данные. Для демо оставить (ТЗ: «обновление не реже 10 с»), после — 10 с + ETag.

### 3.5 Организаторские чтения и N+1
- `SwimResultsUseCase.disciplineResults` и `rankDiscipline`: цикл по заплывам → `details.execute(unit)` / `entries.findByCompetitionUnitId` → по каждой дорожке `results.findByCompetitionUnitEntryId` + `registrations.getById` + разбор JSON-снимка. Для вида с 8 заплывами × 8 дорожек ≈ 130–200 запросов на один вызов «Результаты вида»/«Пересчитать места». Репозиторий `findByCompetitionUnitEntryIdIn` уже есть — надо батчить.
- `SchedulingData`/`ShiftScheduleUseCase`/`PublishUnitsUseCase`: `units.findByDisciplineId` в цикле по дисциплинам (10–20 запросов на событие) — терпимо, батч желателен.
- `ListRegistrationsForReviewUseCase`: списки без пагинации (`findAll()` для админа), но обогащение документами уже батчевое (`DocumentSummaryService`). На 50 соревнований × 200 заявок `findAll()` без фильтра — 10 000 строк JSON. Добавить лимит/пагинацию — этап 2.
- `AnalyticsReadRepository.event()` — ~10 агрегирующих запросов на вызов, CTE `REGISTRATIONS` повторяется 4 раза (каждый раз пересчитывается `exists(...)` по заплывам). Для демо — десятки мс; ответ уже `no-store`. Кэш 30 с на (eventId) — после демо.

### 3.6 Индексы (`db/migration/V002–V034`)
Есть: FK-индексы по `registrations`, `event_disciplines`, `competition_units`, `competition_unit_entries`, `results`, `organization_members`, `participant_documents`, `notification_outbox`, `refresh_tokens`; уникальные по slug/email/ключам.

Не хватает (нужно в новой миграции S03 — V038; V035–V037 заняты S05–S07):
- `events(organization_id)` — список событий организатора (`findByOrganizationIdIn…`) идёт seq scan; `events(status, event_start_at)` — публичный каталог и lifecycle; частичный индекс для шедулера `events(registration_open_at, registration_close_at) where status in ('PUBLISHED','REGISTRATION_OPEN')`.
- `registrations(event_id, status)` составной — списки на проверку, `capacity`, аналитика статусов.
- `competition_units(event_discipline_id, status)` — публичные выборки фильтруют по статусу.
- `rule_execution_logs(event_id, kind, executed_at desc)` — подзапрос `schedule_shifted_at`.
- `notification_outbox(next_attempt_at) where status='PENDING'` — запрос воркера каждые 10 с.
- `event_publication(publication_date) where completion_date is null` — Modulith читает незавершённые.
- `password_reset_tokens(expires_at)` / `refresh_tokens(expires_at)` — если есть/появится очистка просроченных.

FK-ограничений нет у `events.organization_id/venue_id/sport_id/regulation_version_id` (V009), `results.recorded_by_user_id`, `competition_units` на события — только через дисциплины. Это не про скорость, а про целостность; добавлять как `NOT VALID` + `VALIDATE` после проверки сиротских строк — после демо.

### 3.7 Postgres в compose
`postgres:16` с дефолтами: `shared_buffers=128MB`, `work_mem=4MB`, `effective_cache_size=4GB` (врёт для нашей машины), `max_connections=100`, `random_page_cost=4` (для SSD нужен ~1.1), без `pg_stat_statements`, без `log_min_duration_statement`, без `shm_size`. Для 4 ГБ, где главный жилец — JVM, разумно: `shared_buffers=256MB`, `effective_cache_size=768MB`, `work_mem=8MB`, `maintenance_work_mem=64MB`, `max_connections=40`, `random_page_cost=1.1`, `wal_compression=on`, `checkpoint_completion_target=0.9`, `log_min_duration_statement=300`, `shared_preload_libraries=pg_stat_statements`, `mem_limit: 640m`, `shm_size: 256m`.

### 3.8 Фронтенд
- Маршруты: 35 `lazy()` — админка/организатор не грузятся на публичных страницах. Sentry подключается `await import()` — не в основном чанке. PWA-воркер кэширует `/assets/*` на 30 дней, публичный API — NetworkFirst с таймаутом 7 с. Статика отдаётся `immutable`, `index.html` — `no-cache`. Всё правильно.
- Нет контроля размера бандла (`rollup-plugin-visualizer`/`manualChunks`); `react-router` 8 + React 19 ~ 60–70 КБ gz — ожидаемо. Замерить `npm run build` и зафиксировать в отчёте (S03).

### 3.9 Отказоустойчивость (то, что реально ломается на одном VPS)
1. Единственный API-контейнер: любой деплой = 60–120 с 502. Смягчение: Caddy `lb_try_duration` + страница ошибки; AppCDS для быстрого старта; сборка образа до `up` (`compose build` отдельно, затем `up -d --no-build`), чтобы окно простоя было только на перезапуск.
2. Нет лимитов памяти → OOM-killer выбирает жертву сам. С лимитами и `-XX:+ExitOnOutOfMemoryError` (есть) падает и перезапускается только API.
3. Бэкап раз в сутки в 03:00 (`infra/crontab.example`), cron на сервере ещё не поставлен. В дни демо (17–19.10) нужен `pg_dump` каждый час и проверка восстановления.
4. Мониторинг только изнутри того же VPS (Kuma не поднята). Нужен внешний бесплатный монитор на `/actuator/health` (UptimeRobot/BetterStack) + Telegram.
5. Перезагрузка хоста (ждёт «System restart required»): `restart: unless-stopped` поднимет контейнеры, но порядок старта (`depends_on … service_healthy`) и 90 с `start_period` дадут ~2–3 минуты простоя. Это норма; главное — не делать перезагрузку в день демо.

## 4. Оптимизации: эффект и риск

### 4.1 До демо (безопасно, не меняет API и схему данных кроме новых индексов)
| Что | Эффект | Риск |
|-----|--------|------|
| V038: индексы из §3.6 (плоские `create index`, таблицы на сотни строк — секунды) | Убирает seq scan на списках организатора и lifecycle-запросе; готовит к 50 соревнованиям | Минимальный; Flyway применит при деплое за секунды |
| `mem_limit` на все prod-сервисы + `JAVA_TOOL_OPTIONS` с `-XX:MaxRAMPercentage=60 -XX:InitialRAMPercentage=30 -Xss512k -XX:MaxMetaspaceSize=256m -XX:+ExitOnOutOfMemoryError` вместо жёсткого `-Xmx` | Предсказуемая память, JVM видит лимит контейнера; при OOM гибнет только API и перезапускается | Низкий; heap остаётся ~1 ГБ при `mem_limit: 1664m` |
| Hikari: `minimum-idle: 2, maximum-pool-size: 10, connection-timeout: 5000, max-lifetime: 1500000, keepalive-time: 300000`; Tomcat `threads.max: 64` | Нет «залипших» соединений после рестарта Postgres; меньше потоков в простое | Низкий |
| Hibernate `jdbc.batch_size: 50, order_inserts: true, order_updates: true` | Импорт Excel/посев в 3–5 раз меньше round-trip'ов | Низкий (ID назначаются приложением, батчинг работает); проверить тестами импорта |
| Postgres `command:` с параметрами §3.7 + `pg_stat_statements` | Видно реальные медленные запросы; корректный планировщик для SSD | Низкий; требует один перезапуск Postgres (10 с), делать в окно без нагрузки |
| HTTP-кэш публичного API: `ShallowEtagHeaderFilter` на `/api/v1/public/**` + `Cache-Control: public, max-age=5, stale-while-revalidate=30` на JSON (не на PDF/документы) | 304 вместо полного JSON при опросе; меньше трафика на телефонах; Caddy не кэширует, поэтому согласованность не страдает | Низкий; проверить, что `X-Flounder-Cached` и offline-режим PWA не ломаются (NetworkFirst видит 200 из HTTP-кэша браузера) |
| Caddy: `reverse_proxy api:8080 { lb_try_duration 20s lb_try_interval 500ms }` + `handle_errors` со страницей «Идут технические работы, обновите через минуту» | Деплой/рестарт без 502 на публике, если укладывается в 20 с; иначе понятная страница | Низкий |
| Healthcheck API: `interval: 30s`, `start_period: 120s`, путь `/actuator/health/readiness`; `stop_grace_period: 30s` | Меньше ложных рестартов; корректное завершение | Низкий |
| `management.tracing.enabled: false`, `spring.modulith.events.completion-mode: delete` | Чистота конфигурации; `event_publication` не растёт | Низкий |
| `NotificationScheduler`: `fixedDelayString: ${app.mail.worker-delay:15000}`; отдельный `TaskScheduler` c 2 потоками; батч ≤ 20 писем за проход | Письма не блокируют lifecycle-шедулер | Низкий; тесты M05 должны пройти |
| Батчинг в `SwimResultsUseCase.disciplineResults/rankDiscipline` (загрузка дорожек/результатов/заявок списком по ID) | «Результаты вида» и «Пересчитать места» — десятки мс вместо сотен; меньше шанс таймаута на демо | Средний: логика ранжирования — покрыта тестами `results`, менять только способ загрузки |
| k6-прогон на локальном стенде (50 RPS публичные, 10 RPS организатор) с отчётом | Факты вместо ощущений: p95 до/после | Нет (не запускать против prod в день демо) |
| Владелец: cron из `infra/crontab.example` + внешний uptime-монитор | Бэкапы и алерт при падении | Нет |

### 4.2 После демо
| Что | Эффект | Риск / почему не сейчас |
|-----|--------|--------------------------|
| AppCDS/layered jar в Dockerfile (`-XX:SharedArchiveFile`, тренировочный запуск при сборке) | Старт API 40–60 с → 20–30 с | Меняет Dockerfile и сборку; требует проверки на сервере |
| Сборка образов в GitHub Actions, на сервере только `pull` + `up` | Нет пиков CPU/памяти на prod, деплой за 30 с | Нужен registry (GHCR) и секреты |
| Кэш снимка аутентификации (5–10 с) и кэш `EventAccessService` | −2–3 запроса на каждый организаторский вызов | Блокировка пользователя срабатывает с задержкой до TTL — согласовать с QA04 |
| Пагинация `/registrations` и `/events` (контракт API) | Готовность к 50 соревнованиям | Меняет контракт ТЗ §5 и фронт |
| Кэш аналитики 30 с; переписать `REGISTRATIONS` CTE в одну материализацию | Аналитика на 10 000 заявок — сотни мс | Нужны данные объёма |
| Опрос публичных страниц 5 с → 10 с + ETag; дальше SSE для табло | В 2 раза меньше запросов на зрителя | Требование ТЗ «не реже 10 с» надо перечитать с владельцем |
| FK на `events.organization_id/venue_id/sport_id` (`NOT VALID` → `VALIDATE`) | Целостность | Возможны сиротские строки в старых сидах |
| Отдельный VPS под staging/Kuma или 4 vCPU/8 ГБ | Нет конкуренции за память с prod | Деньги |
| Postgres: ежечасный `pg_dump` + `wal-g`/репликация на второй диск | RPO 1 час вместо суток | Инфраструктура |
| Rate limit на публичный API в Caddy (`rate_limit` плагин — отдельная сборка образа) или в Spring (Bucket4j) | Защита от сканеров и перегрева | Отдельная сборка Caddy |

## 5. Целевые показатели (для S03 и отчёта k6)
- Публичные страницы (`/api/v1/public/events/{slug}`, `/start-list`, `/schedule`, `/live`, `/results`, `/athletes?q=`): p95 < 300 мс при 50 RPS суммарно, ошибок < 0,5 %.
- API организатора (`/organizer/events`, `/events/{id}`, `/events/{id}/registrations`, `/events/{id}/disciplines`, `/event-disciplines/{id}/competition-units`, `/event-disciplines/{id}/results`, `/events/{id}/analytics`): p95 < 500 мс при 10 RPS.
- CPU API в простое (без внешних запросов) < 5 % одного ядра по `docker stats`; память API < 1,4 ГБ RSS; Postgres < 400 МБ.
- Восстановление после `docker restart flounder-api` — публичная страница отвечает 200 не позже чем через 90 с.

## 6. Что сделать владельцу прямо сейчас (без Codex)
1. Прогнать блок §2 и прислать вывод — это закроет вопрос «откуда 20–30 %».
2. Поставить cron бэкапов (`crontab -e` → строки из `infra/crontab.example`) и внешний монитор на `https://sportgate.pro/actuator/health` с уведомлением в Telegram.
3. Не делать перезагрузку сервера и деплои 18–19.10; все изменения S03 деплоить 16–17.10 с проверкой по `docs/qa/rehearsal-2026-10-17.md`.

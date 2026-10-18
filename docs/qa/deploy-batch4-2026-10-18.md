# Деплой партии 4 (18.10): S03-A (перф/БД) + D03 PR-1 и PR-2 (UX и визуал v2)

Предусловие: слиты #62 (S03-A, миграция V038), #65 (D03 PR-1), #66 (D03 PR-2) — в этом порядке. CI `main` зелёный. Ревью: `docs/reviews/S03-review-2.md`, `D03-PR1-review-2.md`, `D03-PR2-review-2.md`.

Что меняется на стенде: compose пересоздаёт **все четыре контейнера** (Postgres с `pg_stat_statements` и `shared_buffers=256MB`, лимиты памяти, readiness-проба API, retry и русская страница 503 в Caddy). Данные в томах не трогаются. Новых обязательных переменных в `.env.prod` нет. Flyway применит V038 (10 индексов, секунды). **Простой публичного сайта ≈ 1,5–2,5 мин** — не делать во время репетиции.

Откат: `git checkout bdb8cd8 && ./infra/deploy.sh --no-pull` (ещё ~2 мин простоя); базу откатывать не нужно — V038 для старого кода безвредна.

Команды — по одной, каждый блок с самого начала.

## Блок 1 — бэкап и деплой (~10 мин)
```
ssh root@82.97.248.229
su - deploy
cd ~/flounder
./infra/backup.sh
git pull
./infra/deploy.sh
docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod ps
curl -s https://sportgate.pro/actuator/health
```
Ожидание: четыре контейнера `Up`, `flounder-api … (healthy)`, health `UP`. Предупреждение Docker про «swap limit capabilities» — не ошибка.

## Блок 2 — проверки S03 (2 мин)
```
docker exec flounder-postgres psql -U sport_user -d sport_platform -c "show shared_buffers"
docker exec flounder-postgres psql -U sport_user -d sport_platform -c "select count(*) from pg_stat_statements"
docker exec flounder-postgres psql -U sport_user -d sport_platform -c "select version from flyway_schema_history order by installed_rank desc limit 1"
curl -sI https://sportgate.pro/api/v1/public/events/otkrytyy-kubok-samary-po-plavaniyu/live | grep -i -E 'cache-control|etag'
```
Ожидание: `256MB`; число (не ошибка); `038`; `Cache-Control: max-age=5…` и `ETag`.

## Блок 3 — проверки D03 в браузере (10 мин)
1. Логотип/«SportGate» в шапке: гость → каталог `/`; после входа `org@flounder.demo` → «Мои соревнования»; `parent@flounder.demo` → «Мои заявки»; `judge@flounder.demo` → судейство.
2. `/e/pervenstvo-oblasti-sredi-yunoshey/register` на телефоне и ноутбуке: карточка по центру, заголовок внутри, поля на всю ширину, блок видов неактивен до выбора пола и даты.
3. Кабинет организатора → событие B → «Заявки» на 1366×768: колонка действий видна без горизонтальной прокрутки, у заявки с нулевым взносом есть «Оплачено».
4. Событие B → «Программа»: у неопубликованного вида есть «Показать на публичной странице».
5. Табло `/e/otkrytyy-kubok-samary-po-plavaniyu/live` в светлой и тёмной теме: линейка под шапкой сплошная, в карточке «Сейчас заплыв» видна «Открыть заплыв».
6. Бейджи статусов и тост после действия — цветные (не серые).
7. Прогон 7 шагов `docs/demo-script.md` — репетиция по `docs/qa/rehearsal-2026-10-17.md`.

## После деплоя
- Выставить в `infra/.env.prod` `APP_AUTH_LOGIN_MAX_FAILURES=20` оставить до демо (лимит по IP теперь отдельный — появится с S04 после демо).
- Если табло обновляется медленнее 15 с — это ожидаемо (опрос 15 с + кеш 10 с, до 25 с); в сценарии демо говорить «до полуминуты».

# Деплой партии 3 (16.10): S01 SportGate + M05-3 + S02 три демо-события

Предусловие: в `main` слиты #51 (M05-3), #54 (S01), #56 (S02). S03-A (#62) и D03 (#65, #66) — отдельным деплоем после правок (S03 пересоздаёт контейнер Postgres — см. его PR).

Новых переменных окружения нет. Миграций нет (S02 — только скрипты сида и фронт-тексты). Откат: `git checkout 4571b33 && ./infra/deploy.sh --no-pull` (без изменений БД).

Команды — **по одной**, каждая группа с самого начала (сессия сбрасывается).

## Шаг 1 — деплой кода (~4 мин)
```
ssh root@82.97.248.229
su - deploy
cd ~/flounder
./infra/backup.sh
git pull
./infra/deploy.sh
curl -s https://sportgate.pro/actuator/health
```
Проверка в браузере: заголовок/бренд «SportGate» на публичной странице и в кабинете.

## Шаг 2 — задать e-mail демо-участника (один раз)
```
ssh root@82.97.248.229
su - deploy
cd ~/flounder
grep -q '^DEMO_OWNER_EMAIL=' scripts/demo/.env && echo есть || printf 'DEMO_OWNER_EMAIL=sergeymcdzkz@gmail.com\n' >> scripts/demo/.env
grep -c '^DEMO_SEED_ENABLED=true' scripts/demo/.env
```
Второй вывод должен быть `1`. Если `scripts/demo/.env` нет — остановиться и написать в чат (нужны `API_URL`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `DEMO_PASSWORD`; значения не присылать).

## Шаг 3 — удалить старое одиночное событие (API останавливается на ~1 мин)
```
ssh root@82.97.248.229
su - deploy
cd ~/flounder
curl -fsS https://sportgate.pro/api/v1/public/events/otkrytyy-kubok-samary-po-plavaniyu-demo -o /tmp/s02-legacy-event.json
python3 -c "import json;e=json.load(open('/tmp/s02-legacy-event.json'));assert e['title']=='Открытый кубок Самары по плаванию (демо)';print(json.dumps([{'id':e['id'],'title':e['title'],'slug':e['publicSlug']}],ensure_ascii=False))" > /tmp/s02-legacy-targets.json
cat /tmp/s02-legacy-targets.json
docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod stop api
docker exec -i flounder-postgres psql -X -U sport_user -d sport_platform -v ON_ERROR_STOP=1 -v "demo_targets=$(cat /tmp/s02-legacy-targets.json)" < scripts/demo/reset.sql
docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod start api
sleep 90; curl -s https://sportgate.pro/actuator/health
```
Если `curl` на первом шаге вернул 404 — событие уже удалено, шаг 3 пропустить.

## Шаг 4 — сид трёх событий (5–10 мин, контейнер с Node)
```
ssh root@82.97.248.229
su - deploy
cd ~/flounder
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -e DEMO_DATE=2026-10-19 -e DEMO_EVENTS=A,B,C -v "$PWD":/repo -w /repo/scripts/demo node:22-alpine sh -eu -c 'npm ci && npx tsx seed.ts'
```
В конце вывода — строки `S02 A/B/C` со slug и UUID: скопировать в чат. Ожидается: A `kubok-sportgate-osen` (11.10, COMPLETED), B `otkrytyy-kubok-samary-po-plavaniyu` (20.10, REGISTRATION_CLOSED), C `pervenstvo-oblasti-sredi-yunoshey` (24.10, REGISTRATION_OPEN).

## Шаг 5 — проверки (браузер, 5 минут)
1. `https://sportgate.pro/e/kubok-sportgate-osen/results` — медалисты, PDF итогового протокола.
2. `https://sportgate.pro/e/otkrytyy-kubok-samary-po-plavaniyu/live` — табло указывает на третий заплыв.
3. `https://sportgate.pro/e/pervenstvo-oblasti-sredi-yunoshey` — регистрация открыта, лимит 150.
4. Вход `org@flounder.demo` → три события в кабинете.
5. Письмо на `sergeymcdzkz@gmail.com` (сброс пароля участника) — приходит.

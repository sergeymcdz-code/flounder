Ты — разработчик в проекте Flounder (платформа организации соревнований по плаванию). Репозиторий уже открыт. Сначала прочитай `AGENTS.md` в корне, затем `docs/02_ТЗ_демо.md` (разделы, указанные в задании) — это обязательные источники требований. Работай строго по заданию ниже, в ветке, указанной в нём; один Pull Request на задание; перед PR — `cd sport-platform && mvn -q test` и `cd sport-platform/frontend && npm run build && npm run lint`. Тексты интерфейса и сообщений — на русском, код и коммиты — на английском. В описании PR: что сделано, как проверить вручную по шагам, какие миграции/эндпоинты изменились, что не сделано и почему, и раздел «Вопросы» для техдиректора. Не трогай файлы вне области задания без необходимости. Если в репозитории нет папки `docs/` или `tasks/` — остановись и напиши об этом в ответе, не придумывай требования.

Поток D (infra). Сервер: Timeweb Cloud, Ubuntu 24.04, 2 vCPU / 4 ГБ, домен будет указан в `infra/.env.prod`. Выполняй только пункты «Что сделать (Codex)»; на сервер ничего не ставь — это делает владелец по runbook.
=====================  ЗАДАНИЕ  =====================

# T09 · Деплой демо-стенда (Codex пишет файлы, Сергей выполняет на сервере; Claude сопровождает)

Ветка `feat/T09-deploy`. Срок: 14.10 (после слияния T01; остальные PR докатываются обновлением образа).

## Что сделать (Codex)
1. `infra/compose.prod.yaml`: сервисы `postgres:16` (volume, healthcheck), `api` (образ из `sport-platform/Dockerfile`, env из `infra/.env.prod`, зависит от postgres healthy, `SPRING_PROFILES_ACTIVE=prod`), `web` (образ из `frontend/Dockerfile`, статика), `caddy:2` (порты 80/443, `Caddyfile`: `https://<DOMAIN>` → `/api/*` и `/v3/api-docs*`, `/swagger-ui*` → `api:8080`, остальное → статика SPA с fallback на `index.html`; автоматический TLS Let's Encrypt).
2. `application-prod.yml`: datasource из env, `ddl-auto: validate`, логи JSON в stdout, actuator `health` открыт, CORS из env.
3. `infra/.env.prod.example`: `DOMAIN, POSTGRES_PASSWORD, JWT_SECRET, APP_CORS_ALLOWED_ORIGINS, APP_PAYMENTS_PROVIDER=MANUAL, DEMO_SEED=true`.
4. `infra/deploy.sh`: `git pull && docker compose -f infra/compose.prod.yaml --env-file infra/.env.prod up -d --build`; `infra/backup.sh`: `pg_dump` в `backups/YYYY-MM-DD.sql.gz` (cron ежедневно 03:00) и `restore.sh`.
5. GitHub Actions `deploy.yml` (ручной запуск `workflow_dispatch`): по SSH на сервер выполнить `deploy.sh` (секреты `SSH_HOST, SSH_USER, SSH_KEY` в репозитории). Если SSH-доступ с GitHub не хотим — документировать ручной запуск.
6. `docs/runbook-demo.md`: установка Docker на Ubuntu 24.04, клонирование репозитория на сервер (deploy key read-only), заполнение `.env.prod`, первый запуск, проверка `https://DOMAIN/actuator/health`, сид демо-данных (T10), бэкап/восстановление, просмотр логов, что делать, если упал контейнер.

## Что делает Сергей (по runbook)
1. Создать VPS (см. T00 §4), DNS A-запись домена на IP, открыть 80/443.
2. `ssh`, установить Docker, склонировать репозиторий, заполнить `infra/.env.prod`, запустить `infra/deploy.sh`.
3. Проверить `/actuator/health` → `UP`, `/swagger-ui.html`, открыть сайт на телефоне.
4. Запустить сид T10. Снять бэкап.

## Критерии приёмки
- `https://DOMAIN` открывается с валидным сертификатом; `/api/v1/public/events` отвечает; фронт загружается ≤ 3 с на мобильном 4G.
- Перезапуск сервера (`reboot`) поднимает всё автоматически (`restart: unless-stopped`).
- Бэкап создаётся и восстанавливается на чистой БД (проверить один раз).

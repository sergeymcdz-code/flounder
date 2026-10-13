# QA05: сквозной прогон демо

`npm run test:demo` запускает `playwright.qa05.config.ts` и семь последовательных шагов `qa05-demo.spec.ts`. Нужны настоящий backend на localhost:8080, PostgreSQL с миграциями, Node.js 22+ и зависимости `scripts/demo`. MSW в этом конфиге выключен. Обычные `test:e2e` и `test:organizer` сохранены.

Из корня репозитория:

```sh
cd scripts/demo && npm ci
cd ../../sport-platform
docker compose up -d postgres
# Только локальный тестовый ключ: JwtService требует Base64, >=32 байт после декодирования.
JWT_SECRET=bG9jYWwtZGV2ZWxvcG1lbnQtc2VjcmV0LW11c3QtYmUtYXQtbGVhc3QtMzItY2hhcmFjdGVycy1sb25n \
  mvn spring-boot:run -Dspring-boot.run.profiles=local
```

В другом терминале:

```sh
cd sport-platform/frontend
npm ci
npx playwright install chromium
npm run test:demo
# Для установленного системного Chromium:
PLAYWRIGHT_CHROMIUM_EXECUTABLE=/usr/bin/chromium npm run test:demo
```

Frontend собирается и запускается на localhost:5173 автоматически. Этот origin должен быть разрешён CORS backend. `QA05_API_URL` меняет URL локального backend; `QA05_PASSWORD` меняет пароль тестовых демо-аккаунтов. Значения токенов и паролей в отчёт не записываются.

Перед сидом проверяется hostname `QA05_API_URL`: разрешены только `localhost` и `127.0.0.1`. Другой адрес вызывает ошибку «QA05 запускается только против локального backend; для удалённого стенда задайте QA05_ALLOW_REMOTE=1». Явный `QA05_ALLOW_REMOTE=1` отключает эту защиту; сид создаёт данные на указанном backend.

Setup вызывает настоящий `scripts/demo/seed.ts --skip-import` с уникальным суффиксом ` · QA05 <UTC timestamp>`. Каждый прогон создаёт отдельное событие с нулём заявок; общие аккаунты, регламент и площадка переиспользуются. Данные прошлых прогонов сохраняются; автоматического SQL-сброса нет. UUID и slug записываются в `frontend/work/qa05/seed.json`.

Для организатора, родителя, секретаря и анонимного зрителя используются четыре независимых browser context. Родитель заполняет форму при 375×812. По умолчанию скриншоты всех посещённых страниц сохраняются при 1366×900 и 375×812 в `work/qa05/evidence/screenshots/QA05-*.png`, PDF — в `work/qa05/evidence/QA05-*.pdf`. Снимок валидатора ограничен его панелью. `QA05_EVIDENCE_DIR` переопределяет корень доказательств относительно frontend. Для стартового PDF проверяется `break-inside: avoid` у каждого заплыва в print media, для итогового — у строк таблицы.

Для явной публикации доказательств в `docs/qa/` из frontend:

```sh
QA05_EVIDENCE_DIR=../../docs/qa npm run test:demo
```

После такого прогона перед коммитом отберите необходимые снимки: не больше 15 PNG и 3 МБ суммарно в `docs/qa/screenshots/`. Обычный запуск пишет только в игнорируемый `work/`.

Неисправленные дефекты дают ненулевой exit code. Soft assertions сохраняют ошибки, а не превращают их в успешные проверки. Исключения шагов записываются в `work/qa05/audit.json`. После исключения, блокирующего шаг 2 или 4, все последующие шаги записываются как `blocked: 'skipped: step N failed'` без выполнения. Известные дефекты, зафиксированные soft assertions, не блокируют дальнейшую диагностику. Если UI оплаты заблокирован, тест явно продолжает через реальный `mark-paid` секретаря. Шаг 7 выполняет «Опубликовать результаты → публичная выдача → исправление → Финиш»; обход с повторным «Старт» после публикации удалён в M10. Обход оплаты отмечается в QA-отчёте и не засчитывается как успех исходного UI-сценария.

Собираются `console.error`, `pageerror`, неожиданные HTTP 4xx/5xx и сетевые ошибки. Отмена запроса при навигации (`ERR_ABORTED`) ожидаема. HTTP-ошибки не разрешаются общим allowlist. Playwright JSON-отчёт и trace при падении находятся в `frontend/work/qa05/` и не коммитятся.

Требования и состояние дефектов: [QA05-e2e-demo.md](../../../docs/qa/QA05-e2e-demo.md).

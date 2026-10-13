# QA05: сквозной прогон демо

`npm run e2e` запускает `playwright.qa05.config.ts` и семь последовательных шагов `qa05-demo.spec.ts`. Нужны настоящий backend на localhost:8080, PostgreSQL с миграциями, Node.js 22+ и зависимости `scripts/demo`. MSW в этом конфиге выключен. Обычные `test:e2e` и `test:organizer` сохранены.

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
npm run e2e
# Для установленного системного Chromium:
PLAYWRIGHT_CHROMIUM_EXECUTABLE=/usr/bin/chromium npm run e2e
```

Frontend собирается и запускается на localhost:5173 автоматически. Этот origin должен быть разрешён CORS backend. `QA05_API_URL` меняет URL локального backend; `QA05_PASSWORD` меняет пароль тестовых демо-аккаунтов. Значения токенов и паролей в отчёт не записываются.

Setup вызывает настоящий `scripts/demo/seed.ts --skip-import` с уникальным суффиксом ` · QA05 <UTC timestamp>`. Каждый прогон создаёт отдельное событие с нулём заявок; общие аккаунты, регламент и площадка переиспользуются. Данные прошлых прогонов сохраняются; автоматического SQL-сброса нет. UUID и slug записываются в `frontend/work/qa05/seed.json`.

Для организатора, родителя, секретаря и анонимного зрителя используются четыре независимых browser context. Родитель заполняет форму при 375×812. Скриншоты всех посещённых страниц сохраняются при 1366×900 и 375×812 в `docs/qa/screenshots/QA05-*.png`; PDF — `docs/qa/QA05-*.pdf`. Для стартового PDF проверяется `break-inside: avoid` у каждого заплыва в print media, для итогового — у строк таблицы.

Неисправленные дефекты дают ненулевой exit code. Soft assertions сохраняют ошибки, а не превращают их в успешные проверки. Исключения шагов записываются в `work/qa05/audit.json`; последующие доступные шаги продолжаются. Если UI оплаты заблокирован, тест явно продолжает через реальный `mark-paid` секретаря. Если опубликованные результаты скрыты, после фиксации падения и исправления тест нажимает «Финиш», чтобы отдельно проверить итоговый PDF. Эти обходы отмечаются в QA-отчёте и не засчитываются как успех исходного UI-сценария.

Собираются `console.error`, `pageerror`, неожиданные HTTP 4xx/5xx и сетевые ошибки. Отмена запроса при навигации (`ERR_ABORTED`) ожидаема. HTTP-ошибки не разрешаются общим allowlist. Playwright JSON-отчёт и trace при падении находятся в `frontend/work/qa05/` и не коммитятся.

Требования и состояние дефектов: [QA05-e2e-demo.md](../../../docs/qa/QA05-e2e-demo.md).

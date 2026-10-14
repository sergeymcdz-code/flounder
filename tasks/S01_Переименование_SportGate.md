# S01 — Бренд SportGate во всём интерфейсе (Flounder остаётся именем проекта)

Ветка `feat/S01-sportgate-brand` от актуального `origin/main`. Один PR. Правила — AGENTS.md. Приоритет: высокий (демо 19.10).

## Что сделать
1. Все тексты, видимые пользователю, — «SportGate» вместо «Flounder»: шапки `AppLayout.tsx`/`PublicLayout.tsx`, `document.title` (`… · SportGate`), `index.html` (title, description, apple-touch-icon alt), PWA manifest (name/short_name «SportGate», description), письма `templates/mail/*` («Команда SportGate»), тема письма сброса пароля в `NotificationTemplate`/`PasswordService`, PDF-протоколы (колонтитул/подпись, если есть упоминание), `OpenApiConfig` title «SportGate API», имя выгрузки `sportgate-registrations.xlsx`, MSW-фикстуры/демо-данные («Осенний кубок SportGate»), e2e-ожидания.
2. Иконки: заменить `public/icons/flounder-*.png`, `flounder.svg`, `favicon.svg` на нейтральный знак SportGate (буква S/ворота, 2 цвета из палитры D01) — сгенерировать через `scripts/generate-d01-icons.mjs` с новым именем `sportgate-*.png`; обновить ссылки. Старые файлы удалить.
3. НЕ переименовывать: репозиторий, пакеты `com.acme.sportplatform`, имена контейнеров/томов (`flounder-api`, `flounder_files`), ключи localStorage (`flounder.theme`, `flounder.auth-session` — совместимость с текущими сессиями пользователей), HMAC-метки `flounder-files-*` (иначе все зашифрованные файлы станут нечитаемы), e-mail демо-аккаунтов `@flounder.demo`, заголовок `X-Flounder-Cached`, события `flounder:public-cache`. В `docs/README.md` одной фразой: «Flounder — внутреннее имя проекта, SportGate — продуктовый бренд».
4. Текст подписи в письмах и в футере публичных страниц: «SportGate · sportgate.pro».

## Проверка
`cd frontend && npm test && npm run build && npm run lint`; `mvn -q test -Dtest='MailRenderingTest,PdfRendererTest'`; `grep -rn "Flounder" frontend/src src/main/resources/templates` — остаться должны только технические идентификаторы из п.3. Скриншоты шапки (1366 и 375) в описание PR. Миграций/эндпоинтов нет, TSV не меняется.

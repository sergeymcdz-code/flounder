# M01-followups — ревью 2 (PR #37, debf03a)

PR #37 «M01: желательные правки по ревью», ветка `pr/37` (`feat/M01-followups`), один коммит **debf03a** «fix(admin): close M01 review follow-ups», перебазированный на актуальный `origin/main` **028dc5e** (`git merge-base --is-ancestor origin/main pr/37` — да; merge-base = HEAD main). Ревью 1 смотрело fae4b37 поверх 63263d1 и дало «слить» с единственной оговоркой — конфликт с M02 (#29) в трёх файлах. M02 с тех пор влит в `main`, Codex перебазировал ветку и разрешил конфликты. Ревьюер: Claude, 13.10.2026. Диф с `main`: 10 файлов, +177/−16 (`AdminUserService.java` из дифа исчез — взята версия `main`/M02). CI на debf03a: backend, frontend, infra — `completed/success`; GitHub `mergeable_state: clean`. `git merge-tree --write-tree origin/main pr/37` — чисто. `git diff --check` — чисто.

Локально ни Maven, ни Vitest прогнать не удалось: в этой среде нет `~/.m2` (parent POM `spring-boot-starter-parent:3.5.6` недоступен офлайн) и `registry.npmjs.org` недоступен (DNS `ENOTFOUND` напрямую, `403` через прокси; `npm ci` в worktree `/tmp/wt-37` упал). Особая проверка (5) `npm test -- --run src/features/auth` поэтому не выполнена — принимаю по зелёному frontend-CI на этой же вершине и по описанию PR (101 Vitest). Остальное проверено статически: `git range-diff 63263d1..fae4b37 origin/main..pr/37`, `git show pr/37:…`, `git show origin/main:…`.

**Вердикт: слить.** Ребейз выполнен ровно так, как просило ревью 1: `range-diff` показывает только три ожидаемых отличия от fae4b37 (ветка `Retry-After` внутри 429 в `LoginPage.tsx`, объединённый `LoginPage.test.tsx` с `setAuthSession(null)`, снятая правка `AdminUserService` в пользу делегирования M02). Ничего лишнего не добавлено, тесты не ослаблены, миграций нет, новых эндпоинтов нет (TSV QA04 не требует правок), тексты UI на русском. Все пять особых проверок задания — положительные (пятая — по CI, см. выше).

## Проверено

### Пункты правок Codex

| № | Пункт | Где | Результат |
|---|---|---|---|
| 1 | Ребейз на актуальный `main` (с M02 #29) | `git merge-base origin/main pr/37` = `028dc5e` = HEAD `origin/main`; `--is-ancestor` — истина; один коммит `debf03a` поверх | **выполнен** |
| 2 | `LoginPage.tsx`: if/else из #37 + ветка `Retry-After` из M02 внутри 429 | `LoginPage.tsx:41-55`: `if (error instanceof ApiError)` → 403+`identity.user_blocked` → «Аккаунт заблокирован, обратитесь к администратору»; `else if (status === 429)` → `retryAfter !== undefined ? \`…Попробуйте через ${Math.max(1, Math.ceil(retryAfter / 60))} мин.\` : '…Попробуйте позже'`; 401 → «Проверьте email и пароль.»; прочие `ApiError` → `error.message`; не-`ApiError` → общий текст. Ссылка «Забыли пароль?» (M02) на месте (`:118`). Диф с `main` — только блок `catch` + завершающий перевод строки | **выполнен** |
| 3 | `LoginPage.test.tsx`: объединить оба набора, общий `beforeEach` через `setAuthSession(null)` | `LoginPage.test.tsx:12` `beforeEach(() => setAuthSession(null))`; `:14-30` `it.each` из #37 (403 blocked / 429 без заголовка / 401 / 403 access_denied); `:32-41` тест M02 «Retry-After: 121 → 3 мин.» без изменений. Диф с `main`: только добавления + перестановка импортов. Проверка `sessionStorage.getItem('flounder.auth-session')` валидна: M02 `auth-session.ts:3,25` по-прежнему пишет под этим ключом | **выполнен** |
| 4 | `AdminUserService.java`: делегирование M02 в `PasswordService.changeByAdministrator`, `updated_at` через `@PreUpdate` | `AdminUserService.java:59-62` — ровно версия `main`: `passwords.changeByAdministrator(id, password)`, файл в дифе PR отсутствует. `PasswordService.changeByAdministrator:49-58` (main): проверка 72 байт, `findByIdForUpdate`, `setPasswordHash`, отзыв refresh/reset — хеширование не дублируется. `UserEntity.preUpdate:46-49` ставит `updatedAt = now()`; dirty `passwordHash` → `@PreUpdate` сработает на `em.flush()` в тесте `M01FollowupsIntegrationTest.adminPasswordChangeUpdatesTheUserTimestamp:37-48` (`updated_at` после > `2000-01-01`) | **выполнен** |
| 5 | После ребейза: `mvn -q test`, `npm test/build/lint`, обновить описание PR | Описание PR обновлено: раздел «Ребейз перед слиянием» (три файла и способ разрешения), «Проверки после ребейза» (1231 backend-тест, 101 Vitest, build/lint). Локально у меня не воспроизводимо (нет `~/.m2`, нет доступа к npm-registry); CI backend/frontend/infra на debf03a зелёный | **выполнен (по CI и описанию)** |

### Особые проверки задания

| № | Проверка | Результат |
|---|---|---|
| 1 | `git merge-base --is-ancestor origin/main pr/37` | **да** (`028dc5e`) |
| 2 | `LoginPage.tsx`: ветка 429 с `Retry-After` из M02 сохранена, логика #37 сохранена | **да** — `LoginPage.tsx:41-55`, см. п. 2 выше; порядок проверок (403-blocked → 429 → 401 → прочее) корректен, 403 с другим кодом уходит в `error.message` (тест `access_denied`) |
| 3 | `AdminUserService` делегирует в `PasswordService.changeByAdministrator`, не дублирует хеширование | **да** — `AdminUserService.java:60-62`, `encoder` в классе не используется для паролей; явный `setUpdatedAt` снят, timestamp покрыт `@PreUpdate` + интеграционным тестом |
| 4 | `LoginPage.test.tsx` содержит оба набора | **да** — `it.each` ×4 (#37) + `Retry-After` (M02), всего 5 кейсов, общий `beforeEach(setAuthSession(null))` |
| 5 | `npm test -- --run src/features/auth` в worktree | **не выполнено** — `npm ci` невозможен (registry недоступен из среды), готовых `node_modules` нет; frontend-CI на debf03a зелёный |

### Регрессии / контракты / тесты / миграции

- `range-diff 63263d1..fae4b37 origin/main..pr/37`: единственные отличия от просмотренной в ревью 1 версии — три разрешения конфликтов. Остальное (`UserRoleAssignmentRepository.findRoleCodesByUserIds`, MSW `GET /events` в `handlers.ts:238-243`, `DEMO_ORGANIZATION_ID` в `fixtures.ts:11`, `handlers.test.ts`, `JwtAuthenticationFilterTest.downstreamExceptionsAreNotHandledAsJwtAuthenticationFailures`, `M01FollowupsIntegrationTest`, фраза в §5.8 ТЗ) байт-в-байт как в fae4b37.
- Тесты: диф по `src/test` и `*.test.ts(x)` содержит только `+`; тест M02 на `Retry-After` сохранён целиком; `JwtAuthenticationFilterTest` — старые методы на месте.
- Миграций нет; `qa04-endpoints.tsv` не менялся — новых маппингов `/api/v1/**` нет.
- Файлы вне области задания не тронуты; `package-lock.json` совпадает с `main`.
- Контракты §5: без изменений относительно ревью 1 (состав `items[].roles` в `GET /users` — то, что просило ревью; `PATCH /users/{id}/password` — поведение M02 из `main`).

## Блокирующие

Нет.

## Желательные

Переносятся из ревью 1 без изменений (в этом раунде не требовались и не делались):

1. `UserRepository.search/countSearch` (`main`, `:37-38`, `:47-48`) — фильтр `?role=` по-прежнему ищет по всем назначениям без `organization_id is null and event_id is null`; после п. 1 пользователь со scoped `judge` попадёт в `?role=judge`, но без бейджа. Две строки native SQL + одна проверка в `M01FollowupsIntegrationTest`. Отдельным мелким PR.
2. `handlers.ts:239` — `request.headers.get('Authorization')!` держится на порядке обработчиков (guard `http.all` выше в массиве); безопаснее `?? ''` + `try/catch`, как в `admin-handlers.guard`.
3. Текст «Аккаунт заблокирован, обратитесь к администратору» vs «Пользователь заблокирован. Обратитесь к администратору» из ревью 3 — выровнять с письмами/страницей восстановления M02 при случае.

## Текст правок для Codex

(пусто — вердикт «слить»)

## Порядок слияния

Зависимостей от открытых PR нет: ветка содержит актуальный `main` (M02 #29, #39, #25), `merge-tree` с `origin/main` чистый, GitHub `mergeable_state: clean`. Сливать можно сразу, независимо от остальных открытых PR; для других веток `pr/37` добавляет только одну строку в `UserRoleAssignmentRepository` и блок `catch` в `LoginPage.tsx` — конфликты маловероятны.

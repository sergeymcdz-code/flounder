# M01 — ревью follow-ups (PR #37, fae4b37)

Ветка `pr/37` (`feat/M01-followups`, **fae4b37** «fix(admin): close M01 review follow-ups»), один коммит поверх `origin/main` **63263d1** (merge-base = HEAD main, ветка актуальна). Ревьюер: Claude, 13.10.2026. Диф с `main`: 11 файлов, +185/−13. CI зелёный (по треду). Не прогонял Maven/npm/Docker — проверено статически по `git show pr/37:…`, `git diff origin/main...pr/37`, `git merge-tree --write-tree`.

**Вердикт: слить.** Все 6 пунктов задания выполнены (пункты 5 и 6 оказались уже закрытыми в `main` — добавлены регрессионный тест и уточнение ТЗ); регрессий не вижу, контракты §5 не изменены (только уточнение текста §5.8), старые тесты не ослаблены (в дифе тестов только `+`), миграций нет, новых эндпоинтов нет (TSV QA04 не требует правок). Единственный конфликт — с `pr/29` (M02), три файла, разрешается тривиально при ребейзе M02.

## Проверено по пунктам

| № | Пункт | Где | Результат |
|---|---|---|---|
| 1 | Бейджи ролей в списке — только глобальные назначения | `UserRoleAssignmentRepository.findRoleCodesByUserIds`: в JPQL добавлено `and a.organizationId is null and a.eventId is null` (ровно одна строка, как просило ревью 3). `findRolesByUserId` для `GET /users/{id}` не тронут — scoped-роли с `organizationId`/`eventId` остаются в деталях. Тест `M01FollowupsIntegrationTest.userListBadgesContainOnlyGlobalRolesWhileDetailsKeepBothScopes`: вставляет org-scoped `judge` и event-scoped `operator`, список → `roles == ["participant"]`, детали → 3 роли с ожидаемыми `organizationId`/`eventId` | **выполнен** |
| 2 | `updated_at` при смене пароля админом | `AdminUserService.changePassword`: `user.setUpdatedAt(OffsetDateTime.now())`. Тест `adminPasswordChangeUpdatesTheUserTimestamp` (ставит `2000-01-01`, PATCH → 204, `em.flush()`, `updated_at` после > до). Замечание: в `UserEntity` уже есть `@PreUpdate preUpdate()` с тем же эффектом, строка избыточна, но безвредна; тест — настоящая ценность пункта | **выполнен** |
| 3 | `LoginPage`: ветки 403 `identity.user_blocked` и 429 | `if/else` вместо тернарной цепочки: 403+`user_blocked` → «Аккаунт заблокирован, обратитесь к администратору»; любой 429 → «Слишком много попыток входа. Попробуйте позже»; 401 → прежний текст; прочие `ApiError` → `error.message`; не-`ApiError` → общий. Порядок проверок корректен (403 с другим кодом попадает в `error.message`). Новый `LoginPage.test.tsx`: 4 случая через MSW (403 blocked / 429 / 401 / 403 access_denied), проверяет `role=alert`, кнопка снова активна, сессия не записана | **выполнен** |
| 4 | MSW: мок `GET /api/v1/events` для админа | Из `admin-handlers.ts` удалён `http.get('*/api/v1/events') → []`; в `handlers.ts` добавлен `GET path('/events')` после общего guard'а (`http.all` даёт 401 без токена и 403 без ролей organizer/operator/platform_admin), сам обработчик пускает только `platform_admin` и отдаёт `[state.event]`. `DEMO_ORGANIZATION_ID = id(7,1)` вынесен в `fixtures.ts` и используется в админ-фикстуре организации «Клуб Волна» → событие видно в карточке организации. `browser.ts` (`adminHandlers, handlers, …`) не менялся — нужды нет, дубль убран. Тест `handlers.test.ts`: один и тот же список для admin `/events` и organizer `/organizer/events`, `organizationId` совпадает с админ-организацией; 401/403/403 для anon/participant/organizer | **выполнен** |
| 5 | `try/catch` в `JwtAuthenticationFilter` только вокруг аутентификации | `git show origin/main:…/JwtAuthenticationFilter.java` — `filterChain.doFilter(request, response)` уже последняя строка вне `try` (закрыто в ревью 3, п. 1a). Код фильтра в PR не тронут. Добавлен параметризованный тест `downstreamExceptionsAreNotHandledAsJwtAuthenticationFailures` (4 исключения из цепочки после успешной аутентификации: `UserBlockedException`, `InsufficientAuthenticationException`, `ExpiredTokenException`, `InvalidTokenException` → пробрасываются `isSameAs`, `verifyNoInteractions(entryPoint)`, статус 200, тело пустое). Это и есть регрессия на «не оборачивать цепочку» | **выполнен (тестом)** |
| 6 | §5.8 ТЗ: компромисс логина и 403 на `roles` | Оба абзаца уже в `main` (строки 248 и 261: «403 `identity.user_blocked` только при верном пароле, при неверном — 401…», «`POST /users` с `roles` без прав администратора → 403 `identity.access_denied`, пользователь не создаётся»). PR добавляет только фразу к `GET /users`: «`items[].roles` содержит только глобальные назначения. Scoped-роли остаются в деталях пользователя» — ровно под пункт 1 | **выполнен** |

Тесты на 1, 3, 5 — есть (`M01FollowupsIntegrationTest`, `LoginPage.test.tsx`, `JwtAuthenticationFilterTest`), плюс непрошеные на 2 и 4.

### Регрессии / контракты / тесты / миграции

- Контракты §5: формат `GET /users` не менялся (только состав `roles[]` — то, что просило ревью), `PATCH /users/{id}/password` — тело/204 прежние. Публичные и организаторские эндпоинты не тронуты.
- Старые тесты: диф по `src/test` и `frontend/src/**/*.test.*` содержит только добавления; `JwtAuthenticationFilterTest` — существующие методы на месте.
- Миграций нет; `qa04-endpoints.tsv` не менялся — новых маппингов нет, `fixtureTableCoversEveryApiV1ControllerMapping` не затронут.
- `ModulithArchitectureTest`: правки внутри `identity`, межмодульных зависимостей не добавлено.

## Блокирующие замечания

Нет.

## Желательные замечания

1. `UserRepository.search/countSearch` — фильтр `role=` по-прежнему ищет по всем назначениям (`exists … user_role_assignments … r.code = :role`), без `organization_id is null and event_id is null`. После п. 1 пользователь со scoped `judge` попадёт в выборку `?role=judge`, но бейджа «Судья» у него не будет — визуальная нестыковка. Две строки в native SQL + одна проверка в `M01FollowupsIntegrationTest` (`?role=judge` → `total == 0`). Можно следующим мелким PR.
2. `AdminUserService.changePassword` — `java.time.OffsetDateTime.now()` полным именем в теле метода; и строка дублирует `@PreUpdate`. При ребейзе M02 (`PasswordService.changeByAdministrator`) достаточно оставить тест, а явный `setUpdatedAt` перенести туда или опустить.
3. Текст блокировки «Аккаунт заблокирован, обратитесь к администратору» отличается от предложенного в ревью 3 («Пользователь заблокирован. Обратитесь к администратору») — не принципиально, но после M02 стоит выровнять с текстом письма/страницы восстановления.
4. `handlers.ts` `/events`: `request.headers.get('Authorization')!` — non-null assertion держится на том, что `http.all`-guard стоит выше в том же массиве; если кто-то переставит обработчики, будет TypeError вместо 401. Безопаснее `?? ''` + `try/catch`, как в `admin-handlers.guard`.

## Порядок слияния / конфликты

`git merge-tree --write-tree`:

| С чем | Результат |
|---|---|
| `origin/main` 63263d1 | чисто |
| `pr/39` (QA01 follow-ups) | чисто |
| `pr/36` (M08), `pr/38` (M04), `pr/40` (M07) | чисто (у `pr/38` правится `Qa04SecurityFixture`, но фикстуры `target/org/event`, которые использует новый тест, есть в `main`) |
| `pr/29` (M02) | **конфликт**, 3 файла: `LoginPage.tsx` (content), `LoginPage.test.tsx` (add/add), `AdminUserService.java` (content). `JwtAuthenticationFilter` M02 не трогает — семантического конфликта с новым тестом нет |

Разрешение конфликта с M02 (в ветке M02 при ребейзе):

- `LoginPage.tsx`: взять `if/else` из #37 и внутри ветки 429 оставить вычисление M02: `error.retryAfter !== undefined ? \`…через ${Math.max(1, Math.ceil(retryAfter/60))} мин.\` : 'Слишком много попыток входа. Попробуйте позже'`; ссылку «Забыли пароль?» из M02 сохранить. Тест #37 на 429 без `Retry-After` и тест M02 с `Retry-After: 121` тогда проходят оба.
- `LoginPage.test.tsx`: объединить в один файл — `it.each` из #37 + кейс Retry-After из M02; `beforeEach(() => setAuthSession(null))` из M02 вместо `sessionStorage.clear()` (M02 кэширует сессию в модуле `auth-session.ts`).
- `AdminUserService.java`: взять версию M02 (делегирование в `PasswordService.changeByAdministrator`); явный `setUpdatedAt` либо перенести в `changeByAdministrator`, либо положиться на `@PreUpdate` — `M01FollowupsIntegrationTest.adminPasswordChangeUpdatesTheUserTimestamp` это проверит.

**Рекомендуемый порядок:** слить #37 и #39 первыми (оба маленькие, без миграций, чисто с `main` и друг с другом), затем ребейзить M02 (#29) с разрешением трёх файлов выше и M07 (#40) — см. QA01-followups-review. Обратный порядок (сначала M02, потом ребейз #37) тоже возможен, но тогда правки на `LoginPage`/`AdminUserService` придётся переносить в контекст M02 вручную — объём тот же, а большой PR задерживается.

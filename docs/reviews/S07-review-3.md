# S07 — ревью 3 (PR #59, fab295d): проверка ребейза

Codex по заданию «Ребейз PR #59 (S07)» выполнил `git merge origin/main` (57cea5d, после D03 PR-1/PR-2 и S03-A) в ветку `feat/S07-org-roles-invitations` без rebase и force-push: единственный новый коммит `fab295d` — merge-коммит с родителями `9640b44` (прежняя вершина S07) и `57cea5d`. Разрешены 8 конфликтов из задания. Проверка выполнена сравнением дерева merge-коммита с автоматическим слиянием `git merge-tree --write-tree 9640b44 origin/main`: отличия ровно в 8 конфликтных файлах плюс один тест (`OrganizationSwitcher.test.tsx`, адаптация ожиданий к текстам/навигации D03 и один новый тест). Набор файлов `git diff origin/main...pr/59 --stat` — те же 65 файлов, что и до ребейза (`8d709be...9640b44`); посторонних файлов нет. CI на fab295d: backend/frontend/infra — success; по описанию PR: 1708 backend-тестов (Qa04RoleMatrixIntegrationTest 974 проверки, S07 — 14), 233 Vitest, build/lint — 0. Локально `mvn -q -o compile` (parent POM не резолвится офлайн) и `npm ci` (registry.npmjs.org — ENOTFOUND) по-прежнему недоступны, поэтому конфликтные файлы прочитаны построчно, компиляция принята по CI.

**Вердикт: слить** (после демо 19.10 и ретро, после S04 — см. «Порядок слияния»).

## Проверено

| № | Файл / пункт | Что проверено | Результат |
|---|---|---|---|
| 1 | `frontend/src/api/events.api.ts` | `getMyEvents(accessToken, includePrivateVenues = true, organizationId?)` (`:104-109`): сохранена загрузка площадок/часовых поясов D03, добавлен query `organizationId` S07 третьим параметром. Все вызовы согласованы: `OrganizerEventsPage.tsx:48` передаёт `(token, isOrganizer \|\| isPlatformAdmin, isPlatformAdmin ? undefined : currentOrgId)`; `OrganizerDashboardPage.tsx:65`, `CompetitionUnitsPage.tsx:44` — как в main | OK |
| 2 | `frontend/src/app/App.tsx` (особая проверка а) | Diff к main = только S07: lazy `InvitationPage`/`OrganizationPage` (`:29-30`), `invitations/:token` (`:138`), `organizer/organization` (`:177`). Маршруты main сохранены: `index → Navigate /organizer/events` (`:176`), `judge` в `RequireRole` кабинета (`:169`, main уже содержит), аналитика, афиша, судейство. Ничего из прежних добавлений ветки не потеряно | OK |
| 3 | `frontend/src/app/providers/AuthProvider.tsx` (особая проверка в) | Весь S07 на месте: `Membership`/`EMPTY_MEMBERSHIPS`/`Profile` (`:26-28`), `memberships/currentOrgId/selectOrg/userId/authLoading/refreshProfile` в контексте (`:30-35`), загрузка `/auth/me` по токену (`:97-109`), `roles = effectiveRoles ?? JWT` (`:145`), useCallback/useMemo со всеми зависимостями (`:147-175`). Из main: сигнатура `login → Promise<AuthSession & { roles }>` (`:46`) для D03-редиректа по роли; реализация (`:119-133`) возвращает `user?.effectiveRoles ?? getRoles(jwt)` — секретарь-по-членству без глобальной staff-роли сразу попадает в `/organizer/events`. Покрыто новым тестом `OrganizationSwitcher.test.tsx:90-106` | OK (лучше, чем в main) |
| 4 | `frontend/src/components/AppLayout.tsx` (особая проверка б) | Файл = main (шапка D03, мобильное меню, `roleLabel`, `handleLogout`, подгрузка имени) + 2 строки S07: импорт и `<OrganizationSwitcher />` в `userMenu` (`:7`, `:56`). UX-16: `<NavLink className={styles.brand} to={roleHomeTarget(roles, isAuthenticated)} aria-label="На главную">SportGate</NavLink>` (`:37`) — на месте, тест `OrganizationSwitcher.test.tsx:26,104` проверяет href `/organizer/events` у секретаря. Прежняя правка ветки `canManageEvents … \|\| hasRole('judge')` сознательно не перенесена: в D03 судья получает пункт «Судейство» → `/operator/results` (`:41`), а `OrganizerLayout` ограничивает судью страницей результатов — модель main новее и согласована с `roleHomeTarget` | OK |
| 5 | `frontend/src/features/admin/OrganizationsAdminPage.tsx` | Diff к main = импорт и `<OrganizationInvitationList organizationId={id} />` (`:7`, `:127`); кнопки `fl-btn`, заголовок «Соревнования организации», `<div>`-обёртка таблицы — из main | OK |
| 6 | `frontend/src/features/organizer/OrganizerEventsPage.tsx` | Из main: `isOrganizer/isPlatformAdmin`, `useSearchParams` + автопереход к единственному активному соревнованию (`:72`), классы `fl-btn`, «Мои соревнования». Из S07: `currentOrgId/memberships`, `canCreate` (`:24-25`), фильтр организации в `getMyEvents` (`:48`), `currentOrgId` в зависимостях эффекта (`:69`), кнопка создания только для owner/admin/organizer/platform_admin (`:82`) | OK |
| 7 | `frontend/src/features/organizer/OrganizerLayout.tsx` | Из main: `isOperator`, ограничение судьи (`:35-37`), `navigationItems.slice(0,1)` для оператора, «Аналитика». Из S07: `memberships/currentOrgId`, `canCreate`, `canManageOrg` (`:30-33`), фильтр «Создать соревнование» и пункт «Организация» → `/organizer/organization` (`:46`) | OK |
| 8 | `src/test/resources/security/qa04-endpoints.tsv` | 132 строки main + 7 новых S07 = 139, дублей по (метод, путь) нет; строка main `POST /profiles/{profileId}/documents/{id}/review` сохранена (`:139`). Три строки main отличаются только политикой `manage → organize` (`DELETE /event-disciplines/{id}`, `PATCH /events/{id}`, `POST /events/{id}/archive`) — это намеренное ужесточение S07 из раундов 1–2, в ветке до ребейза было так же | OK |
| 9 | Файлы вне области ветки | `git diff origin/main...pr/59 --stat` — 65 файлов, список совпадает с `8d709be...9640b44` один в один (разница только в счётчике строк 1373/−134 против 1356/−138 за счёт адаптации теста и сигнатуры `getMyEvents`) | OK |
| 10 | Тесты | `git diff origin/main pr/59 -- '*test*' '*spec*'`: 14 файлов, +548/−24; множество удалённых строк в тестах совпадает с удалённым в ветке до ребейза (новых удалений нет). `OrganizationSwitcher.test.tsx`: ожидания переименованы под D03 («Мои соревнования», «Создать соревнование», «Загружаем соревнования…»), `initialEntries=['/organizer/events?all=1']` — чтобы автопереход D03 не уводил со списка; проверка исчезнувшего в D03 пункта «Заявки и платежи» заменена на проверку логотипа; добавлен тест login→effectiveRoles. Ни один тест main и ветки не удалён и не ослаблен | OK |
| 11 | Сборка (особая проверка г) | `mvn -q -o compile` — parent POM `spring-boot-starter-parent:3.5.6` офлайн недоступен; `npm ci` — ENOTFOUND registry.npmjs.org. CI PR на fab295d: backend/frontend/infra — success; `mergeable_state: clean` | Принято по CI |
| 12 | `git merge-tree --write-tree origin/main pr/59` | Чисто (016f95f) | OK |
| 13 | `git merge-tree --write-tree pr/59 pr/76` (особая проверка д) | **Чисто, конфликтов нет.** `pr/76` (21a07f3, SEC04) содержит `9640b44` как предка (merge-base pr/76 pr/59 = 9640b44). Сейчас `merge-tree origin/main pr/76` конфликтует в тех же 8 файлах — исключительно из-за унаследованного старого S07; после слияния #59 в main достаточно `git merge origin/main` в `fix/SEC04-auth-hardening` без ручных правок | OK |
| 14 | `git merge-tree --write-tree origin/fix/S04-stage1-followups pr/59` | В задании S04 для S07 не указан, проверено дополнительно: 2 тривиальных конфликта «обе стороны» — `NotificationTemplate.java:13-17` (константы `schedule_changed` S04 и `organization_invitation` S07 в enum) и `NotificationEventListener.java:67` (метод `scheduleChanged` S04 рядом с листенером приглашений S07). Остальное (`GlobalExceptionHandler`, `MailRenderingTest`) сливается автоматически | К исполнению после слияния S04 |

Дополнительно: миграции не тронуты (V037 S07 без изменений, V038 из main; `outOfOrder` нет), описание PR содержит раздел «Ребейз 18.10» с перечнем 8 конфликтов, история не переписана, UI-тексты на русском.

## Блокирующие

Нет.

## Желательные

1. `AuthProvider.tsx:119-133`: `login` сам запрашивает `/auth/me` и кладёт профиль в state, после чего `setAuthSession` меняет `token`, и эффект `:100-108` запрашивает `/auth/me` повторно. Плюс `AppLayout.tsx:19-25` делает третий запрос ради имени. Функционально безвредно (ответы идемпотентны), но для этапа 2 стоит вынести `firstName/email` в `Profile` контекста и убрать дубли.
2. `OrganizerEventsPage.tsx:24`, `OrganizerLayout.tsx:30`: `memberships = []` в деструктуризации лишний (контекст даёт стабильный `EMPTY_MEMBERSHIPS`) — перенос из ревью 2, безвредно.
3. Перенесённые из ревью 2 (на этап 2): `updateOrganization` пишет `"city": null`, а `createOrganization` ключ не пишет; публичный `GET /invitations/{token}` делает `for update` + `update … EXPIRED` в read-path.

## Текст правок для Codex

(пусто — слить)

## Порядок слияния

1. Сначала `origin/fix/S04-stage1-followups` (aa75a1c) → main (с main сливается чисто).
2. Затем в `feat/S07-org-roles-invitations` ещё раз `git merge origin/main` — разрешить 2 конфликта «обе стороны» (`NotificationTemplate.java`: оставить обе константы `schedule_changed` и `organization_invitation`, порядок — S04 выше S07; `NotificationEventListener.java`: оставить оба метода), прогнать `MailRenderingTest`, `Qa04RoleMatrixIntegrationTest`, `npm test`; затем слить #59.
3. После слияния #59 — в `fix/SEC04-auth-hardening` (#76) выполнить `git merge origin/main`: по `merge-tree pr/59 pr/76` конфликтов не ожидается; после этого повторить ревью #76 на актуальной базе.
4. Далее по-прежнему: ребейз #63 (S08) — конфликты `App.tsx` и `qa04-endpoints.tsv` («обе стороны»), полная матрица QA04 и e2e QA05 на staging; перед деплоем — `select member_role, count(*) from organization_members group by 1`; после деплоя — вход демо-аккаунтов и `GET /auth/me` у `sec@` с `effectiveRoles: operator`.

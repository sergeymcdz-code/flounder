# SEC05 — ревью 1 (PR #74, f80e46a)

PR #74 «fix(deps): harden managed dependencies and enable weekly updates», ветка `pr/74` (1 коммит f80e46a поверх `origin/main`). Задание — раздел «SEC05. Spring Boot 3.5.16 и зависимости» ТЗ по безопасности (вне репозитория): parent 3.5.6 → 3.5.16, патч pdfbox 2.0.x, `.github/dependabot.yml` (4 экосистемы, weekly, лимит 5), vitest в `scripts/demo` (tinypool), `npm audit fix` во frontend без мажоров, полный прогон, таблица «было → стало». Ревьюер: Claude, 18.10.2026. Диф: 5 файлов, +590/−497 — только `sport-platform/pom.xml` (2 строки), `scripts/demo/package.json` (1 строка), `scripts/demo/package-lock.json`, `sport-platform/frontend/package-lock.json` (1 пакет), новый `.github/dependabot.yml`. Исходники, миграции, тесты, эндпоинты не тронуты. CI PR (backend / frontend / infra) — зелёный (run 39065301607). `git merge-tree --write-tree origin/main pr/74` — чисто. В этой сессии Maven Central, registry.npmjs.org и spring.io недоступны (egress-прокси), поэтому `mvn`/`npm ci` не прогонялись — принимаю по CI и описанию PR; версии сверены по открытым источникам (spring.io blog через поиск, javadoc.io, mvnrepository, pdfbox.apache.org).

**Вердикт: слить.** Все шесть пунктов задания выполнены, правок кода нет, lock-файлы согласованы с манифестами, версии реальные. Единственный неподтверждённый мной пункт — строка «Jackson Databind 2.19.2 → 2.21.4» в таблице PR (не смог открыть список управляемых версий Boot 3.5.16; автор ссылается на `mvn dependency:tree`) — проверить на стенде, на вердикт не влияет.

## Проверено

| № | Требование | Где | Результат |
|---|---|---|---|
| 1 | `spring-boot-starter-parent` 3.5.6 → 3.5.16, версия реальная, других правок pom нет кроме вынужденных | `sport-platform/pom.xml:9` (`3.5.16`); diff pom — ровно 2 строки (parent и `pdfbox.version`) | **Выполнено.** 3.5.16 существует: релиз 25–26.06.2026 ([spring.io blog «Spring Boot 3.5.16 available now»](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/), [javadoc.io 3.5.16](https://javadoc.io/doc/org.springframework.boot/spring-boot/3.5.16), [mvnrepository spring-boot-dependencies 3.5.16, Jun 26 2026](https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-dependencies/3.5.16)); docs.spring.io/spring-boot/3.5 показывает заголовок «Spring Boot 3.5.16» — последний выпуск ветки. Backend-CI (`mvn -B test`) на этом SHA зелёный ⇒ артефакт разрешился из Central. Прямой WebFetch `repo1.maven.org/.../maven-metadata.xml` заблокирован прокси (PROVENANCE_REQUIRED) — зафиксировано как ограничение. `spring-modulith` 1.4.1 и `testcontainers` 2.0.2 совместимы с Boot 3.5.x — не трогались, верно |
| 2 | pdfbox 2.0.x — патч; openhtmltopdf 1.0.10 не трогать, отметить риск | `pom.xml:23` `pdfbox.version` 2.0.35 → 2.0.37; `openhtmltopdf.version` 1.0.10 без изменений (`pom.xml:22`) | **Выполнено.** 2.0.37 — последний выпуск ветки 2.0 (15.07.2026, [pdfbox.apache.org](https://pdfbox.apache.org/)). Риск openhtmltopdf и перенос на PDFBox 3.x описаны в PR («Ограничения и оставшиеся риски») |
| 3 | `.github/dependabot.yml`: maven `/sport-platform`, npm `/sport-platform/frontend` и `/scripts/demo`, github-actions; weekly; лимит 5 | `.github/dependabot.yml:1-22` | **Выполнено.** `version: 2`, 4 записи, каталоги точные, у каждой `interval: "weekly"` и `open-pull-requests-limit: 5`; github-actions с `directory: "/"` (корректно для `.github/workflows`) |
| 4а | `scripts/demo`: обновить vitest, убрать уязвимый tinypool | `scripts/demo/package.json:17` `^3.2.4` → `^4.1.12`; lock: `node_modules/vitest` 4.1.12, `node_modules/vite` 8.3.4, `tinypool` в lock отсутствует (в main был 1.1.1) | **Выполнено.** Lock согласован с манифестом (`packages[""].devDependencies.vitest == ^4.1.12`, lockfileVersion 3). Vitest 4 — мажор, но изменение манифеста вынужденное и указано в PR; `engines.node >=22` удовлетворяет vite 8 (`^20.19 || >=22.12`). Тесты (64) по PR проходят; CI `scripts/demo` не гоняет (как и раньше) — принимаю по описанию |
| 4б | frontend: `npm audit fix` dev-зависимостей без мажоров | `sport-platform/frontend/package-lock.json:7001-7004` — только `source-map-js` 1.2.1 → 1.2.2 | **Выполнено.** `package.json` frontend не менялся, lock-правка единственная; frontend-CI (`npm ci`, test, lint, build) зелёный |
| 5 | Полный прогон `mvn -q test`, `npm run build && npm run lint`, `npm test` (demo) | PR: 73 набора / 1644 теста backend, build+lint frontend, 64 теста demo, `npm audit` 0 в обоих npm-проектах | **Выполнено** по описанию PR + CI backend/frontend/infra зелёный |
| 6 | Таблица «было → стало» (Tomcat, Spring Framework, Hibernate, Jackson) | Описание PR, раздел «Версии» | **Выполнено.** Boot 3.5.6 → 3.5.16, Tomcat 10.1.46 → 10.1.55, Spring Framework 6.2.11 → 6.2.19, Hibernate 6.6.29 → 6.6.53, Jackson Databind 2.19.2 → 2.21.4, PDFBox 2.0.35 → 2.0.37, Vitest 3.2.7 → 4.1.12, Vite 7.3.7 → 8.3.4, source-map-js 1.2.1 → 1.2.2. Значение Jackson 2.21.4 мной не подтверждено независимо (см. «Что проверить на стенде», п. 2) |
| 7 | Безопасность / Modulith / миграции / TSV / тексты | Диф не содержит Java/TS/SQL-файлов | **Не применимо** — ничего не изменилось; `qa04-endpoints.tsv` не нужен. Секретов в дифе нет |
| 8 | Файлы вне области, переформатирование | 5 файлов, все в области задания | **Чисто** |
| 9 | Конфликты | `git merge-tree --write-tree origin/main pr/74` — чисто | Пересечения с другими SEC-PR возможны только по `pom.xml` при одновременных правках зависимостей — в задании ревью таких не указано |

## Блокирующие

Нет.

## Желательные

1. **Dependabot для `scripts/demo`**: vitest 4.x теперь получает минорные/патч-обновления автоматически; при появлении vitest 5 Dependabot предложит мажор. Можно позже добавить `ignore`/`groups` (например, группировать `vite*`/`@vitest/*`), но для первого включения текущий минимальный файл корректен.
2. **Jackson в таблице PR**: при проверке на стенде убедиться, что `mvn dependency:tree -Dincludes=com.fasterxml.jackson.core:jackson-databind` действительно даёт 2.21.4 под Boot 3.5.16 (для патч-ветки это нетипичный скачок на два минора). Если окажется 2.19.x — поправить таблицу в описании PR (на код не влияет).
3. **CI не покрывает `scripts/demo`** (job `frontend` работает только в `sport-platform/frontend`): после мажорного обновления vitest стоит добавить шаг `npm ci && npm test` в `scripts/demo` — отдельной задачей, не в этом PR.

## Текст правок для Codex

(пусто — «слить»)

## Порядок слияния

Независим от других открытых PR. Если параллельно сливаются другие PR, меняющие `sport-platform/pom.xml` или lock-файлы, — после них перезапустить CI на объединённом main (конфликт маловероятен: изменены только строки `<version>` parent и `pdfbox.version`).

## Что проверить на стенде

1. После деплоя: приложение стартует на Boot 3.5.16 (в логе `Spring Boot :: (v3.5.16)`), `/actuator/health` UP, Flyway без новых миграций.
2. `cd sport-platform && mvn dependency:tree -Dincludes=org.apache.tomcat.embed:tomcat-embed-core,org.springframework:spring-core,org.hibernate.orm:hibernate-core,com.fasterxml.jackson.core:jackson-databind,org.apache.pdfbox:*` — сверить с таблицей PR (особенно Jackson).
3. Скачать стартовый и итоговый PDF-протоколы на демо-событии: файл открывается, кириллица отображается (PDFBox 2.0.37 + openhtmltopdf 1.0.10).
4. Вход, публичная страница события, подача заявки — быстрый smoke после обновления Spring Framework 6.2.19 / Spring Security.
5. `cd scripts/demo && npm ci && npm test && npm ls tinypool --all` — 64 теста, tinypool «(empty)».
6. В GitHub через неделю: вкладка Insights → Dependency graph → Dependabot показывает 4 активные конфигурации и первые PR (не более 5 на экосистему).

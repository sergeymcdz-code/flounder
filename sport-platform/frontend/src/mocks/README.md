# Organizer demo mocks (T04)

Run from `sport-platform/frontend`:

```sh
npm ci
VITE_API_MOCK=true npm run dev
```

Open `/organizer/events/11111111-1111-4111-8111-111111111111` and log in with any syntactically valid email and nonempty password. Mock login issues an organizer/operator demo token. MSW is started only when `VITE_API_MOCK=true`; it is removed from the normal production bundle by Vite. Do not enable this flag in production.

The fixture contains one event, two sessions, four disciplines, thirty registrations and six heats. Changes remain in memory while navigating tabs; a full reload resets the fixture. The import accepts an `.xlsx` file and returns a fixed report (three new registrations, one update, one error). The downloadable template is a real workbook with the contract headers. This is a UI fixture, not an Excel parser or production seeding implementation.

API contracts and integration notes:

- The event generation summary is `{disciplines: [{eventDisciplineId, result: GenerationResult}], durationMs}; the previous flat form is also accepted`.
- `GET /age-group-presets` returns `[{code, name, groups}]`.
- Pool data comes from numeric `venueMeta.lanes` and `venueMeta.courseLength` in the existing venues list. Missing pool dimensions are shown as unknown; lane choices fall back to the largest assigned lane in the event, or eight when no lanes are assigned.
- There is no separate results-publication flag: recorded results and the existing heat publication status are used. Publishing a discipline uses the existing per-unit `/publication` operation sequentially; the operation can partially succeed and retry safely.
- The organizer registration DTO adds camelCase `seedTimeMs`, `paymentStatus`, `source`. Age group year bounds can be `null`.

Browser verification:

```sh
npx playwright install chromium
npm run test:organizer
ORGANIZER_SCREENSHOT_DIR=screenshots/T04 ORGANIZER_SCREENSHOT_TABS=entries,program npm run test:organizer
```

For an existing system Chromium, set `PLAYWRIGHT_CHROMIUM_EXECUTABLE=/usr/bin/chromium`. The test server enables MSW automatically. Tests cover the complete acceptance flow, validation feedback, lifecycle and result publication, 401/403, all tabs at 1280/1024 px, session CRUD, registration actions, and validator focus.

Discipline creation/editing loads `GET /api/v1/discipline-templates` and filters templates by `template.sportId === event.sportId` and sends the selected `disciplineTemplateId` with an explicit form payload. Admission is shown from the most recent generation result for each discipline. Admission results are stored in memory only until the page is reloaded; they are not persisted. Registration changes refresh only the registration list; a lane change refreshes only its heat. Session estimates fall back to the latest scheduled heat plus its discipline duration (180 seconds when unset). Dates use the browser timezone.

# T07: evidence

Captured by Playwright on Chromium with the opt-in MSW demo (four disciplines,
eight lanes, six heats selected from set-M). `*-375x812.png` are exact viewport
screenshots; `*-375.png` are full-page captures at the same width.

- [Start-list PDF](start-list.pdf): six intact heats on two A4 pages.
- [Results PDF](results.pdf): published results grouped by age, two A4 pages.
- [PDF geometry verification](pdf-summary.json).
- [Lighthouse mobile report](lighthouse.report.html): production build and the
  same-origin fixture API, served with gzip; not the MSW development bundle.
- [Lighthouse summary](lighthouse-summary.json).

Regenerate screenshots/PDFs with `npm run build && npm run test:e2e`.
Artifacts are written to `work/verification` by default; override with
`T07_ARTIFACT_DIR`. See [manual verification](../../T07.md).

Production installability, shell/API caching and offline reload/recovery are
asserted in `e2e/public.spec.ts`, independently of the MSW worker.

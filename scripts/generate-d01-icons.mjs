// Run from the repository root: node scripts/generate-d01-icons.mjs
import { createRequire } from 'node:module'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
const require = createRequire(new URL('../sport-platform/frontend/package.json', import.meta.url))
const { chromium } = require('@playwright/test')
const root = new URL('../sport-platform/frontend/public/', import.meta.url)
const svg = await readFile(new URL('icons/sportgate.svg', root), 'utf8')
const browser = await chromium.launch({ executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE })
try {
  for (const [size, maskable] of [[192, false], [512, false], [512, true]]) {
    const page = await browser.newPage({ viewport: { width: size, height: size }, deviceScaleFactor: 1 })
    const source = (maskable ? svg.replace('rx="96"', 'rx="0"') : svg)
      .replace('viewBox="0 0 512 512"', `viewBox="0 0 512 512" width="${size}" height="${size}"`)
    await page.setContent(`<style>body{margin:0}svg{display:block}</style>${source}`)
    await page.screenshot({ path: fileURLToPath(new URL(`icons/sportgate-${maskable ? 'maskable-' : ''}${size}.png`, root)), omitBackground: !maskable })
    await page.close()
  }
} finally {
  await browser.close()
}

import { demoCalendar, demoOrganization } from '../src/mocks/calendar-data.ts'
// Same-origin fixture API for exercising the production PWA without MSW's worker.
import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import { resolve, extname } from 'node:path'
import { gzipSync } from 'node:zlib'
import {
  demoEvent,
  demoForm,
  demoResults,
  demoSchedule,
  demoStartList,
} from '../src/mocks/demo-data.ts'

const units = demoStartList.sessions.flatMap((session) =>
  session.disciplines.flatMap((discipline) =>
    discipline.heats.map((heat) => ({
      ...heat,
      disciplineId: discipline.id,
      disciplineNumber: discipline.number,
      disciplineName: discipline.name,
      sequenceInSession: demoSchedule.find(
        (item) =>
          item.disciplineName === discipline.name &&
          item.heatNumber === heat.number &&
          item.scheduledAt === heat.scheduledAt,
      )?.sequenceInSession,
    })),
  ),
)
const dist = resolve('dist')
const types: Record<string, string> = {
  '.html': 'text/html',
  '.js': 'application/javascript',
  '.css': 'text/css',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.webmanifest': 'application/manifest+json',
}
const server = createServer(async (request, response) => {
  const url = new URL(request.url ?? '/', 'http://localhost:5175')
  if (url.pathname === '/api/v1/public/calendar' || url.pathname === '/api/v1/public/organizations/volna') {
    response.writeHead(200, { 'Content-Type': 'application/json' })
    response.end(JSON.stringify(url.pathname.endsWith('/volna') ? demoOrganization : demoCalendar))
    return
  }
  if (url.pathname === '/api/v1/public/events') {
    response.writeHead(200, { 'Content-Type': 'application/json' })
    response.end(JSON.stringify([demoEvent]))
    return
  }
  if (url.pathname.startsWith('/api/v1/public/events/')) {
    const resource = url.pathname.split('/').slice(6).join('/')
    const data: Record<string, unknown> = {
      '': demoEvent,
      'start-list': demoStartList,
      live: {
        currentUnit: { ...units[0], status: 'IN_PROGRESS' },
        next: units.slice(1, 4),
        updatedAt: new Date().toISOString(),
      },
      results: demoResults,
      schedule: demoSchedule,
      'registration-form': demoForm,
      athletes: [],
    }
    response.writeHead(200, {
      'Content-Type': 'application/json',
      'Cache-Control': 'no-store',
    })
    response.end(JSON.stringify(data[resource] ?? {}))
    return
  }
  const filename = resolve(dist, '.' + url.pathname)
  if (!filename.startsWith(dist + '/') && filename !== dist) {
    response.writeHead(403)
    response.end()
    return
  }
  try {
    const isRoute = !extname(url.pathname)
    const target = isRoute ? resolve(dist, 'index.html') : filename
    const body = await readFile(target)
    const compressed = /gzip/.test(request.headers['accept-encoding'] ?? '')
    response.writeHead(200, {
      'Content-Type': types[extname(target)] ?? 'application/octet-stream',
      'Cache-Control': 'no-cache',
      ...(compressed
        ? { 'Content-Encoding': 'gzip', Vary: 'Accept-Encoding' }
        : {}),
    })
    response.end(compressed ? gzipSync(body) : body)
  } catch {
    response.writeHead(404)
    response.end()
  }
})
server.listen(5175, '127.0.0.1')

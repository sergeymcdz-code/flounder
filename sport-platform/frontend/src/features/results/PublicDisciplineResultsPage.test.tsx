import { render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { http, HttpResponse } from 'msw'
import { afterAll, expect, it, vi } from 'vitest'
import { server } from '../../test/setup'
import { PublicDisciplineResultsPage } from './PublicDisciplineResultsPage'

vi.hoisted(() => vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080'))
afterAll(() => vi.unstubAllEnvs())

it('shows canonical result codes matching the legend and keeps ordinary status labels', async () => {
  server.use(
    http.get('*/api/v1/public/events/demo', () => HttpResponse.json({
      id: 'event', title: 'Соревнование', disciplines: [{ id: 'discipline', name: '50 м' }],
    })),
    http.get('*/api/v1/public/events/event/disciplines/discipline/results', () => HttpResponse.json({
      eventId: 'event', eventDisciplineId: 'discipline', units: [{
        id: 'heat', label: 'Заплыв 1', sequenceNumber: 1,
        entries: ['DNS', 'DNF', 'DSQ', 'CORRECTED', 'DID_NOT_START', 'DID_NOT_FINISH', 'DISQUALIFIED', 'VALID', 'PENDING', 'EXHIBITION'].map((status, index) => ({
          status, participantName: `Участник ${index + 1}`, place: null, clubName: null,
          laneOrPosition: null, rawValue: null, resultType: 'TIME',
        })),
      }],
    })),
  )
  render(<MemoryRouter initialEntries={['/events/demo/disciplines/discipline/results']}>
    <Routes><Route path="/events/:publicSlug/disciplines/:disciplineId/results" element={<PublicDisciplineResultsPage />} /></Routes>
  </MemoryRouter>)
  const table = within(await screen.findByRole('table'))
  for (const code of ['DNS', 'DNF', 'DSQ', 'CORRECTED']) {
    for (const element of table.getAllByText(code, { exact: true })) expect(element.tagName).toBe('ABBR')
  }
  expect(table.getAllByText('DNS', { exact: true })).toHaveLength(2)
  expect(table.getByText('Учтён')).toBeInTheDocument()
  expect(table.getByText('Ожидает пересчёта')).toBeInTheDocument()
  expect(table.getByText('Вне зачёта')).toBeInTheDocument()
  expect(screen.getByLabelText('Обозначения статусов результатов')).toHaveTextContent(
    'DNS — не стартовал · DNF — не финишировал · DSQ — дисквалифицирован · исправлено — результат исправлен после публикации',
  )
})

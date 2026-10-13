import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { HeatProtocol, PrintHeader, ResultsTable } from './Protocol'
import { demoEvent, demoStartList } from '../../mocks/demo-data'
import type { ResultRow } from './public.types'

describe('QA04: public content security', () => {
  it('renders malicious titles, surnames and club names as text', () => {
    const title = '<script>window.qa04Executed = true</script>'
    const surname = '<img src=x onerror="window.qa04Executed = true">'
    const club = '<svg onload="window.qa04Executed = true">'
    const heat = demoStartList.sessions[0].disciplines[0].heats[0]
    const row: ResultRow = {
      lastName: surname,
      firstName: 'Иван',
      birthYear: 2014,
      club,
      display: '00:32.15',
      timeMs: 32150,
      status: 'VALID',
      place: 1,
    }
    const { container } = render(
      <MemoryRouter>
        <PrintHeader event={{ ...demoEvent, title }} title="Протокол" />
        <HeatProtocol
          heat={{
            ...heat,
            lanes: [{
              lane: 1,
              athlete: { lastName: surname, firstName: 'Иван', birthYear: 2014, club },
              registrationId: 'qa04-registration',
              seedTimeMs: 32150,
            }],
          }}
          lanes={8}
        />
        <ResultsTable rows={[row]} />
      </MemoryRouter>,
    )
    expect(screen.getByText(`${title} · Протокол`)).toBeInTheDocument()
    expect(screen.getAllByText(club)).toHaveLength(2)
    expect(screen.getAllByText(`${surname} Иван`)).toHaveLength(2)
    expect(container.querySelector('script, img, svg')).toBeNull()
    expect(container.querySelector('[onerror], [onload]')).toBeNull()
  })
})

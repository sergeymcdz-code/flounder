import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { HeatProtocol, PrintButton, ResultsTable } from './Protocol'
import type { ResultRow } from './public.types'
import { demoStartList } from '../../mocks/demo-data'

describe('Public protocols', () => {
  it('renders all eight lanes, NT, a withdrawn athlete and empty lanes', () => {
    const heat = demoStartList.sessions[0].disciplines[0].heats[0]
    render(
      <MemoryRouter>
        <HeatProtocol
          heat={{
            ...heat,
            lanes: heat.lanes
              .slice(0, 7)
              .map((lane, index) =>
                index === 1 ? { ...lane, withdrawn: true } : lane,
              ),
          }}
          lanes={8}
          slug="demo"
        />
      </MemoryRouter>,
    )
    expect(screen.getAllByRole('row')).toHaveLength(9)
    expect(screen.getByText('NT')).toBeInTheDocument()
    expect(screen.getByText('Снят')).toBeInTheDocument()
    const last = screen.getAllByRole('row').at(-1)!
    expect(within(last).getAllByText('—')).toHaveLength(4)
    expect(screen.getByRole('link')).toHaveAttribute(
      'href',
      `/e/demo/heats/${heat.id}`,
    )
  })

  it('sorts non-finishers last, preserves shared places and uses age-group places', () => {
    const base: ResultRow = {
      lastName: 'Иванов',
      firstName: 'Иван',
      birthYear: 2012,
      club: 'Волна',
      display: '00:32.00',
      timeMs: 32000,
      status: 'VALID',
      place: 1,
    }
    const rows: ResultRow[] = [
      {
        ...base,
        lastName: 'Не стартовал',
        status: 'DNS',
        place: null,
        timeMs: null,
      },
      base,
      { ...base, lastName: 'Петров', status: 'OK' },
    ]
    const { rerender } = render(<ResultsTable rows={rows} />)
    expect(screen.getAllByRole('row').at(-1)).toHaveTextContent('DNS')
    expect(
      screen.getAllByRole('rowheader').map((row) => row.textContent),
    ).toEqual(['1', '1', '—'])
    rerender(
      <ResultsTable
        rows={rows.map((row) => ({
          ...row,
          place: row.status === 'VALID' || row.status === 'OK' ? 2 : null,
        }))}
        grouped
      />,
    )
    expect(
      screen.getAllByRole('rowheader').map((row) => row.textContent),
    ).toEqual(['2', '2', '—'])
  })

  it('prints once after data is ready when print=1', async () => {
    const print = vi.spyOn(window, 'print').mockImplementation(() => {})
    Object.defineProperty(document, 'fonts', {
      configurable: true,
      value: { ready: Promise.resolve() },
    })
    const { rerender } = render(
      <MemoryRouter initialEntries={['/e/demo/start-list?print=1']}>
        <PrintButton ready={false} />
      </MemoryRouter>,
    )
    expect(print).not.toHaveBeenCalled()
    rerender(
      <MemoryRouter initialEntries={['/e/demo/start-list?print=1']}>
        <PrintButton ready />
      </MemoryRouter>,
    )
    await vi.waitFor(() => expect(print).toHaveBeenCalledTimes(1))
    rerender(
      <MemoryRouter>
        <PrintButton ready />
      </MemoryRouter>,
    )
    expect(print).toHaveBeenCalledTimes(1)
  })
})

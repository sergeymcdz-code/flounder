import { render, screen, within } from '@testing-library/react'
import { expect, it } from 'vitest'
import { DataTable, type DataTableColumn } from './DataTable'

type Row = { id: string; name: string; time: string }
const columns: DataTableColumn<Row>[] = [
  { key: 'name', header: 'Участник', rowHeader: true, render: row => row.name },
  { key: 'time', header: 'Время', numeric: true, render: row => row.time },
]
const rows = [{ id: 'a', name: 'Анна', time: '00:31.02' }, { id: 'b', name: 'Мария', time: '00:32.15' }]
it('exposes a caption, row/column headers and a keyboard-focusable scroll region', () => {
  render(<DataTable caption="Результаты" columns={columns} rows={rows} rowKey={row => row.id} minWidth={640} />)
  const region = screen.getByRole('region', { name: 'Результаты' })
  expect(region).toHaveAttribute('tabindex', '0')
  expect(within(region).getByRole('table', { name: 'Результаты' })).toHaveStyle({ minWidth: '640px' })
  expect(screen.getByRole('columnheader', { name: 'Время' })).toHaveAttribute('scope', 'col')
  expect(screen.getByRole('columnheader', { name: 'Время' })).toHaveAttribute('data-numeric', 'true')
  expect(screen.getByRole('rowheader', { name: 'Анна' })).toHaveAttribute('scope', 'row')
  expect(screen.getByRole('cell', { name: '00:31.02' })).toHaveAttribute('data-numeric', 'true')
})
it('retains table semantics and a meaningful empty message', () => {
  render(<DataTable caption="Результаты" columns={columns} rows={[]} rowKey={row => row.id} emptyMessage="Результатов пока нет" />)
  expect(screen.getByRole('cell', { name: 'Результатов пока нет' })).toHaveAttribute('colspan', '2')
  expect(screen.getAllByRole('columnheader')).toHaveLength(2)
})
it('preserves row identity when results are reordered', () => {
  const { rerender } = render(<DataTable caption="Результаты" columns={columns} rows={rows} rowKey={row => row.id} />)
  const anna = screen.getByRole('rowheader', { name: 'Анна' }).closest('tr')
  rerender(<DataTable caption="Результаты" columns={columns} rows={[...rows].reverse()} rowKey={row => row.id} compact />)
  expect(screen.getAllByRole('row')[2]).toBe(anna)
})

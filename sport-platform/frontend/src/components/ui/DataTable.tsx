import { useId, type CSSProperties, type Key, type ReactNode } from 'react'

export type DataTableColumn<T> = {
  key: string
  header: ReactNode
  render: (row: T) => ReactNode
  numeric?: boolean
  rowHeader?: boolean
}

export type DataTableProps<T> = {
  caption: string
  columns: readonly DataTableColumn<T>[]
  rows: readonly T[]
  rowKey: (row: T) => Key
  emptyMessage?: string
  compact?: boolean
  minWidth?: CSSProperties['minWidth']
  maxHeight?: CSSProperties['maxHeight']
}

/** Keep overflow in a labelled, keyboard-scrollable region, including in flex/grid parents. */
export function DataTable<T>({ caption, columns, rows, rowKey, emptyMessage = 'Нет данных', compact = false, minWidth = '100%', maxHeight = 'min(60vh, 640px)' }: DataTableProps<T>) {
  const captionId = useId()
  return <div className="fl-data-table-scroll" role="region" aria-labelledby={captionId} tabIndex={0} style={{ maxHeight }}>
    <table className={`fl-table fl-data-table${compact ? ' fl-table-compact' : ''}`} style={{ minWidth }}>
      <caption id={captionId}>{caption}</caption>
      <thead><tr>{columns.map(column => <th key={column.key} scope="col" data-numeric={column.numeric || undefined}>{column.header}</th>)}</tr></thead>
      <tbody>{rows.length ? rows.map(row => <tr key={rowKey(row)}>{columns.map(column => column.rowHeader
        ? <th key={column.key} scope="row" data-numeric={column.numeric || undefined}>{column.render(row)}</th>
        : <td key={column.key} data-numeric={column.numeric || undefined}>{column.render(row)}</td>)}</tr>)
        : <tr><td colSpan={columns.length} className="fl-data-table-empty">{emptyMessage}</td></tr>}</tbody>
    </table>
  </div>
}

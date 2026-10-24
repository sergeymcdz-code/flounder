import { useState } from 'react'
import { NavLink } from 'react-router'
import { paymentLabels } from '../../api/registration-payments.api'
import { statusLabel } from '../../components/ui/status-dictionary'
import type { Registration } from './registration.types'
import { participantName, eventLabel } from './registration-display'
import styles from './RegistrationDirectory.module.css'

export function RegistrationDirectory({ registrations, onSelect, selectedId, disabled = false }: {
  registrations: Registration[]
  onSelect?: (registration: Registration) => void
  selectedId?: string
  disabled?: boolean
}) {
  const [query, setQuery] = useState('')
  const [eventId, setEventId] = useState('')
  const events = [...new Map(registrations.map(row => [row.eventId, row])).values()]
  const normalized = query.trim().toLocaleLowerCase('ru-RU')
  const visible = registrations.filter(row => (!eventId || row.eventId === eventId)
    && `${participantName(row)} ${row.eventTitle || ''}`.toLocaleLowerCase('ru-RU').includes(normalized))

  return <div className={styles.directory}>
    <form className={`fl-form ${styles.filters}`} onSubmit={event => event.preventDefault()} role="search">
      <label className="fl-field">Поиск по ФИО или соревнованию
        <input type="search" value={query} onChange={event => setQuery(event.target.value)} />
      </label>
      <label className="fl-field">Соревнование
        <select value={eventId} onChange={event => setEventId(event.target.value)}>
          <option value="">Все соревнования</option>
          {events.map(row => <option key={row.eventId} value={row.eventId}>{eventLabel(row)}</option>)}
        </select>
      </label>
    </form>
    <p role="status">Найдено заявок: {visible.length}</p>
    {!visible.length ? <p className="fl-empty">{registrations.length ? 'Ничего не найдено. Измените поиск или соревнование.' : 'Заявок пока нет.'}</p>
      : <div className={styles.tableWrap}><table className={styles.table}>
        <caption className={styles.srOnly}>Заявки участников</caption>
        <thead><tr><th scope="col">Участник</th><th scope="col">Соревнование</th><th scope="col">Статусы и оплата</th><th scope="col">Действия</th></tr></thead>
        <tbody>{visible.map(row => {
          const year = row.participantBirthYear ?? row.participantSnapshot?.birthDate?.slice(0, 4)
          const club = row.participantClubName || row.participantSnapshot?.clubName
          return <tr key={row.id} aria-selected={onSelect ? row.id === selectedId : undefined}>
            <td><strong>{participantName(row)}</strong><span className={styles.meta}>{year ? `${year} г. р.` : 'Год рождения не указан'} · {club || 'Клуб не указан'}</span></td>
            <td>{eventLabel(row)}</td>
            <td><span>{statusLabel('registration', row.status)}</span><span className={styles.meta}>{paymentLabels[row.paymentStatus || ''] || 'Статус оплаты не указан'}</span><span className={styles.meta}>{row.feeAmount == null ? 'Взнос не назначен' : new Intl.NumberFormat('ru-RU', { style: 'currency', currency: row.feeCurrency || 'RUB' }).format(Number(row.feeAmount))}</span></td>
            <td>{onSelect ? <button className="fl-btn fl-btn-secondary" type="button" disabled={disabled} onClick={() => onSelect(row)} aria-label={`Выбрать заявку: ${participantName(row)}, ${eventLabel(row)}`}>{selectedId === row.id ? 'Выбрана' : 'Выбрать'}</button>
              : <NavLink className="fl-btn fl-btn-secondary" to={`/operator/registrations/${row.id}`} aria-label={`Открыть заявку: ${participantName(row)}, ${eventLabel(row)}`}>Открыть</NavLink>}</td>
          </tr>
        })}</tbody>
      </table></div>}
  </div>
}

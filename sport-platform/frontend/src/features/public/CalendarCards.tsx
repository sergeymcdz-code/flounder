import { Link } from 'react-router'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { plural } from '../../utils/plural'
import type { CalendarCard } from './calendar.types'
import styles from './CalendarCards.module.css'

const day = (value: string) => new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', timeZone: 'UTC' }).format(new Date(value))

export function CalendarCards({ items, grouped = false }: { items: CalendarCard[]; grouped?: boolean }) {
  if (!items.length) return <EmptyState title="Пока нет соревнований" action={<Button as={Link} to="/?status=past" variant="ghost">Показать прошедшие</Button>} />
  const groups = new Map<string, CalendarCard[]>()
  for (const item of items) {
    const key = grouped ? item.startDate?.slice(0, 7) ?? '' : ''
    groups.set(key, [...(groups.get(key) ?? []), item])
  }
  return <>{[...groups].map(([month, cards]) => <section key={month} className={styles.month}>
    {grouped && <h2>{month ? new Intl.DateTimeFormat('ru-RU', { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(new Date(`${month}-01`)).replace(/^./, c => c.toUpperCase()).replace(' г.', '') : 'Дата уточняется'}</h2>}
    <div className={styles.cards}>{cards.map(card => <article key={card.slug} className={styles.card}>
      <div className={styles.cardTop}>
        <p className={styles.date}>{card.startDate ? <time dateTime={card.startDate}>{day(card.startDate)}</time> : 'Дата уточняется'}{card.endDate && card.endDate !== card.startDate && <> — <time dateTime={card.endDate}>{day(card.endDate)}</time></>}{card.city && ` · ${card.city}`}</p>
        <StatusBadge kind="event" code={card.status} />
      </div>
      <h3><Link className={styles.cardLink} to={`/e/${encodeURIComponent(card.slug)}`}>{card.title}</Link></h3>
      <p className={styles.venue}>{card.venueName || 'Площадка не указана'}{card.organization && <> · {card.organization.publicSlug ? <Link className={styles.organization} to={`/o/${encodeURIComponent(card.organization.publicSlug)}`}>{card.organization.name}</Link> : card.organization.name}</>}</p>
      <p className={styles.meta}>{card.registrationCloseAt ? `Регистрация до ${new Intl.DateTimeFormat('ru-RU', { day: '2-digit', month: '2-digit', timeZone: card.venueTimezone ?? 'UTC' }).format(new Date(card.registrationCloseAt))}` : card.registrationOpen ? 'Регистрация открыта' : 'Регистрация закрыта'} · {card.disciplinesCount} {plural(card.disciplinesCount, ['вид', 'вида', 'видов'])} · {card.participantsCount} {plural(card.participantsCount, ['участник', 'участника', 'участников'])}</p>
      {(card.status === 'LIVE' || card.hasResults) && <div className={styles.cardActions}>
        {card.status === 'LIVE' && <Button as={Link} to={`/e/${encodeURIComponent(card.slug)}/live`} variant="ghost" size="sm">Табло</Button>}
        {card.hasResults && <Button as={Link} to={`/e/${encodeURIComponent(card.slug)}/results`} variant="ghost" size="sm">Результаты</Button>}
      </div>}
    </article>)}</div>
  </section>)}</>
}

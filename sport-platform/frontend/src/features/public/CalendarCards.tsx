import { Link } from 'react-router'
import type { CalendarCard } from './calendar.types'
import styles from './Calendar.module.css'
const day = (value: string) => new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', timeZone: 'UTC' }).format(new Date(value))
export function CalendarCards({ items, grouped = false }: { items: CalendarCard[]; grouped?: boolean }) {
  if (!items.length) return <p className={styles.empty}>Пока нет соревнований</p>
  const groups = new Map<string, CalendarCard[]>()
  for (const item of items) {
    const key = grouped ? item.startDate?.slice(0, 7) ?? '' : ''
    groups.set(key, [...(groups.get(key) ?? []), item])
  }
  return <>{[...groups].map(([month, cards]) => <section key={month} className={styles.month}>
    {grouped && <h2>{month ? new Intl.DateTimeFormat('ru-RU', { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(new Date(`${month}-01`)).replace(/^./, c => c.toUpperCase()).replace(' г.', '') : 'Дата уточняется'}</h2>}
    <div className={styles.cards}>{cards.map(card => <article key={card.slug} className={styles.card}>
      <p className={styles.date}>{card.startDate ? day(card.startDate) : 'Дата уточняется'}{card.endDate && card.endDate !== card.startDate ? ` — ${day(card.endDate)}` : ''}</p>
      <h3><Link to={`/e/${encodeURIComponent(card.slug)}`}>{card.title}</Link></h3>
      <p className={styles.muted}>{[card.city, card.venueName].filter(Boolean).join(' · ') || 'Площадка не указана'}</p>
      {card.organization && <p>{card.organization.publicSlug ? <Link to={`/o/${encodeURIComponent(card.organization.publicSlug)}`}>{card.organization.name}</Link> : card.organization.name}</p>}
      <div className={styles.badges}>
        {card.status === 'LIVE' ? <span className={styles.live}>Идёт сейчас</span> : card.registrationOpen ? <span>Регистрация открыта{card.registrationCloseAt ? ` до ${new Intl.DateTimeFormat('ru-RU', { day: '2-digit', month: '2-digit', timeZone: 'Europe/Moscow' }).format(new Date(card.registrationCloseAt))}` : ''}</span> : <span>Регистрация закрыта</span>}
        {card.hasResults && <Link to={`/e/${encodeURIComponent(card.slug)}/results`}>Результаты →</Link>}
      </div>
      <p className={styles.muted}>Видов программы: {card.disciplinesCount} · Участников: {card.participantsCount}</p>
      <Link className={styles.more} to={`/e/${encodeURIComponent(card.slug)}`}>Подробнее →</Link>
    </article>)}</div>
  </section>)}</>
}

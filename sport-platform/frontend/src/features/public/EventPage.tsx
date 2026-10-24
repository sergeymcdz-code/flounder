import { Link } from 'react-router'
import { money } from '../../api/registration-payments.api'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { KeyValue } from '../../components/ui/KeyValue'
import { Notice } from '../../components/ui/Notice'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { formatDateTime } from '../../utils/formatDateTime'
import { usePublicEvent } from './publicContext'
import { date, localTimeLabel } from './format'
import { SessionTimeline, type TimelineSession } from './SessionTimeline'
import styles from './EventPage.module.css'

export function PublicEventPage() {
  const event = usePublicEvent()
  const fees = event.disciplines.map(d => d.entryFeeAmount).filter((v): v is number => v != null)
  const minimumFee = fees.length ? Math.min(...fees) : null
  const feeCopy = event.payment?.feeMode === 'PER_EVENT' ? money(event.payment.eventFeeAmount, event.payment.currency)
    : minimumFee != null ? `${new Set(fees).size > 1 ? 'от ' : ''}${money(minimumFee, event.disciplines.find(d => d.entryFeeAmount === minimumFee)?.entryFeeCurrency ?? event.payment?.currency ?? 'RUB')}` : 'по видам программы'
  const startDate = date(event.eventStartAt, event.venue?.timezone)
  const endDate = date(event.eventEndAt, event.venue?.timezone)
  const base = `/e/${encodeURIComponent(event.publicSlug)}`
  const organizationPath = event.organization?.publicSlug ? `/o/${encodeURIComponent(event.organization.publicSlug)}` : null
  const full = event.maxParticipants != null && (event.participantsCount ?? 0) >= event.maxParticipants
  const action = event.status === 'LIVE' ? ['live', 'Открыть табло']
    : event.status === 'COMPLETED' || event.status === 'FINISHED' || event.status === 'ARCHIVED' ? ['results', 'Результаты']
    : event.registrationOpen ? ['register', 'Подать заявку'] : ['start-list', 'Стартовый протокол']
  const navigation = [
    ['start-list', 'Стартовый протокол', 'Заплывы и дорожки'],
    ['schedule', 'Расписание', 'Старты по сессиям'],
    ['live', 'Табло', 'Сейчас в бассейне'],
    ['results', 'Результаты', 'Абсолют и группы'],
  ].sort((a, b) => event.status === 'LIVE' ? Number(b[0] === 'live') - Number(a[0] === 'live') : 0)
  const entries = (sessionId: string | null) => event.disciplines
    .filter(item => sessionId === null ? item.sessionId === null || !event.sessions.some(s => s.id === item.sessionId) : item.sessionId === sessionId)
    .sort((a, b) => (a.orderInSession ?? Infinity) - (b.orderInSession ?? Infinity) || a.number - b.number)
    .map(item => ({ id: item.id, title: `Вид № ${item.number} · ${item.name}`, detail: !item.unitsPublished ? 'Стартовый протокол ещё не опубликован' : undefined }))
  const sessions: TimelineSession[] = [...event.sessions].sort((a, b) => a.orderNo - b.orderNo)
    .map(session => ({ id: session.id, name: session.name, startsAt: session.startsAt, entries: entries(session.id) }))
  const unassigned = entries(null)
  if (unassigned.length) sessions.push({ id: 'unassigned', name: 'Без сессии', entries: unassigned })

  return <div className={styles.page}>
    <header className={styles.hero}>
      <div>
        <p className={styles.eyebrow}>Соревнования</p>
        <h1 className={styles.title}>{event.title}</h1>
        <StatusBadge kind="event" code={event.status === 'FINISHED' ? 'COMPLETED' : event.status} />
        <p className={styles.facts}>{startDate}{endDate !== startDate ? ` — ${endDate}` : ''}{event.venue && <> · {event.venue.name}, {event.venue.city} · <span>{event.venue.lanes} дорожек · {event.venue.courseLength != null ? `${event.venue.courseLength} м` : 'длина бассейна не указана'}</span></>}</p>
      </div>
      <div className={styles.action}>
        <Button as={Link} to={`${base}/${action[0]}`} variant="primary" size="lg">{action[1]}</Button>
        {action[0] === 'register' && <p className={styles.hint}>{event.payment?.enabled && <>Взнос {feeCopy} · </>}{event.registrationCloseAt && <>до {formatDateTime(event.registrationCloseAt, event.venue?.timezone)}</>}</p>}
        {action[0] === 'start-list' && <Notice tone="warning">Регистрация закрыта{event.registrationCloseAt && <> · {formatDateTime(event.registrationCloseAt, event.venue?.timezone)}</>}.</Notice>}
        {event.registrationOpen && full && <Notice tone="warning">Мест нет, запись в лист ожидания.</Notice>}
      </div>
    </header>
    <nav className={styles.sections} aria-label="Подробнее о соревновании">
      {navigation.map(([path, title, description]) => <Link key={path} className={styles.cardLink} to={`${base}/${path}`}>
        <strong>{title} →</strong><span>{description}</span>
        {path === 'live' && event.status === 'LIVE' && <StatusBadge kind="event" code="LIVE" />}
      </Link>)}
    </nav>
    <div className={styles.content}>
      <section className={styles.card} aria-labelledby="event-about">
        <h2 id="event-about">О соревновании</h2>
        {event.description && <p>{event.description}</p>}
        <dl className={styles.details}>
          <KeyValue label="Площадка">{event.venue ? `${event.venue.name}, ${event.venue.city}` : 'Площадка не указана'}</KeyValue>
          {event.organization && <KeyValue label="Организатор">{organizationPath ? <Link className={styles.textLink} to={organizationPath}>{event.organization.name}</Link> : event.organization.name}</KeyValue>}
          {event.payment?.enabled && <KeyValue label="Взнос"><span className="fl-numeric">{feeCopy}</span> {event.payment.feeMode === 'PER_EVENT' ? 'за соревнование' : 'за дистанцию'}</KeyValue>}
          {event.maxParticipants != null && <KeyValue label="Участники"><span className="fl-numeric">{event.participantsCount ?? 0} из {event.maxParticipants}</span> · {full ? 'мест нет' : 'есть места'}</KeyValue>}
          {organizationPath && <KeyValue label="Контакты"><Link className={styles.textLink} to={organizationPath}>На странице организации →</Link></KeyValue>}
        </dl>
        <Button as={Link} to={`${base}/poster`} variant="secondary" className={styles.poster}>QR-плакат A4</Button>
      </section>
      <section className={styles.card} aria-labelledby="event-program">
        <h2 id="event-program">Программа</h2>
        <p className={styles.hint}>{localTimeLabel(event.eventStartAt, event.venue?.timezone)}</p>
        {sessions.length ? <SessionTimeline sessions={sessions} timezone={event.venue?.timezone} /> : <EmptyState title="Программа пока не опубликована" action={<Button as={Link} to={`${base}/schedule`}>Открыть расписание</Button>}><p>Сессии и виды программы появятся после публикации организатором.</p></EmptyState>}
      </section>
    </div>
  </div>
}

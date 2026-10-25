import { Button } from '../../components/ui/Button'
import { plural } from '../../utils/plural'
import styles from './EventChecklist.module.css'
import { StepIcon } from '../../components/ui/icons'
import { Link } from 'react-router'
import type { EventContext } from './organizer-event-context'
import { formatDate, isHeatPublished } from './organizer-event-context'
import { normalizeStatus } from '../../components/ui/status-dictionary'

export function checklistSteps({ event, sessions, registrations, heats }: Pick<EventContext, 'event' | 'sessions' | 'registrations' | 'heats'>) {
  const status = normalizeStatus(event.status)
  const pending = registrations.filter(r => ['SUBMITTED', 'UNDER_REVIEW'].includes(r.status)).length
  const entered = event.disciplines.filter(d => registrations.some(r => r.eventDisciplineId === d.id && ['CONFIRMED', 'PAID', 'ADMITTED'].includes(r.status)))
  const seeded = entered.filter(d => heats.some(h => h.eventDisciplineId === d.id)).length
  const finished = heats.filter(h => ['FINISHED', 'COMPLETED'].includes(h.status)).length
  const participants = new Set(registrations.filter(r => ['SUBMITTED', 'UNDER_REVIEW', 'CONFIRMED', 'PAID', 'ADMITTED'].includes(r.status)).map(r => r.participantProfileId)).size
  return [
    { title: 'Программа', done: event.disciplines.length > 0 && sessions.length > 0, summary: `${event.disciplines.length} ${plural(event.disciplines.length, ['вид', 'вида', 'видов'])} · ${sessions.length} ${plural(sessions.length, ['сессия', 'сессии', 'сессий'])}`, path: '/program', action: 'Добавить сессии и виды' },
    { title: status === 'DRAFT' ? 'Публикация и регистрация' : status === 'PUBLISHED' ? 'Открытие регистрации' : 'Регистрация открыта', done: ['REGISTRATION_OPEN', 'REGISTRATION_CLOSED', 'LIVE', 'COMPLETED', 'ARCHIVED'].includes(status), summary: `до ${formatDate(event.registrationCloseAt, event.venue?.timezone)} · ${event.maxParticipants == null ? `${participants} ${plural(participants, ['участник', 'участника', 'участников'])} · без лимита` : `лимит ${participants}/${event.maxParticipants}`}`, path: '', action: status === 'DRAFT' ? 'Опубликовать' : 'Открыть регистрацию' },
    { title: 'Заявки', done: registrations.length > 0 && pending === 0, summary: `${registrations.length} · ${pending} на рассмотрении`, path: '/entries?status=SUBMITTED', action: 'Проверить' },
    { title: 'Заплывы сформированы', done: entered.length > 0 && seeded === entered.length, summary: `${seeded} из ${entered.length} ${plural(entered.length, ['вида', 'видов', 'видов'])}`, path: '/heats', action: 'Сформировать' },
    { title: 'Стартовый протокол', done: heats.length > 0 && heats.every(h => isHeatPublished(h.status)), summary: heats.length > 0 && heats.every(h => isHeatPublished(h.status)) ? 'опубликован' : 'не опубликован', path: '/heats', action: 'Опубликовать протокол' },
    { title: 'Результаты', done: heats.length > 0 && finished === heats.length, summary: `${finished} из ${heats.length} ${plural(heats.length, ['заплыва', 'заплывов', 'заплывов'])}`, path: '/results', action: 'Ввести результаты' },
    { title: 'Соревнование завершено', done: ['COMPLETED', 'ARCHIVED'].includes(status), summary: '', path: '', action: 'Завершить соревнование' },
  ]
}
export type ChecklistAction = { label: string; step: number; reason?: string }

export function EventChecklist({ context, onTransition, action, busy = false }: {
  context: Pick<EventContext, 'event' | 'sessions' | 'registrations' | 'heats'>
  onTransition: (step: number) => void
  action?: ChecklistAction
  busy?: boolean
}) {
  const steps = checklistSteps(context)
  const first = steps.findIndex(step => !step.done)
  const current = first === -1 ? (action?.step ?? -1) : first
  const base = `/organizer/events/${context.event.id}`
  const completed = steps.filter((step, index) => step.done && index !== action?.step)
  function row(step: typeof steps[number], index: number) {
    const active = index === current
    const reason = [action?.reason, busy ? 'Дождитесь завершения текущего действия' : undefined].filter(Boolean).join(' · ') || undefined
    return <li className={styles.row} key={step.title} aria-current={active ? 'step' : undefined}>
      <StepIcon state={step.done && !active ? 'done' : active ? 'current' : 'pending'} />
      <Link className={styles.stepLink} to={`${base}${step.path}`}>
        <span className={styles.number}>{index + 1}.</span>{' '}
        <span>{step.title}{step.summary && <small>{step.summary}</small>}</span>
      </Link>
      {action?.step === index ? <div className={styles.action}>
        <Button variant="primary" loading={busy} disabled={busy} title={reason}
          aria-describedby={reason ? `checklist-reason-${index}` : undefined}
          onClick={() => onTransition(index)}>{action.label}</Button>
        {reason && <small id={`checklist-reason-${index}`}>{reason}</small>}
      </div> : <Button as={Link} variant="ghost" size="sm" className={`${styles.open} fl-back-link`} to={`${base}${step.path}`}>
        {active && !action ? step.action : 'Открыть →'}
      </Button>}
    </li>
  }
  return <article className={`fl-card fl-checklist ${styles.card}`} aria-label="Что дальше">
    <h2>Что дальше</h2>
    <ol className={styles.list}>{steps.map((step, index) => !step.done || index === action?.step ? row(step, index) : null)}</ol>
    {completed.length > 0 && <details className={styles.completed}>
      <summary className="fl-btn fl-btn-ghost">Выполнено: {completed.length} из 7</summary>
      <ol className={styles.list}>{steps.map((step, index) => step.done && index !== action?.step ? row(step, index) : null)}</ol>
    </details>}
  </article>
}

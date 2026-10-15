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
    { title: 'Программа', done: event.disciplines.length > 0 && sessions.length > 0, summary: `${event.disciplines.length} видов · ${sessions.length} сессий`, path: '/program', action: 'Добавить сессии и виды' },
    { title: 'Регистрация открыта', done: ['REGISTRATION_OPEN', 'REGISTRATION_CLOSED', 'LIVE', 'COMPLETED', 'ARCHIVED'].includes(status), summary: `до ${formatDate(event.registrationCloseAt, event.venue?.timezone)} · ${event.maxParticipants == null ? `${participants} участников · без лимита` : `лимит ${participants}/${event.maxParticipants}`}`, path: '', action: status === 'DRAFT' ? 'Опубликовать' : 'Открыть регистрацию' },
    { title: 'Заявки', done: registrations.length > 0 && pending === 0, summary: `${registrations.length} · ${pending} на рассмотрении`, path: '/entries?status=SUBMITTED', action: 'Проверить' },
    { title: 'Заплывы сформированы', done: entered.length > 0 && seeded === entered.length, summary: `${seeded} из ${entered.length} видов`, path: '/heats', action: 'Сформировать' },
    { title: 'Стартовый протокол', done: heats.length > 0 && heats.every(h => isHeatPublished(h.status)), summary: heats.length > 0 && heats.every(h => isHeatPublished(h.status)) ? 'опубликован' : 'не опубликован', path: '/heats', action: 'Опубликовать протокол' },
    { title: 'Результаты', done: heats.length > 0 && finished === heats.length, summary: `${finished} из ${heats.length} заплывов`, path: '/results', action: 'Ввести результаты' },
    { title: 'Соревнование завершено', done: ['COMPLETED', 'ARCHIVED'].includes(status), summary: '', path: '', action: 'Завершить соревнование' },
  ]
}
export function EventChecklist({ context, onTransition }: { context: Pick<EventContext, 'event' | 'sessions' | 'registrations' | 'heats'>; onTransition: (step: number) => void }) {
  const steps = checklistSteps(context)
  const current = steps.findIndex(step => !step.done)
  const base = `/organizer/events/${context.event.id}`
  return <article className="fl-checklist" aria-label="Что дальше"><h2>Что дальше</h2><ol className={styles.list}>
    {steps.map((step, index) => <li className={styles.row} key={step.title} aria-current={index === current ? 'step' : undefined}>
      <StepIcon state={step.done ? 'done' : index === current ? 'current' : 'pending'} />
      <Link className={styles.stepLink} to={`${base}${step.path}`}><span className={styles.number}>{index + 1}.</span>{' '}<span>{step.title}<small>{step.summary}</small></span></Link>
      {index === current && [1, 6].includes(index) ? <button className={'fl-btn fl-btn-primary'} onClick={() => onTransition(index)} disabled={index === 6 && !steps[5].done}>{step.action}</button>
        : <Link className={index === current ? 'fl-btn fl-btn-primary' : 'fl-btn fl-btn-ghost'} to={`${base}${step.path}`}>{index === current ? step.action : 'Открыть →'}</Link>}
    </li>)}
  </ol></article>
}

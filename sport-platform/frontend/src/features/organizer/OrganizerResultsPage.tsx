import { useCallback, useState } from 'react'
import { Link } from 'react-router'
import { ResultsPrintControls } from './PrintFormsControls'
import { useAuth } from '../../app/providers/AuthProvider'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EmptyState } from '../../components/ui/EmptyState'
import { Notice } from '../../components/ui/Notice'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { useToast } from '../../components/ui/Toast'
import { publicApi } from '../../api/public.api'
import { changeCompetitionUnitPublication } from '../../api/results.api'
import { publishDisciplineResults } from '../../api/scheduling.api'
import { LIVE_POLL_MS, usePublicData } from '../public/usePublicData'
import { formatDate, useOrganizerEvent } from './organizer-event-context'
import type { Heat, SwimDiscipline } from '../../api/organizer.types'
import styles from './ResultsTab.module.css'

const disciplineNumber = (discipline: SwimDiscipline, index: number) => discipline.number ?? discipline.orderInSession ?? index + 1
const published = (heat: Heat) => ['FINISHED', 'COMPLETED'].includes(heat.status)
const complete = (heat: Heat) => published(heat) || (
  ['PUBLISHED', 'IN_PROGRESS'].includes(heat.status) && heat.entries.length > 0 &&
  heat.entries.every(entry => entry.rawValue != null || (entry.resultStatus != null && entry.resultStatus !== 'PENDING'))
)

export function OrganizerResultsPage() {
  const { event, heats, sessions, accessToken, busy, run, error, clearError } = useOrganizerEvent()
  const toast = useToast()
  const { isOrganizer, isPlatformAdmin, isOperator } = useAuth()
  const canManage = isOrganizer || isPlatformAdmin || isOperator
  const [confirmation, setConfirmation] = useState<{ id: string; published: boolean } | null>(null)
  const load = useCallback((signal: AbortSignal) => event.publicSlug ? publicApi.live(event.publicSlug, signal) : Promise.resolve(null), [event.publicSlug])
  const live = usePublicData(load, LIVE_POLL_MS)
  const current = live.data?.currentUnit
  const discipline = event.disciplines.find(d => d.id === confirmation?.id)
  const archived = event.status === 'ARCHIVED'
  const firstReady = event.disciplines.find(d => {
    const units = heats.filter(h => h.eventDisciplineId === d.id)
    return units.length > 0 && units.every(complete) && !units.every(published)
  })?.id
  const groups = [
    ...sessions.map(session => ({ id: session.id, name: session.name, disciplines: event.disciplines.filter(d => d.sessionId === session.id) })),
    { id: 'unassigned', name: 'Без сессии', disciplines: event.disciplines.filter(d => !sessions.some(s => s.id === d.sessionId)) },
  ].filter(group => group.disciplines.some(d => heats.some(h => h.eventDisciplineId === d.id)))

  async function confirmPublication() {
    if (!confirmation) return
    const units = heats.filter(h => h.eventDisciplineId === confirmation.id)
    const ok = await run(async () => {
      if (confirmation.published) await publishDisciplineResults(units, accessToken)
      else for (const heat of units.filter(published)) {
        try {
          await changeCompetitionUnitPublication(heat.id, false, accessToken)
        } catch (cause) {
          const message = cause instanceof Error ? cause.message : 'Повторите попытку.'
          throw new Error(`Заплыв ${heat.sequenceNumber ?? heat.label}: ${message}`)
        }
      }
    })
    if (ok) {
      setConfirmation(null)
      toast({ text: confirmation.published ? 'Результаты опубликованы' : 'Публикация результатов снята', href: `/e/${event.publicSlug}/results` })
    }
  }

  return <div className={styles.page}>
    <div className={styles.top}>
      <section className={styles.card} aria-label="Текущий заплыв">
        {live.loading && <div role="status"><p>Загружаем текущий заплыв…</p><div className={styles.skeleton} aria-hidden="true" /></div>}
        {live.error && <Notice tone="danger"><p>{live.errorStatus ? `Сервер ответил ошибкой (${live.errorStatus}).` : live.error} Выберите заплыв в списке или повторите загрузку.</p><Button onClick={live.retry}>Повторить</Button></Notice>}
        {current ? <>
          <p className={styles.current}>Идёт заплыв {heats.find(h => h.id === current.id)?.sequenceNumber ?? current.number} из {heats.length}</p>
          <p className={styles.muted}>{current.disciplineName ?? 'Текущий заплыв'}{current.disciplineNumber != null && ` · Вид № ${current.disciplineNumber}`}</p>
          <Button as={Link} variant="primary" size="lg" to={`/operator/competition-units/${current.id}?eventId=${encodeURIComponent(event.id)}`}>Открыть текущий заплыв</Button>
        </> : !live.loading && !live.error && <><p className={styles.current}>Нет текущего заплыва</p><p className={styles.muted}>Выберите заплыв в списке, чтобы открыть судейскую панель.</p></>}
      </section>
      <div className={styles.printHeading}><h2>Результаты соревнования</h2><p className={styles.muted}>Неофициальные результаты</p>{canManage && <ResultsPrintControls />}</div>
    </div>
    {!canManage && <Notice>Доступен просмотр результатов. Для публикации и печати нужны права организатора или секретаря.</Notice>}
    {confirmation && discipline && <ConfirmDialog
      title={confirmation.published ? `Опубликовать результаты «${discipline.name}»?` : `Снять публикацию «${discipline.name}»?`}
      description={confirmation.published ? 'Места и времена появятся на публичной странице как неофициальные результаты.' : 'Результаты вида будут скрыты с публичной страницы. Их можно будет опубликовать повторно.'}
      confirmLabel={confirmation.published ? 'Опубликовать' : 'Снять публикацию'} tone={confirmation.published ? 'primary' : 'danger'}
      busy={busy} error={error} onClose={() => setConfirmation(null)} onConfirm={() => void confirmPublication()} />}
    {!heats.length ? <EmptyState title="Заплывы пока не сформированы" action={<Button as={Link} to={`/organizer/events/${event.id}/heats`}>К заплывам</Button>}><p>Сначала сформируйте заплывы, затем введите результаты в судейской панели.</p></EmptyState> : <div className={styles.columns}>
      <section className={`fl-judging-list ${styles.heatList}`} aria-label="Заплывы по сессиям">
        {groups.map((group, index) => <details className={styles.card} key={group.id} open={index === 0}>
          <summary>{group.name}</summary>
          {group.disciplines.map(d => {
            const units = heats.filter(h => h.eventDisciplineId === d.id)
            if (!units.length) return null
            return <section key={d.id} className={styles.discipline}>
              <h3>Вид № {disciplineNumber(d, event.disciplines.indexOf(d))} · {d.name}</h3>
              <div className={styles.tableScroll}>
                <table className={`fl-table fl-table-compact ${styles.table}`}>
                  <caption className="fl-sr-only">Заплывы · {d.name}</caption>
                  <thead><tr><th scope="col">№</th><th scope="col" data-numeric>Время</th><th scope="col">Статус</th><th scope="col">Действие</th></tr></thead>
                  <tbody>{units.map(heat => <tr key={heat.id}>
                    <td data-label="Заплыв" className={styles.number}>{heat.sequenceNumber ?? heat.label}</td>
                    <td data-label="Время" data-numeric>{formatDate(heat.scheduledAt, event.venue?.timezone)}</td>
                    <td data-label="Статус"><span className={styles.statuses}><StatusBadge kind="heat" code={heat.status} />{heat.entries.some(entry => entry.resultStatus === 'CORRECTED') && <StatusBadge kind="result" code="CORRECTED" />}</span></td>
                    <td><Button as={Link} variant="ghost" to={`/operator/competition-units/${heat.id}?eventId=${encodeURIComponent(event.id)}`}>Открыть →</Button></td>
                  </tr>)}</tbody>
                </table>
              </div>
            </section>
          })}
        </details>)}
      </section>
      {canManage && <section className={`${styles.card} ${styles.publication}`} aria-label="Публикация по видам">
        <h2>Публикация результатов</h2>
        {event.disciplines.map((d, index) => {
          const units = heats.filter(h => h.eventDisciplineId === d.id)
          const allPublished = units.length > 0 && units.every(published)
          const reason = archived ? 'Соревнование в архиве: доступен только просмотр' : busy ? 'Дождитесь завершения текущего действия' : allPublished && units.some(h => h.status === 'COMPLETED') ? 'Результаты завершённого соревнования нельзя снять с публикации' : !units.length ? 'Сначала сформируйте заплывы' : !units.every(complete) ? 'Не все заплывы завершены' : undefined
          return <article className={styles.publicationRow} key={d.id}>
            <h3>Вид № {disciplineNumber(d, index)} · {d.name}</h3>
            <p className={styles.muted}><span className={styles.numeric}>{units.filter(complete).length} из {units.length}</span> заплывов завершено</p>
            <Button variant={!current && firstReady === d.id && !archived ? 'primary' : 'secondary'} disabled={!!reason} title={reason} aria-describedby={reason ? `publication-${d.id}` : undefined} onClick={() => { clearError(); setConfirmation({ id: d.id, published: !allPublished }) }}>{allPublished ? 'Снять публикацию' : 'Опубликовать результаты вида'}</Button>
            {reason && <small id={`publication-${d.id}`} className={styles.muted}>{reason}</small>}
          </article>
        })}
      </section>}
    </div>}
  </div>
}

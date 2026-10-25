import { useCallback, useState } from 'react'
import { Link } from 'react-router'
import { publicApi } from '../../api/public.api'
import { PdfDownloadButton } from '../../components/PdfDownloadButton'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Notice } from '../../components/ui/Notice'
import { PageHeader } from '../../components/ui/PageHeader'
import { PrintButton, PrintHeader } from './Protocol'
import { ResultsTable } from './ResultsTable'
import { usePublicEvent } from './publicContext'
import { RESULTS_POLL_MS, usePublicData } from './usePublicData'
import styles from './ResultsPage.module.css'

export function ResultsPage() {
  const event = usePublicEvent()
  const [grouped, setGrouped] = useState<boolean | null>(null)
  const [query, setQuery] = useState('')
  const [disciplineId, setDisciplineId] = useState<string | null>(null)
  const [updatedAt, setUpdatedAt] = useState<string>()
  const load = useCallback(async (signal: AbortSignal) => {
    const results = await publicApi.results(event.publicSlug, signal)
    if (!signal.aborted) setUpdatedAt(new Date().toISOString())
    return results
  }, [event.publicSlug])
  const state = usePublicData(load, RESULTS_POLL_MS)
  const hasResults = state.data?.disciplines.some(discipline =>
    discipline.overall.length > 0 || discipline.byAgeGroup.some(group => group.rows.length > 0),
  ) ?? false
  const showGroups = grouped ?? Boolean(state.data?.disciplines[0]?.byAgeGroup.length)
  const surname = query.trim().toLocaleLowerCase('ru')
  const matches = (row: { lastName: string }) => row.lastName.toLocaleLowerCase('ru').includes(surname)
  const selectedId = state.data?.disciplines.some(discipline => discipline.id === disciplineId) ? disciplineId : null
  const visibleDisciplines = state.data?.disciplines
    .filter(discipline => selectedId === null || selectedId === discipline.id)
    .map(discipline => ({
      ...discipline,
      useAgeGroups: showGroups && discipline.byAgeGroup.length > 0,
      overall: discipline.overall.filter(matches),
      byAgeGroup: discipline.byAgeGroup
        .map(group => ({ ...group, rows: group.rows.filter(matches) }))
        .filter(group => group.rows.length > 0),
    }))
    .filter(discipline => discipline.useAgeGroups
      ? discipline.byAgeGroup.length > 0 : discipline.overall.length > 0) ?? []
  const base = `/e/${encodeURIComponent(event.publicSlug)}`
  const unavailable = !state.data ? 'Дождитесь загрузки результатов' : 'PDF станет доступен после публикации результатов'

  return (
    <div className={`${styles.page} fl-results-page`} data-theme="dark">
      <PrintHeader event={event} title="Итоговый протокол" />
      <div className={`${styles.printWarning} fl-unofficial`}>
        Неофициальные результаты — публикуются сразу после заплыва и могут быть исправлены до утверждения судейской коллегией
      </div>
      <PageHeader title="Результаты" actions={
        <div className={`${styles.actions} fl-actionbar fl-print-actions fl-scroll-x`}>
          <PrintButton ready={!!state.data} disabledReason="Дождитесь загрузки результатов" />
          <span className={styles.pdf} title={!state.data?.disciplines.length ? unavailable : 'Скачать итоговый протокол'}>
            <PdfDownloadButton size="sm" variant="secondary"
              path={`/api/v1/public/events/${encodeURIComponent(event.publicSlug)}/results.pdf`}
              filename="Итоговый-протокол.pdf" disabled={!state.data?.disciplines.length} />
          </span>
          {hasResults && <span className={styles.pdf}>
            <PdfDownloadButton size="sm" variant="secondary" path={`/api/v1/public/events/${encodeURIComponent(event.publicSlug)}/awards.pdf`}
              filename="Лист-награждения.pdf" label="Лист награждения (PDF)" />
          </span>}
          {(!state.data || !state.data.disciplines.length) && <small className={styles.actionReason}>{unavailable}</small>}
        </div>
      } />
      <div className={styles.publication}>
        {/* The current public contract has no approval flag; keep the existing preliminary condition. */}
        <span className={`${styles.publicationBadge} fl-badge fl-badge-warning`} title="Результаты могут быть исправлены до утверждения судейской коллегией">
          Предварительно
        </span>
        <span className={styles.updated} title="Время последней успешной загрузки результатов">
          {state.data && updatedAt ? <>Обновлено <time dateTime={updatedAt}>{
            new Intl.DateTimeFormat('ru-RU', {
              timeZone: event.venue?.timezone || undefined, hour: '2-digit', minute: '2-digit', second: '2-digit',
            }).format(new Date(updatedAt))
          }</time> · каждые {RESULTS_POLL_MS / 1000} с</> : 'Ожидаем данные'}
        </span>
      </div>
      {hasResults && <>
        <div className={styles.controls}>
          <div className={styles.segments} role="group" aria-label="Вид результатов">
            <Button variant="ghost" size="sm" aria-pressed={!showGroups} onClick={() => setGrouped(false)}>Абсолют</Button>
            <Button variant="ghost" size="sm" aria-pressed={showGroups} onClick={() => setGrouped(true)}>По группам</Button>
          </div>
          <label className="fl-field">Фамилия
            <input type="search" value={query} onChange={event => setQuery(event.target.value)}
              placeholder="Найти участника" autoComplete="off" />
          </label>
        </div>
        <div className={`${styles.disciplines} fl-scroll-x`} role="group" aria-label="Виды программы">
          <Button variant="ghost" size="sm" aria-pressed={selectedId === null} onClick={() => setDisciplineId(null)}>Все виды</Button>
          {state.data?.disciplines.map(discipline =>
            <Button key={discipline.id} variant="ghost" size="sm" aria-pressed={selectedId === discipline.id}
              onClick={() => setDisciplineId(discipline.id)}>№ {discipline.number} · {discipline.name}</Button>,
          )}
        </div>
      </>}
      {state.loading && !state.data && <div className={styles.loading} role="status">
        <p>Загружаем результаты…</p>
        {[0, 1, 2].map(index => <div key={index} className={styles.skeleton} aria-hidden="true" />)}
      </div>}
      {state.error && <div className={styles.error}>
        <Notice tone="danger">
          {state.errorStatus === 404 ? 'Данные не найдены.' : state.errorStatus
            ? `Сервер ответил ошибкой (${state.errorStatus}). ${state.error}` : state.error}
          {state.data && ' Показаны последние успешно загруженные результаты.'}
          <div><Button variant="secondary" onClick={state.retry}>Повторить</Button></div>
        </Notice>
      </div>}
      {state.data && (!hasResults ? <div><EmptyState title="Результаты ещё не опубликованы"
        action={<Button as={Link} to={`${base}/live`} variant="primary">Открыть табло</Button>}>
        <p>После публикации здесь появятся места и время участников.</p>
      </EmptyState>
        {state.data.disciplines.map(discipline => <h2 key={discipline.id} className={styles.pendingDiscipline}>
          Вид № {discipline.number}. {discipline.name}
        </h2>)}
      </div> : !visibleDisciplines.length ? <div className={styles.noPrint}>
        <EmptyState title="Участники не найдены"
          action={<Button onClick={() => { setQuery(''); setDisciplineId(null) }}>Сбросить фильтры</Button>}>
          <p>Проверьте фамилию или выберите другой вид программы.</p>
        </EmptyState>
      </div> : visibleDisciplines.map(discipline =>
        <section key={discipline.id} className={styles.results} aria-labelledby={`result-${discipline.id}`}>
          <h2 id={`result-${discipline.id}`}>Вид № {discipline.number}. {discipline.name}</h2>
          {discipline.useAgeGroups ? discipline.byAgeGroup.map(group =>
            <div key={group.code} className={styles.group}>
              <h3>{group.name}</h3>
              <ResultsTable rows={group.rows} query={surname} caption={`${discipline.name} · ${group.name}`} />
            </div>,
          ) : <ResultsTable rows={discipline.overall} query={surname} caption={`${discipline.name} · Абсолют`} />}
        </section>,
      ))}
    </div>
  )
}

import { ResultStatus } from '../results/ResultStatus'
import { plural } from '../../utils/plural'
import { sameSwimmer, swimmerStarts, useMySwimmers } from './my-swimmers'
import { StarIcon } from '../../components/ui/icons'
import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router'
import { publicApi } from '../../api/public.api'
import type {
  AthleteMatch,
  Discipline,
  LiveUnit,
  Live,
  ScheduleItem,
  StartList,
} from './public.types'
import { findHeat, isValidResult, time } from './format'
import { LaneTable } from './Protocol'
import { DataState } from './PublicLayout'
import { usePublicEvent } from './publicContext'
import { LIVE_POLL_MS, SCHEDULE_POLL_MS, usePublicData } from './usePublicData'
import styles from './Public.module.css'

export function LiveBoard({
  live,
  list = { sessions: [] },
  lanes,
  slug,
  schedule = [],
  disciplines = [],
  timeZone,
}: {
  live: Live
  list?: StartList
  lanes: number
  slug: string
  schedule?: ScheduleItem[]
  disciplines?: Discipline[]
  timeZone?: string
}) {
  function metadata(unit: LiveUnit) {
    const found = findHeat(list, unit.id)
    const discipline = disciplines.find((item) => item.id === unit.disciplineId)
    const primaryName = unit.disciplineName ?? discipline?.name
    const scheduled = schedule.find(
      (item) =>
        (primaryName === undefined || item.disciplineName === primaryName) &&
        (unit.sequenceInSession !== undefined
          ? item.sequenceInSession === unit.sequenceInSession &&
            (unit.scheduledAt === null || item.scheduledAt === unit.scheduledAt)
          : item.scheduledAt === unit.scheduledAt &&
            item.heatNumber === unit.number),
    )
    const name =
      unit.disciplineName ??
      discipline?.name ??
      found?.discipline.name ??
      scheduled?.disciplineName
    const number =
      unit.disciplineNumber ??
      discipline?.number ??
      found?.discipline.number ??
      disciplines.find((item) => item.name === name)?.number
    const scheduledHeats = schedule.filter(
      (item) =>
        item.disciplineName === name &&
        (!scheduled || item.sessionName === scheduled.sessionName),
    )
    const total = scheduledHeats.length
      ? Math.max(...scheduledHeats.map((item) => item.heatNumber))
      : found?.discipline.heats.length
    return { name, number, total }
  }
  const current = live.currentUnit && metadata(live.currentUnit)
  const delayed = (live.delaySec ?? 0) > 0
  return (
    <>
      {delayed && (
        <p role="status" className={styles.card}>
          Соревнование идёт с задержкой {Math.ceil(live.delaySec! / 60)} мин, время рассчитано с учётом задержки
        </p>
      )}
      <section
        className={`${styles.card} ${styles.live}`}
        aria-live="polite"
        aria-atomic="true"
      >
        <p className={styles.eyebrow}>
          {live.currentUnit?.status === 'COMPLETED' ||
          live.currentUnit?.status === 'FINISHED'
            ? 'Заплыв завершён'
            : live.currentUnit?.status === 'CANCELLED'
              ? 'Заплыв отменён'
              : 'Сейчас в бассейне'}
        </p>
        {live.currentUnit ? (
          <>
            <h2>
              Сейчас заплыв{' '}
              <span className={styles.heatNumber}>
                №{live.currentUnit.number}
              </span>
            </h2>
            <p className={styles.liveDescription}>
              {current?.name
                ? `Вид № ${current.number ?? '—'} · ${current.name}`
                : 'Текущий заплыв'}
            </p>
            <p>
              Заплыв {live.currentUnit.number}
              {current?.total ? ` из ${current.total}` : ''} · Старт{' '}
              {time(live.currentUnit.scheduledAt, timeZone)}
            </p>
            <LaneTable heat={live.currentUnit} lanes={lanes} />
            <Link
              className="fl-btn fl-btn-ghost"
              to={`/e/${encodeURIComponent(slug)}/heats/${encodeURIComponent(live.currentUnit.id)}`}
            >
              Открыть заплыв
            </Link>
          </>
        ) : (
          <>
            <h2>Сейчас нет активного заплыва</h2>
            <p>Расписание следующих стартов появится здесь.</p>
          </>
        )}
      </section>
      <section className={styles.card}>
        <h2>Далее</h2>
        {live.next.length ? (
          <ol className={styles.list}>
            {live.next.slice(0, 3).map((unit) => {
              const found = metadata(unit)
              return (
                <li key={unit.id}>
                  <Link
                    className={styles.timelineLink}
                    to={`/e/${encodeURIComponent(slug)}/heats/${encodeURIComponent(unit.id)}`}
                  >
                    <strong>
                      {time(
                        delayed ? unit.estimatedStartAt ?? unit.scheduledAt : unit.scheduledAt,
                        timeZone,
                      )} · Заплыв {unit.number}
                    </strong>
                    <span>{found.name ?? 'Следующий заплыв'}</span>
                    {delayed && unit.estimatedStartAt && <small>Расчётное время старта</small>}
                  </Link>
                </li>
              )
            })}
          </ol>
        ) : (
          <p className="fl-empty">Следующих заплывов нет · <Link to={`/e/${encodeURIComponent(slug)}/schedule`}>Открыть расписание</Link></p>
        )}
      </section>
      <p className={styles.muted}>
        Обновлено: {time(live.updatedAt, timeZone)} · Обновляется каждые 15 с
      </p>
    </>
  )
}

export function AthleteSearch({
  slug,
  timeZone,
}: {
  slug: string
  timeZone?: string
}) {
  const { swimmers, toggle } = useMySwimmers()
  const [input, setInput] = useState('')
  const [query, setQuery] = useState('')
  const [state, setState] = useState<{
    query: string
    data?: AthleteMatch[]
    error?: string
  }>({ query: '' })
  const { hash } = useLocation()
  useEffect(() => {
    if (hash === '#find-me')
      document.getElementById('find-me')?.scrollIntoView({ block: 'start' })
  }, [hash])
  useEffect(() => {
    const timer = window.setTimeout(() => setQuery(input.trim()), 300)
    return () => window.clearTimeout(timer)
  }, [input])
  useEffect(() => {
    if (query.length < 2) return
    const controller = new AbortController()
    void publicApi
      .athletes(slug, query, controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setState({ query, data })
      })
      .catch(() => {
        if (!controller.signal.aborted)
          setState({
            query,
            error: 'Не удалось выполнить поиск. Проверьте подключение.',
          })
      })
    return () => controller.abort()
  }, [query, slug])
  const validQuery = input.trim().length >= 2
  const current = query === input.trim() && state.query === query
  return (
    <section id="find-me" className={styles.card}>
      <h2>Найти себя</h2>
      <label className="fl-field">
        Фамилия
        <input
          type="search"
          autoComplete="family-name"
          placeholder="Например, Коновалов"
          value={input}
          onChange={(event) => setInput(event.target.value)}
        />
      </label>
      {!validQuery && (
        <p className={styles.muted}>Введите хотя бы две буквы фамилии.</p>
      )}
      <div aria-live="polite">
        {validQuery && !current && <p role="status">Ищем спортсмена…</p>}
        {validQuery && current && state.error && (
          <p role="alert">{state.error}</p>
        )}
        {validQuery && current && state.data?.length === 0 && (
          <p>Спортсмены не найдены. Проверьте фамилию.</p>
        )}
        {validQuery &&
          current &&
          state.data?.map((athlete) => (
            <article key={athlete.athleteKey} className={styles.athlete}>
              <button className="fl-btn fl-btn-ghost fl-btn-sm" type="button" aria-label={`Мои пловцы: ${athlete.lastName} ${athlete.firstName}`} aria-pressed={swimmers.some(v => sameSwimmer(v, { name: `${athlete.lastName} ${athlete.firstName}`, club: athlete.club, yearOfBirth: athlete.birthYear }))} onClick={() => toggle({ name: `${athlete.lastName} ${athlete.firstName}`, club: athlete.club, yearOfBirth: athlete.birthYear })}><StarIcon filled={swimmers.some(v => sameSwimmer(v, { name: `${athlete.lastName} ${athlete.firstName}`, club: athlete.club, yearOfBirth: athlete.birthYear }))} /></button>
              <h3>
                {athlete.lastName} {athlete.firstName}
              </h3>
              <p>
                {athlete.birthYear} · {athlete.club ?? 'Клуб не указан'}
              </p>
              <ul className={styles.list}>
                {athlete.swims.map((swim, index) => (
                  <li key={`${swim.disciplineName}-${index}`}>
                    <strong>{swim.disciplineName}</strong>
                    <p>
                      Заплыв {swim.heatNumber} · дорожка {swim.lane ?? '—'} ·{' '}
                      {time(swim.scheduledAt, timeZone)}
                    </p>
                    {swim.result ? (
                      <p>
                        Результат:{' '}
                        {isValidResult(swim.result.status)
                          ? swim.result.display
                          : <ResultStatus status={swim.result.status} />}
                        {swim.result.place !== null &&
                          ` · место ${swim.result.place}`}
                        {swim.result.placeInAgeGroup !== null &&
                          ` · место в группе ${swim.result.placeInAgeGroup}`}
                      </p>
                    ) : (
                      <p className={styles.muted}>
                        Результат ещё не опубликован
                      </p>
                    )}
                  </li>
                ))}
              </ul>
            </article>
          ))}
      </div>
    </section>
  )
}

export function LivePage() {
  const event = usePublicEvent()
  const { swimmers } = useMySwimmers()
  const loadList = useCallback(
    (signal: AbortSignal) => publicApi.startList(event.publicSlug, signal),
    [event.publicSlug],
  )
  const loadLive = useCallback(
    (signal: AbortSignal) => publicApi.live(event.publicSlug, signal),
    [event.publicSlug],
  )
  const loadSchedule = useCallback(
    (signal: AbortSignal) => publicApi.schedule(event.publicSlug, signal),
    [event.publicSlug],
  )
  const schedule = usePublicData(loadSchedule, SCHEDULE_POLL_MS)
  const list = usePublicData(loadList)
  const live = usePublicData(loadLive, LIVE_POLL_MS)
  return (
    <>
      <h1 className="fl-live-page">Табло</h1>
      <DataState
        loading={live.loading}
        error={
          live.error ??
          (!live.data ? (list.error ?? schedule.error) : undefined)
        }
        data={live.data}
        retry={live.retry}
      />
      {swimmers.length > 0 && live.data && list.data && <section className="fl-my-swimmers" aria-label="Мои пловцы"><h2>★ Мои пловцы</h2>{swimmers.map(swimmer => <div key={`${swimmer.name}-${swimmer.club}-${swimmer.yearOfBirth}`}><strong>{swimmer.name}</strong>{swimmerStarts(swimmer, list.data!, live.data!).length ? swimmerStarts(swimmer, list.data!, live.data!).slice(0, 1).map(start => <p key={start.heat.id}>Вид № {start.discipline.number} · {start.discipline.name} · заплыв {start.heat.number} · дорожка {start.lane} · ~{time(start.heat.scheduledAt, event.venue?.timezone)}<br /><span className="fl-swimmer-distance">{start.remaining === null ? 'Ожидает старта' : start.remaining === 0 ? 'Сейчас' : `через ${start.remaining} ${plural(start.remaining, ['заплыв', 'заплыва', 'заплывов'])}`}</span></p>) : <p>Предстоящих стартов в опубликованном протоколе нет.</p>}</div>)}<a className="fl-btn fl-btn-ghost" href="#find-me" onClick={() => document.getElementById('find-me')?.scrollIntoView({ block: 'start' })}>Изменить</a></section>}
      {live.data && (
        <LiveBoard
          live={live.data}
          list={list.data}
          schedule={schedule.data}
          disciplines={event.disciplines}
          lanes={event.venue?.lanes ?? 8}
          slug={event.publicSlug}
          timeZone={event.venue?.timezone}
        />
      )}
      {!live.loading && (
        <AthleteSearch slug={event.publicSlug} timeZone={event.venue?.timezone} />
      )}
    </>
  )
}

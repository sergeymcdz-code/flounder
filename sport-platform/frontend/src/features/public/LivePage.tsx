import { ResultStatus } from '../results/ResultStatus'
import { plural } from '../../utils/plural'
import { sameSwimmer, swimmerStarts, useMySwimmers } from './my-swimmers'
import { StarIcon } from '../../components/ui/icons'
import { useCallback, useEffect, useState, type ReactNode } from 'react'
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
import { LiveLanes } from './LiveLanes'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Notice } from '../../components/ui/Notice'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { usePublicEvent } from './publicContext'
import { LIVE_POLL_MS, SCHEDULE_POLL_MS, usePublicData } from './usePublicData'
import styles from './LivePage.module.css'

export function LiveBoard({
  live,
  list = { sessions: [] },
  lanes,
  slug,
  schedule = [],
  disciplines = [],
  timeZone,
  swimmersCard,
  search,
}: {
  live: Live
  list?: StartList
  lanes: number
  slug: string
  schedule?: ScheduleItem[]
  disciplines?: Discipline[]
  timeZone?: string
  swimmersCard?: ReactNode
  search?: ReactNode
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
  const status = live.currentUnit?.status
  const running = status === 'IN_PROGRESS'
  const finished = status === 'FINISHED' || status === 'COMPLETED'
  return (
    <>
      {delayed && (
        <Notice tone="warning" className={styles.delay}>
          <span role="status">
            Соревнование идёт с задержкой {Math.ceil(live.delaySec! / 60)} мин,
            время рассчитано с учётом задержки
          </span>
        </Notice>
      )}
      <div className={styles.grid}>
        <section
          className={`${styles.card} ${styles.live} ${running ? styles.running : ''}`}
          aria-live="polite"
          aria-atomic="true"
          aria-label="Текущий заплыв"
        >
          {live.currentUnit ? (
            <>
              <div className={styles.boardTop}>
                <p className={styles.eyebrow}>
                  {finished
                    ? 'Заплыв завершён'
                    : status === 'CANCELLED'
                      ? 'Заплыв отменён'
                      : '● Сейчас в бассейне'}
                </p>
                <span className={styles.startTime}>
                  Старт{' '}
                  {time(
                    live.currentUnit.actualStartedAt ??
                      live.currentUnit.scheduledAt,
                    timeZone,
                  )}
                </span>
              </div>
              <p className={styles.liveDescription}>
                {current?.name
                  ? `Вид № ${current.number ?? '—'} · ${current.name}`
                  : 'Текущий заплыв'}
              </p>
              <h2
                className={styles.heatTitle}
                aria-label={`${finished ? 'Завершён' : 'Сейчас'} заплыв №${live.currentUnit.number}`}
              >
                <span className={styles.heatNumber}>
                  Заплыв {live.currentUnit.number}
                </span>{' '}
                {current?.total && <small>из {current.total}</small>}
              </h2>
              <div className={styles.heatStatus}>
                {status && <StatusBadge kind="heat" code={status} />}
                {running && <span>Идёт заплыв</span>}
                {finished && (
                  <span>
                    Опубликованные результаты — в разделе «Результаты».
                  </span>
                )}
              </div>
              <LiveLanes heat={live.currentUnit} lanes={lanes} />
              <Button
                as={Link}
                variant="ghost"
                className={styles.openHeat}
                to={`/e/${encodeURIComponent(slug)}/heats/${encodeURIComponent(live.currentUnit.id)}`}
              >
                Открыть заплыв <span aria-hidden="true">→</span>
              </Button>
            </>
          ) : (
            <EmptyState
              title="Сейчас нет активного заплыва"
              action={
                <Button
                  as={Link}
                  variant="ghost"
                  to={`/e/${encodeURIComponent(slug)}/schedule`}
                >
                  Открыть расписание
                </Button>
              }
            >
              <p>Расписание следующих стартов появится здесь.</p>
            </EmptyState>
          )}
        </section>
        <aside className={styles.sidebar} aria-label="Следующие старты и поиск">
          {swimmersCard}
          <section className={`${styles.card} ${styles.next}`}>
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
                            delayed
                              ? (unit.estimatedStartAt ?? unit.scheduledAt)
                              : unit.scheduledAt,
                            timeZone,
                          )}{' '}
                          · Заплыв {unit.number}
                        </strong>
                        <span>{found.name ?? 'Следующий заплыв'}</span>
                        {delayed && unit.estimatedStartAt && (
                          <small>Расчётное время старта</small>
                        )}
                      </Link>
                    </li>
                  )
                })}
              </ol>
            ) : (
              <EmptyState
                title="Следующих заплывов нет"
                action={
                  <Button
                    as={Link}
                    variant="ghost"
                    to={`/e/${encodeURIComponent(slug)}/schedule`}
                  >
                    Открыть расписание
                  </Button>
                }
              />
            )}
          </section>
          {search}
        </aside>
      </div>
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
    <section id="find-me" className={`${styles.card} ${styles.search}`}>
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
          <Notice tone="danger">
            {state.error} Измените фамилию, чтобы повторить поиск.
          </Notice>
        )}
        {validQuery && current && state.data?.length === 0 && (
          <EmptyState
            title="Спортсмены не найдены"
            action={
              <Button
                variant="ghost"
                onClick={() => {
                  setInput('')
                  document
                    .querySelector<HTMLInputElement>('#find-me input')
                    ?.focus()
                }}
              >
                Изменить фамилию
              </Button>
            }
          >
            <p>Проверьте фамилию и попробуйте ещё раз.</p>
          </EmptyState>
        )}
        {validQuery &&
          current &&
          state.data?.map((athlete) => (
            <article key={athlete.athleteKey} className={styles.athlete}>
              <Button
                variant="ghost"
                size="sm"
                className={styles.star}
                type="button"
                aria-label={`Мои пловцы: ${athlete.lastName} ${athlete.firstName}`}
                aria-pressed={swimmers.some((v) =>
                  sameSwimmer(v, {
                    name: `${athlete.lastName} ${athlete.firstName}`,
                    club: athlete.club,
                    yearOfBirth: athlete.birthYear,
                  }),
                )}
                onClick={() =>
                  toggle({
                    name: `${athlete.lastName} ${athlete.firstName}`,
                    club: athlete.club,
                    yearOfBirth: athlete.birthYear,
                  })
                }
              >
                <StarIcon
                  filled={swimmers.some((v) =>
                    sameSwimmer(v, {
                      name: `${athlete.lastName} ${athlete.firstName}`,
                      club: athlete.club,
                      yearOfBirth: athlete.birthYear,
                    }),
                  )}
                />
              </Button>
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
                        {isValidResult(swim.result.status) ? (
                          swim.result.display
                        ) : (
                          <ResultStatus status={swim.result.status} />
                        )}
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
  const search = (
    <AthleteSearch slug={event.publicSlug} timeZone={event.venue?.timezone} />
  )
  const swimmersCard = swimmers.length > 0 && live.data && list.data && (
    <section
      className={`${styles.card} ${styles.swimmers} fl-my-swimmers`}
      aria-label="Мои пловцы"
    >
      <div className={styles.sectionHeading}>
        <h2>★ Мои пловцы</h2>
        <Button
          as="a"
          variant="ghost"
          size="sm"
          href="#find-me"
          onClick={() =>
            document.querySelector<HTMLInputElement>('#find-me input')?.focus()
          }
        >
          Изменить
        </Button>
      </div>
      {swimmers.map((swimmer) => {
        const start = swimmerStarts(swimmer, list.data!, live.data!)[0]
        const next = live.data!.next.find((unit) => unit.id === start?.heat.id)
        const estimated =
          (live.data!.delaySec ?? 0) > 0 ? next?.estimatedStartAt : undefined
        return (
          <div
            key={`${swimmer.name}-${swimmer.club}-${swimmer.yearOfBirth}`}
            className={styles.swimmer}
          >
            <strong>{swimmer.name}</strong>
            {start ? (
              <>
                <p className={styles.muted}>
                  Вид № {start.discipline.number} · {start.discipline.name} ·
                  заплыв {start.heat.number} · дорожка {start.lane} · ~
                  {time(
                    estimated ?? start.heat.scheduledAt,
                    event.venue?.timezone,
                  )}
                </p>
                <p className={`${styles.distance} fl-swimmer-distance`}>
                  {start.remaining === null
                    ? 'Ожидает старта'
                    : start.remaining === 0
                      ? 'Сейчас'
                      : `через ${start.remaining} ${plural(start.remaining, ['заплыв', 'заплыва', 'заплывов'])}`}
                </p>
                {estimated && <small>Расчётное время старта</small>}
              </>
            ) : (
              <p>Предстоящих стартов в опубликованном протоколе нет.</p>
            )}
          </div>
        )
      })}
    </section>
  )
  const error =
    live.error ?? (!live.data ? (list.error ?? schedule.error) : undefined)
  return (
    <div className={styles.page} data-theme="dark">
      <div className={styles.pageHeading}>
        <h1 className="fl-live-page">Табло</h1>
        {live.data && (
          <p className={styles.updated}>
            обновлено {time(live.data.updatedAt, event.venue?.timezone)} ·
            каждые 15 с
          </p>
        )}
      </div>
      {live.loading && (
        <section
          className={`${styles.card} ${styles.skeleton}`}
          role="status"
          aria-label="Загружаем табло"
        >
          <p>Загружаем табло…</p>
          {Array.from({ length: 8 }, (_, index) => (
            <div key={index} aria-hidden="true" />
          ))}
        </section>
      )}
      {error && (
        <Notice tone="danger" className={styles.delay}>
          <p>
            {error}
            {live.data && ' Показаны последние данные.'}
          </p>
          <Button variant="secondary" onClick={live.retry}>
            Повторить
          </Button>
        </Notice>
      )}
      {live.data && (
        <LiveBoard
          live={live.data}
          list={list.data}
          schedule={schedule.data}
          disciplines={event.disciplines}
          lanes={event.venue?.lanes ?? 8}
          slug={event.publicSlug}
          timeZone={event.venue?.timezone}
          swimmersCard={swimmersCard}
          search={search}
        />
      )}
      {!live.loading && !live.data && search}
    </div>
  )
}

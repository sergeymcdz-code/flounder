import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useAuth } from '../../app/providers/AuthProvider'
import { ApiError } from '../../api/http'
import { getOrganizations, type OrganizationResponse } from '../../api/organizations.api'
import { downloadAnalytics, getEventAnalytics, getOrganizationAnalytics, type Breakdown, type Count, type EventAnalytics, type OrganizationAnalytics } from '../../api/analytics.api'
import { ErrorState } from '../../components/ErrorState'
import { LoadingState } from '../../components/LoadingState'
import { statusLabels } from '../organizer/organizer-event-context'
import styles from './AnalyticsPage.module.css'

const number = (value: number) => new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 1 }).format(value)
const time = (value: string | null, timezone?: string) => value ? new Intl.DateTimeFormat('ru-RU', { dateStyle: 'short', timeStyle: 'short', timeZone: timezone }).format(new Date(value)) : '—'
const duration = (value: number | null) => value === null ? '—' : `${Math.floor(Math.round(value) / 60)}:${String(Math.round(value) % 60).padStart(2, '0')}`
const organizationAccessMessage = 'Доступно владельцам и администраторам организации'
function useAnalytics<T>(id: string, load: (id: string, token: string) => Promise<T>, forbiddenMessage = 'Нет доступа к этой аналитике.') {
  const { session, logout } = useAuth(); const navigate = useNavigate()
  const token = session?.accessToken ?? ''
  const [state, setState] = useState<{ requestKey: string; data?: T; error?: string }>()
  const [version, setVersion] = useState(0)
  const requestKey = `${id}:${token}:${version}`
  const report = useCallback((cause: unknown) => {
    if (cause instanceof ApiError && cause.status === 401) { logout(); navigate('/login', { replace: true }); return }
    setState(previous => ({ ...previous, requestKey, error: cause instanceof ApiError && cause.status === 403 ? forbiddenMessage : cause instanceof Error ? cause.message : 'Не удалось загрузить аналитику.' }))
  }, [logout, navigate, requestKey, forbiddenMessage])
  useEffect(() => {
    let active = true
    load(id, token).then(data => { if (active) setState({ requestKey, data }) })
      .catch(cause => { if (active) { report(cause) } })
    return () => { active = false }
  }, [id, token, load, requestKey, report])
  return { data: state?.requestKey === requestKey ? state.data : undefined, loading: state?.requestKey !== requestKey, error: state?.requestKey === requestKey ? state.error : undefined, token, report, refresh: () => setVersion(v => v + 1) }
}
function Metric({ label, value }: { label: string; value: number | string | null }) {
  return <div className={styles.card}><span>{label}</span><strong>{value === null ? '—' : typeof value === 'number' ? number(value) : value}</strong></div>
}
function Counts({ title, values }: { title: string; values: Count[] }) {
  return <div className={styles.table}><table><caption>{title}</caption><thead><tr><th>Статус</th><th>Количество</th></tr></thead><tbody>{values.map(v => <tr key={v.key}><td>{statusLabels[v.key] ?? ({ UNDER_REVIEW: 'На проверке' } as Record<string, string>)[v.key] ?? 'Другой статус'}</td><td>{number(v.count)}</td></tr>)}</tbody></table></div>
}
function Distribution({ title, values }: { title: string; values: Breakdown[] }) {
  return <section className={styles.panel}><div className={styles.table}><table><caption>{title}</caption><thead><tr><th>Название</th><th>Заявки</th><th>Участники</th></tr></thead><tbody>{values.map(v => <tr key={v.key}><td>{v.name}</td><td>{number(v.registrations)}</td><td>{number(v.participants)}</td></tr>)}</tbody></table></div>{values.length === 0 && <p>Заявок пока нет</p>}</section>
}
export function DailyChart({ days }: { days: EventAnalytics['registrations']['byDay'] }) {
  const max = Math.max(1, ...days.map(d => d.count))
  const points = days.map((d, i) => `${40 + (days.length === 1 ? 250 : i * 500 / (days.length - 1))},${200 - d.count * 160 / max}`).join(' ')
  return <section className={styles.panel}><h2>Заявки по дням</h2>
    {days.every(d => d.count === 0) && <p>Заявок пока нет</p>}
    {days.length > 0 && <svg className={styles.chart} viewBox="0 0 600 240" role="img" aria-label={`Заявки по дням, максимум ${max}. Точные значения в таблице ниже.`}>
      <path d="M40 30 V200 H560" fill="none" stroke="var(--color-border, #9caabd)" /><text x="8" y="45" fontSize="14">{max}</text><text x="15" y="205" fontSize="14">0</text>
      <polyline points={points} fill="none" stroke="var(--color-primary, #2269a5)" strokeWidth="3" />
      {days.length === 1 && <circle cx="290" cy={200 - days[0].count * 160 / max} r="4" fill="var(--color-primary, #2269a5)" />}
      <text x="40" y="230" fontSize="13">{days[0].date}</text><text x="540" y="230" textAnchor="end" fontSize="13">{days.at(-1)?.date}</text>
    </svg>}
    <details><summary>Показать значения по дням</summary><div className={styles.table}><table><thead><tr><th>Дата</th><th>Заявки</th></tr></thead><tbody>{days.map(d => <tr key={d.date}><td>{d.date}</td><td>{number(d.count)}</td></tr>)}</tbody></table></div></details>
  </section>
}
function Actions({ scope, id, token, refresh, report }: { scope: 'events' | 'organizations'; id: string; token: string; refresh: () => void; report: (cause: unknown) => void }) {
  const [busy, setBusy] = useState(false)
  return <div className={styles.actions}><button onClick={refresh}>Обновить</button><button disabled={busy} onClick={async () => {
    setBusy(true); try { await downloadAnalytics(scope, id, token) } catch (cause) { report(cause) } finally { setBusy(false) }
  }}>{busy ? 'Формируем файл…' : 'Скачать XLSX'}</button></div>
}
export function EventAnalyticsPage() {
  const { eventId = '' } = useParams(); const state = useAnalytics(eventId, getEventAnalytics)
  if (state.loading) return <LoadingState message="Загружаем аналитику…" />
  if (!state.data) return <ErrorState message={state.error ?? 'Не удалось загрузить аналитику.'} onRetry={state.refresh} />
  return <div className={styles.page}><div className={styles.header}><h2>Аналитика события</h2><Actions scope="events" id={eventId} {...state} /></div>
    {state.error && <p role="alert" className={styles.error}>{state.error}</p>}
    <p className={styles.muted}>Данные на {time(state.data.generatedAt, state.data.timezone)} · Обновление не чаще раза в минуту · Часовой пояс: {state.data.timezone}</p>
    <EventAnalyticsView data={state.data} />
  </div>
}
export function EventAnalyticsView({ data: a }: { data: EventAnalytics }) {
  const f = a.registrations.funnel
  const stages = [['Подано', f.submitted], ['Подтверждено', f.confirmed], ['Оплачено / без взноса', f.paid], ['Допущено в посев', f.admitted]] as const
  return <>
    <div className={styles.cards}><Metric label="Всего заявок" value={a.registrations.total} /><Metric label="Участники" value={a.registrations.participants} /><Metric label="Опубликовано заплывов" value={a.results.publishedHeats} /><Metric label="Исправленные результаты" value={a.results.corrected} /></div>
    {a.registrations.total === 0 && <p>Заявок пока нет</p>}
    <DailyChart days={a.registrations.byDay} />
    <section className={styles.panel}><h2>Воронка заявок</h2><div className={styles.bars}>{stages.map(([label, value]) => <div key={label}><div className={styles.barLabel}><span>{label}</span><strong>{number(value)}</strong></div><div className={styles.track}><div className={styles.fill} style={{ width: `${f.submitted ? value / f.submitted * 100 : 0}%` }} /></div></div>)}</div>
      <p className={styles.muted}>«Подано» включает отклонённые и снятые заявки; это текущее состояние, а не история переходов. Каждый этап входит в предыдущий. Заявки без взноса считаются оплаченными; допуск требует действующего назначения в заплыв.</p>
    </section>
    <section className={styles.panel}><Counts title="Заявки по статусам" values={a.registrations.byStatus} /></section>
    <section className={styles.panel}><div className={styles.table}><table><caption>По видам</caption><thead><tr><th>Вид</th><th>Заявки</th><th>Заплывы</th><th>Участники</th></tr></thead><tbody>{a.byDiscipline.map(d => <tr key={d.id}><td>{d.name}</td><td>{number(d.registrations)}</td><td>{number(d.heats)}</td><td>{number(d.participants)}</td></tr>)}</tbody></table></div></section>
    <Distribution title="По возрастным группам" values={a.byAgeGroup} /><Distribution title="По клубам — топ-20 и прочие" values={a.byClub} /><Distribution title="По полу" values={a.byGender} />
    <section className={styles.panel}><div className={styles.table}><table><caption>Загрузка сессий</caption><thead><tr><th>Сессия</th><th>Заплывы</th><th>Минуты</th><th>Расчётное окончание</th><th>Отставание, сек</th></tr></thead><tbody>{a.sessions.map(s => <tr key={s.id}><td>{s.name}</td><td>{number(s.heats)}</td><td>{number(s.estimatedDurationMin)}</td><td>{time(s.estimatedEndsAt, a.timezone)}</td><td>{s.delaySec ?? '—'}</td></tr>)}</tbody></table></div><p className={styles.muted}>Максимальное отставание фактического старта от плана; заплывы без факта старта не учитываются.</p></section>
    <section className={styles.panel}><h2>Время публикации результатов</h2><div className={styles.cards}><Metric label="Медиана, м:сс" value={duration(a.results.medianPublicationSec)} /><Metric label="p95, м:сс" value={duration(a.results.p95PublicationSec)} /><Metric label="Заплывов в выборке" value={a.results.latencySamples} /></div>
      {a.results.latencySource === 'UNAVAILABLE' ? <p>Нет данных: нет отметок фактического финиша и публикации для расчёта задержки.</p> : <p>Цель p95: не более 420 сек (7 минут). {a.results.p95PublicationSec !== null && a.results.p95PublicationSec <= 420 ? 'Цель достигнута.' : 'Цель превышена.'}</p>}
    </section>
    <section className={styles.panel}><h2>Последний прогон валидатора</h2>{a.validator.runId ? <><p>{time(a.validator.executedAt, a.timezone)}</p><div className={styles.cards}><Metric label="Ошибки" value={a.validator.errors} /><Metric label="Предупреждения" value={a.validator.warnings} /></div></> : <p>Валидатор ещё не запускался</p>}</section>
    <section className={styles.panel}><h2>Последние импорты</h2>{!a.importStats.available ? <p>{a.importStats.reason}</p> : <div className={styles.table}><table><thead><tr><th>Время</th><th>Строк</th><th>Создано</th><th>Обновлено</th><th>Ошибок</th><th>Длительность, мс</th></tr></thead><tbody>{a.importStats.recent.map((i, index) => <tr key={index}><td>{time(i.executedAt, a.timezone)}</td><td>{i.rows}</td><td>{i.created}</td><td>{i.updated}</td><td>{i.errors}</td><td>{i.durationMs}</td></tr>)}</tbody></table></div>}</section>
  </>
}
export function OrganizationAnalyticsPage() {
  const { orgId = '' } = useParams(); const state = useAnalytics(orgId, getOrganizationAnalytics, organizationAccessMessage)
  if (state.loading) return <LoadingState message="Загружаем аналитику организации…" />
  if (!state.data) return <ErrorState message={state.error ?? 'Не удалось загрузить аналитику.'} onRetry={state.refresh} />
  const a: OrganizationAnalytics = state.data
  return <div className={styles.page}><Link to="/organizer/organizations">← Организации</Link><div className={styles.header}><h1>{a.name}: аналитика</h1><Actions scope="organizations" id={orgId} {...state} /></div>{state.error && <p role="alert">{state.error}</p>}
    <p className={styles.muted}>Данные на {time(a.generatedAt)}. Участия — уникальные спортсмены внутри каждого события; повторные участники выступают минимум в двух событиях. Отклонённые, снятые и черновые заявки исключены.</p>
    <div className={styles.cards}><Metric label="События" value={a.events} /><Metric label="Участия в событиях" value={a.participants} /><Metric label="Уникальные участники" value={a.uniqueParticipants} /><Metric label="Повторные участники" value={a.repeatParticipants} /><Metric label="Средний размер события" value={a.averageEventSize} /></div>
    {a.events === 0 && <p>Событий пока нет</p>}
    <section className={styles.panel}><Counts title="События по статусам" values={a.byStatus} /></section>
    <section className={styles.panel}><h2>Выручка по оплатам</h2>{a.revenue.length === 0 ? <p>Подтверждённых оплат пока нет</p> : <div className={styles.cards}>{a.revenue.map(r => <Metric key={r.currency} label={r.currency} value={number(r.amount)} />)}</div>}</section>
  </div>
}
const loadOrganizations = (_id: string, token: string) => getOrganizations(token)
export function OrganizationsAnalyticsListPage() {
  const state = useAnalytics<OrganizationResponse[]>('list', loadOrganizations)
  if (state.loading) return <LoadingState message="Загружаем организации…" />
  if (!state.data) return <ErrorState message={state.error ?? 'Не удалось загрузить организации.'} onRetry={state.refresh} />
  return <div className={styles.page}><h1>Аналитика организаций</h1><p>Доступна владельцам и администраторам организации, а также администратору платформы.</p>{state.data.length === 0 && <p>Организаций пока нет</p>}{state.data.map(o => <section key={o.id} className={styles.panel}><h2>{o.name}</h2><p>{organizationAccessMessage}</p><Link to={`/organizer/organizations/${o.id}/analytics`}>Открыть аналитику</Link></section>)}</div>
}

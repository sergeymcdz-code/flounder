import { useState, useEffect, useEffectEvent, type FormEvent } from 'react'
import { ApiError, type FieldViolation } from '../../api/http'
import {
  saveSession,
  deleteSession,
  getAgeGroupPresets,
} from '../../api/sessions.api'
import {
  saveDiscipline,
  deleteDiscipline,
  publishDiscipline,
  getDisciplineTemplates,
} from '../../api/organizer.api'
import type {
  Session,
  SessionInput,
  DisciplineInput,
  SwimDiscipline,
  AgeGroupPreset,
  DisciplineTemplate,
} from '../../api/organizer.types'
import { OrganizerDialog, OrganizerConfirm } from './OrganizerDialog'
import {
  formatDate,
  sessionSummary,
  statusLabels,
  useOrganizerEvent,
} from './organizer-event-context'
import styles from './OrganizerEventPage.module.css'

const strokes: Record<string, string> = {
  FREE: 'Вольный стиль',
  BACK: 'На спине',
  BREAST: 'Брасс',
  FLY: 'Баттерфляй',
  MEDLEY: 'Комплекс',
}
const genders: Record<string, string> = {
  F: 'Женский',
  M: 'Мужской',
  X: 'Смешанный',
}
function disciplineLimit(d: SwimDiscipline) {
  if (d.maxParticipants == null) return d.participantLimit
  return d.participantLimit == null ? d.maxParticipants : Math.min(d.maxParticipants, d.participantLimit)
}
function localDateTime(value: string) {
  const date = new Date(value)
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000)
  return local.toISOString().slice(0, 16)
}
export function OrganizerProgramPage() {
  const ctx = useOrganizerEvent()
  const { event, sessions, heats, registrations, accessToken, busy, run } = ctx
  const [sessionEdit, setSessionEdit] = useState<Session | 'new' | null>(null)
  const [disciplineEdit, setDisciplineEdit] = useState<
    SwimDiscipline | 'new' | null
  >(null)
  const [remove, setRemove] = useState<{
    title: string
    action: () => Promise<unknown>
  } | null>(null)
  return (
    <div className={styles.stack}>
      <article className={styles.card}>
        <div className={styles.toolbar}>
          <h2>Сессии соревнования</h2>
          <button
            className={styles.primary}
            disabled={busy || event.status === 'ARCHIVED'}
            onClick={() => setSessionEdit('new')}
          >
            Добавить сессию
          </button>
        </div>
        <div className={styles.table}>
          <table>
            <thead>
              <tr>
                <th>Порядок</th>
                <th>Название</th>
                <th>Старт — расч. окончание · заплывы</th>
                <th>Пауза между видами</th>
                <th>Действия</th>
              </tr>
            </thead>
            <tbody>
              {[...sessions]
                .sort((a, b) => a.orderNo - b.orderNo)
                .map((s) => (
                  <tr key={s.id}>
                    <td>{s.orderNo}</td>
                    <td>{s.name}</td>
                    <td>
                      {formatDate(s.startsAt)} —{' '}
                      {formatDate(
                        sessionSummary(s, event.disciplines, heats)
                          .estimatedEndsAt,
                      )}{' '}
                      · {sessionSummary(s, event.disciplines, heats).heatsCount}{' '}
                      заплывов
                    </td>
                    <td>{s.breakBetweenEventsSec} с</td>
                    <td>
                      <div className={styles.actions}>
                        <button
                          disabled={busy || event.status === 'ARCHIVED'}
                          onClick={() => setSessionEdit(s)}
                        >
                          Изменить
                        </button>
                        <button
                          disabled={busy || event.status === 'ARCHIVED'}
                          onClick={() =>
                            setRemove({
                              title: `Удалить сессию «${s.name}»?`,
                              action: () => deleteSession(s.id, accessToken),
                            })
                          }
                        >
                          Удалить
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>
        {sessions.length === 0 && (
          <p className={styles.muted}>Добавьте первую сессию.</p>
        )}
      </article>
      <article className={styles.card}>
        <div className={styles.toolbar}>
          <h2>Виды программы</h2>
          <button
            className={styles.primary}
            disabled={busy || event.status === 'ARCHIVED'}
            onClick={() => setDisciplineEdit('new')}
          >
            Добавить вид
          </button>
        </div>
        <div className={styles.table}>
          <table>
            <thead>
              <tr>
                {[
                  '№',
                  'Название',
                  'Дистанция',
                  'Стиль',
                  'Пол',
                  'Группы',
                  'Сессия',
                  'Порядок',
                  'Статус',
                  'Заявок',
                  'Действия',
                ].map((h) => (
                  <th key={h}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {event.disciplines.map((d, index) => (
                <tr key={d.id}>
                  <td>{d.number ?? index + 1}</td>
                  <td>
                    {d.name}
                    <p className={styles.muted}>{d.code}</p>
                    {disciplineLimit(d) != null && <span className={styles.badge}>Лимит: {registrations.filter(r => r.eventDisciplineId === d.id && ['SUBMITTED', 'UNDER_REVIEW', 'CONFIRMED', 'PAID', 'ADMITTED'].includes(r.status)).length}/{disciplineLimit(d)}</span>}
                  </td>
                  <td>{d.distanceM} м</td>
                  <td>{strokes[d.stroke] ?? d.stroke}</td>
                  <td>{genders[d.gender] ?? d.gender}</td>
                  <td>{d.ageGroups?.map((g) => g.name).join(', ') || 'Все'}</td>
                  <td>
                    {sessions.find((s) => s.id === d.sessionId)?.name ??
                      'Не назначена'}
                  </td>
                  <td>{d.orderInSession}</td>
                  <td>{statusLabels[d.status] ?? d.status}</td>
                  <td>
                    {
                      registrations.filter((r) => r.eventDisciplineId === d.id)
                        .length
                    }
                  </td>
                  <td>
                    <div className={styles.actions}>
                      <button
                        disabled={busy || event.status === 'ARCHIVED'}
                        onClick={() => setDisciplineEdit(d)}
                      >
                        Изменить
                      </button>
                      <button
                        disabled={busy || event.status === 'ARCHIVED'}
                        onClick={() =>
                          setRemove({
                            title: `Удалить вид «${d.name}»?`,
                            action: () => deleteDiscipline(d.id, accessToken),
                          })
                        }
                      >
                        Удалить
                      </button>
                      {d.status !== 'PUBLISHED' && (
                        <button
                          disabled={busy || event.status === 'ARCHIVED'}
                          onClick={() => {
                            void run(() =>
                              publishDiscipline(event.id, d.id, accessToken),
                            )
                          }}
                        >
                          Опубликовать вид
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {event.disciplines.length === 0 && (
          <p className={styles.muted}>Добавьте первый вид программы.</p>
        )}
      </article>
      {sessionEdit && (
        <SessionEditor
          session={sessionEdit === 'new' ? undefined : sessionEdit}
          onClose={() => setSessionEdit(null)}
        />
      )}
      {disciplineEdit && (
        <DisciplineEditor
          discipline={disciplineEdit === 'new' ? undefined : disciplineEdit}
          onClose={() => setDisciplineEdit(null)}
        />
      )}
      {remove && (
        <OrganizerConfirm
          title={remove.title}
          message="Запись будет удалена из программы соревнования. Если она используется в других разделах, сначала уберите связанные записи."
          busy={busy}
          onClose={() => setRemove(null)}
          onConfirm={() => {
            void run(remove.action).then((ok) => {
              if (ok) setRemove(null)
            })
          }}
        />
      )}
    </div>
  )
}
function SessionEditor({
  session,
  onClose,
}: {
  session?: Session
  onClose: () => void
}) {
  const { event, sessions, accessToken, busy, run } = useOrganizerEvent()
  const [form, setForm] = useState<SessionInput>(
    session ?? {
      name: '',
      orderNo: sessions.length + 1,
      startsAt: event.eventStartAt,
      breakBetweenEventsSec: 120,
    },
  )
  const [violations, setViolations] = useState<FieldViolation[]>([])
  const [date, setDate] = useState(localDateTime(form.startsAt))
  const error = (field: string) =>
    violations
      .filter((v) => v.field === field)
      .map((v) => v.message)
      .join('; ')
  async function submit(e: FormEvent) {
    e.preventDefault()
    setViolations([])
    const ok = await run(async () => {
      try {
        await saveSession(
          event.id,
          session?.id,
          { ...form, startsAt: new Date(date).toISOString() },
          accessToken,
        )
      } catch (cause) {
        if (cause instanceof ApiError) setViolations(cause.violations)
        throw cause
      }
    })
    if (ok) onClose()
  }
  return (
    <OrganizerDialog
      title={session ? 'Изменить сессию' : 'Добавить сессию'}
      onClose={onClose}
    >
      <form className={styles.form} onSubmit={submit}>
        <label className={styles.field}>
          Название
          <input
            required
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
          />
          <span className={styles.error}>{error('name')}</span>
        </label>
        <label className={styles.field}>
          Дата и время старта (местное время)
          <input
            required
            type="datetime-local"
            value={date}
            onChange={(e) => setDate(e.target.value)}
          />
          <span className={styles.error}>{error('startsAt')}</span>
        </label>
        <label className={styles.field}>
          Порядок
          <input
            required
            type="number"
            min="1"
            value={form.orderNo}
            onChange={(e) =>
              setForm({ ...form, orderNo: Number(e.target.value) })
            }
          />
          <span className={styles.error}>{error('orderNo')}</span>
        </label>
        <label className={styles.field}>
          Пауза между видами, секунды
          <input
            required
            type="number"
            min="0"
            value={form.breakBetweenEventsSec}
            onChange={(e) =>
              setForm({
                ...form,
                breakBetweenEventsSec: Number(e.target.value),
              })
            }
          />
          <span className={styles.error}>{error('breakBetweenEventsSec')}</span>
        </label>
        <button className={styles.primary} disabled={busy || event.status === 'ARCHIVED'}>
          Сохранить сессию
        </button>
      </form>
    </OrganizerDialog>
  )
}
function DisciplineEditor({
  discipline,
  onClose,
}: {
  discipline?: SwimDiscipline
  onClose: () => void
}) {
  const { event, sessions, accessToken, busy, run } = useOrganizerEvent()
  const [form, setForm] = useState<DisciplineInput>(
    discipline ? { ...discipline, maxParticipants: discipline.maxParticipants ?? null, limitsByAgeGroup: discipline.limitsByAgeGroup ?? {} } : {
      maxParticipants: null,
      limitsByAgeGroup: {},
      code: '',
      name: '',
      disciplineTemplateId: '',
      distanceM: 50,
      stroke: 'FREE',
      gender: 'F',
      seedingMode: 'BY_AGE_GROUP',
      sessionId: sessions[0]?.id ?? null,
      orderInSession: event.disciplines.length + 1,
      heatDurationSec: null,
      ageGroups: [],
    },
  )
  const [violations, setViolations] = useState<FieldViolation[]>([])
  const [presets, setPresets] = useState<AgeGroupPreset[]>([])
  const [preset, setPreset] = useState('')
  const [presetBusy, setPresetBusy] = useState(false)
  const [templates, setTemplates] = useState<DisciplineTemplate[]>([])
  const [templatesState, setTemplatesState] = useState<
    'loading' | 'loaded' | 'failed'
  >('loading')
  const [templateError, setTemplateError] = useState('')
  const templatesLoading = templatesState === 'loading'
  const onTemplatesLoaded = useEffectEvent((items: DisciplineTemplate[]) => {
    const matching = items.filter(
      (template) => template.sportId === event.sportId,
    )
    setTemplates(matching)
    setForm((previous) => ({
      ...previous,
      disciplineTemplateId: matching.some(
        (template) => template.id === previous.disciplineTemplateId,
      )
        ? previous.disciplineTemplateId
        : (matching[0]?.id ?? ''),
    }))
    setTemplateError('')
    setTemplatesState('loaded')
  })
  const onTemplatesError = useEffectEvent((cause: unknown) => {
    if (cause instanceof ApiError && [401, 403].includes(cause.status))
      void run(() => Promise.reject(cause), 'none')
    else
      setTemplateError(
        cause instanceof Error
          ? cause.message
          : 'Не удалось загрузить шаблоны дисциплин.',
      )
    setTemplatesState('failed')
  })
  useEffect(() => {
    let active = true
    getDisciplineTemplates(accessToken)
      .then((items) => {
        if (active) onTemplatesLoaded(items)
      })
      .catch((cause) => {
        if (active) onTemplatesError(cause)
      })
    return () => {
      active = false
    }
  }, [event.sportId, accessToken])
  const error = (field: string) =>
    violations
      .filter((v) => v.field === field)
      .map((v) => v.message)
      .join('; ')
  const patch = <K extends keyof DisciplineInput>(
    key: K,
    value: DisciplineInput[K],
  ) => setForm((f) => ({ ...f, [key]: value }))
  async function loadPresets() {
    setPresetBusy(true)
    await run(async () => {
      const items = await getAgeGroupPresets(accessToken)
      setPresets(items)
      setPreset(items[0]?.code ?? '')
    }, 'none')
    setPresetBusy(false)
  }
  async function submit(e: FormEvent) {
    e.preventDefault()
    setViolations([])
    const ok = await run(async () => {
      try {
        await saveDiscipline(
          event.id,
          discipline?.id,
          { ...form, name: form.name?.trim() || undefined,
            limitsByAgeGroup: Object.fromEntries(Object.entries(form.limitsByAgeGroup ?? {}).filter(([code]) => form.ageGroups.some(g => g.code === code))) },
          accessToken,
        )
      } catch (cause) {
        if (cause instanceof ApiError) setViolations(cause.violations)
        throw cause
      }
    })
    if (ok) onClose()
  }
  return (
    <OrganizerDialog
      side
      title={discipline ? 'Изменить вид программы' : 'Добавить вид программы'}
      onClose={onClose}
    >
      <form className={styles.form} onSubmit={submit}>
        <label className={styles.field}>
          Шаблон
          <select
            aria-label="Шаблон"
            required
            disabled={templatesLoading}
            value={form.disciplineTemplateId ?? ''}
            onChange={(e) => patch('disciplineTemplateId', e.target.value)}
          >
            <option value="" disabled>
              {templatesLoading ? 'Загрузка шаблонов…' : 'Выберите шаблон'}
            </option>
            {discipline &&
              templatesState === 'failed' &&
              form.disciplineTemplateId && (
                <option value={form.disciplineTemplateId}>
                  Текущий шаблон вида
                </option>
              )}
            {templates.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
          {templateError && (
            <span className={styles.error} role="alert">
              {templateError}
            </span>
          )}
          {templatesState === 'loaded' && templates.length === 0 && (
            <span className={styles.muted}>
              Для вида спорта события нет шаблонов дисциплин
            </span>
          )}
          <span className={styles.error}>{error('disciplineTemplateId')}</span>
        </label>
        <label className={styles.field}>
          Код вида (необязательно)
          <input
            value={form.code ?? ''}
            onChange={(e) => patch('code', e.target.value)}
            placeholder="F-50-FREE"
          />
          <span className={styles.error}>{error('code')}</span>
        </label>
        <label className={styles.field}>
          Название (необязательно)
          <input
            value={form.name ?? ''}
            onChange={(e) => patch('name', e.target.value)}
            placeholder="Формируется автоматически"
          />
          <span className={styles.error}>{error('name')}</span>
        </label>
        <label className={styles.field}>
          Дистанция
          <select
            value={form.distanceM}
            onChange={(e) => patch('distanceM', Number(e.target.value))}
          >
            {[25, 50, 100, 200, 400, 800, 1500].map((n) => (
              <option key={n} value={n}>
                {n} м
              </option>
            ))}
          </select>
          <span className={styles.error}>{error('distanceM')}</span>
        </label>
        <label className={styles.field}>
          Стиль
          <select
            value={form.stroke}
            onChange={(e) => patch('stroke', e.target.value)}
          >
            {Object.entries(strokes).map(([key, name]) => (
              <option key={key} value={key}>
                {name}
              </option>
            ))}
          </select>
          <span className={styles.error}>{error('stroke')}</span>
        </label>
        <label className={styles.field}>
          Пол
          <select
            value={form.gender}
            onChange={(e) => patch('gender', e.target.value)}
          >
            {Object.entries(genders).map(([key, name]) => (
              <option key={key} value={key}>
                {name}
              </option>
            ))}
          </select>
          <span className={styles.error}>{error('gender')}</span>
        </label>
        <label className={styles.field}>
          Режим посева
          <select
            value={form.seedingMode}
            onChange={(e) => patch('seedingMode', e.target.value)}
          >
            <option value="BY_AGE_GROUP">По возрастным группам</option>
            <option value="BY_TIME_ALL">Абсолютный</option>
          </select>
          <span className={styles.error}>{error('seedingMode')}</span>
        </label>
        <label className={styles.field}>
          Сессия
          <select
            required
            value={form.sessionId ?? ''}
            onChange={(e) => patch('sessionId', e.target.value || null)}
          >
            <option value="">Выберите сессию</option>
            {sessions.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
          <span className={styles.error}>{error('sessionId')}</span>
        </label>
        <label className={styles.field}>
          Порядок в сессии
          <input
            required
            min="1"
            type="number"
            value={form.orderInSession}
            onChange={(e) => patch('orderInSession', Number(e.target.value))}
          />
          <span className={styles.error}>{error('orderInSession')}</span>
        </label>
        <label className={styles.field}>
          Длительность заплыва, секунды (необязательно)
          <input
            min="1"
            type="number"
            value={form.heatDurationSec ?? ''}
            onChange={(e) =>
              patch(
                'heatDurationSec',
                e.target.value ? Number(e.target.value) : null,
              )
            }
          />
          <span className={styles.error}>{error('heatDurationSec')}</span>
        </label>
        <label className={styles.field}>Лимит участников вида
          <input type="number" min="1" step="1" value={form.maxParticipants ?? ''} placeholder="Без лимита"
            onChange={e => patch('maxParticipants', e.target.value ? Number(e.target.value) : null)} />
          <span className={styles.error}>{error('maxParticipants')}</span>
        </label>
        <div className={styles.toolbar}>
          <h2>Возрастные группы</h2>
          <button
            type="button"
            disabled={presetBusy}
            onClick={() => {
              void loadPresets()
            }}
          >
            {presetBusy ? 'Загружаем…' : 'Добавить пресет'}
          </button>
        </div>
        {presets.length > 0 && (
          <div className={styles.actions}>
            <label className={styles.field}>
              Пресет
              <select
                value={preset}
                onChange={(e) => setPreset(e.target.value)}
              >
                {presets.map((p) => (
                  <option key={p.code} value={p.code}>
                    {p.name}
                  </option>
                ))}
              </select>
            </label>
            <button
              type="button"
              onClick={() => {
                const groups =
                  presets.find((p) => p.code === preset)?.groups ?? []
                patch('ageGroups', [
                  ...form.ageGroups,
                  ...groups.filter(
                    (g) =>
                      (g.gender === form.gender || form.gender === 'X') &&
                      !form.ageGroups.some(
                        (existing) => existing.code === g.code,
                      ),
                  ),
                ])
                setPresets([])
              }}
            >
              Применить пресет
            </button>
          </div>
        )}
        <div className={styles.groups}>
          {form.ageGroups.map((g, index) => (
            <div className={styles.group} key={index}>
              {(
                [
                  'code',
                  'name',
                  'gender',
                  'birthYearFrom',
                  'birthYearTo',
                ] as const
              ).map((key) => (
                <label className={styles.field} key={key}>
                  {
                    {
                      code: 'Код',
                      name: 'Название',
                      gender: 'Пол (M/F/X)',
                      birthYearFrom: 'От года',
                      birthYearTo: 'До года',
                    }[key]
                  }
                  <input
                    required={!key.startsWith('birth')}
                    type={key.startsWith('birth') ? 'number' : 'text'}
                    min={key.startsWith('birth') ? 1900 : undefined}
                    max={key.startsWith('birth') ? 2100 : undefined}
                    value={g[key] ?? ''}
                    onChange={(e) =>
                      patch(
                        'ageGroups',
                        form.ageGroups.map((item, i) =>
                          i === index
                            ? {
                                ...item,
                                [key]: key.startsWith('birth')
                                  ? e.target.value
                                    ? Number(e.target.value)
                                    : null
                                  : e.target.value,
                              }
                            : item,
                        ),
                      )
                    }
                  />
                  <span className={styles.error}>
                    {error(`ageGroups[${index}].${key}`)}
                  </span>
                </label>
              ))}
              <label className={styles.field}>Лимит группы
                <input type="number" min="1" step="1" value={form.limitsByAgeGroup?.[g.code] ?? ''} placeholder="Без лимита"
                  onChange={e => { const limits = { ...form.limitsByAgeGroup }; if (e.target.value) limits[g.code] = Number(e.target.value); else delete limits[g.code]; patch('limitsByAgeGroup', limits) }} />
              </label>
              <button
                type="button"
                onClick={() =>
                  patch(
                    'ageGroups',
                    form.ageGroups.filter((_, i) => i !== index),
                  )
                }
              >
                Удалить группу
              </button>
            </div>
          ))}
        </div>
        <span className={styles.error}>{error('ageGroups')}</span>
        <button
          type="button"
          onClick={() =>
            patch('ageGroups', [
              ...form.ageGroups,
              {
                code: '',
                name: '',
                gender: form.gender,
                birthYearFrom: 2014,
                birthYearTo: 2015,
              },
            ])
          }
        >
          Добавить группу вручную
        </button>
        <button
          className={styles.primary}
          disabled={
            busy ||
            templatesLoading ||
            (!templates.length &&
              !(discipline && templatesState === 'failed')) ||
            !form.disciplineTemplateId
          }
        >
          Сохранить вид
        </button>
      </form>
    </OrganizerDialog>
  )
}

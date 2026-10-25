import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { apiFetch } from '../../api/http'
import { getMyRegistrations } from '../../api/registrations.api'
import { publicApi, PublicApiError } from '../../api/public.api'
import { useAuth } from '../../app/providers/AuthProvider'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Notice } from '../../components/ui/Notice'
import { PageHeader } from '../../components/ui/PageHeader'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { useToast } from '../../components/ui/Toast'
import { date, time } from './format'
import { usePublicEvent } from './publicContext'
import { usePublicData } from './usePublicData'
import type { RegistrationRequest, RegistrationDiscipline } from './public.types'
import {
  eligible, emptyRegistrationDraft, maskSeedTime, parseSeedTime, readRegistrationDraft,
  validateRegistration, type RegistrationProfile,
} from './registrationFields'
import styles from './RegistrationPage.module.css'

const stepNames = ['Спортсмен', 'Виды и время', 'Проверка']
const fieldNames: Record<string, string> = {
  lastName: 'Фамилия', firstName: 'Имя', middleName: 'Отчество', birthDate: 'Дата рождения',
  gender: 'Пол', club: 'Клуб', disciplines: 'Виды программы', consent: 'Согласие',
}
const money = (value: number) => `${new Intl.NumberFormat('ru-RU').format(value)} ₽`
const fee = (value: number) => value === 0 ? 'Бесплатно' : money(value)

function isDisciplineFull(item: RegistrationDiscipline, profile: RegistrationProfile) {
  if (item.maxParticipants != null && (item.participantsCount ?? 0) >= item.maxParticipants) return true
  const year = Number(profile.birthDate.slice(0, 4))
  const group = item.ageGroups.find(g => (g.gender === 'X' || g.gender === profile.gender)
    && (g.birthYearFrom == null || year >= g.birthYearFrom) && (g.birthYearTo == null || year <= g.birthYearTo))
  const limit = group ? item.limitsByAgeGroup?.[group.code] : undefined
  return group != null && limit != null && (item.participantsByAgeGroup?.[group.code] ?? 0) >= limit
}

export function PublicRegistrationPage() {
  const event = usePublicEvent()
  const auth = useAuth()
  const returnTo = `/e/${encodeURIComponent(event.publicSlug)}/register`
  if (!auth.isAuthenticated) return <section className={styles.single}>
    <PageHeader title="Войдите или зарегистрируйтесь" />
    <EmptyState title="Для подачи заявки нужен аккаунт участника" action={<div className={styles.actions}>
      <Button as={Link} variant="primary" to={`/login?next=${encodeURIComponent(returnTo)}`} state={{ from: returnTo }}>Войти</Button>
      <Button as={Link} to={`/register?next=${encodeURIComponent(returnTo)}`} state={{ from: returnTo }}>Зарегистрироваться</Button>
    </div>} />
  </section>
  if (!auth.isParticipant) return <section className={styles.single}>
    <PageHeader title="Заявка на участие" />
    <Notice tone="warning">Подача заявки доступна с аккаунта участника.</Notice>
    <Button as={Link} variant="ghost" to={`/e/${encodeURIComponent(event.publicSlug)}`}>К соревнованию</Button>
  </section>
  return <RegistrationEditor key={`${event.publicSlug}:${auth.userId ?? ''}`} />
}

function RegistrationEditor() {
  const event = usePublicEvent()
  const auth = useAuth()
  const toast = useToast()
  const draftKey = auth.userId ? `sportgate.registration.${event.publicSlug}.${auth.userId}` : null
  const [draft, setDraft] = useState(() => draftKey ? readRegistrationDraft(draftKey) : emptyRegistrationDraft())
  const { profile, selected, consent, step } = draft
  const [showAll, setShowAll] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [ownProfile, setOwnProfile] = useState<Partial<RegistrationProfile> | null>(null)
  const [profileInfo, setProfileInfo] = useState('')
  const [storageError, setStorageError] = useState(false)
  const [email, setEmail] = useState('')
  const [success, setSuccess] = useState<string[]>()
  const [capacityError, setCapacityError] = useState(false)
  const focusTarget = useRef<string | null>(null)
  const load = useCallback(async (signal: AbortSignal) => {
    if (auth.session) {
      const user = await apiFetch<{ email: string }>('/api/v1/auth/me', { accessToken: auth.session.accessToken, signal }).catch(() => null)
      if (user && !signal.aborted) setEmail(user.email)
    }
    return publicApi.registrationForm(event.publicSlug, signal)
  }, [event.publicSlug, auth.session])
  const state = usePublicData(load)
  const today = new Date().toISOString().slice(0, 10)
  const profileReady = !!profile.gender && !!profile.birthDate && profile.birthDate <= today
  const disciplines = state.data?.disciplines.filter(item => Object.hasOwn(selected, item.id)) ?? []
  const currentErrors = state.data ? validateRegistration(profile, selected, consent, state.data, today) : {}
  const firstInvalid = Object.keys(currentErrors)[0]
  const total = disciplines.reduce((sum, item) => sum + item.entryFee, 0)
  const metaParts = [
    state.data ? (state.data.disciplines.some(item => item.entryFee > 0) ? 'Взнос указан у каждого вида программы' : 'Участие бесплатно') : '',
    event.registrationCloseAt ? `регистрация до ${date(event.registrationCloseAt, event.venue?.timezone)} ${time(event.registrationCloseAt, event.venue?.timezone)}` : '',
  ].filter(Boolean)
  const eventFull = state.data?.maxParticipants != null && (state.data.participantsCount ?? 0) >= state.data.maxParticipants
  const waitlisted = eventFull || disciplines.some(item => isDisciplineFull(item, profile))
  const back = <Button as={Link} variant="ghost" to={`/e/${encodeURIComponent(event.publicSlug)}`}>К соревнованию</Button>

  useEffect(() => {
    if (!draftKey || success) return
    // oxlint-disable-next-line react/set-state-in-effect -- Reflect external storage availability in the UI.
    try { localStorage.setItem(draftKey, JSON.stringify({ ...draft, version: 1, savedAt: Date.now() })); setStorageError(false) }
    catch { setStorageError(true) }
  }, [draft, draftKey, success])

  useEffect(() => {
    const target = focusTarget.current
    if (!target) return
    const element = document.getElementById(target)
    if (element) { element.focus(); element.scrollIntoView?.({ block: 'center' }) }
    focusTarget.current = null
  }, [step, errors])

  function fieldError(key: string) {
    return <p id={`registration-error-${key}`} className={`${styles.fieldError} fl-field-error`}>
      {errors[key] || <span aria-hidden="true">&nbsp;</span>}
    </p>
  }
  function changeProfile(key: keyof RegistrationProfile, value: string) {
    const next = { ...profile, [key]: value } as RegistrationProfile
    setDraft(previous => ({ ...previous, profile: next }))
    if (errors[key] && state.data) setErrors(previous => ({ ...previous, [key]: validateRegistration(next, selected, consent, state.data!, today)[key] ?? '' }))
  }
  function targetFor(key: string) {
    return key === 'disciplines' ? 'registration-disciplines' : `registration-${key}`
  }
  function showValidation(nextErrors: Record<string, string>) {
    setErrors(nextErrors)
    const key = Object.keys(nextErrors)[0]
    if (!key) return
    const nextStep = key === 'consent' ? 2 : key === 'disciplines' || state.data?.disciplines.some(item => item.id === key) ? 1 : 0
    focusTarget.current = targetFor(key)
    setDraft(previous => ({ ...previous, step: nextStep }))
  }
  function goTo(nextStep: number) {
    focusTarget.current = `registration-step-${nextStep}`
    setDraft(previous => ({ ...previous, step: nextStep }))
  }
  function next() {
    const keys = step === 0 ? Object.keys(fieldNames).filter(key => !['disciplines', 'consent'].includes(key))
      : ['disciplines', ...Object.keys(selected)]
    const nextErrors = Object.fromEntries(Object.entries(currentErrors).filter(([key]) => keys.includes(key)))
    if (Object.keys(nextErrors).length) showValidation(nextErrors)
    else { setErrors({}); goTo(step + 1) }
  }
  useEffect(() => {
    if (!auth.session) return
    let active = true
    async function loadOwnProfile() {
      try {
        const cached = email && sessionStorage.getItem(`sportgate.profile.${email}`)
        if (cached) {
          if (active) { setOwnProfile(JSON.parse(cached) as Partial<RegistrationProfile>); setProfileInfo('') }
          return
        }
        const rows = await getMyRegistrations(auth.session!.accessToken)
        if (!active) return
        const own = rows[0]?.participantSnapshot
        if (!own) { setOwnProfile(null); setProfileInfo('В профиле пока нет данных спортсмена. Заполните их ниже.'); return }
        setOwnProfile({ firstName: own.firstName, lastName: own.lastName, middleName: own.middleName ?? '',
          birthDate: own.birthDate ?? '', gender: own.gender === 'F' ? 'F' : own.gender === 'M' ? 'M' : '', club: own.clubName ?? '' })
        setProfileInfo('')
      } catch { if (active) setProfileInfo('Не удалось загрузить профиль. Заполните данные ниже.') }
    }
    void loadOwnProfile()
    return () => { active = false }
  }, [auth.session, email])
  function fillOwnProfile() {
    if (!ownProfile) return
    setDraft(previous => ({ ...previous, profile: { ...previous.profile, ...ownProfile } }))
    setErrors({})
  }
  async function submit(submitEvent: FormEvent<HTMLFormElement>) {
    submitEvent.preventDefault()
    if (submitting || !state.data || !auth.session) return
    if (step < 2) { next(); return }
    if (Object.keys(currentErrors).length) { showValidation(currentErrors); return }
    setSubmitting(true)
    setCapacityError(false)
    setErrors({})
    try {
      await publicApi.register(event.publicSlug, {
        profile: { ...profile, gender: profile.gender === 'F' ? 'F' : 'M', lastName: profile.lastName.trim(),
          firstName: profile.firstName.trim(), middleName: profile.middleName.trim(), club: profile.club.trim() },
        items: disciplines.map(item => ({ eventDisciplineId: item.id, seedTimeMs: parseSeedTime(selected[item.id]) ?? null })),
      } satisfies RegistrationRequest, auth.session.accessToken)
      if (draftKey) { try { localStorage.removeItem(draftKey) } catch { /* Submission still succeeded. */ } }
      setSuccess(disciplines.map(item => item.name))
      toast({ text: 'Заявка отправлена', tone: 'success' })
    } catch (error) {
      const mapped: Record<string, string> = {}
      if (error instanceof PublicApiError) {
        for (const violation of error.violations) {
          const index = /items\[(\d+)\]/.exec(violation.field)?.[1]
          const discipline = index !== undefined ? disciplines[Number(index)] : disciplines.find(item => violation.field.includes(item.id))
          mapped[discipline?.id ?? violation.field.replace(/^profile\./, '')] = violation.message
        }
        setCapacityError(error.status === 409 && /capacity|limit|full|waitlist|мест|лимит/i.test(`${error.code} ${error.message}`))
      }
      mapped.form = error instanceof PublicApiError ? error.message : 'Нет связи. Не удалось отправить заявку. Проверьте подключение и повторите.'
      if (Object.keys(mapped).some(key => key !== 'form')) showValidation(mapped)
      else { setErrors(mapped); focusTarget.current = 'registration-errors' }
    } finally { setSubmitting(false) }
  }
  const summaryErrors = Object.entries(errors).filter(([, message]) => message)
  const errorSummary = summaryErrors.length > 0 && <div id="registration-errors" tabIndex={-1}>
    <Notice tone={capacityError ? 'warning' : 'danger'}>
      <strong>{capacityError ? 'Мест нет, заявка попадёт в лист ожидания.' : 'Проверьте заявку'}</strong>
      <ul>{summaryErrors.map(([key, message]) => <li key={key}>
        {key !== 'form' && `${fieldNames[key] ?? state.data?.disciplines.find(item => item.id === key)?.name ?? 'Поле'}: `}{message}
      </li>)}</ul>
      {errors.form && <p>Данные сохранены в форме. {capacityError ? 'Уточните условия у организатора перед повторной отправкой.' : 'Исправьте ошибки или повторите отправку.'}</p>}
    </Notice>
  </div>

  if (success) return <section className={`${styles.single} ${styles.success}`}>
    <span className="fl-step-icon fl-step-done" aria-hidden="true">✓</span>
    <PageHeader title="Заявка отправлена" />
    <p>Организатор проверит её и подтвердит; письмо придёт на {email || 'ваш email'}. После подтверждения появится оплата.</p>
    <ul>{success.map(name => <li key={name}>{name} — отправлена</li>)}</ul>
    {waitlisted && <Notice tone="warning">Выбран вид с лимитом мест. Организатор сообщит, попала ли заявка в лист ожидания и когда освободится место.</Notice>}
    <div className={styles.actions}><Button as={Link} variant="primary" to="/my/registrations">Мои заявки</Button>{back}</div>
  </section>
  if (!event.registrationOpen || state.data?.registrationOpen === false) return <section className={styles.single}>
    <PageHeader title="Заявка на участие" /><Notice tone="warning">Регистрация закрыта.</Notice>{back}
  </section>

  return <div className={styles.layout}>
    <aside className={styles.eventCard} aria-label="О соревновании">
      <h2>О соревновании</h2><strong>{event.title}</strong>
      {event.eventStartAt && <p>{date(event.eventStartAt, event.venue?.timezone)}</p>}
      {event.venue && <p>{event.venue.name} · {event.venue.city}</p>}
      {event.registrationCloseAt && <p>Регистрация до {date(event.registrationCloseAt, event.venue?.timezone)} {time(event.registrationCloseAt, event.venue?.timezone)}</p>}
      <p>Оплата — после подтверждения заявки.</p>
    </aside>
    <section className={styles.card}>
      <PageHeader title="Заявка на участие" meta={metaParts.length ? metaParts.join(' · ') : undefined} />
      {state.loading && !state.data && <div role="status" className={styles.loading}>
        <p>Загружаем форму заявки…</p>{Array.from({ length: 6 }, (_, index) => <div key={index} className={styles.skeleton} aria-hidden="true" />)}
      </div>}
      {state.error && <Notice tone="danger">
        <p>{state.errorStatus === 404 ? 'Данные формы не найдены.' : state.errorStatus ? `Сервер ответил ошибкой (${state.errorStatus}). ${state.error}` : state.error}</p>
        <p>{state.data ? 'Данные формы сохранены. Повторите загрузку.' : 'Повторите загрузку формы или вернитесь к соревнованию.'}</p>
        <Button onClick={state.retry}>Повторить</Button>
      </Notice>}
      {state.data && !state.data.disciplines.length && <EmptyState title="Виды программы пока не добавлены" action={back}>
        <p>Организатор ещё готовит программу. Вернитесь к соревнованию и проверьте её позднее.</p>
      </EmptyState>}
      {state.data && state.data.disciplines.length > 0 && <form className={`${styles.form} fl-form`} onSubmit={submit} noValidate>
        <nav aria-label="Шаги заявки"><ol className={`${styles.wizard} fl-wizard`}>
          {stepNames.map((name, index) => <li key={name} aria-current={step === index ? 'step' : undefined}>
            <Button variant="ghost" disabled={submitting} title={submitting ? 'Заявка отправляется' : undefined} onClick={() => goTo(index)}>
              <span className={styles.stepNumber}>{index + 1}</span> {name}
            </Button>
          </li>)}
        </ol></nav>
        <p className={styles.draft}>{storageError || !draftKey ? 'Черновик не сохраняется в этом браузере. Не закрывайте страницу до отправки.' : 'Черновик сохраняется автоматически до отправки заявки.'}</p>
        {errorSummary}
        {eventFull && <Notice tone="warning"><span role="status">Мест нет, запись в лист ожидания. Организатор сообщит, когда освободится место.</span></Notice>}
        <fieldset hidden={step !== 0} disabled={submitting} className="fl-fieldset">
          <legend className="fl-sr-only">Участник</legend>
          <h2 id="registration-step-0" tabIndex={-1}>1. Спортсмен</h2>
          {ownProfile && <Button onClick={fillOwnProfile}>Это я</Button>}
          {profileInfo && <Notice tone="info">{profileInfo}</Notice>}
          <div className={styles.pair}>
            {([['lastName', 'Фамилия', 'family-name'], ['firstName', 'Имя', 'given-name']] as const).map(([key, label, autoComplete]) => <div className="fl-field" key={key}>
              <label htmlFor={`registration-${key}`}>{label}</label>
              <input id={`registration-${key}`} autoComplete={autoComplete} maxLength={120} required value={profile[key]}
                aria-invalid={!!errors[key]} aria-describedby={`registration-error-${key}`} onChange={e => changeProfile(key, e.target.value)} />{fieldError(key)}
            </div>)}
          </div>
          <div className="fl-field"><label htmlFor="registration-middleName">Отчество <span className={styles.optional}>(необязательно)</span></label>
            <input id="registration-middleName" autoComplete="additional-name" maxLength={120} value={profile.middleName} aria-invalid={!!errors.middleName} aria-describedby="registration-error-middleName" onChange={e => changeProfile('middleName', e.target.value)} />{fieldError('middleName')}
          </div>
          <div className={styles.pair}>
            <div className="fl-field"><label htmlFor="registration-birthDate">Дата рождения</label>
              <input id="registration-birthDate" type="date" required max={today} value={profile.birthDate} aria-invalid={!!errors.birthDate} aria-describedby="registration-date-hint registration-error-birthDate" onChange={e => changeProfile('birthDate', e.target.value)} />
              <small id="registration-date-hint">Формат даты: ДД.ММ.ГГГГ</small>{fieldError('birthDate')}
            </div>
            <div className="fl-field"><label htmlFor="registration-gender">Пол</label>
              <select id="registration-gender" required value={profile.gender} aria-invalid={!!errors.gender} aria-describedby="registration-error-gender" onChange={e => changeProfile('gender', e.target.value)}>
                <option value="">Выберите пол</option><option value="M">Мужской</option><option value="F">Женский</option>
              </select>{fieldError('gender')}
            </div>
          </div>
          <div className="fl-field"><label htmlFor="registration-club">Клуб / город <span className={styles.optional}>(необязательно)</span></label>
            <input id="registration-club" maxLength={180} value={profile.club} aria-invalid={!!errors.club} aria-describedby="registration-error-club" onChange={e => changeProfile('club', e.target.value)} />{fieldError('club')}
          </div>
        </fieldset>
        <fieldset hidden={step !== 1} disabled={submitting || !profileReady} className="fl-fieldset" aria-describedby="registration-disciplines-hint">
          <legend className="fl-sr-only">Виды (до трёх)</legend>
          <h2 id="registration-step-1" tabIndex={-1}>2. Виды и время</h2>
          <div id="registration-disciplines-hint">{profileReady ? <p>Выберите до трёх видов программы. Заявочное время — мм:сс.сс.</p> : <Notice tone="info">Укажите пол и дату рождения, чтобы увидеть доступные виды</Notice>}</div>
          <Button variant="ghost" aria-pressed={showAll} onClick={() => setShowAll(value => !value)}>Показать все виды</Button>
          <div id="registration-disciplines" tabIndex={-1}>{fieldError('disciplines')}</div>
          {Object.keys(selected).some(id => !state.data?.disciplines.some(item => item.id === id)) && <Notice tone="warning">Программа изменилась. <Button onClick={() => setDraft(previous => ({ ...previous, selected: {}, withoutTime: {} }))}>Выбрать виды заново</Button></Notice>}
          {state.data.disciplines.filter(item => !profileReady || showAll || eligible(item, profile) || Object.hasOwn(selected, item.id)).map(item => {
            const allowed = profileReady && eligible(item, profile)
            const checked = Object.hasOwn(selected, item.id)
            const groupNames = item.ageGroups.filter(group => !profileReady || ((group.gender === 'X' || group.gender === profile.gender)
              && (group.birthYearFrom == null || Number(profile.birthDate.slice(0, 4)) >= group.birthYearFrom)
              && (group.birthYearTo == null || Number(profile.birthDate.slice(0, 4)) <= group.birthYearTo))).map(group => group.name).join(', ')
            return <div key={item.id} className={styles.discipline} data-selected={checked}>
              <Button className={styles.disciplineChoice} variant="ghost" aria-pressed={checked} disabled={!allowed && !checked}
                title={!allowed && !checked ? 'Не подходит по полу или году рождения' : undefined}
                onClick={() => setDraft(previous => { const next = { ...previous.selected }; if (checked) delete next[item.id]; else next[item.id] = ''; return { ...previous, selected: next } })}>
                <span><strong>{item.name}</strong> · {groupNames || 'Все возрастные группы'}</span>
                <span className={styles.disciplineMeta}>{fee(item.entryFee)} · {checked ? 'Выбрано' : 'Выбрать'}</span>
              </Button>
              {profileReady && isDisciplineFull(item, profile) && <StatusBadge kind="registration" code="WAITLISTED" />}
              {!allowed && <small>{profileReady ? 'Не подходит по полу или году рождения' : 'Сначала укажите пол и дату рождения'}</small>}
              {checked && <div className={`${styles.seed} fl-field`}>
                <label htmlFor={`registration-${item.id}`}>Заявочное время · {item.name}</label>
                <input id={`registration-${item.id}`} className={styles.numeric} inputMode="decimal" placeholder="мм:сс.сс" maxLength={8}
                  required={state.data?.requiresSeedTime} disabled={!state.data?.requiresSeedTime && draft.withoutTime[item.id] === true} value={selected[item.id]} aria-invalid={!!errors[item.id]} aria-describedby={`hint-${item.id} registration-error-${item.id}`}
                  onChange={e => { const value = maskSeedTime(e.target.value); setDraft(previous => ({ ...previous, selected: { ...previous.selected, [item.id]: value } })) }} />
                <small id={`hint-${item.id}`}>{state.data?.requiresSeedTime ? 'Укажите заявочное время' : 'Можно оставить пустым — без времени (NT)'}</small>
                {!state.data?.requiresSeedTime && <label className={styles.check}><input type="checkbox" checked={draft.withoutTime[item.id] === true}
                  onChange={e => { const checked = e.target.checked; setDraft(previous => ({ ...previous, withoutTime: { ...previous.withoutTime, [item.id]: checked }, selected: { ...previous.selected, [item.id]: '' } })) }} />Без времени (NT)</label>}
                {fieldError(item.id)}
              </div>}
              {!checked && errors[item.id] && fieldError(item.id)}
            </div>
          })}
          {profileReady && !state.data.disciplines.some(item => eligible(item, profile)) && <EmptyState title="Подходящих видов программы нет" action={<Button onClick={() => goTo(0)}>Проверить данные спортсмена</Button>}><p>Проверьте пол и дату рождения или уточните программу у организатора.</p></EmptyState>}
        </fieldset>
        <fieldset hidden={step !== 2} disabled={submitting} className="fl-fieldset">
          <legend className="fl-sr-only">Проверка заявки</legend>
          <h2 id="registration-step-2" tabIndex={-1}>3. Проверка</h2>
          <dl className={styles.review}>
            <dt>Участник</dt><dd>{[profile.lastName, profile.firstName, profile.middleName].filter(Boolean).join(' ') || 'Не указан'}</dd>
            <dt>Дата рождения · пол</dt><dd>{profile.birthDate ? profile.birthDate.split('-').reverse().join('.') : 'Не указана'} · {profile.gender === 'M' ? 'Мужской' : profile.gender === 'F' ? 'Женский' : 'Не указан'}</dd>
            <dt>Клуб / город</dt><dd>{profile.club || 'Не указан'}</dd>
          </dl>
          <h3>Виды и заявочное время</h3>
          {disciplines.length ? <ul className={styles.reviewList}>{disciplines.map(item => <li key={item.id}><span>{item.name}</span><strong className={styles.numeric}>{selected[item.id] || 'Без времени (NT)'}</strong><span className={styles.numeric}>{fee(item.entryFee)}</span></li>)}</ul> : <Notice tone="warning">Выберите хотя бы один вид программы на втором шаге.</Notice>}
          <div className={`${styles.total} fl-summary-cell`}><span>Итого · {disciplines.length} {disciplines.length === 1 ? 'дистанция' : 'дистанции'}</span><strong className={styles.numeric}>{fee(total)}</strong></div>
          {total > 0 && <p>Оплата после подтверждения заявки организатором. Сейчас оплачивать не нужно.</p>}
          {waitlisted && <Notice tone="warning">Мест нет, заявка попадёт в лист ожидания. Оплата будет доступна после подтверждения организатором.</Notice>}
          <h3>Контакты</h3><p>Ответ организатора придёт на <strong>{email || 'email вашего аккаунта'}</strong>.</p>
          <label className={styles.check}><input id="registration-consent" type="checkbox" required checked={consent} aria-invalid={!!errors.consent} aria-describedby="registration-error-consent" onChange={e => setDraft(previous => ({ ...previous, consent: e.target.checked }))} />Я даю согласие на обработку персональных данных</label>
          {fieldError('consent')}
        </fieldset>
        <div className={styles.sticky}>
          <div className={styles.actions}>
            {step > 0 && <Button disabled={submitting} title={submitting ? 'Заявка отправляется' : undefined} onClick={() => goTo(step - 1)}>Назад</Button>}
            {step < 2 ? <Button key="next" variant="primary" size="lg" onClick={next}>Далее: {stepNames[step + 1]}</Button>
              : <Button key="submit" variant="primary" size="lg" type="submit" loading={submitting} disabled={!!firstInvalid}
                title={firstInvalid ? currentErrors[firstInvalid] : submitting ? 'Заявка отправляется' : undefined} aria-describedby={firstInvalid ? 'registration-disabled-reason' : undefined}>Отправить заявку</Button>}
          </div>
          {step === 2 && firstInvalid && <small id="registration-disabled-reason">{currentErrors[firstInvalid]}</small>}
          {submitting && <small role="status">Отправляем заявку…</small>}
        </div>
      </form>}
    </section>
  </div>
}

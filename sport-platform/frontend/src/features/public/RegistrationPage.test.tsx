import { configure, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { PublicRegistrationPage } from './RegistrationPage'
import { publicApi, PublicApiError } from '../../api/public.api'
import type { RegistrationForm } from './public.types'
import { emptyRegistrationDraft, readRegistrationDraft } from './registrationFields'

// Multi-step interactions also run on shared CI workers.
vi.setConfig({ testTimeout: 30_000 })
configure({ asyncUtilTimeout: 10_000 })
afterAll(() => configure({ asyncUtilTimeout: 1_000 }))

const { form, auth, dataState, eventData } = vi.hoisted(() => ({
  eventData: { publicSlug: 'qa03', registrationOpen: true, registrationCloseAt: null as string | null },
  auth: { userId: 'test-user', isAuthenticated: true, isParticipant: true, session: { accessToken: 'test-token' } },
  dataState: { loading: false, error: undefined as string | undefined, errorStatus: undefined as number | undefined },
  form: {
    requiresSeedTime: true,
    disciplines: Array.from({ length: 4 }, (_, index) => ({
      id: `discipline-${index}`, name: `Вид ${index + 1}`, distanceM: 50,
      stroke: 'FREE', gender: 'M', ageGroups: [], entryFee: 0,
    })),
  } as RegistrationForm,
}))
vi.mock('../../app/providers/AuthProvider', () => ({
  useAuth: () => auth,
}))
vi.mock('./publicContext', async (importOriginal) => ({
  ...await importOriginal<typeof import('./publicContext')>(),
  usePublicEvent: () => eventData,
}))
vi.mock('./usePublicData', () => ({ usePublicData: () => ({ data: dataState.loading || dataState.error ? undefined : form, ...dataState, retry: vi.fn() }) }))

function setup(count = 1, fillTimes = true) {
  const view = render(<MemoryRouter><PublicRegistrationPage /></MemoryRouter>)
  fireEvent.change(screen.getByLabelText('Фамилия'), { target: { value: 'Демов' } })
  fireEvent.change(screen.getByLabelText('Имя'), { target: { value: 'Тимофей' } })
  fireEvent.change(screen.getByLabelText('Пол'), { target: { value: 'M' } })
  fireEvent.change(screen.getByLabelText('Дата рождения'), { target: { value: '2014-04-18' } })
  fireEvent.click(screen.getByText('Далее: Виды и время'))
  for (let index = 0; index < count; index++) fireEvent.click(screen.getByText(`Вид ${index + 1}`, { exact: true }))
  if (fillTimes) screen.getAllByPlaceholderText('мм:сс.сс').forEach((input, index) =>
    fireEvent.change(input, { target: { value: `0032${15 + index}` } }))
  fireEvent.click(screen.getByText('Проверка', { exact: true }))
  fireEvent.click(screen.getByLabelText('Я даю согласие на обработку персональных данных'))
  return view.container.querySelector('form')!
}

describe('QA03 public registration submission', () => {
  beforeEach(() => {
    localStorage.clear()
    eventData.registrationCloseAt = null
    auth.isAuthenticated = true
    auth.isParticipant = true
    dataState.loading = false
    dataState.error = undefined
    dataState.errorStatus = undefined
    form.disciplines.forEach(item => { item.entryFee = 0 })
    form.requiresSeedTime = true
    form.registrationOpen = true
    form.maxParticipants = null
    form.participantsCount = 0
    form.disciplines.forEach(item => { item.ageGroups = []; item.limitsByAgeGroup = null; item.participantsByAgeGroup = {} })
    vi.spyOn(publicApi, 'register').mockResolvedValue([])
  })

  it('QA03-01 blocks an empty time when the API requires seed times', async () => {
    const element = setup(1, false)
    fireEvent.submit(element)
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Укажите заявочное время'))
    expect(publicApi.register).not.toHaveBeenCalled()
    expect(screen.getByPlaceholderText('мм:сс.сс')).toBeRequired()
    expect(screen.getByPlaceholderText('мм:сс.сс')).toHaveFocus()
    expect(screen.queryByText(/Можно оставить пустым/)).not.toBeInTheDocument()
  })

  it.each([1, 2, 3])('submits %i disciplines with their individual times', async (count) => {
    fireEvent.submit(setup(count))
    await waitFor(() => expect(publicApi.register).toHaveBeenCalledTimes(1))
    expect(vi.mocked(publicApi.register).mock.calls[0][1].items).toEqual(
      Array.from({ length: count }, (_, index) => ({ eventDisciplineId: `discipline-${index}`, seedTimeMs: 32150 + index * 10 })),
    )
    expect(await screen.findByText('Заявка отправлена')).toBeInTheDocument()
  })

  it('QA03-02 blocks more than three disciplines', async () => {
    fireEvent.submit(setup(4))
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Выберите не больше трёх видов программы'))
    expect(publicApi.register).not.toHaveBeenCalled()
  })

  it('does not submit without consent', async () => {
    const element = setup()
    fireEvent.click(screen.getByLabelText('Я даю согласие на обработку персональных данных'))
    fireEvent.submit(element)
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Необходимо согласие'))
    expect(publicApi.register).not.toHaveBeenCalled()
  })

  it('preserves NT when the API allows an empty time', async () => {
    form.requiresSeedTime = false
    fireEvent.submit(setup(1, false))
    await waitFor(() => expect(publicApi.register).toHaveBeenCalledTimes(1))
    expect(vi.mocked(publicApi.register).mock.calls[0][1].items[0].seedTimeMs).toBeNull()
  })
  it('M06 hides submission when registration closes after the event page was loaded', () => {
    form.registrationOpen = false
    render(<MemoryRouter><PublicRegistrationPage /></MemoryRouter>)
    expect(screen.getByText('Регистрация закрыта.')).toBeInTheDocument()
    expect(screen.queryByLabelText('Фамилия')).not.toBeInTheDocument()
    expect(publicApi.register).not.toHaveBeenCalled()
  })

  it('M06 shows the waitlist notice while allowing submission when the event is full', async () => {
    form.maxParticipants = 1
    form.participantsCount = 1
    const element = setup()
    expect(screen.getByText(/Мест нет, запись в лист ожидания/)).toBeInTheDocument()
    fireEvent.submit(element)
    await waitFor(() => expect(publicApi.register).toHaveBeenCalledTimes(1))
  })

  it('M06 shows an age-group waitlist after entering the athlete birth date', () => {
    form.disciplines[0].ageGroups = [{ code: 'young', name: 'Младшие', gender: 'M', birthYearFrom: 2014, birthYearTo: 2015 }]
    form.disciplines[0].limitsByAgeGroup = { young: 1 }
    form.disciplines[0].participantsByAgeGroup = { young: 1 }
    render(<MemoryRouter><PublicRegistrationPage /></MemoryRouter>)
    expect(screen.queryByText('Лист ожидания')).not.toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Пол'), { target: { value: 'M' } })
  fireEvent.change(screen.getByLabelText('Дата рождения'), { target: { value: '2014-04-18' } })
    fireEvent.click(screen.getByRole('button', { name: /2 Виды и время/ }))
    expect(screen.getByText('Лист ожидания')).toBeInTheDocument()
  })

})

describe('D04-2g registration wizard', () => {
  beforeEach(() => {
    localStorage.clear()
    eventData.registrationCloseAt = null
    auth.isAuthenticated = true
    auth.isParticipant = true
    dataState.loading = false
    dataState.error = undefined
    dataState.errorStatus = undefined
    form.registrationOpen = true
    form.maxParticipants = null
    form.requiresSeedTime = true
    form.disciplines.forEach(item => { item.entryFee = 500; item.ageGroups = [] })
    vi.spyOn(publicApi, 'register').mockResolvedValue([])
  })

  it('validates the first step, shows a summary, and focuses the first invalid field', () => {
    render(<MemoryRouter><PublicRegistrationPage /></MemoryRouter>)
    fireEvent.click(screen.getByRole('button', { name: 'Далее: Виды и время' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Фамилия: Укажите фамилию спортсмена.')
    expect(screen.getByLabelText('Фамилия')).toHaveFocus()
    expect(screen.getByLabelText('Фамилия')).toHaveAttribute('aria-invalid', 'true')
    expect(publicApi.register).not.toHaveBeenCalled()
  })

  it('validates only the current step when the form is submitted with Enter', () => {
    const { container } = render(<MemoryRouter><PublicRegistrationPage /></MemoryRouter>)
    fireEvent.change(screen.getByLabelText('Фамилия'), { target: { value: 'Иванов' } })
    fireEvent.change(screen.getByLabelText('Имя'), { target: { value: 'Иван' } })
    fireEvent.change(screen.getByLabelText('Дата рождения'), { target: { value: '2014-04-18' } })
    fireEvent.change(screen.getByLabelText('Пол'), { target: { value: 'M' } })
    fireEvent.submit(container.querySelector('form')!)
    expect(screen.getByRole('heading', { name: '2. Виды и время' })).toBeVisible()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    fireEvent.submit(container.querySelector('form')!)
    expect(screen.getByRole('alert')).toHaveTextContent('Выберите хотя бы один вид программы.')
    expect(screen.getByRole('alert')).not.toHaveTextContent('Необходимо согласие')
    expect(publicApi.register).not.toHaveBeenCalled()
  })

  it('does not render empty metadata or a leading separator before the deadline', () => {
    dataState.loading = true
    const { container, rerender } = render(<MemoryRouter><PublicRegistrationPage /></MemoryRouter>)
    expect(container.querySelector('.fl-meta')).not.toBeInTheDocument()
    eventData.registrationCloseAt = '2026-10-31T12:00:00Z'
    rerender(<MemoryRouter><PublicRegistrationPage /></MemoryRouter>)
    expect(container.querySelector('.fl-meta')).toHaveTextContent(/^регистрация до /)
    expect(container.querySelector('.fl-meta')).not.toHaveTextContent('·')
    dataState.loading = false
    rerender(<MemoryRouter><PublicRegistrationPage /></MemoryRouter>)
    expect(container.querySelector('.fl-meta')).toHaveTextContent(/^Взнос указан у каждого вида программы · регистрация до /)
  })

  it('labels a zero fee as free in both the selected item and total', () => {
    form.disciplines.forEach(item => { item.entryFee = 0 })
    setup()
    const review = within(screen.getByRole('group', { name: 'Проверка заявки' }))
    expect(review.queryByText('0 ₽')).not.toBeInTheDocument()
    expect(review.getAllByText('Бесплатно')).toHaveLength(2)
  })

  it('keeps the selected card and time when going back and restores the step after remount', () => {
    const { unmount } = render(<MemoryRouter><PublicRegistrationPage /></MemoryRouter>)
    fireEvent.change(screen.getByLabelText('Фамилия'), { target: { value: 'Иванова' } })
    fireEvent.change(screen.getByLabelText('Имя'), { target: { value: 'Анна' } })
    fireEvent.change(screen.getByLabelText('Дата рождения'), { target: { value: '2014-04-18' } })
    fireEvent.change(screen.getByLabelText('Пол'), { target: { value: 'M' } })
    fireEvent.click(screen.getByRole('button', { name: 'Далее: Виды и время' }))
    const card = screen.getByRole('button', { name: /Вид 1 ·/ })
    fireEvent.click(card)
    fireEvent.change(screen.getByPlaceholderText('мм:сс.сс'), { target: { value: '003215' } })
    unmount()
    render(<MemoryRouter><PublicRegistrationPage /></MemoryRouter>)
    expect(screen.getByRole('button', { name: /Вид 1 ·/ })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByPlaceholderText('мм:сс.сс')).toHaveValue('00:32.15')
    expect(screen.getByRole('heading', { name: '2. Виды и время' })).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Назад' }))
    expect(screen.getByLabelText('Фамилия')).toHaveValue('Иванова')
  })

  it('shows the total fee and clears only this draft after successful submission', async () => {
    localStorage.setItem('sportgate.registration.other.test-user', 'keep')
    const element = setup(2)
    expect(screen.getByText('1 000 ₽', { exact: true })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Оплатить' })).not.toBeInTheDocument()
    fireEvent.submit(element)
    await screen.findByText('Заявка отправлена')
    expect(localStorage.getItem('sportgate.registration.qa03.test-user')).toBeNull()
    expect(localStorage.getItem('sportgate.registration.other.test-user')).toBe('keep')
    expect(vi.mocked(publicApi.register).mock.calls[0][1]).toEqual({
      profile: { lastName: 'Демов', firstName: 'Тимофей', middleName: '', birthDate: '2014-04-18', gender: 'M', club: '' },
      items: [{ eventDisciplineId: 'discipline-0', seedTimeMs: 32150 }, { eventDisciplineId: 'discipline-1', seedTimeMs: 32160 }],
    })
  })

  it('maps server item and profile errors to their fields and preserves the draft', async () => {
    vi.mocked(publicApi.register).mockRejectedValue(new PublicApiError(400, 'Проверьте данные', 'validation', [
      { field: 'profile.lastName', message: 'Проверьте фамилию' },
      { field: 'items[0].seedTimeMs', message: 'Недопустимое время' },
    ]))
    fireEvent.submit(setup())
    await waitFor(() => expect(screen.getByLabelText('Фамилия')).toHaveAttribute('aria-invalid', 'true'))
    expect(screen.getByLabelText('Фамилия')).toHaveFocus()
    expect(screen.getByRole('alert')).toHaveTextContent('Вид 1: Недопустимое время')
    expect(localStorage.getItem('sportgate.registration.qa03.test-user')).not.toBeNull()
  })

  it('shows a capacity warning without claiming the failed request succeeded', async () => {
    vi.mocked(publicApi.register).mockRejectedValue(new PublicApiError(409, 'Лимит мест исчерпан', 'registration.capacity_exceeded'))
    fireEvent.submit(setup())
    await screen.findByText('Мест нет, заявка попадёт в лист ожидания.')
    expect(screen.getByText('Лимит мест исчерпан')).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Заявка отправлена' })).not.toBeInTheDocument()
  })

  it('requires an explicit send after returning to review with consent already checked', async () => {
    setup()
    fireEvent.click(screen.getByRole('button', { name: 'Назад' }))
    fireEvent.click(screen.getByRole('button', { name: 'Далее: Проверка' }))
    expect(screen.getByRole('heading', { name: '3. Проверка' })).toBeVisible()
    expect(screen.getByLabelText('Я даю согласие на обработку персональных данных')).toBeChecked()
    expect(publicApi.register).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Отправить заявку' }))
    await waitFor(() => expect(publicApi.register).toHaveBeenCalledTimes(1))
    await screen.findByText('Заявка отправлена')
  })

  it('prevents a second request while submission is pending', async () => {
    let resolve!: (value: unknown) => void
    vi.mocked(publicApi.register).mockImplementation(() => new Promise(done => { resolve = done }))
    const element = setup()
    fireEvent.submit(element)
    fireEvent.submit(element)
    expect(publicApi.register).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: 'Отправить заявку' })).toBeDisabled()
    resolve([])
    await screen.findByText('Заявка отправлена')
  })

  it('tolerates malformed localStorage data', () => {
    localStorage.setItem('sportgate.registration.qa03.test-user', '{invalid')
    render(<MemoryRouter><PublicRegistrationPage /></MemoryRouter>)
    expect(screen.getByLabelText('Фамилия')).toHaveValue('')
  })

  it.each(['loading', 'empty', 'error'])('renders the %s state without a submission button', (state) => {
    const original = form.disciplines
    if (state === 'loading') dataState.loading = true
    if (state === 'empty') form.disciplines = []
    if (state === 'error') { dataState.error = 'Нет связи.'; dataState.errorStatus = 500 }
    render(<MemoryRouter><PublicRegistrationPage /></MemoryRouter>)
    if (state === 'loading') expect(screen.getByRole('status')).toHaveTextContent('Загружаем форму заявки')
    if (state === 'empty') expect(screen.getByText('Виды программы пока не добавлены')).toBeVisible()
    if (state === 'error') { expect(screen.getByRole('alert')).toHaveTextContent('Сервер ответил ошибкой (500)'); expect(screen.getByRole('button', { name: 'Повторить' })).toBeVisible() }
    expect(screen.queryByRole('button', { name: 'Отправить заявку' })).not.toBeInTheDocument()
    form.disciplines = original
  })

  it('shows login actions instead of athlete fields to anonymous visitors', () => {
    auth.isAuthenticated = false
    render(<MemoryRouter><PublicRegistrationPage /></MemoryRouter>)
    expect(screen.getByRole('link', { name: 'Войти' })).toHaveAttribute('href', '/login?next=%2Fe%2Fqa03%2Fregister')
    expect(screen.queryByLabelText('Фамилия')).not.toBeInTheDocument()
  })
})


describe('D04-2g draft lifetime', () => {
  const key = 'sportgate.registration.qa03.test-user'
  const now = Date.UTC(2026, 9, 24, 12)
  const week = 7 * 24 * 60 * 60 * 1000

  beforeEach(() => {
    localStorage.clear()
    vi.spyOn(Date, 'now').mockReturnValue(now)
  })

  it.each([undefined, now - week - 1])('discards a draft with missing or expired savedAt (%s)', (savedAt) => {
    localStorage.setItem(key, JSON.stringify({ ...emptyRegistrationDraft(), version: 1, savedAt,
      profile: { ...emptyRegistrationDraft().profile, lastName: 'Иванова' }, step: 2 }))
    expect(readRegistrationDraft(key)).toEqual(emptyRegistrationDraft())
  })

  it('restores a draft up to and including the seven-day boundary', () => {
    const draft = { ...emptyRegistrationDraft(), profile: { ...emptyRegistrationDraft().profile, lastName: 'Иванова' }, step: 2 }
    localStorage.setItem(key, JSON.stringify({ ...draft, version: 1, savedAt: now - week }))
    expect(readRegistrationDraft(key)).toEqual(draft)
  })
})

import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PublicRegistrationPage } from './RegistrationPage'
import { publicApi } from '../../api/public.api'
import type { RegistrationForm } from './public.types'

const { form } = vi.hoisted(() => ({
  form: {
    requiresSeedTime: true,
    disciplines: Array.from({ length: 4 }, (_, index) => ({
      id: `discipline-${index}`, name: `Вид ${index + 1}`, distanceM: 50,
      stroke: 'FREE', gender: 'M', ageGroups: [], entryFee: 0,
    })),
  } as RegistrationForm,
}))
vi.mock('../../app/providers/AuthProvider', () => ({
  useAuth: () => ({ isAuthenticated: true, isParticipant: true, session: { accessToken: 'test-token' } }),
}))
vi.mock('./publicContext', async (importOriginal) => ({
  ...await importOriginal<typeof import('./publicContext')>(),
  usePublicEvent: () => ({ publicSlug: 'qa03', registrationOpen: true }),
}))
vi.mock('./usePublicData', () => ({ usePublicData: () => ({ data: form }) }))

function setup(count = 1, fillTimes = true) {
  const view = render(<MemoryRouter><PublicRegistrationPage /></MemoryRouter>)
  fireEvent.change(screen.getByLabelText('Фамилия'), { target: { value: 'Демов' } })
  fireEvent.change(screen.getByLabelText('Имя'), { target: { value: 'Тимофей' } })
  fireEvent.change(screen.getByLabelText('Дата рождения'), { target: { value: '2014-04-18' } })
  for (let index = 0; index < count; index++) fireEvent.click(screen.getByLabelText(`Вид ${index + 1} · Бесплатно`))
  if (fillTimes) screen.getAllByPlaceholderText('мм:сс.сс').forEach((input, index) =>
    fireEvent.change(input, { target: { value: `0032${15 + index}` } }))
  fireEvent.click(screen.getByLabelText('Я даю согласие на обработку персональных данных (демо)'))
  return view.container.querySelector('form')!
}

describe('QA03 public registration submission', () => {
  beforeEach(() => {
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
    expect(screen.queryByText('Можно оставить пустым')).not.toBeInTheDocument()
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
    fireEvent.click(screen.getByLabelText('Я даю согласие на обработку персональных данных (демо)'))
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
    expect(screen.getByRole('status')).toHaveTextContent('Мест нет, запись в лист ожидания')
    fireEvent.submit(element)
    await waitFor(() => expect(publicApi.register).toHaveBeenCalledTimes(1))
  })

  it('M06 shows an age-group waitlist after entering the athlete birth date', () => {
    form.disciplines[0].ageGroups = [{ code: 'young', name: 'Младшие', gender: 'M', birthYearFrom: 2014, birthYearTo: 2015 }]
    form.disciplines[0].limitsByAgeGroup = { young: 1 }
    form.disciplines[0].participantsByAgeGroup = { young: 1 }
    render(<MemoryRouter><PublicRegistrationPage /></MemoryRouter>)
    expect(screen.queryByText(/Вид 1 · Мест нет/)).not.toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Дата рождения'), { target: { value: '2014-04-18' } })
    expect(screen.getByText(/Вид 1 · Мест нет, запись в лист ожидания/)).toBeInTheDocument()
  })

})

import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { http, HttpResponse } from 'msw'
import { expect, it } from 'vitest'
import { server } from '../../test/setup'
import { PublicOrganizationPage } from './PublicOrganizationPage'
import { demoOrganization } from '../../mocks/calendar-data'
function mount() { return render(<MemoryRouter initialEntries={['/o/volna']}><Routes><Route path="/o/:slug" element={<PublicOrganizationPage />} /></Routes></MemoryRouter>) }
it('shows organization contacts, event cards and page metadata', async () => {
  server.use(http.get('*/api/v1/public/organizations/:slug', () => HttpResponse.json(demoOrganization)))
  mount()
  expect(await screen.findByRole('heading', { level: 1, name: demoOrganization.name })).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Прошедших стартов пока нет' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Открыть календарь организации' })).toHaveAttribute('href', '/?organization=volna&status=past')
  await waitFor(() => expect(document.title).toBe(`${demoOrganization.name} · SportGate`))
})
it('retries after a server error and retains one main heading for empty data', async () => {
  let fail = true
  server.use(http.get('*/api/v1/public/organizations/:slug', () => fail ? HttpResponse.json({ message: 'Сервер недоступен' }, { status: 503 }) : HttpResponse.json({ ...demoOrganization, upcoming: [], past: [] })))
  mount()
  expect(await screen.findByRole('alert')).toBeInTheDocument()
  fail = false
  await userEvent.click(screen.getByRole('button', { name: 'Повторить' }))
  expect(await screen.findByRole('heading', { name: 'Ближайших стартов пока нет' })).toBeInTheDocument()
  expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
})
it('offers calendar recovery for an unpublished organization', async () => {
  server.use(http.get('*/api/v1/public/organizations/:slug', () => HttpResponse.json({}, { status: 404 })))
  mount()
  expect(await screen.findByRole('heading', { name: 'Организация не найдена' })).toBeInTheDocument()
  expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
  expect(screen.getByRole('link', { name: 'Найти соревнование' })).toHaveAttribute('href', '/')
})

import { render, screen } from '@testing-library/react'
import { MemoryRouter, Outlet, Route, Routes, useOutletContext } from 'react-router'
import { expect, it, vi } from 'vitest'
import { RequireRole } from './RequireRole'
vi.mock('../app/providers/AuthProvider', () => ({ useAuth: () => ({ isAuthenticated: true, authLoading: false, hasAnyRole: () => true }) }))
it('preserves the event outlet context across a nested role guard (UI-001)', () => {
  const context = { event: { title: 'Кубок SportGate' }, busy: false }
  function PaymentProbe() { const value = useOutletContext<typeof context>(); return <p>{value.event.title}</p> }
  render(<MemoryRouter initialEntries={['/payments']}><Routes><Route element={<Outlet context={context} />}><Route element={<RequireRole roles={['organizer']} />}><Route path="payments" element={<PaymentProbe />} /></Route></Route></Routes></MemoryRouter>)
  expect(screen.getByText('Кубок SportGate')).toBeInTheDocument()
})

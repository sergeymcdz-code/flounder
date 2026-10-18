import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route, Outlet } from 'react-router'
import { it, expect } from 'vitest'
import { demoEvent } from '../../mocks/demo-data'
import { ResultsPage } from './ResultsPage'
it('shows an unofficial warning in the document flow used for screen and print', async () => {
 render(<MemoryRouter><Routes><Route element={<Outlet context={{ event: demoEvent }} />}><Route index element={<ResultsPage />} /></Route></Routes></MemoryRouter>)
 const notice = screen.getByText(/Неофициальные результаты — публикуются сразу/)
 expect(notice).toHaveClass('fl-unofficial', 'fl-notice-warning')
 expect(notice.closest('[aria-hidden="true"]')).toBeNull()
 expect(notice.className).not.toMatch(/noPrint/)
 expect(await screen.findByRole('button', { name: 'По группам' })).toHaveClass('fl-btn-sm')
})

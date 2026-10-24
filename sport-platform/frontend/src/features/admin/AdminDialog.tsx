import type { ReactNode } from 'react'
import { OrganizerDialog } from '../organizer/OrganizerDialog'

export function AdminDialog({ title, children, busy, onClose }: {
  title: string; children: ReactNode; busy: boolean; onClose: () => void
}) {
  return <OrganizerDialog title={title} busy={busy} onClose={onClose}>{children}</OrganizerDialog>
}

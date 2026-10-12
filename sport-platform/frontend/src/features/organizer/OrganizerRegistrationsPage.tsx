import { Navigate, useParams } from 'react-router'

export function OrganizerRegistrationsPage() {
  const { eventId } = useParams()
  return <Navigate to={`/organizer/events/${encodeURIComponent(eventId ?? '')}/entries`} replace />
}

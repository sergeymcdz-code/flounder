import { Button } from '../../components/ui/Button'
import { QRCodeSVG } from 'qrcode.react'
import { usePublicEvent } from './publicContext'
import { date } from './format'
import './PosterPage.css'
export function PosterPage() {
  const event = usePublicEvent()
  const url = `${window.location.origin}/e/${encodeURIComponent(event.publicSlug)}`
  return <section className="fl-poster"><div className="fl-poster-controls"><Button variant="primary" onClick={() => window.print()}>Печатать QR-плакат A4</Button></div><h1>{event.title}</h1><p>{date(event.eventStartAt, event.venue?.timezone)} · {event.venue?.name}</p><QRCodeSVG value={url} size={227} level="M" marginSize={4} title="Открыть соревнование" /><p>Расписание · Табло · Результаты — наведите камеру</p><strong>sportgate.pro</strong></section>
}

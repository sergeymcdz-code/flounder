import { normalizeStatus, statusLabel, statusTone, type StatusKind } from './status-dictionary'
export function StatusBadge({ kind, code }: { kind: StatusKind; code: string }) {
  const tone = statusTone(kind, code)
  const live = ['LIVE', 'IN_PROGRESS'].includes(normalizeStatus(code))
  return <span className={`fl-status fl-status-${tone} fl-badge fl-badge-${tone}${live ? ' fl-badge-live' : ''}`}>{['success', 'danger'].includes(tone) && <span className="fl-badge-dot" aria-hidden="true" />}{statusLabel(kind, code)}</span>
}

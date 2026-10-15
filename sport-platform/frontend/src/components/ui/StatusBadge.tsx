import { statusLabel, statusTone, type StatusKind } from './status-dictionary'
export function StatusBadge({ kind, code }: { kind: StatusKind; code: string }) {
  return <span className={`fl-status fl-status-${statusTone(kind, code)}`}>{statusLabel(kind, code)}</span>
}

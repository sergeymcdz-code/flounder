/** Digit-only input uses MMSScc; explicit seconds and hour formats are also accepted. */
export function maskTime(value: string): string {
  if (!/^\d*$/.test(value) && !/^\d{2}:\d{0,4}(?:\.\d{0,2})?$/.test(value)) return value
  const digits = value.replace(/\D/g, '').slice(0, 6)
  return digits.slice(0, 2) + (digits.length > 2 ? ':' + digits.slice(2, 4) : '')
    + (digits.length > 4 ? '.' + digits.slice(4) : '')
}

export function normalizeTime(value: string): string | null {
  const text = value.trim()
  const match = /^(?:(?:(\d+):)?(\d+):)?(\d{1,2})[.,](\d{2})$/.exec(text)
  if (!match) return null
  const hours = Number(match[1] ?? 0)
  const minutes = Number(match[2] ?? 0)
  const seconds = Number(match[3])
  if (seconds > 59 || (match[1] !== undefined && minutes > 59)) return null
  return `${String(hours * 60 + minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}.${match[4]}`
}

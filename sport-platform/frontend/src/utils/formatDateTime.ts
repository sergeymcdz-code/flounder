export function formatDateTime(value: string | null | undefined, timeZone = 'UTC') {
  if (!value || !Number.isFinite(new Date(value).getTime())) return 'Не назначено'
  return new Intl.DateTimeFormat('ru-RU', { timeZone: timeZone?.trim() || 'UTC', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value)).replace(', ', ' ')
}

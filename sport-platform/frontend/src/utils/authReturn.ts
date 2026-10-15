export function authReturnTarget(search: string, stateFrom?: string, roles: string[] = []) {
  const normalized = roles.map(role => role.toLowerCase().replace('platformadmin', 'platform_admin'))
  const fallback = normalized.some(role => ['organizer', 'platform_admin', 'operator'].includes(role))
    ? '/organizer/events' : normalized.includes('judge') ? '/operator/results' : '/my/registrations'
  const next = new URLSearchParams(search).get('next')
  const candidate = next ?? stateFrom
  if (!candidate || !candidate.startsWith('/') || candidate.startsWith('//') || candidate.includes('\\') || /[\r\n]/.test(candidate)) return fallback
  return candidate
}

export function authReturnTarget(search: string, stateFrom?: string) {
  const next = new URLSearchParams(search).get('next')
  const candidate = next ?? stateFrom
  // Keep redirects inside this app, including when the URL was edited manually.
  if (
    !candidate ||
    !candidate.startsWith('/') ||
    candidate.startsWith('//') ||
    candidate.includes('\\')
  )
    return '/my/registrations'
  return candidate
}

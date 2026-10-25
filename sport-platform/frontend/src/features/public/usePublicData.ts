import { useCallback, useEffect, useState } from 'react'

export const LIVE_POLL_MS = 15_000
export const SCHEDULE_POLL_MS = 30_000
// Together with the public API cache TTL (10s), corrections appear within 15s.
export const RESULTS_POLL_MS = 5_000

export function usePublicData<T>(
  loader: (signal: AbortSignal) => Promise<T>,
  pollMs?: number,
  enabled = true,
) {
  const [state, setState] = useState<{
    owner: typeof loader
    data?: T
    error?: string
    errorStatus?: number
    loading: boolean
  }>({ owner: loader, loading: true })
  const [retryKey, setRetryKey] = useState(0)
  useEffect(() => {
    if (!enabled) return
    let disposed = false
    let running = false
    let controller: AbortController | undefined
    async function refresh() {
      if (running || (pollMs && document.hidden)) return
      running = true
      controller = new AbortController()
      try {
        const data = await loader(controller.signal)
        if (!disposed) setState({ owner: loader, data, loading: false })
      } catch (error) {
        if (!disposed && !controller.signal.aborted) {
          setState((previous) => ({
            ...(previous.owner === loader ? previous : { owner: loader }),
            loading: false,
            errorStatus:
              error instanceof Error &&
              'status' in error &&
              typeof error.status === 'number'
                ? error.status
                : undefined,
            error:
              error instanceof Error && error.name === 'PublicApiError'
                ? error.message
                : 'Нет связи. Не удалось обновить данные.',
          }))
        }
      } finally {
        running = false
      }
    }
    void refresh()
    const timer = pollMs
      ? window.setInterval(() => {
          void refresh()
        }, pollMs)
      : undefined
    function onVisibility() {
      if (!document.hidden) void refresh()
    }
    if (pollMs) document.addEventListener('visibilitychange', onVisibility)
    window.addEventListener('online', onVisibility)
    return () => {
      disposed = true
      controller?.abort()
      window.clearInterval(timer)
      document.removeEventListener('visibilitychange', onVisibility)
      window.removeEventListener('online', onVisibility)
    }
  }, [loader, pollMs, retryKey, enabled])
  const retry = useCallback(() => setRetryKey((key) => key + 1), [])
  return {
    data: enabled && state.owner === loader ? state.data : undefined,
    error: enabled && state.owner === loader ? state.error : undefined,
    errorStatus: enabled && state.owner === loader ? state.errorStatus : undefined,
    loading: enabled && (state.owner !== loader || state.loading),
    retry,
  }
}

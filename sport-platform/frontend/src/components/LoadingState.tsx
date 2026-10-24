export function LoadingState({ message = 'Загружаем данные…' }: { message?: string }) {
  return <div className="fl-loading-state" role="status" aria-live="polite"><span>{message}</span><div aria-hidden="true" className="fl-skeleton">{[0, 1, 2].map(row => <div key={row} />)}</div></div>
}

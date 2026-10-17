export function StepIcon({ state }: { state: 'done' | 'current' | 'pending' }) {
  return <svg width="20" height="20" viewBox="0 0 20 20" role="img" aria-label={state === 'done' ? 'выполнено' : state === 'current' ? 'текущий шаг' : 'не начато'} className={`fl-step-icon fl-step-${state}`}>
    {state === 'done' ? <path d="m4 10 4 4 8-8" fill="none" stroke="currentColor" strokeWidth="2" /> : <circle cx="10" cy="10" r="6" fill={state === 'current' ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="2" />}
  </svg>
}
export function MenuIcon() { return <svg width="24" height="24" viewBox="0 0 24 24" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16" stroke="currentColor" strokeWidth="2" /></svg> }
export function StarIcon({ filled }: { filled: boolean }) { return <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true"><path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9Z" fill={filled ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="1.5" /></svg> }

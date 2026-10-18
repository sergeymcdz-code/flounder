import { createContext, useContext, useEffect, useState, type PropsWithChildren } from 'react'
import { Link } from 'react-router'
type ToastMessage = { text: string; href?: string; tone?: 'success' | 'danger' }
const Context = createContext<(message: ToastMessage) => void>(() => {})
export function ToastProvider({ children }: PropsWithChildren) {
  const [toast, setToast] = useState<ToastMessage | null>(null)
  useEffect(() => { if (!toast) return; const timer = setTimeout(() => setToast(null), 5000); return () => clearTimeout(timer) }, [toast])
  return <Context.Provider value={setToast}>{children}<div role="status" aria-live="polite" className={toast ? `fl-toast fl-toast-${toast.tone ?? 'success'}` : undefined}>{toast && <>{toast.text} {toast.href && <Link to={toast.href}>Открыть публичную страницу ↗</Link>}</>}</div></Context.Provider>
}
export const useToast = () => useContext(Context)

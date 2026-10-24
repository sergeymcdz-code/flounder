import { createContext, useContext, useEffect, type ReactNode } from 'react'

export const HeaderContext = createContext<((value: ReactNode) => void) | null>(null)
export function useHeaderContext(value: ReactNode) {
  const setContext = useContext(HeaderContext)
  useEffect(() => { setContext?.(value); return () => setContext?.(null) }, [setContext, value])
}

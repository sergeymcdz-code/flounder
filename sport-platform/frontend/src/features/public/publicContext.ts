import { createContext } from 'react'
import { useOutletContext } from 'react-router'
import type { PublicEvent } from './public.types'

export const PublicConnectionContext = createContext(false)

export function usePublicEvent() {
  return useOutletContext<{ event: PublicEvent }>().event
}

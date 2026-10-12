import { useOutletContext } from 'react-router'
import type { PublicEvent } from './public.types'

export function usePublicEvent() {
  return useOutletContext<{ event: PublicEvent }>().event
}

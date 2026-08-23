/**
 * Search configuration — one service instance for the whole app.
 *
 * The service is created once and kept in a ref. Swapping the provider from
 * Settings must not tear down the screen that is using it, and the fault
 * injector must read the *current* setting rather than the one captured when the
 * provider was constructed — hence the getter passed into MockProvider.
 *
 * Nothing above this file knows which provider is installed (§11).
 */

import { createContext, useContext, useMemo, useRef, useState, type ReactNode } from 'react'
import { LocationSearchService } from './search/LocationSearchService'
import { MockProvider, type Fault } from './search/MockProvider'
import { NominatimProvider } from './search/NominatimProvider'

export type ProviderKind = 'osm' | 'mock'

export const FAULTS: readonly Fault[] = [
  'none',
  'empty',
  'network',
  'timeout',
  'rate-limited',
  'provider',
  'malformed',
]

export const FAULT_LABEL: Record<Fault, string> = {
  none: 'None',
  empty: 'No results',
  network: 'Offline',
  timeout: 'Timeout',
  'rate-limited': 'Rate limit',
  provider: 'Outage',
  malformed: 'Bad data',
}

export interface SearchConfig {
  service: LocationSearchService
  providerKind: ProviderKind
  providerName: string
  setProviderKind: (kind: ProviderKind) => void
  /** Prototype-only: forces a failure so every error state can be designed. */
  fault: Fault
  setFault: (fault: Fault) => void
}

const SearchContext = createContext<SearchConfig | null>(null)

export function SearchConfigProvider({ children }: { children: ReactNode }) {
  // Default to the offline provider: the design work must not depend on a
  // network, and a live geocoder makes the loading states unrepeatable.
  const [providerKind, setKind] = useState<ProviderKind>('mock')
  const [fault, setFault] = useState<Fault>('none')

  const faultRef = useRef(fault)
  faultRef.current = fault

  const providers = useRef<{ osm: NominatimProvider; mock: MockProvider } | null>(null)
  if (!providers.current) {
    providers.current = {
      osm: new NominatimProvider(),
      mock: new MockProvider(() => faultRef.current),
    }
  }

  const serviceRef = useRef<LocationSearchService | null>(null)
  if (!serviceRef.current) {
    serviceRef.current = new LocationSearchService(providers.current[providerKind])
  }
  const service = serviceRef.current

  const value = useMemo<SearchConfig>(
    () => ({
      service,
      providerKind,
      providerName: service.providerName,
      setProviderKind: (kind) => {
        service.cancel()
        service.setProvider(providers.current![kind])
        setKind(kind)
      },
      fault,
      setFault,
    }),
    [service, providerKind, fault],
  )

  return <SearchContext.Provider value={value}>{children}</SearchContext.Provider>
}

export function useSearchConfig(): SearchConfig {
  const ctx = useContext(SearchContext)
  if (!ctx) throw new Error('useSearchConfig must be used inside <SearchConfigProvider>')
  return ctx
}

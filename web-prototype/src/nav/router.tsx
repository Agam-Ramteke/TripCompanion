/**
 * Screen stack.
 *
 * Deliberately not a URL router: this prototype models an Android app, and the
 * thing worth getting right is the back stack — including "open a screen and
 * wait for what it returns", which is how the location flow has to work
 * (search → map → confirm → back to the editor with a place).
 *
 * Frames below the top stay mounted so a half-written event survives the trip
 * out to search and back. Losing a draft to a navigation is the exact failure
 * §14 is trying to avoid.
 */

import {
  createContext,
  useContext,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import type { IsoDate, SearchResultLocation } from '../domain/types'

export type Route =
  | { name: 'trips' }
  | { name: 'home' }
  | { name: 'timeline'; focusDate?: IsoDate }
  | { name: 'event'; eventId: number }
  | { name: 'editor'; tripId: number; eventId?: number; date?: IsoDate }
  | { name: 'tripEditor'; tripId?: number }
  | { name: 'search'; initialQuery?: string }
  /** `zoom` is passed only when the caller has no confident coordinate yet. */
  | { name: 'map'; place: SearchResultLocation; zoom?: number }
  | { name: 'photos'; eventId: number }
  | { name: 'settings' }

export type RouteName = Route['name']

interface Frame {
  key: number
  route: Route
  resolve?: (value: unknown) => void
}

export interface Nav {
  stack: readonly Frame[]
  push: (route: Route) => void
  /** Open a screen and wait for its result; resolves null if it is dismissed. */
  pushForResult: <T>(route: Route) => Promise<T | null>
  pop: (result?: unknown) => void
  /** Replace the whole stack — used by the lab's jump list and by trip switching. */
  reset: (routes: Route[]) => void
  popTo: (name: RouteName) => void
}

const NavContext = createContext<Nav | null>(null)

export function NavProvider({ initial, children }: { initial: Route[]; children: ReactNode }) {
  const nextKey = useRef(1)
  const [stack, setStack] = useState<Frame[]>(() =>
    initial.map((route) => ({ key: nextKey.current++, route })),
  )

  const nav = useMemo<Nav>(() => {
    const frame = (route: Route, resolve?: (value: unknown) => void): Frame => ({
      key: nextKey.current++,
      route,
      resolve,
    })

    return {
      stack,
      push: (route) => setStack((s) => [...s, frame(route)]),

      pushForResult: <T,>(route: Route) =>
        new Promise<T | null>((resolve) => {
          setStack((s) => [...s, frame(route, (value) => resolve((value ?? null) as T | null))])
        }),

      pop: (result) =>
        setStack((s) => {
          if (s.length <= 1) return s
          const top = s[s.length - 1]
          top?.resolve?.(result)
          return s.slice(0, -1)
        }),

      reset: (routes) => {
        // Anything waiting on a result will never get one; tell it so.
        setStack((s) => {
          s.forEach((f) => f.resolve?.(null))
          return routes.map((route) => frame(route))
        })
      },

      popTo: (name) =>
        setStack((s) => {
          const index = s.findIndex((f) => f.route.name === name)
          if (index < 0 || index === s.length - 1) return s
          s.slice(index + 1).forEach((f) => f.resolve?.(null))
          return s.slice(0, index + 1)
        }),
    }
  }, [stack])

  return <NavContext.Provider value={nav}>{children}</NavContext.Provider>
}

export function useNav(): Nav {
  const ctx = useContext(NavContext)
  if (!ctx) throw new Error('useNav must be used inside <NavProvider>')
  return ctx
}

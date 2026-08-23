/**
 * Local-first store — spec §26.
 *
 * The prototype's stand-in for Room + DataStore. Everything lives in memory,
 * mirrored to localStorage on every change, so the app survives a reload the
 * way the Android build must survive process death. No network is required to
 * read anything back.
 */

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useReducer,
  type ReactNode,
} from 'react'
import type { Event, PlannedPhoto, SearchResultLocation, Trip, TripLocation } from '../domain/types'
import { SEED_EVENTS, SEED_LOCATIONS, SEED_PHOTOS, SEED_TRIPS } from './seed'

export type ThemeName = 'nothing' | 'brutalist'

const STORAGE_KEY = 'trip-companion-prototype/v1'

export interface StoreState {
  trips: Trip[]
  events: Event[]
  locations: TripLocation[]
  photos: PlannedPhoto[]
  selectedTripId: number | null
  theme: ThemeName
  nextId: number
}

const seedState = (): StoreState => ({
  trips: SEED_TRIPS,
  events: SEED_EVENTS,
  locations: SEED_LOCATIONS,
  photos: SEED_PHOTOS,
  selectedTripId: SEED_TRIPS[0]?.id ?? null,
  theme: 'nothing',
  nextId: 1000,
})

type Action =
  | { type: 'reset' }
  | { type: 'setTheme'; theme: ThemeName }
  | { type: 'selectTrip'; tripId: number | null }
  | { type: 'saveTrip'; trip: Omit<Trip, 'id'> & { id?: number } }
  | { type: 'deleteTrip'; tripId: number }
  | { type: 'saveEvent'; event: Omit<Event, 'id'> & { id?: number } }
  | { type: 'deleteEvent'; eventId: number }
  | { type: 'setEventStatus'; eventId: number; status: Event['status'] }
  | { type: 'confirmLocation'; result: SearchResultLocation; id: number }
  | { type: 'savePhoto'; photo: Omit<PlannedPhoto, 'id'> & { id?: number } }
  | { type: 'deletePhoto'; photoId: number }

function reducer(state: StoreState, action: Action): StoreState {
  switch (action.type) {
    case 'reset':
      return seedState()

    case 'setTheme':
      return { ...state, theme: action.theme }

    case 'selectTrip':
      return { ...state, selectedTripId: action.tripId }

    case 'saveTrip': {
      if (action.trip.id != null) {
        const id = action.trip.id
        return {
          ...state,
          trips: state.trips.map((t) => (t.id === id ? ({ ...t, ...action.trip, id } as Trip) : t)),
        }
      }
      const id = state.nextId
      const trip = { ...action.trip, id } as Trip
      return { ...state, trips: [...state.trips, trip], selectedTripId: id, nextId: id + 1 }
    }

    case 'deleteTrip': {
      const events = state.events.filter((e) => e.tripId !== action.tripId)
      const removedEventIds = new Set(
        state.events.filter((e) => e.tripId === action.tripId).map((e) => e.id),
      )
      const trips = state.trips.filter((t) => t.id !== action.tripId)
      return {
        ...state,
        trips,
        events,
        photos: state.photos.filter((p) => !removedEventIds.has(p.eventId)),
        selectedTripId:
          state.selectedTripId === action.tripId ? (trips[0]?.id ?? null) : state.selectedTripId,
      }
    }

    case 'saveEvent': {
      if (action.event.id != null) {
        const id = action.event.id
        return {
          ...state,
          events: state.events.map((e) =>
            e.id === id ? ({ ...e, ...action.event, id } as Event) : e,
          ),
        }
      }
      const id = state.nextId
      return {
        ...state,
        events: [...state.events, { ...action.event, id } as Event],
        nextId: id + 1,
      }
    }

    case 'deleteEvent':
      return {
        ...state,
        events: state.events.filter((e) => e.id !== action.eventId),
        photos: state.photos.filter((p) => p.eventId !== action.eventId),
      }

    case 'setEventStatus':
      return {
        ...state,
        events: state.events.map((e) =>
          e.id === action.eventId ? { ...e, status: action.status } : e,
        ),
      }

    case 'confirmLocation': {
      const { result, id } = action
      const fields = {
        name: result.name,
        address: result.formattedAddress,
        latitude: result.latitude,
        longitude: result.longitude,
        category: result.category,
        providerPlaceId: result.providerPlaceId,
        providerName: result.providerName,
      }
      const exists = state.locations.some((l) => l.id === id)
      return {
        ...state,
        locations: exists
          ? state.locations.map((l) => (l.id === id ? { ...l, ...fields } : l))
          : [...state.locations, { id, ...fields }],
        nextId: Math.max(state.nextId, id + 1),
      }
    }

    case 'savePhoto': {
      if (action.photo.id != null) {
        const id = action.photo.id
        return {
          ...state,
          photos: state.photos.map((p) =>
            p.id === id ? ({ ...p, ...action.photo, id } as PlannedPhoto) : p,
          ),
        }
      }
      const id = state.nextId
      return {
        ...state,
        photos: [...state.photos, { ...action.photo, id } as PlannedPhoto],
        nextId: id + 1,
      }
    }

    case 'deletePhoto':
      return { ...state, photos: state.photos.filter((p) => p.id !== action.photoId) }

    default:
      return state
  }
}

function load(): StoreState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return seedState()
    const parsed = JSON.parse(raw) as Partial<StoreState>
    const base = seedState()
    return {
      trips: parsed.trips ?? base.trips,
      events: parsed.events ?? base.events,
      locations: parsed.locations ?? base.locations,
      photos: parsed.photos ?? base.photos,
      selectedTripId: parsed.selectedTripId ?? base.selectedTripId,
      theme: parsed.theme === 'brutalist' ? 'brutalist' : 'nothing',
      nextId: parsed.nextId ?? base.nextId,
    }
  } catch {
    // A corrupt or unreadable store must not brick the app — fall back to seed.
    return seedState()
  }
}

// ── Context ──────────────────────────────────────────────────────────────────

export interface StoreApi extends StoreState {
  /** The trip the app is currently pointed at. */
  selectedTrip: Trip | null
  eventsForTrip: (tripId: number) => Event[]
  locationById: (id: number | null | undefined) => TripLocation | null
  eventById: (id: number | null | undefined) => Event | null
  photosForEvent: (eventId: number) => PlannedPhoto[]

  reset: () => void
  setTheme: (theme: ThemeName) => void
  selectTrip: (tripId: number | null) => void
  saveTrip: (trip: Omit<Trip, 'id'> & { id?: number }) => void
  deleteTrip: (tripId: number) => void
  saveEvent: (event: Omit<Event, 'id'> & { id?: number }) => void
  deleteEvent: (eventId: number) => void
  setEventStatus: (eventId: number, status: Event['status']) => void
  /**
   * Persist a confirmed place and return its id, so the caller can attach it to
   * an event immediately. Re-confirming the same provider place updates it in
   * place instead of accumulating duplicates.
   */
  confirmLocation: (result: SearchResultLocation, reuseId?: number) => number
  savePhoto: (photo: Omit<PlannedPhoto, 'id'> & { id?: number }) => void
  deletePhoto: (photoId: number) => void
}

const StoreContext = createContext<StoreApi | null>(null)

export function StoreProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(reducer, undefined, load)

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
    } catch {
      // Quota exceeded (usually a large pasted image). The in-memory state is
      // still correct for this session; surfacing a dialog here would be noise.
    }
  }, [state])

  const api = useMemo<StoreApi>(() => {
    const locationById = (id: number | null | undefined) =>
      id == null ? null : state.locations.find((l) => l.id === id) ?? null

    return {
      ...state,
      selectedTrip: state.trips.find((t) => t.id === state.selectedTripId) ?? null,
      eventsForTrip: (tripId: number) => state.events.filter((e) => e.tripId === tripId),
      locationById,
      eventById: (id) => (id == null ? null : state.events.find((e) => e.id === id) ?? null),
      photosForEvent: (eventId: number) =>
        state.photos.filter((p) => p.eventId === eventId).sort((a, b) => a.order - b.order),

      reset: () => dispatch({ type: 'reset' }),
      setTheme: (theme) => dispatch({ type: 'setTheme', theme }),
      selectTrip: (tripId) => dispatch({ type: 'selectTrip', tripId }),
      saveTrip: (trip) => dispatch({ type: 'saveTrip', trip }),
      deleteTrip: (tripId) => dispatch({ type: 'deleteTrip', tripId }),
      saveEvent: (event) => dispatch({ type: 'saveEvent', event }),
      deleteEvent: (eventId) => dispatch({ type: 'deleteEvent', eventId }),
      setEventStatus: (eventId, status) => dispatch({ type: 'setEventStatus', eventId, status }),
      confirmLocation: (result, reuseId) => {
        const existing =
          (reuseId != null ? state.locations.find((l) => l.id === reuseId) : undefined) ??
          (result.providerPlaceId
            ? state.locations.find((l) => l.providerPlaceId === result.providerPlaceId)
            : undefined)
        // The id is chosen here rather than in the reducer so it can be returned
        // to the caller; the reducer treats it as given.
        const id = existing?.id ?? state.nextId
        dispatch({ type: 'confirmLocation', result, id })
        return id
      },
      savePhoto: (photo) => dispatch({ type: 'savePhoto', photo }),
      deletePhoto: (photoId) => dispatch({ type: 'deletePhoto', photoId }),
    }
  }, [state])

  return <StoreContext.Provider value={api}>{children}</StoreContext.Provider>
}

export function useStore(): StoreApi {
  const ctx = useContext(StoreContext)
  if (!ctx) throw new Error('useStore must be used inside <StoreProvider>')
  return ctx
}

// ── Simulated clock ─────────────────────────────────────────────────────────
/**
 * The state engine is a function of the clock (§21), so the prototype needs to
 * be able to move the clock. Without this, five of the six statuses are
 * unreachable and the HUD can't be evaluated at all.
 */

export interface ClockApi {
  now: string
  live: boolean
  setNow: (iso: string) => void
  setLive: (live: boolean) => void
}

const ClockContext = createContext<ClockApi | null>(null)

export function ClockProvider({ value, children }: { value: ClockApi; children: ReactNode }) {
  return <ClockContext.Provider value={value}>{children}</ClockContext.Provider>
}

export function useClock(): ClockApi {
  const ctx = useContext(ClockContext)
  if (!ctx) throw new Error('useClock must be used inside <ClockProvider>')
  return ctx
}

/** Convenience: the current simulated time only. */
export function useNow(): string {
  return useClock().now
}

/** Stable callback helper used by screens that mutate then navigate. */
export function useCommit<T extends unknown[]>(fn: (...args: T) => void): (...args: T) => void {
  return useCallback(fn, [fn])
}

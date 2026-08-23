/**
 * The app inside the phone.
 *
 * Renders the whole nav stack rather than only the top screen: every frame stays
 * mounted so a half-written event survives a trip out to search and back (§14).
 * Only the top frame is visible.
 */

import type { Route } from './nav/router'
import { useNav } from './nav/router'
import { TripListScreen } from './screens/TripListScreen'
import { HomeScreen } from './screens/HomeScreen'
import { TimelineScreen } from './screens/TimelineScreen'
import { EventDetailScreen } from './screens/EventDetailScreen'
import { EventEditorScreen } from './screens/EventEditorScreen'
import { TripEditorScreen } from './screens/TripEditorScreen'
import { LocationSearchScreen } from './screens/LocationSearchScreen'
import { MapConfirmScreen } from './screens/MapConfirmScreen'
import { PhotoPlanScreen } from './screens/PhotoPlanScreen'
import { SettingsScreen } from './screens/SettingsScreen'

function renderRoute(route: Route) {
  switch (route.name) {
    case 'trips':
      return <TripListScreen />
    case 'home':
      return <HomeScreen />
    case 'timeline':
      return <TimelineScreen focusDate={route.focusDate} />
    case 'event':
      return <EventDetailScreen eventId={route.eventId} />
    case 'editor':
      return (
        <EventEditorScreen tripId={route.tripId} eventId={route.eventId} date={route.date} />
      )
    case 'tripEditor':
      return <TripEditorScreen tripId={route.tripId} />
    case 'search':
      return <LocationSearchScreen initialQuery={route.initialQuery} />
    case 'map':
      return <MapConfirmScreen place={route.place} zoom={route.zoom} />
    case 'photos':
      return <PhotoPlanScreen eventId={route.eventId} />
    case 'settings':
      return <SettingsScreen />
  }
}

/**
 * `data-theme` is deliberately *not* set here — the harness owns it, so the phone
 * frame's own chrome (the status bar) sits inside the same theme as the app.
 */
export function App() {
  const nav = useNav()
  const top = nav.stack.length - 1
  return (
    <div className="app">
      {nav.stack.map((frame, index) => (
        <div key={frame.key} className="app__frame" data-top={index === top}>
          {renderRoute(frame.route)}
        </div>
      ))}
    </div>
  )
}

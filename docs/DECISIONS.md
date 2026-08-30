# DECISIONS.md

Architecture Decision Records for Trip Companion. Each entry records a decision that is **visible in
the code or the product spec** and would be expensive or risky to reverse. Where the *reasoning*
isn't recorded anywhere in the repo, that is stated rather than guessed — this file is memory, not
invention. "Evidence" points at where the decision is enforced so a future session can verify it
still holds.

Format: Decision · Status · Context/why · Evidence · Consequences.

---

## ADR-001 — Native Android + Jetpack Compose (not cross-platform)

- **Status:** Accepted, in force.
- **Context:** The app is built as a single-module native Android app in Kotlin with Compose/M3,
  targeting Android only. Deep platform integration (osmdroid, PDF text extraction, photo picker,
  app-private storage) is used throughout.
- **Evidence:** `app/build.gradle.kts`, `gradle/libs.versions.toml`, all UI under `ui/` is Compose.
- **Consequences:** No iOS/web from this codebase; a separate web prototype exists but is not this
  app. Reversing would be a rewrite.

## ADR-002 — Local-first, offline-capable; no backend of our own

- **Status:** Accepted, in force.
- **Context:** A trip must be fully usable with no network (airplane mode on a train). All trip data
  lives in Room + app-private files; the only network calls are optional enrichment (live train
  status, geocoding, map tiles) that degrade gracefully when absent.
- **Evidence:** Media.kt KDoc ("what makes it work in airplane mode"); no server code; providers all
  have offline fallbacks; `ScheduleProjectionTrainStatusProvider` exists precisely for offline.
- **Consequences:** No cross-device sync except manual trip export/import (`data/transfer`). Every
  external call must have a no-network path.

## ADR-003 — Clean layered architecture with a hard "§11 boundary"

- **Status:** Accepted, in force. **A constraint, not a preference.**
- **Context:** HTTP/JSON/vendor SDK/API-key details must never leak above `data/`. External
  capabilities are exposed to the app as domain **ports** (interfaces) returning domain values; the
  concrete adapter lives in `data/` and is chosen by a Hilt module.
- **Evidence:** `domain/service/*` ports vs `data/network/*` impls; `TrainStatusProvider`,
  `PnrLookupService`, `LocationSearchService` all follow this; product spec §11/§18.
- **Consequences:** Adding an external feature means: define/extend a port in `domain`, implement in
  `data`, bind in `di`. More indirection, but vendors are swappable (see ADR-008) and the domain is
  unit-testable without Android.

## ADR-004 — Generic trip engine: only `event.type` / `location.category` drive logic

- **Status:** Accepted, in force. **A constraint.**
- **Context:** The app is a *generic* trip companion; the Udaipur/Agra content is sample data, not
  the product. No business logic may branch on a specific place name.
- **Evidence:** Spec §3/§18; place-specific strings appear only in
  `data/SampleTripSeeder.kt`. Any `if (name == "Udaipur")` elsewhere is a defect.
- **Consequences:** Features must generalize across trip types. New behavior keys off `EventType` /
  location category, never identity.

## ADR-005 — Current trip state is computed, never persisted

- **Status:** Accepted, in force.
- **Context:** "What's happening now / next" is derived from events + the clock by
  `TripStateEngine`, not stored, so it can't drift out of sync with the timeline or the current time.
- **Evidence:** `domain/engine/TripStateEngine`; `core/time/TimeProvider` injected as the clock;
  spec §5/§14.
- **Consequences:** State is always consistent with data + now; the clock must be injected (not
  `LocalDateTime.now()` inline) so state is testable.

## ADR-006 — Explicit Room migrations; **no** destructive fallback

- **Status:** Accepted, in force. **A constraint.**
- **Context:** A hand-entered trip plan is not disposable user data; an unhandled schema version must
  fail loudly rather than silently wipe the database.
- **Evidence:** `DatabaseModule` comment ("a trip plan the user typed by hand is not disposable");
  `Migrations.ALL` with explicit `MIGRATION_1_2..5_6`; schemas exported under `app/schemas/`;
  instrumented `MigrationTest`. **No `fallbackToDestructiveMigration` anywhere.**
- **Consequences:** Every schema change needs a real migration + exported schema JSON + a migration
  test. This holds **even during testing**, when the developer's own current data is disposable —
  the shipping policy is what's being protected.

## ADR-007 — Two swappable train-status providers (live vs. offline projection)

- **Status:** Accepted, in force.
- **Context:** Live tracking needs a paid API and a network; the app must still show a sensible
  moving position without either. So there are two `TrainStatusProvider`s and a policy layer over
  them.
- **Evidence:** `RailRadarTrainStatusProvider` (`isLive=true`) vs
  `ScheduleProjectionTrainStatusProvider` (`isLive=false`); `TrainModule` selects by whether the API
  key is non-blank; `DefaultTrainStatusService` owns caching/TTL/timeout policy.
- **Consequences:** UI reads `isLive` to decide what to promise the user; the projection is the
  guaranteed floor. Cache TTL 2 min / timeout 12 s live in one place.

## ADR-008 — RailRadar replaces the `indianrailapi.com` vendor; app is TLS-only

- **Status:** Accepted, in force.
- **Context:** The prior vendor used key-in-URL-path auth and a different JSON shape and *required a
  cleartext exception*. RailRadar uses header auth (`X-API-Key`), a `{success,data,meta}` envelope,
  and is HTTPS/TLS-only — so switching also let the app drop its last cleartext allowance.
- **Evidence:** `data/network/railradar/*`; deleted `IndianRailApi*`; `network_security_config.xml`
  now `cleartextTrafficPermitted="false"` everywhere.
- **Consequences:** Do **not** re-add the old vendor or any cleartext exception.

## ADR-009 — Images are app-private `file://` URIs only (no remote image URLs)

- **Status:** Accepted, in force.
- **Context:** Photos are copied into app-private storage on pick, so they survive offline, aren't
  subject to link rot, and carry no external fetch. There are no remote image URLs in the app.
- **Evidence:** Media.kt KDoc; `ImageStorageHelper.saveImageToInternalStorage`; Coil is fed
  `file://` paths; image-picker screens copy on pick.
- **Consequences:** Every "set a photo" flow must copy via `ImageStorageHelper` and store the
  returned path. Trip export must bundle the image files (`data/transfer`).

## ADR-010 — Maps via osmdroid / OpenStreetMap (no API key, no Google Maps)

- **Status:** Accepted, in force.
- **Context:** Map display and geocoding use OpenStreetMap (osmdroid tiles + Nominatim), which need
  no API key or billing account and cache tiles locally.
- **Evidence:** `osmdroid 6.1.20` in `libs.versions.toml`; `ui/components/OsmMap`;
  `NominatimLocationSearchProvider`.
- **Consequences:** Respect OSM/Nominatim usage policy (user-agent, rate). No Play Services maps
  dependency.

## ADR-011 — Expected failures cross the boundary as classified values, not exceptions

- **Status:** Accepted, in force.
- **Context:** Network/parse failures are normal, not exceptional, and the UI needs to say *which*
  failure to the user. So providers return sealed `*Outcome` / classified `*Error` enums; HTTP
  exceptions never propagate above `data/`.
- **Evidence:** `TrainStatusOutcome`/`TrainStatusError`, `TrainScheduleOutcome`,
  `PnrLookupOutcome`/`PnrLookupError`.
- **Consequences:** New provider methods follow the same shape. UI maps enum → message; it never
  catches an `IOException`.

## ADR-012 — Deleting a train must not delete its `JOURNEY` event

- **Status:** Accepted, in force. **A constraint / known footgun.**
- **Context:** A train booking and its timeline `JOURNEY` event are linked but independently
  meaningful; removing the booking must leave the trip's timeline intact.
- **Evidence:** `JourneyEventLinker`; recorded in [CURRENT_STATE.md](CURRENT_STATE.md) "Do Not Redo".
- **Consequences:** Any cascade/delete work on trains must preserve the linked event.

## ADR-013 — Single-Activity Compose navigation with string args parsed defensively

- **Status:** Accepted, in force.
- **Context:** One Activity, one `NavHost`; all route args are strings parsed with `toLongOrNull`,
  and cross-screen results return via the caller's `SavedStateHandle` rather than route args.
- **Evidence:** `ui/navigation/Routes.kt` + `AppNavHost`; `Routes.LocationPicker.RESULT_LOCATION_ID`.
- **Consequences:** **Route argument names are load-bearing** — renaming one without updating the
  matching `SavedStateHandle` read breaks that screen silently (lands on empty state, no crash).

## ADR-014 — Keyed pastel basemap with graceful CARTO fallback; tiles stay UI-map, not a domain port

- **Status:** Accepted, in force.
- **Context:** Low-contrast basemaps (`positron` / `dark-matter` / `dataviz`) allow routes and markers
  to remain the primary subject without visual distraction.
- **Evidence:** `app/build.gradle.kts`, `OsmMap.kt`.
- **Consequences:** The map style recedes and emphasizes itinerary waypoints.

## ADR-015 — Device location via the framework `LocationManager` (no Google Play Services)

- **Status:** Accepted, in force.
- **Context:** The map needs GPS fixes without proprietary Google Play Services dependencies.
- **Evidence:** `domain/service/DeviceLocationProvider.kt`, `data/location/AndroidDeviceLocationProvider.kt`.
- **Consequences:** Zero Play Services lock-in. Works offline and on AOSP/microG systems.

## ADR-016 — Road routing via OpenRouteService behind a `RoutePlanService` port; straight-line fallback

- **Status:** Accepted, in force.
- **Context:** Real road travel distance/time gives an honest itinerary.
- **Evidence:** `domain/service/RoutePlanService.kt`, `DefaultRoutePlanService.kt`.
- **Consequences:** Failed routes degrade gracefully to haversine straight lines.

## ADR-017 — Numbered status markers derived from `TripStateEngine`

- **Status:** Accepted, in force.
- **Context:** Stop order matches sheet card `orderInDay`. State is derived from `TripStateEngine`.
- **Evidence:** `OsmMap.kt`, `TeardropPinMarker.kt`.

## ADR-018 — External turn-by-turn via `ExternalNavigator` Intent; no in-app navigation

- **Status:** Accepted, in force.
- **Context:** Hands off turn-by-turn directions to external maps apps.
- **Evidence:** `core/util/ExternalNavigator.kt`.

## ADR-019 — Map top label reads "Day N · <trip name>"

- **Status:** Accepted, in force.
- **Context:** Floating pill provides consistent trip orientation without reverse-geocoding latency.
- **Evidence:** `TripMapScreen.kt`.

## ADR-020 — Illustrated Teardrop Pin Badges with Floating Text Halos

- **Status:** Accepted, in force (2026-08-27).
- **Context:** Visualizing an itinerary map requires clear categorization, vibrant distinction, and
  uncluttered typography. Standard rectangular label badges create visual noise and occlusion.
- **Evidence:** `TeardropPinMarker.kt` renders an illustrated canvas teardrop pin with a 2.2dp white
  border, drop shadow, category glyphs (🏛️ Sight, 🏨 Hotel, 🍴 Food, 📷 Photo, 🏰 Castle), and
  floating typography placed right of the pin with high-contrast text halos (white in light mode,
  slate in dark mode).
- **Consequences:** Clean vector illustration aesthetics without boxy label occlusion.

## ADR-021 — Cohesive Understated Android Motion System with Stationary Sliding Pill Navigation

- **Status:** Accepted, in force (2026-08-27).
- **Context:** The app's minimal dark UI requires fluid, native feedback that reinforces the visual
  language without flashy distractions. The map screen is explicitly excluded to preserve high-performance
  canvas rendering.
- **Evidence:** `ui/theme/MotionTokens.kt` defines duration tokens (220–280ms) and standard easing
  (`FastOutSlowInEasing`). `AppNavHost.kt` features a stationary bottom bar with a single sliding blue
  pill indicator (`accentSoft`), directional tab transitions (16dp slide + fade), and vertical stack
  pushes (24dp). `AppPrimitives.kt` adds tactile press scaling (`PrimaryButton` 0.97f, FAB 0.92f) and
  calm empty-state entrance animation. Accessibility reduced-motion scale is strictly respected via
  `LocalReducedMotion`.
- **Consequences:** The app feels responsive and cohesive while respecting accessibility settings.

## ADR-022 — Itinerary Horizontal Date Pager with Independent Vertical Scroll and Tab Synchronization

- **Status:** Accepted, in force (2026-08-27).
- **Context:** Navigating across itinerary days should feel seamless via natural left/right horizontal
  swiping while keeping the top header and date selector tab strip in perfect sync.
- **Evidence:** `TimelineScreen.kt` integrates `HorizontalPager` bounded to `state.days`, synchronizing
  bidirectionally with `DaySelector` (including auto-scroll). Each date maintains independent vertical
  scroll state via isolated `LazyColumn`s. `TimelineUiState` caches multi-day collections (`allDayEvents`,
  `allPlaces`, etc.) to prevent re-query flicker during gestures.
- **Consequences:** Fluid date transitions without losing vertical reading position on other days.

## ADR-023 — Idempotent Seeding and Pre-Insert Entity Deduplication

- **Status:** Accepted, in force (2026-08-27).
- **Context:** Repeated seeding or duplicate imports should never clutter the database with redundant
  places, trips, or trains.
- **Evidence:** `LocationDao.kt`, `TrainDao.kt`, and `TripDao.kt` provide lookup queries (`findLocationByName`,
  `getTripByName`, etc.). `SampleTripSeeder.kt` verifies existence before insertion. `LocationRepositoryImpl`
  and DAOs prevent duplicate entities.
- **Consequences:** Clean database state across restarts and migrations.

## ADR-024 — Modern Floating Stadium Navigation Bar with Shape-Aware Overlay Occlusion

- **Status:** Accepted, in force (2026-08-27).
- **Context:** Modern Android navigation requires a true floating capsule overlay sitting above full-bleed
  scrollable content, avoiding rectangular layout barriers or premature clipping scrims.
- **Evidence:** `AppNavHost.kt` renders `NavHost` full-bleed in a root `Box` and overlays `AppBottomNavigation`
  at `Alignment.BottomCenter`. The navigation bar is an elevated stadium capsule (`RoundedCornerShape(30.dp)`,
  `height = 58.dp`, `padding(horizontal = 20.dp, bottom = 16.dp)`), with refined Light (`#FFFFFF` surface,
  6dp soft elevation, `#EAF2FF` active capsule) and Dark (`#1B1E23` surface, `#2EFFFFFF` border, 6dp soft
  shadow, `#152238` deep blue active capsule) palettes. Houses 5 root tabs (Home, Trips, Itinerary, Trains, More)
  with an animated concentric indicator capsule (`48.dp × 34.dp`, `FastOutSlowInEasing`, 220ms) and 1.05f icon scaling.
- **Consequences:** Content scrolls naturally underneath the floating bar, remaining visible through the
  transparent side margins, rounded corners, and gesture bar insets, with occlusion strictly confined to the
  physical capsule footprint.

## ADR-025 — Multi-Select Place Deletion & Ticket Details Screen Hierarchy Refinements

- **Status:** Accepted, in force (2026-08-27).
- **Context:** Travellers need the ability to select and delete multiple places simultaneously, and the train ticket screen must accurately reflect real-world travel conditions by prioritizing the actual boarding station over the booked-from station while preventing visual collision of metadata fields.
- **Evidence:** 
  1. `PlacesScreen.kt` and `PlacesViewModel.kt` support multi-selection mode with select-all, item check indicators, and atomic bulk deletion (`deleteMultiple`) with confirmation dialog.
  2. `TrainTicketScreen.kt` and `Cards.kt:TicketCard` display the actual boarding station (`train.boardingName` / `train.boardingCode`) as the primary departure in the journey header, clearly labeled as **BOARDING** and **ARRIVAL**, while preserving the booked-from origin in the explanatory warning card.
  3. `TicketCard` provides 3 independent layout columns for `DATE | CLASS | PLATFORM` (`Modifier.weight(1.3f)` / `Modifier.weight(0.85f)` / `Modifier.weight(0.85f)`) and compact date formatting (`formatShortDateWithYear`), preventing text overlap.
  4. Added PNR copy affordance and compact passenger list formatting (`TicketPassengerRow`).
- **Consequences:** Accurate boarding guidance for travellers, clean metadata presentation without text overlap, and efficient multi-place management.

## ADR-026 — Strict LocationIQ Forward Geocoding, Station Resolution, and Auto-Healing

- **Status:** Accepted, in force (2026-08-30).
- **Context:** Previous geocoders and fallbacks occasionally resolved Indian railway stations and places to international locations with similar names (e.g. Agra Cantt resolving to Wah Cantt, Pakistan). Stored coordinates in Room DB would persist these bad coordinates and reuse them on subsequent launches without re-querying.
- **Evidence:**
  1. `SearchModule.kt` strictly provides `LocationIqLocationSearchProvider` and eliminates fallback search providers.
  2. `LocationIqClient.kt` enforces `countrycodes=in` on all queries (`/v1/search`, `/v1/autocomplete`).
  3. `JourneyEventLinker.kt:resolveStationLocation` validates that existing stored `Location` entities match Indian bounding boxes (`lat in 6.0..38.0, lon in 68.0..98.0`) and have provider `LocationIQ`; if invalid or during forced refresh, it re-geocodes with LocationIQ and overwrites the SQLite record in-place.
  4. `TripMapViewModel.kt` runs an auto-heal check on map load to re-query LocationIQ for any legacy/out-of-bounds records and exposes `refreshLocations()` for user-initiated re-geocoding.
- **Consequences:** Zero incorrect foreign coordinates for Indian transit/places; transparent auto-healing of existing on-device database caches.

## ADR-027 — Hotel Stay Check-In Lifecycle & Daytime Next-Up Activity Progression

- **Status:** Accepted, in force (2026-08-30).
- **Context:** Stays (hotel bookings) typically span 24–48 hours from check-in to check-out. Previously, once a stay was checked in, `TripStateEngine` kept the stay active as `currentEvent` in the Next Up HUD card with a "Check out" button, preventing intermediate daytime activities (sightseeing, food, tours) from surfacing.
- **Evidence:**
  1. `TripStateEngine.kt:computeState` updated so that an active stay event with `actualStartTime != null` yields Next Up focus to active and upcoming daytime events.
  2. The hotel stay only returns to the Next Up HUD card when all daytime activities are completed or check-out time is imminent.
  3. Validated by unit test `testStayCheckIn_advancesToIntermediateActivitiesAndThenCheckOut` in `TripStateEngineTest.kt`.
- **Consequences:** Realistic multi-day itinerary progression where travelers see their next sightseeing/food activity after checking into their hotel.

## ADR-028 — Editorial Map HUD, Frosted Callout Badges & Floating Thumb Controls

- **Status:** Accepted, in force (2026-08-30).
- **Context:** The itinerary map requires balanced top navigation, high-contrast label badges that remain legible over complex map tiles, and one-handed thumb reachability for map actions without crowding.
- **Evidence:**
  1. `TripMapScreen.kt` provides a balanced top bar: circular Back button, flexible centered `DayTripPill` (`weight(1f)`), and a dedicated circular Refresh button with an infinite rotation animation during network activity.
  2. Placed the Recenter Day FAB in the lower-right corner floating above the bottom sheet peek for thumb reach.
  3. `TeardropPinMarker.kt` encloses stop numbers and place names in frosted, high-contrast surface pills (`RoundedCornerShape(8.dp)`, shadow elevation, subtle outline) for dark and light map modes.
- **Consequences:** Clean, uncrowded HUD layout with high marker legibility and responsive tactile interactions.

---

*If you add a decision here, also enforce it where it lives (code/comment/test) — a decision only
recorded in this file but not in the code will rot.*

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
  `Migrations.ALL` with explicit `MIGRATION_1_2..4_5`; schemas exported under `app/schemas/`;
  instrumented `MigrationTest`. **No `fallbackToDestructiveMigration` anywhere.**
- **Consequences:** Every schema change needs a real migration + exported schema JSON + a migration
  test. This holds **even during testing**, when the developer's own current data is disposable —
  the shipping policy is what's being protected. (Task 3's new `Event` column needs `MIGRATION_5_6`.)

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

- **Status:** Accepted, in force (implemented this session, **uncommitted**).
- **Context:** The prior vendor used key-in-URL-path auth and a different JSON shape and *required a
  cleartext exception*. RailRadar uses header auth (`X-API-Key`), a `{success,data,meta}` envelope,
  and is HTTPS/TLS-only — so switching also let the app drop its last cleartext allowance.
- **Evidence:** `data/network/railradar/*`; deleted `IndianRailApi*`; `network_security_config.xml`
  now `cleartextTrafficPermitted="false"` everywhere; plan file
  `.claude/plans/combine-the-journey-with-reflective-cook.md`.
- **Consequences:** Do **not** re-add the old vendor or any cleartext exception. Key format caveat:
  the working key is `rg_…` while docs show `rr_live_…` — unverified; app falls back to projection if
  rejected (see [API_CONTRACTS.md](API_CONTRACTS.md), [CURRENT_STATE.md](CURRENT_STATE.md)).

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
- **Consequences:** Any cascade/delete work on trains must preserve the linked event. **Reason for
  the exact linking design not documented in existing project history** beyond the spec's Journey model.

## ADR-013 — Single-Activity Compose navigation with string args parsed defensively

- **Status:** Accepted, in force.
- **Context:** One Activity, one `NavHost`; all route args are strings parsed with `toLongOrNull`,
  and cross-screen results return via the caller's `SavedStateHandle` rather than route args.
- **Evidence:** `ui/navigation/Routes.kt` + `AppNavHost`; `Routes.LocationPicker.RESULT_LOCATION_ID`.
- **Consequences:** **Route argument names are load-bearing** — renaming one without updating the
  matching `SavedStateHandle` read breaks that screen silently (lands on empty state, no crash).

## ADR-014 — Keyed pastel basemap (MapTiler prebuilt raster) with graceful CARTO fallback; tiles stay UI-map, not a domain port

- **Status:** Accepted, in force (Map-redesign pass, **uncommitted**).
- **Context:** The trip map read as a generic high-contrast street screenshot with app chrome
  floating on top. A low-contrast, warm basemap that *recedes* makes the plan (route + numbered
  stops) the subject. MapTiler's prebuilt raster styles give that with **no client-side style
  engine** — a plain XYZ tile URL, the same shape osmdroid already consumes for CARTO/Esri. The one
  wrinkle vs. ADR-003: a *tile source* is inherently an osmdroid (`OnlineTileSourceBase`) type, so it
  cannot live behind a domain port the way RailRadar/ORS do. It stays in the UI map component
  (`ui/components/OsmMap.kt`) next to the existing CARTO/Esri sources; **only the secret is new**, and
  it crosses via `BuildConfig` exactly like the RailRadar/ORS keys — nothing HTTP/vendor leaks above
  `data/`+UI-map. When the key is blank the map falls back to the keyless CARTO Voyager/DarkMatter
  sets, so the app is fully usable unkeyed.
- **Evidence:** `app/build.gradle.kts` reads `MAPTILER_API_KEY` from `local.properties` →
  `BuildConfig.MAPTILER_API_KEY`; `OsmMap.kt` `maptilerSource(styleId, key)` (URL
  `https://api.maptiler.com/maps/{style}/256/{z}/{x}/{y}.png?key=…`), style ids `MAPTILER_LIGHT_STYLE
  = "dataviz"` / `MAPTILER_DARK_STYLE = "dataviz-dark"`, and the `BuildConfig.MAPTILER_API_KEY.isNotBlank()`
  fork to `VoyagerTiles`/`DarkMatterTiles`; attribution names MapTiler only when it is the live source.
- **Consequences:** The palette is swappable by changing two constants (`landscape`/`pastel`/`bright-v2`
  are the warmer prebuilt alternatives) — no code path. The MapTiler key is a **secret**: `local.properties`
  only, never committed or logged. A `403` in logcat means a bad/absent key (falls back to CARTO). Do
  **not** promote tiles to a domain port — that fights osmdroid's type model for no isolation gain.

## ADR-015 — Device location via the framework `LocationManager` (no Google Play Services), behind a `DeviceLocationProvider` port

- **Status:** Accepted, in force (Map-redesign pass, **uncommitted**).
- **Context:** The map needs a "where am I" dot, GPS-anchored search later, and (per spec) proximity
  trip-state. The fused-location provider would pull in Play Services — a proprietary dependency the
  app has deliberately avoided everywhere else (osmdroid tiles, not the Maps SDK; keyless basemaps).
  The framework `LocationManager` gives a good-enough glanceable fix with **zero new dependencies**
  and works on a de-Googled device, at the cost of a little more code (two providers to juggle, a
  last-known seed).
- **Evidence:** `domain/service/DeviceLocationProvider.kt` (`DeviceLocation(latitude, longitude,
  accuracyMeters: Float?)`, `fun locationUpdates(): Flow<DeviceLocation?>`); impl
  `data/location/AndroidDeviceLocationProvider.kt` (framework `LocationManager` via `callbackFlow`,
  GPS+NETWORK, last-known seed, `@SuppressLint("MissingPermission")` guarded by `hasPermission()`);
  bound in `di/LocationModule.kt`; `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` in the manifest.
- **Consequences:** Follows the §11 port pattern — swapping to a fused provider later is one binding,
  no ViewModel/screen change. A missing permission or disabled provider is emitted as a **null fix**
  (the ordinary "no location" state), never a thrown `SecurityException`; the map draws no dot and
  center-on-me falls back to recentring on the trip. The app must remain fully usable with permission
  **denied**.

## ADR-016 — Road routing via OpenRouteService behind a `RoutePlanService` port; per-leg segments; straight-line fallback

- **Status:** Accepted, in force (Map-redesign pass, **uncommitted**).
- **Context:** Connecting stops with straight lines misrepresents travel; a road-following polyline
  with real per-gap distance/time makes the itinerary honest. ORS gives keyed multi-stop directions
  as GeoJSON. Routing has **no offline projection** to compute (unlike trains), so there is one
  provider, always bound; the key decides whether it reaches the network, and an unconfigured/failed
  route degrades to a great-circle **haversine** distance with **no invented drive time**.
- **Evidence:** `domain/service/RoutePlanService.kt` (`RoutePoint`, `RouteLeg(distanceMeters,
  durationSeconds)`, `PlannedRoute(points, legs)`, `RoutePlanOutcome.{Routed(points, legs),
  Unavailable(error)}`, `RoutePlanError`); provider `data/network/openrouteservice/OpenRouteServiceRouteProvider.kt`
  (POST `…/v2/directions/driving-car/geojson`, key in the `Authorization` header, parses
  `features[0].properties.segments[].{distance,duration}` alongside geometry); policy
  `data/routing/DefaultRoutePlanService.kt` (`MIN_WAYPOINTS=2`, `MAX_WAYPOINTS=50`,
  `ROUTE_TIMEOUT_MS=12_000`); bound in `di/RoutingModule.kt`; `TripMapViewModel` aligns `legs` to the
  visible pins (leg *i* = pin *i*→*i*+1) and discards a leg breakdown **wholesale** when its count
  ≠ gap count. Covered by `OpenRouteServiceRouteProviderTest` (segment→leg parse) and
  `TripMapViewModelTest` (haversine fallback / routed passthrough / partial-breakdown discard).
- **Consequences:** New geometry-vs-legs shape is an **additive** contract change (documented in
  API_CONTRACTS). Never pair a partial leg list against the gaps — a distance under the wrong gap is
  worse than an honest straight line. ORS key is a secret (`local.properties` → `BuildConfig`); a
  `401` in logcat means it was rejected. The driving-car profile is baked in (a trip map is a driving
  plan); a mode selector would be a future extension.

## ADR-017 — Numbered status markers derived from `TripStateEngine`, not place-typed pins

- **Status:** Accepted, in force (Map-redesign pass, **uncommitted**).
- **Context:** Giant Google-style type-glyph pins fight the recede-behind basemap and don't tie the
  map to the sheet. Instead each stop is a small **numbered** chip whose number equals the sheet
  card's `orderInDay`, in one of three states — Completed (muted, a check), Upcoming (the event's
  category colour, quiet), Current/Next (largest, accent, shadow + restrained halo). State is derived
  from the **same** `TripStateEngine.computeEventStatus(event, now)` the Home card uses, so map and
  Home always agree, and it keys off `EventStatus`/`EventType` — never a place name (ADR-004).
- **Evidence:** `OsmMap.kt` `enum class MarkerState { Completed, Upcoming, Current }`,
  `data class MapMarker(… state)`, `MapPinMarker` rendering the three chips; `TripMapScreen`'s
  `markerStateOf` maps engine status → `MarkerState`.
- **Consequences:** The number is the primary content (no type glyph on the pin). Marker/sheet
  numbers must stay in lockstep — both come from `orderInDay`. Do not reintroduce category type-icon
  pins as the *primary* marker; category colour is a tint, not a glyph.

## ADR-018 — External turn-by-turn via `ExternalNavigator` Intent; no in-app navigation

- **Status:** Accepted, in force (Map-redesign pass, **uncommitted**).
- **Context:** The in-app osmdroid map is for *display and planning*. Turn-by-turn is a solved problem
  owned by the user's maps app; reimplementing it would be a large, redundant surface. The Navigate
  action fires a platform `Intent`.
- **Evidence:** `core/util/ExternalNavigator.kt` (`navigateTo(context, latitude, longitude, label)` —
  tries `google.navigation:q=lat,lon`, catches `ActivityNotFoundException`, falls back to
  `geo:0,0?q=lat,lon(label)`, absorbs a second failure silently). Pure platform Intent — no `data/`
  footprint, no §11 concern.
- **Consequences:** No in-app routing UI to maintain. If neither a navigation app nor a `geo:` handler
  exists the action is a silent no-op (acceptable — the map still shows the stop). Not to be confused
  with ORS road-line *display* (ADR-016), which is in-app.

## ADR-019 — Map top label reads "Day N · <trip name>" (name-over-city tradeoff accepted)

- **Status:** Accepted, in force (Map-redesign pass, **uncommitted**).
- **Context:** The floating top pill needs a stable, cheap label. Reverse-geocoding the day's city
  would add a network call and a failure mode; the trip name is already in hand. Chosen **knowingly**:
  on a trip whose days span cities, the pill may show the trip name over a different-city day. Map,
  pins, route and sheet always agree because they are all driven by the **selected day**; only the
  *name text* is the trip's, by design.
- **Evidence:** `TripMapScreen` `dayTripLabel(...)` → the centred `DayTripPill`; the day switcher
  (`MapDaySwitcher`) sits beneath it. No reverse-geocode call on this screen.
- **Consequences:** Do **not** re-litigate this as a bug — it is the accepted tradeoff. A future
  per-day city label would require a reverse-geocode capability (new port) and is out of scope here.

---

*If you add a decision here, also enforce it where it lives (code/comment/test) — a decision only
recorded in this file but not in the code will rot.*

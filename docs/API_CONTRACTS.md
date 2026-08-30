# API_CONTRACTS.md

Contracts the app depends on: **external HTTP services** and the **internal domain ports** that wrap
them. External details are confined to `data/` (ADR-003). Anything not confirmed from vendor docs or
a real response is marked `UNKNOWN` / `UNVERIFIED` — **do not invent request/response fields**. The
field-level RailRadar notes captured while integrating are in
[railradar-api.md](railradar-api.md); this file is the stable summary + the internal ports.

The app has **no API of its own** (no backend, no auth server) — see [ARCHITECTURE.md](ARCHITECTURE.md).

---

## External: RailRadar (train status, schedule, PNR)

- **Base URL:** `https://api.railradar.in/v1` — HTTPS/TLS only, no cleartext.
- **Auth:** header `X-API-Key: <key>` (documented alternative: `Authorization: Bearer <key>`).
- **Response envelope:** `{ "success": bool, "data": {...}, "meta": {...} }`. The parser unwraps
  `data` and treats `success=false` (or a non-2xx) as a classified error.
- **Free tier:** ~1000 requests/month (informational; enforced server-side → `429`).
- **Key caveat:** working key is `rg_e04caaa9deb04b8c849f5411d7bdec0e`; vendor docs show `rr_live_…`.
  Prefix mismatch is **UNVERIFIED**; a rejected key → app falls back to projection but still labels
  status "Live" while a key is merely present. Watch for `401`. **The key is a secret** — it lives in
  `local.properties` → `BuildConfig.RAILRADAR_API_KEY`; never commit or echo it.

Error mapping (HTTP → `TrainStatusError` / `PnrLookupError`): `401/403` → key rejected /
`NOT_CONFIGURED`-adjacent, `404` → `TRAIN_NOT_FOUND` / `NOT_FOUND`, `429` → `RATE_LIMITED`, timeout →
`TIMEOUT`, no network → `NETWORK_UNAVAILABLE`, unparseable → `MALFORMED_RESPONSE`.

### `GET /v1/trains/{number}/live` → live status
Consumed by `RailRadarTrainStatusProvider.fetchStatus` → domain `TrainRunStatus`.
- `data.currentLocation.stationCode` → `currentStationCode` (name resolved from `route[]`).
- `data.delayMinutes` → `delayMinutes`; `data.nextHalt` → `nextStopCode`/`nextStopName`.
- `data.route[]` → `List<TrainStopStatus>`: `sequence`→`serialNo`, scheduled/actual arrival &
  departure, `delayArrival`/`delayDeparture`, `distance`→`distanceKm`, `isDeparted`
  (from `actualDeparture`/status), `isCurrent` (`stationCode == currentLocation.stationCode`).
- `progressFraction` **derived** from cumulative `distance` (+ `currentLocation.segmentProgress`) over
  route total — never a raw field. `nextStopEta` from next stop's `actualArrival ?: scheduled(+delay)`.
- **Time format is `UNKNOWN`** (docs don't specify `HH:mm` vs ISO). Parser accepts both; confirm
  against a real device response before trusting.

### `GET /v1/trains/{number}` → schedule / timetable
Consumed by `RailRadarTrainStatusProvider.fetchSchedule` → `List<TrainStop>`.
- `data.route[]` → stops: `sequence`, `station:{code,name}` (**nested** — bare top-level
  `arrival`/`departure` was the MALFORMED bug; do not revert), scheduled arrival/departure, `distance`.
- `dayOffset` **derived** from midnight rollovers across scheduled times.

### `GET /v1/pnr/{pnr}` → booking / boarding details
Consumed by `RailRadarPnrLookupService.lookup` → domain `ParsedTicket`.
- `data.train` → number/name/origin/destination; `data.train.boardingPoint` →
  `boardingCode`/`boardingName` (drives `Train.boardsElsewhere`); `data.journey` → `travelClass`/`quota`.
- `departure`/`arrival` stay **null** (PNR carries no times).
- `data.passengers[]` → `List<TrainPassenger>`: `coach`, `berthNumber`, `berthCode`→`BerthType`,
  status from `bookingStatus`/`currentStatus`. **Names/ages/genders are always blank** — Indian
  Railways PNR never returns them; surfaced to the user as a notice, not silently.
- Key blank → `isAvailable=false`, `lookup` returns `Failed(NOT_CONFIGURED)`.

---

## External: OpenRouteService (road-following trip route + per-leg travel)

- **Endpoint:** `POST https://api.openrouteservice.org/v2/directions/driving-car/geojson` — HTTPS/TLS.
  The driving-car profile is baked into the path (a trip map is a driving plan). The **GET** directions
  endpoint takes only start+end, so a multi-stop day needs the **POST** with a `coordinates` array.
- **Auth:** header `Authorization: <key>` (**not** a URL param — an error message may still name the
  endpoint without leaking the key). **The key is a secret** — `local.properties` →
  `BuildConfig.OPENROUTESERVICE_API_KEY`; never commit or echo it. Blank key → `isConfigured=false` →
  the service returns `Unavailable(NOT_CONFIGURED)` and the map draws straight lines.
- **Request body:** `{ "coordinates": [[lon,lat], …] }` — **lon,lat order** (GeoJSON), the reverse of
  the app's `RoutePoint(lat, lon)`; the provider swaps the order on the way out. 2–50 waypoints
  (`RoutePlanService.MIN_WAYPOINTS`/`MAX_WAYPOINTS`); over the cap is truncated.
- **Response (GeoJSON):**
  - `features[0].geometry.coordinates[]` → the polyline (`[lon,lat]` pairs → `RoutePoint(lat, lon)`).
  - `features[0].properties.segments[].{distance,duration}` → one `RouteLeg(distanceMeters,
    durationSeconds)` **per gap** between consecutive waypoints (segment *i* = stop *i*→*i*+1).
- **Timeout:** 12 s (`RoutePlanService.ROUTE_TIMEOUT_MS`), enforced in `DefaultRoutePlanService`.
- Error mapping (→ `RoutePlanError`): `401/403` → key rejected, `404`/empty → `NO_ROUTE`, `429` →
  `RATE_LIMITED`, timeout → `TIMEOUT`, no network → `NETWORK_UNAVAILABLE`, unparseable →
  `MALFORMED_RESPONSE`, else `PROVIDER_ERROR`/`UNKNOWN`. Every one crosses the boundary as
  `RoutePlanOutcome.Unavailable(error)` — never a thrown HTTP exception (ADR-011, ADR-016).
## External: LocationIQ (geocoding & place search)

- **Endpoints:**
  - Forward Geocoding: `GET https://us1.locationiq.com/v1/search?key={key}&q={query}&format=json&countrycodes=in&addressdetails=1&limit=10`
  - Autocomplete: `GET https://us1.locationiq.com/v1/autocomplete?key={key}&q={query}&format=json&countrycodes=in&limit=10`
- **Auth:** Query parameter `key=<key>`. Key stored in `local.properties` → `BuildConfig.LOCATIONIQ_API_KEY`.
- **Country Constraint:** Queries append `countrycodes=in` to ensure results are strictly focused on India.
- **Response Format:** JSON array of place objects:
  - `place_id` → `providerPlaceId`
  - `display_name` → `formattedAddress`
  - `lat`, `lon` → `latitude`, `longitude` (parsed as `Double`)
  - `class`, `type` → `category`
- **Error Mapping:**
  - `401/403` → `NOT_CONFIGURED` / `PROVIDER_ERROR`
  - `404` / empty array → `NO_RESULTS` (empty outcome, not a crash)
  - `429` → `RATE_LIMITED`
  - `5xx` / timeout / network → `TIMEOUT`, `NETWORK_UNAVAILABLE`, `PROVIDER_ERROR`
- **Auto-Healing:** Stored `LocationEntity` entries with out-of-bounds or legacy coordinates are automatically validated and refreshed against LocationIQ upon map load or manual refresh.

---

## External: Geoapify & OpenRouteService (road routing & vector basemaps)

- **Routing Endpoint:**
  - Geoapify: `GET https://api.geoapify.com/v1/routing?waypoints={lat,lon|...}&mode=drive&apiKey={key}`
  - OpenRouteService: `POST https://api.openrouteservice.org/v2/directions/driving-car/geojson` with `{ "coordinates": [[lon,lat], ...] }`
- **Vector Basemap Styles:** Geoapify MapLibre style JSON endpoints (`osm-bright-smooth`, `dark-matter`, `positron`).
- **Response:** GeoJSON FeatureCollection with line coordinates and segment metrics (distance & drive duration).

---

## External: Map basemap tiles (osmdroid & MapLibre vector/raster)

Raster XYZ tiles and vector styles rendered by `ui/components/OsmMap` and `TripVectorMap`:
- **Low-contrast travel styles:** `positron` (light) and `dark-matter` (dark) vector/raster basemaps.
- **Glowing day route:** Electric azure (`#388AF6`) in light mode; glowing cyan (`#00D2C4`) in dark mode.
- **Illustrated pins:** `TeardropPinMarker` canvas rendering with category glyphs and high-contrast frosted text badges (ADR-025).

---

## Domain Ports (Internal Service Boundaries)

| Port Interface | Concrete Data Provider | Notes |
|---|---|---|
| `LocationSearchProvider` | `LocationIqLocationSearchProvider` | Turns text queries into `SearchResultLocation` candidates. |
| `RoutePlanProvider` | `GeoapifyRoutePlanProvider` / `OpenRouteServiceRouteProvider` | Turn-by-turn road geometry & leg distances. |
| `TrainStatusProvider` | `RailRadarTrainStatusProvider` | Live running status & delay minutes. |
| `PnrLookupService` | `RailRadarPnrLookupService` | PNR status, coach, berth allotments. |
| `DeviceLocationProvider` | `AndroidDeviceLocationProvider` | Real-time GPS location via Android LocationManager. |
| `TrainStatusService` | `refresh(trainId, force)`, `refreshSchedule(trainId)` | `TrainStatusOutcome` (`Updated`/`Cached`/`Failed`), `TrainScheduleOutcome` | `DefaultTrainStatusService` |
| `PnrLookupService` | `lookup(pnr)`; `isAvailable` | `PnrLookupOutcome` (`Found(ParsedTicket)`/`Failed(error)`) | `RailRadarPnrLookupService` |
| `LocationSearchService` | search by query | `SearchResultLocation` list + classified failure | `DefaultLocationSearchService` → Nominatim provider |
| `TicketImportService` | import an IRCTC e-ticket PDF | `ParsedTicket` (+ warnings) | `IrctcTicketImportService` |
| `TripTransferService` | export/import a trip (+ images) | archive / restore outcome | `TripTransferServiceImpl` |
| `DeviceLocationProvider` | `locationUpdates()` | `Flow<DeviceLocation?>` (null = no fix / no permission) | `AndroidDeviceLocationProvider` (framework `LocationManager`) |

**Policy constants (one place):** cache TTL **2 min**, request timeout **12 s** in
`DefaultTrainStatusService` / `TrainStatusService`.

---

## Motion & UI Contracts (`ui/theme/MotionTokens.kt`)

Standard animation constants and easing curves for the app motion system (ADR-021):
- `PAGE_TRANSITION_DURATION = 240` ms
- `CONTENT_ENTER_DURATION = 280` ms
- `CONTENT_STAGGER_DELAY = 25` ms
- `BUTTON_PRESS_DURATION = 120` ms
- `NAV_PILL_DURATION = 220` ms
- `EMPTY_STATE_ENTER_DURATION = 300` ms
- `StandardEasing = FastOutSlowInEasing`
- `LocalReducedMotion`: CompositionLocal for system reduced-motion accessibility preference.

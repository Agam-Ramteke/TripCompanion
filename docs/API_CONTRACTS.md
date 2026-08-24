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
- **Consumer note (`TripMapViewModel`):** legs are aligned to the visible pins; if the provider
  returns a leg count ≠ gap count, the whole breakdown is **discarded** and the map falls back to
  per-gap **haversine** distance with **no** drive time (mirrors the polyline's all-or-nothing choice).

---

## External: Nominatim (OpenStreetMap geocoding / place search)

- Consumed by `NominatimLocationSearchProvider` behind `LocationSearchService` →
  `List<SearchResultLocation>`.
- **Endpoint / exact params / rate limits: `UNVERIFIED` here** — read
  `NominatimLocationSearchProvider` for the live truth before relying on specifics. Nominatim's public
  usage policy requires a valid User-Agent and low request rates; assume that applies.
- Returns place name + lat/lon (+ address bits) that seed a `Location`. Failures are classified like
  the other providers (network/timeout/rate/malformed).

## External: Map basemap tiles (osmdroid raster)

Raster XYZ tiles rendered by `ui/components/OsmMap` (osmdroid 6.1.20). No structured response the app
parses; osmdroid manages the disk cache and User-Agent. Tile sources are an osmdroid type, so they
live in the UI-map layer (not a domain port) — only the MapTiler **key** is new and it crosses via
`BuildConfig`, exactly like the other secrets (ADR-014). Three sources, chosen at render time:

- **MapTiler prebuilt (keyed, default when present):** `https://api.maptiler.com/maps/{style}/256/{z}/{x}/{y}.png?key=…`
  — HTTPS. Style id is the whole style: `dataviz` (light) / `dataviz-dark` (dark), swappable
  (`landscape`/`pastel`/`bright-v2`) via the `MAPTILER_LIGHT_STYLE`/`MAPTILER_DARK_STYLE` constants.
  **Key is a secret** — `local.properties` → `BuildConfig.MAPTILER_API_KEY`. A `403` = bad/absent key.
- **CARTO (keyless fallback):** `VoyagerTiles` (light) / `DarkMatterTiles` (dark) — used whenever
  `BuildConfig.MAPTILER_API_KEY` is blank, so the map works fully unkeyed.
- **Esri World Imagery (keyless, satellite alternate):** `.../tile/{z}/{y}/{x}` (row-before-column;
  assembled by hand). Selected by the layers control, unchanged by this pass.
- **Attribution** follows the live source: "© MapTiler · © OpenStreetMap" when keyed, CARTO/OSM when
  falling back, "© Esri, Maxar, Earthstar Geographics" on satellite. Light/dark picked by surface
  luminance (as the map already did).

---

## Internal domain ports (the app's real "contracts")

These interfaces are the stable surface the app codes against; the vendor is an implementation detail.

| Port (`domain/service`) | Methods | Result type | Bound impl (`di`) |
|---|---|---|---|
| `TrainStatusProvider` | `fetchStatus(train, schedule)`, `fetchSchedule(number)`; `providerName`, `isLive` | `TrainRunStatus?` / `List<TrainStop>?` | RailRadar (live) or ScheduleProjection — by key |
| `TrainStatusService` | `refresh(trainId, force)`, `refreshSchedule(trainId)` | `TrainStatusOutcome` (`Updated`/`Cached`/`Failed`), `TrainScheduleOutcome` | `DefaultTrainStatusService` |
| `PnrLookupService` | `lookup(pnr)`; `isAvailable` | `PnrLookupOutcome` (`Found(ParsedTicket)`/`Failed(error)`) | `RailRadarPnrLookupService` |
| `LocationSearchService` | search by query | `SearchResultLocation` list + classified failure | `DefaultLocationSearchService` → Nominatim provider |
| `TicketImportService` | import an IRCTC e-ticket PDF | `ParsedTicket` (+ warnings) | `IrctcTicketImportService` |
| `TripTransferService` | export/import a trip (+ images) | archive / restore outcome | `TripTransferServiceImpl` |
| `RoutePlanService` (over `RoutePlanProvider`) | `planRoute(waypoints)` | `RoutePlanOutcome` (`Routed(points, legs)` / `Unavailable(error)`) | `DefaultRoutePlanService` → `OpenRouteServiceRouteProvider` |
| `DeviceLocationProvider` | `locationUpdates()` | `Flow<DeviceLocation?>` (null = no fix / no permission) | `AndroidDeviceLocationProvider` (framework `LocationManager`) |

**Policy constants (one place):** cache TTL **2 min**, request timeout **12 s** in
`DefaultTrainStatusService` / `TrainStatusService`.

**Error enums** (never raw exceptions above `data/`): `TrainStatusError` {NETWORK_UNAVAILABLE,
TIMEOUT, RATE_LIMITED, PROVIDER_ERROR, MALFORMED_RESPONSE, TRAIN_NOT_FOUND, NO_SCHEDULE, UNKNOWN};
`PnrLookupError` {NOT_CONFIGURED, NETWORK_UNAVAILABLE, TIMEOUT, RATE_LIMITED, NOT_FOUND, MALFORMED,
UNKNOWN}; `RoutePlanError` {NOT_CONFIGURED, NETWORK_UNAVAILABLE, TIMEOUT, RATE_LIMITED, NO_ROUTE,
PROVIDER_ERROR, MALFORMED_RESPONSE, UNKNOWN}.

**Trip archive format (`.trip` zip):** the manifest (`data/transfer/TripManifest.kt`) is written
**by hand**, not reflectively — a domain field is *not* in the file until it's added to
`encodeEvent`/`decodeEvent` (etc.). Every image (trip cover, place photo, planned-shot reference,
stay photo, and — since Task 3 — an activity's `backgroundImage`) is stored as an **archive entry
name** (`images/img_N.jpg`), never a device URI: the exporter's `ImageCollector.carry` bundles the
bytes, the importer's `local(...)` swaps the name back for a freshly written local file. Reading is
forgiving (a missing optional field → the model's default), so adding an optional field is
**additive and non-breaking** — `TripArchive.VERSION` is only bumped when a change would make an
older reader misread a file, which an ignored-by-old-apps optional field does not. Round-trip
covered by `TripManifestTest`.

---

## Local IPC / platform contracts (not HTTP)

- **Photo picker:** `ActivityResultContracts.PickVisualMedia` (image only) → URI copied into
  app-private storage by `ImageStorageHelper.saveImageToInternalStorage(context, uri)`; the app then
  stores only the returned `file://` path (ADR-009).
- **PDF import:** IRCTC e-ticket PDF read as text (`PdfTextExtractor`) then parsed
  (`IrctcTicketParser`). Real e-tickets contain passenger names/PNRs → `docs/Trip/*.pdf` is
  git-ignored; never commit sample tickets.
- **DataStore:** `UserPreferencesStore` persists theme preference (key/values — read the file for
  exact keys; not reproduced here to avoid drift).
- **External navigation:** `core/util/ExternalNavigator.navigateTo(context, latitude, longitude, label)`
  fires a platform `Intent` — `google.navigation:q=lat,lon` first, then `geo:0,0?q=lat,lon(label)`,
  then a silent no-op — so the map's Navigate action hands off to the user's maps app (ADR-018). No
  in-app turn-by-turn; distinct from the ORS road line, which is in-app *display* only.

## Map rendering contracts (UI layer, `ui/components/OsmMap`)

Not domain ports — these are the UI-map's own value types the screen passes in. Kept here because the
screen↔map coupling is load-bearing:

- **`MapMarker(… number, state: MarkerState, color)`** with `enum MarkerState { Completed, Upcoming,
  Current }`. `number` **must equal** the sheet card's `orderInDay` (marker/sheet numbers move in
  lockstep); `state` is derived by the screen from `TripStateEngine.computeEventStatus` — never a
  place name (ADR-004, ADR-017).
- **Basemap selection** (MapTiler-keyed → CARTO fallback → Esri satellite) and its attribution string
  live in `OsmMap`; see the "Map basemap tiles" external above (ADR-014).

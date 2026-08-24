# ARCHITECTURE.md

Actual architecture as discovered in the repository (2026-08-24). Where something is inferred rather
than confirmed by reading the file, it is marked `UNVERIFIED`. The product spec's *conceptual* model
is in [Udaipur_Trip_Companion_Product_Spec_v2.md](Udaipur_Trip_Companion_Product_Spec_v2.md); this
file documents what is *implemented*.

## System overview

Single-module (`:app`), single-Activity native Android app. Clean layered architecture with
one-way dependencies: **UI → Feature (ViewModels) → Domain → Data**. No backend of our own; the only
network egress is RailRadar (trains), Nominatim (geocoding), OpenRouteService (trip road routing),
and OSM/MapTiler basemap tiles. Everything a trip needs is stored locally (Room + app-private files),
so the app is fully usable offline.

```mermaid
flowchart TD
    UI["ui/ — Compose screens, components, theme, navigation<br/>(single Activity, NavHost)"]
    VM["feature/ — @HiltViewModel + StateFlow&lt;UiState&gt;"]
    DOM["domain/ — models, repository interfaces,<br/>service ports, engines (pure Kotlin)"]
    DATA["data/ — repository impls, network providers,<br/>Room, pdf, ticket, transfer, routing, location, prefs"]
    ROOM[("Room DB<br/>trip_companion.db (v6)")]
    NET["RailRadar / Nominatim / OpenRouteService (HTTPS)"]
    TILES["OSM / MapTiler tiles (osmdroid)"]
    GPS["Device GPS (framework LocationManager)"]
    FS["Filesystem<br/>app-private images, trip archives"]

    UI -->|observe state / call methods| VM
    VM -->|call interfaces| DOM
    DOM -.implemented by.-> DATA
    DATA --> ROOM
    DATA --> NET
    DATA --> GPS
    UI --> TILES
    DATA --> FS
```

The **§11 boundary**: HTTP/JSON/vendor/API-key details never appear above `data/`. Domain exposes
**ports** (interfaces) and value/enum results; `data/` holds the concrete providers and the Hilt
module chooses which one is bound.

## Directory / module responsibilities

Package root: `com.tripcompanion.app`.

| Package | Responsibility |
|---|---|
| `MainActivity`, `TripCompanionApp` | Single Activity host; `@HiltAndroidApp` application. |
| `core/time` | `TimeProvider` — the clock, injected so "now" is testable. |
| `core/util` | `DateTimeUtils`, `GeoUtils` — pure helpers (formatting, distance); `ExternalNavigator` — hands off to the device maps app via `Intent` (ADR-018). |
| `domain/model` | Plain Kotlin data classes + enums: `Trip`, `Event`, `EventType`, `EventStatus`, `Location`, `Activity`, `PlannedPhoto`, `Train`, `TrainStop`, `TrainRunStatus`, `TrainPassenger`, `TrainFare`, `TrainAllotment`, `StayDetails`, `ParsedTicket`, `SearchResultLocation`, `TripStatus`, `ActivityStatus`. |
| `domain/repository` | Repository **interfaces** (one per aggregate): `Trip/Event/Location/Activity/PlannedPhoto/Train/StayDetails`. |
| `domain/service` | Ports + result types: `TrainStatusProvider`, `TrainStatusService`, `PnrLookupService`, `LocationSearchService`, `TicketImportService`, `TripTransferService`, `JourneyEventLinker`, `RoutePlanService`/`RoutePlanProvider` (trip road route + per-leg travel), `DeviceLocationProvider` (device GPS). |
| `domain/engine` | Deterministic computation: `TripStateEngine` (current state), `TrainProgress` (position/ETA from status), `TripStats`. |
| `data/local` | Room: `TripDatabase`, `dao/*`, `entity/*`, `converter/Converters`, `EntityMappers` (entity⇄domain), `Migrations`, `ImageStorageHelper`. |
| `data/repository` | `*RepositoryImpl` — implement domain repositories over DAOs + mappers. |
| `data/network` | `NominatimLocationSearchProvider`, `ScheduleProjectionTrainStatusProvider`, `railradar/` (client, parser, error, live provider, PNR service, key qualifier), and `openrouteservice/` (route provider + key qualifier). |
| `data/routing` | `DefaultRoutePlanService` — the routing policy layer (waypoint bounds, 12 s timeout, outcome mapping) over a `RoutePlanProvider`. |
| `data/location` | `AndroidDeviceLocationProvider` — framework `LocationManager` (GPS+NETWORK, no Play Services) behind `DeviceLocationProvider`. |
| `data/train` | `DefaultTrainStatusService` — the policy layer (cache TTL, force refresh, outcome mapping) over a `TrainStatusProvider`. |
| `data/ticket`, `data/pdf` | IRCTC e-ticket import: `TicketFileReader`, `PdfTextExtractor`/`PdfText`, `IrctcTicketParser`, `IrctcTicketImportService`. |
| `data/transfer` | Trip export/import: `TripArchive`, `TripArchiveFiles`, `TripManifest`, `TripImagesDir`, `TripTransferServiceImpl`. |
| `data/search` | `DefaultLocationSearchService` (policy over the search provider). |
| `data/prefs` | `UserPreferencesStore` (DataStore — theme). |
| `data/SampleTripSeeder` | The **only** place trip-specific ("Udaipur"/"Agra") *data* lives. |
| `di` | Hilt modules: `DatabaseModule`, `RepositoryModule`, `TrainModule`, `SearchModule`, `TicketModule`, `TransferModule`, `TimeModule`, `RoutingModule` (route provider + service), `LocationModule` (device-location provider). |
| `feature/*` | ViewModels grouped by feature: `trip` (Home, Timeline, TripList, TripEditor), `event`, `train`, `stay`, `place`, `location`, `map`, `photo`, `settings`, `more`. |
| `ui/screens` | One Composable screen per destination (~20 screens). |
| `ui/components` | Reusable Compose: `AppPrimitives` (`AppCard`, `AppMediaCard`, buttons…), `Cards`, `Media` (`AppImage`, `PhotoBackdrop`, `HeroImage`…), `Timeline`, `OsmMap`, `EventTypeVisuals`, `TrainVisuals`, `SettingsRow`. |
| `ui/theme` | `AppTheme`, `AppMetrics`, `Color`, `ExtendedColors`, `Shape`, `Type`, `ThemeManager`. |
| `ui/navigation` | `Routes` (every route + `createRoute` + arg-key constants), `AppNavHost` (the `NavHost`). |

## UI architecture

- **Jetpack Compose + Material 3**, single Activity, `NavHost` with routes defined in
  [Routes.kt](../app/src/main/java/com/tripcompanion/app/ui/navigation/Routes.kt). Each `Routes`
  object owns its pattern and `createRoute(...)`; **argument names are load-bearing** — they are
  read back by name from a `SavedStateHandle` in the matching ViewModel, and all args are passed as
  strings parsed with `toLongOrNull` (a bad deep link lands on an empty state, not a crash).
- Screens are stateless-ish: they `collectAsStateWithLifecycle()` a ViewModel `StateFlow` and call
  VM methods. Theme tokens come from `AppThemeExtended` (colors/metrics/text) layered over M3.
- Cross-screen results (e.g. the location picker's chosen place) return via the **calling entry's**
  `SavedStateHandle`, not a route arg, so a half-filled form isn't recreated
  (see `Routes.LocationPicker.RESULT_LOCATION_ID`).

## State management

MVVM. `@HiltViewModel` ViewModels expose a single immutable `data class *State` via
`StateFlow`, updated with `_state.update { it.copy(...) }`. Repositories return `Flow`s (from Room);
ViewModels `combine`/`map`/derive into UI state. Coroutines via `viewModelScope`. No global event bus.

## Data flow (example: Home next-up card)

```mermaid
sequenceDiagram
    participant Room
    participant Repo as Repositories (Event/Location/Train/Stay/Photo)
    participant VM as HomeViewModel
    participant UI as HomeScreen
    Room-->>Repo: Flow<entities>
    Repo-->>VM: Flow<domain models> (mapped)
    VM->>VM: TripStateEngine computes "next up"; nextUpImageUri() resolves photo
    VM-->>UI: StateFlow<HomeUiState>
    UI->>UI: render NextUpSection (PhotoBackdrop for activity / solid for train)
    UI->>VM: onSkip / onComplete / onOpen
    VM->>Repo: mutate → Room → Flow re-emits → UI updates
```

## Persistence layer (Room)

- DB `trip_companion.db`, `@Database(version = 6, exportSchema = true)` in
  [TripDatabase.kt](../app/src/main/java/com/tripcompanion/app/data/local/TripDatabase.kt).
- **Entities (11):** `TripEntity`, `EventEntity`, `LocationEntity`, `ActivityEntity`,
  `PlannedPhotoEntity`, `TrainEntity`, `TrainStopEntity`, `TrainRunStatusEntity`, `TrainRunStopEntity`,
  `TrainPassengerEntity`, `StayDetailsEntity` (11 `@Entity` classes in 10 files —
  `TrainRunStopEntity` shares a file). The 10 DAOs in `data/local/dao/*` return `Flow`s.
- **Entity ⇄ domain mapping** is centralized in
  [EntityMappers.kt](../app/src/main/java/com/tripcompanion/app/data/local/EntityMappers.kt).
- **Type converters** in `converter/Converters.kt` (e.g. `LocalDateTime` ⇄ stored form). `UNVERIFIED`:
  exact stored representation.
- **Migrations** in [Migrations.kt](../app/src/main/java/com/tripcompanion/app/data/local/Migrations.kt):
  `MIGRATION_1_2`, `_2_3`, `_3_4`, `_4_5`, `_5_6`, exposed as `Migrations.ALL`. Schemas exported to
  `app/schemas/…TripDatabase/` (`1,3,4,5,6.json`). **No destructive fallback** (see
  [DECISIONS.md](DECISIONS.md)). Instrumented `MigrationTest` validates each step.
- **Implemented schema is a pragmatic subset/adaptation of the spec's §18 conceptual model.**
  Notably: no separate `Traveler`/`Task` tables; a *Journey* is realized as a `Train` booking plus a
  `JOURNEY`-type `Event`, linked by `JourneyEventLinker`; type-detail is split into `StayDetails`
  (stay), `Train`/`TrainStop` (journey), `Activity` (visit), `PlannedPhoto` (photo inspiration/media).

## Networking / external services (behind ports)

- **Train status** — port `TrainStatusProvider`; two impls:
  - `railradar/RailRadarTrainStatusProvider` (`isLive = true`) — real API, via `RailRadarClient`
    (HTTPS, `X-API-Key`) + `RailRadarParser` (unwraps `{success,data,meta}`).
  - `ScheduleProjectionTrainStatusProvider` (`isLive = false`) — offline; projects a position from
    the stored timetable + clock.
  - Selected in [TrainModule.kt](../app/src/main/java/com/tripcompanion/app/di/TrainModule.kt) by
    whether `BuildConfig.RAILRADAR_API_KEY` is non-blank. `DefaultTrainStatusService` adds caching
    (Room snapshot, 2-min TTL, 12 s timeout) and maps to `TrainStatusOutcome` (`Updated`/`Cached`/`Failed`).
- **PNR lookup** — port `PnrLookupService`; impl `railradar/RailRadarPnrLookupService` (always bound;
  reports `isAvailable = false` when the key is blank so the UI hides the affordance). Maps
  `GET /v1/pnr/{pnr}` → `ParsedTicket` (fills coach/berth/status; names blank — IR PNR omits them).
- **Location search / geocoding** — port `LocationSearchService`/provider; impl
  `NominatimLocationSearchProvider` (OpenStreetMap Nominatim).
- **Trip road routing** — port `RoutePlanService` (over `RoutePlanProvider`); impl
  `DefaultRoutePlanService` → `openrouteservice/OpenRouteServiceRouteProvider` (POST
  `…/driving-car/geojson`, `Authorization`-header key, GeoJSON geometry + per-gap `segments`). One
  provider, always bound; a blank key or a failed route degrades to a straight-line **haversine**
  distance with no drive time (ADR-016). Bound in `di/RoutingModule.kt`.
- **Device location (GPS)** — port `DeviceLocationProvider`; impl `AndroidDeviceLocationProvider`
  (framework `LocationManager`, GPS+NETWORK, last-known seed, **no Play Services**). Emits a **null
  fix** when permission is absent or location is off — never throws (ADR-015). Bound in
  `di/LocationModule.kt`.
- **Maps** — osmdroid raster tiles rendered by `ui/components/OsmMap`: **MapTiler** prebuilt style
  when `BuildConfig.MAPTILER_API_KEY` is set (else the keyless **CARTO** Voyager/DarkMatter fallback),
  plus an **Esri** satellite alternate. Tiles are a UI-map type, not a domain port (ADR-014); tiles
  cached by osmdroid.
- **Ticket import** — `TicketImportService`/`IrctcTicketImportService`: read PDF text
  (`PdfTextExtractor`) → parse (`IrctcTicketParser`) → `ParsedTicket` → editor fills the form.
- **Trip transfer** — `TripTransferService`/impl: export/import a trip (+ images) as an archive.

## Background processing

`UNVERIFIED` — no WorkManager dependency is present; refresh appears to be foreground/on-demand
(pull-to-refresh, screen open). The spec mentions notifications/WorkManager as future work; not yet
wired. Confirm before assuming any background scheduling exists.

## Authentication

None — the app has no user accounts or login. The only credentials are three **optional** API keys,
all read from `local.properties` → `BuildConfig` at build time and all degrading gracefully when
blank: `RAILRADAR_API_KEY` (train status/PNR; header auth → offline projection when absent),
`OPENROUTESERVICE_API_KEY` (trip road route; `Authorization` header → straight-line legs when absent),
and `MAPTILER_API_KEY` (pastel basemap → keyless CARTO tiles when absent). **Device location/GPS is
wired** (2026-08-25 Map redesign): `AndroidManifest.xml` declares `ACCESS_FINE_LOCATION` +
`ACCESS_COARSE_LOCATION` alongside `INTERNET`/`ACCESS_NETWORK_STATE`; the trip map requests the
permission at runtime and reads fixes through `DeviceLocationProvider` (framework `LocationManager`,
no Play Services — ADR-015). Denied permission is a supported state (no dot; center-on-me recentres on
the trip). Remaining spec location features (proximity-first search, proximity trip-state) are still
TODO (see [TODO.md](TODO.md)).

## Important design patterns

- **Ports & adapters** for every external capability (the §11 boundary).
- **MVVM + unidirectional state** (`StateFlow<State>`, `copy`-based updates).
- **Repository pattern** with interface/impl split across `domain`/`data` and centralized mapping.
- **Provider-selection via Hilt** `@Provides` returning an interface, choosing impl at runtime
  (`javax.inject.Provider` defers construction of the unused branch).
- **Result-as-value** for expected failures (sealed `*Outcome`, classified `*Error` enums).
- **Designed empty/placeholder states** (e.g. `AppImage` renders a category-tinted gradient instead
  of a broken-image box).
- **Deterministic engines** isolated in `domain/engine` for unit-testing without Android.

## Key dependencies (see `gradle/libs.versions.toml`)

Compose BOM `2025.06.01`, Material3, Hilt `2.56.2`, Room `2.7.1`, Navigation-Compose `2.9.0`,
DataStore `1.1.1`, Coil `2.7.0`, osmdroid `6.1.20`, Coroutines `1.10.2`. Test: JUnit4, Turbine,
`kotlinx-coroutines-test`, `org.json` (real, for parser tests), Room testing, Hilt testing.

The 2026-08-25 Map redesign added **no new dependencies**: routing uses the existing HTTP style
(`OpenRouteServiceRouteProvider`), device GPS uses the platform `LocationManager` (not Play Services),
and the pastel basemap is another osmdroid raster tile source (MapTiler) beside the CARTO/Esri ones.

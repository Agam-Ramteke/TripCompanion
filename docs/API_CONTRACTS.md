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

## External: Nominatim (OpenStreetMap geocoding / place search)

- Consumed by `NominatimLocationSearchProvider` behind `LocationSearchService` →
  `List<SearchResultLocation>`.
- **Endpoint / exact params / rate limits: `UNVERIFIED` here** — read
  `NominatimLocationSearchProvider` for the live truth before relying on specifics. Nominatim's public
  usage policy requires a valid User-Agent and low request rates; assume that applies.
- Returns place name + lat/lon (+ address bits) that seed a `Location`. Failures are classified like
  the other providers (network/timeout/rate/malformed).

## External: OpenStreetMap tiles (osmdroid)

- Map rendering only, via `ui/components/OsmMap` (osmdroid 6.1.20). No API key; osmdroid manages the
  tile source, disk cache, and User-Agent. No structured response the app parses.

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

**Policy constants (one place):** cache TTL **2 min**, request timeout **12 s** in
`DefaultTrainStatusService` / `TrainStatusService`.

**Error enums** (never raw exceptions above `data/`): `TrainStatusError` {NETWORK_UNAVAILABLE,
TIMEOUT, RATE_LIMITED, PROVIDER_ERROR, MALFORMED_RESPONSE, TRAIN_NOT_FOUND, NO_SCHEDULE, UNKNOWN};
`PnrLookupError` {NOT_CONFIGURED, NETWORK_UNAVAILABLE, TIMEOUT, RATE_LIMITED, NOT_FOUND, MALFORMED,
UNKNOWN}.

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

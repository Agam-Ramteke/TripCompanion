# RailRadar API — live train status

Source: <https://railradar.in/docs/live-train-status>, fetched 23 August 2026.
Kept here because the provider implementation has to be written against exact field
names, and the doc pages are behind a landing page that does not carry the schema.

Not affiliated with Indian Railways / IRCTC; the site describes its data as crowd-powered.
Free sandbox tier is **1,000 requests per month**, which is the number that decides the
caching policy — see "What this means for us" at the bottom.

## Auth and transport

```
GET https://api.railradar.in/v1/trains/{number}/live
Authorization: Bearer rr_live_YOUR_API_KEY
```

`X-API-Key: <key>` is accepted in place of the bearer header. HTTPS only — so unlike
`indianrailapi.com` this needs **no cleartext exception** in the network security config.

The key goes in a header rather than the URL path, which also means request URLs are safe
to log. Response bodies are not: they are keyed to a real train run.

## Query parameters

| Name | Type | Default | Notes |
|---|---|---|---|
| `date` | `YYYY-MM-DD` | today (IST) | omit to auto-detect the current run |
| `authoritative` | bool | `false` | forces an upstream telemetry fetch, skipping caches |
| `haltsOnly` | bool | `false` | only halting stops in `route[]` |
| `geometry` | bool | `false` | include route track geometry |
| `format` | `polyline` \| `geojson` \| `coordinates` | `polyline` | only with `geometry=true` |
| `includeCoordinates` | bool | `false` | adds station GPS coords to route stops |

## Response envelope

Every endpoint returns the same wrapper:

- `success` — bool
- `data` — the payload
- `meta` — `traceId`, `timestamp` (ISO-8601 `+05:30`), `executionTime` (ms), `source`

Errors: `success: false`, `error { code, message }`, same `meta`.
Codes seen: `400` malformed params, `401` bad/absent key, `404` unknown train,
`429` quota exhausted, `503` upstream telemetry degraded.

## `data`

Scalars: `trainNumber`, `trainName`, `startDate` (`YYYY-MM-DD`), `lastUpdatedAt` (ISO-8601),
`status` (`"running"`), `delayMinutes` (int), `isLive` (bool).

`data.train` — `number`, `name`, `type`, `category`, `source { code, name }`,
`destination { code, name }`, `runDays` (`["mon"…"sun"]`), `distance` (km), `duration` (min),
`avgSpeed`, `maxSpeed`, `totalHalts`, `returnTrain`.

`data.currentLocation` — `stationCode`, `sequence`, `status` (`"departed"`), `isHalt`,
`isDiverted`, `isActualPosition`, `segmentProgress` (0.0–1.0), `speedKmh`, `bearingDegrees`.

`data.previousHalt` / `data.nextHalt` — `stationCode`, `stationName`, `sequence`, `distance`.

`data.route[]` — `sequence`, `stationCode`, `stationName`, `isHalt`, `lat`, `lng`,
`scheduledArrival`, `scheduledDeparture`, `actualArrival`, `actualDeparture`,
`delayArrival`, `delayDeparture`, `status` (`"departed"` / `"upcoming"`), `distance`,
`speedToNextStationKmph`, `platform`.

All timestamps are **full ISO-8601 with a `+05:30` offset**, not the bare `"04:27PM"`
strings the older provider used. Nulls are real nulls, not `"-"`.

`data.exceptions[]` — `type` (e.g. `"DIVERTED"`), `message`, `partiallyCancelled`,
`rescheduled`, and for a diversion: `diverted { from, to, divertedStations[],
skippedStations[], hasReversal, geometry, distanceKm }`.

## Other endpoints

Trains: `/{number}` (timetable, halts, run days), `/{number}/live`, `/{number}/route`
(GeoJSON), `/{number}/coaches`, `/{number}/coaches/{station}`, `/{number}/seats`,
`/{number}/fare`, `/between/{from}/{to}`.

PNR: `/pnr/{pnr}`, `/pnr/{pnr}/prediction`, `/pnr/{pnr}/refund`.

Stations: `/stations/{code}/trains`, `/stations/{code}/live`.

Lookup: `/lookup/search/stations`, `/lookup/search/trains`, `/lookup/stations`,
`/lookup/trains`, plus `…/compressed` variants that stream `CODE|Name` lines.

## What this means for us

Three things this API has that `indianrailapi.com` did not, each of which removes a
compromise already documented in the code:

1. **Platform is a real field** (`route[].platform`), so the user-entered platform on
   `Train` stops being the only source.
2. **Speed is a real field** (`currentLocation.speedKmh`, `route[].speedToNextStationKmph`),
   so `TrainProgress`'s derived "Avg speed" no longer has to stand in for it.
3. **Coordinates on every stop** (`lat` / `lng`), which is what a live map pin needs.

Against that, 1,000 requests a month is ~33 a day. The existing cache-and-stamp policy —
snapshot in Room with `fetchedAt`, refresh at most once every couple of minutes while
foregrounded, always render the cached snapshot with "updated N min ago" — is what keeps
that budget from being burned by a screen rotation, so it stays. `authoritative=true` is
deliberately never sent: it is the flag that skips the vendor's own cache.

`/pnr/{pnr}` is worth noting beside the e-ticket import: the PDF gives the booking as it
was sold, and this endpoint gives the same PNR as the chart has it now.

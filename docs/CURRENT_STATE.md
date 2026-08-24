# CURRENT_STATE.md — live project handoff

> **Read this first after any compaction or new session.** It is the single place that says what
> is being worked on right now and what to do next. Update it whenever major implementation state
> changes, and always before ending a session or running low on context.
>
> Source code is the ultimate truth; this file is the memory of *intent and progress* that the code
> alone doesn't carry.

**Last updated:** 2026-08-25 · **App version:** 3.0 (`versionCode 3`) · **DB schema:** v6 · **Branch:** `main`

---

## Current Goal

**DONE — all pending work committed to `main`.** Two commits just landed (2026-08-25):

1. **`1211106` — "Carry activity card-background photo through trip export/import"** — the Task 3
   follow-up that wired `backgroundImageUri` through `TripManifest` encode/decode and
   `TripTransferServiceImpl` carry/local, plus `TripManifestTest` and a strengthened
   `EntityMapperTest`.
2. **Trip Map redesign — "unified premium travel HUD"** — the major map redesign (plan
   `.claude/plans/snug-skipping-tiger.md`, ADR-014…019) landing MapTiler/CARTO basemap, numbered
   status markers, device GPS, ORS road routing with per-leg legs, redesigned itinerary sheet,
   Navigate → Google Maps, and all supporting infrastructure. On-device screenshot-verified
   2026-08-25.

Both commits land three of the four **Map & location epic** sub-items — map redesign, **device GPS**,
and **navigation → Google Maps**. **Proximity-first search is the one epic item still pending.**

The next code task is the user's pick among the remaining backlog (details in [TODO.md](TODO.md)):

1. **Proximity-first place search** (the leftover Map-epic item) — feed a viewport/anchor (trip
   locations, map camera, or the now-available device GPS) into `NominatimLocationSearchProvider`
   (`viewbox`+`bounded=0` plumbing already exists) and sort results by distance via `GeoUtils`.
2. **Stay check-in / check-out (requested 2026-08-24, "future but essential").** A `STAY` activity
   should carry **two** datetime fields — **check-in** and **check-out** — and, when the stay spans
   days, appear as **two** itinerary entries (check-in on arrival day, check-out on departure day).
   Likely a schema **v7** migration; design not yet settled.
3. **Back-navigation fix (requested 2026-08-24, "for later").** System Back should return to
   **Home** from anywhere, **except** Settings and the activity editor.

## Current Status

- ✅ **Trip Map redesign — COMMITTED on `main` (2026-08-25).** A premium travel-HUD redesign of the
  itinerary map (plan `.claude/plans/snug-skipping-tiger.md`, Workstreams A–J): keyed **MapTiler**
  pastel basemap with graceful **CARTO** fallback; **numbered status markers** (Completed/Upcoming/
  Current) whose numbers match the sheet, state derived from `TripStateEngine`; route **casing**
  under a refined-blue line; **fit-the-day camera** + animate-to-tapped-stop; **device GPS**
  (framework `LocationManager` behind `DeviceLocationProvider`, runtime permission, live dot +
  accuracy ring + center-on-me); **per-leg travel** (ORS `segments` → `RouteLeg`, haversine
  fallback) in a redesigned M3 draggable sheet; **NEXT STOP** card with Navigate
  (`ExternalNavigator` → Google Maps) / Details; **"Day N · <trip name>"** top pill. Unit tests +
  `assembleDebug` green. On-device verified (Workstream J) 2026-08-25, all checks passed.
- ✅ **Task 3 — Activity-card background (schema v6)** — DONE & **user-confirmed on-device**
  (2026-08-24). Committed on `main`.
- ✅ **Task 3 follow-up — export/import round-trip for `backgroundImageUri`** — DONE (2026-08-24),
  unit-tested, **committed on `main` (2026-08-25)**.
- ✅ **RailRadar integration** (live status, schedule, PNR lookup) — implemented, unit-tested,
  device-verified. Committed on `main`.
- ✅ **Next-up card photo backdrop** — activity next-up cards show the activity photo blurred +
  faded; **train** next-up cards stay solid; the grey "shadow-slab" bug is fixed. Committed on `main`.
- ✅ **Auto-rotation fixed & device-verified (2026-08-24)** — `MainActivity` locked to portrait.
  Committed on `main`.
- ✅ **Persistent memory layer** (this doc system: `CLAUDE.md` + `docs/*`) — created & committed.
- 📦 **On `main`** (ahead of `origin/main` — **not pushed**): all of the above. Working tree is
  **clean**.
- ⚠️ **RailRadar API key format** unverified: current key is `rg_e04caaa9deb04b8c849f5411d7bdec0e`;
  vendor docs show `rr_live_…`. App degrades gracefully if rejected (falls back to projection), but
  labels status "Live" whenever a key is merely *present*. Watch `logcat` for `401`.

## Active Work

**None — awaiting user's pick for the next feature.** All pending code is committed on `main`.
Working tree is clean. The three candidate features are listed under *Current Goal* above and
detailed in [TODO.md](TODO.md).

Optional: to see the pastel MapTiler basemap instead of the CARTO fallback, add
`MAPTILER_API_KEY=…` to `local.properties` (git-ignored).

## Committed on `main` — summary

| Commit | Description |
|--------|-------------|
| `1211106` | Carry activity card-background photo through trip export/import |
| *(next)* | Redesign Trip Map into a unified premium travel HUD |
| `f247c3f` | (earlier) RailRadar; photo backdrop + shadow fix; portrait lock; project docs |

## Current Architecture State (only what's relevant to active work)

- **Home next-up photo source:** `HomeViewModel.nextUpImageUri(event, place, trip)`
  (in [HomeViewModel.kt](../app/src/main/java/com/tripcompanion/app/feature/trip/HomeViewModel.kt))
  now resolves, in order: **`event.backgroundImageUri` (Task 3, user-set) →** STAY→`StayDetails.photoUri`
  → `Location.photoUri` → first `PlannedPhoto.referenceImageUri` for the event → `Trip.coverImageUri`.
- **`Event.backgroundImageUri`** is the Task 3 field, persisted via schema v6 (`MIGRATION_5_6`).
- **Map basemap:** MapTiler (pastel DataViz style) when `MAPTILER_API_KEY` is set in
  `local.properties`; CARTO Voyager/DarkMatter fallback when unkeyed. Tile source stays in the
  UI-map layer (osmdroid, not promoted to a domain port; only the key crosses via `BuildConfig`).
- **Device GPS:** `DeviceLocationProvider` port → `AndroidDeviceLocationProvider` (framework
  `LocationManager`, GPS+NETWORK, no Play Services). Missing permission = null fix, never thrown.
- **Routing:** `RoutePlanService` port → `DefaultRoutePlanService` → `OpenRouteServiceRouteProvider`
  (ORS driving-car/geojson, `segments`→`RouteLeg`). Haversine fallback when ORS is unavailable or
  returns partial legs.
- **Navigation:** `ExternalNavigator.navigateTo()` → `google.navigation:q=lat,lon` → `geo:` →
  silent no-op.

## Current Bugs

- **None open/blocking.** Bugs hit earlier (schedule MALFORMED; grey slab; auto-rotation) are all
  fixed and verified — see *Do Not Redo* so they aren't reintroduced.
- **Watch item (not a confirmed bug):** RailRadar key may `401` (see *Current Status*). If live
  fetches fail, confirm the key in the RailRadar dashboard; the app still works on the projection.

## Unresolved Questions

- **Ordering of the remaining backlog** — Map epic vs. Stay check-in/check-out vs. Back-navigation
  fix. User to pick which is next.
- **Stay check-in/check-out — design not settled:** store two datetimes on the event/`StayDetails`
  and split into two itinerary rows at the grouping layer, *or* model two linked events? If persisted
  as new columns it's another migration (v7). Decide before building.

## Next Steps (exact, in order)

1. **(Optional)** User may add `MAPTILER_API_KEY=…` to `local.properties` (git-ignored) to swap the
   pastel MapTiler basemap in for the CARTO fallback.
2. **Await the user's pick** for the next feature: **proximity-first search** (the leftover Map-epic
   item), **Stay check-in/check-out**, or the deferred **Back-navigation fix** (all in [TODO.md](TODO.md)).

## Do Not Redo (settled — don't revert without a strong, documented reason)

- **Don't** re-add the `indianrailapi.com` vendor or a cleartext network exception — RailRadar
  replaced it; app is TLS-only.
- **Don't** re-add the "Transgender" gender chip to the train editor UI (removed per user request;
  the `PassengerGender.TRANSGENDER` enum value stays in the model).
- **Don't** reintroduce `fallbackToDestructiveMigration` in `DatabaseModule`.
- **Don't** revert the RailRadar schedule parser to reading flat `arrival`/`departure` — the real
  payload nests them under `station:{}` (the MALFORMED bug).
- **Don't** delete a `JOURNEY` event when a train is deleted.
- **Grey-slab fix:** don't give a transparent `Surface`/card a non-zero `shadow` elevation; keep
  `TrainCard` at `elevation = 0.dp` when it sits inside an already-elevated container.
- **Task 3:** keep the next-up **train** cards solid (no `PhotoBackdrop`); the background photo is an
  **activities-only** feature.
- **Map redesign (ADR-014…019):** basemap **tiles stay in the UI-map layer** (osmdroid type) — do not
  promote them to a domain port; only the MapTiler *key* crosses via `BuildConfig`. GPS uses the
  **framework `LocationManager`, not Play Services** (no new dep); a missing permission is a **null
  fix**, never a thrown exception. Marker **numbers must equal** the sheet's `orderInDay` and state
  comes from `TripStateEngine` — **no** type-glyph/place-typed primary pins (ADR-004). A **partial**
  ORS leg breakdown is **discarded wholesale** (haversine, no drive time) — never paired against the
  wrong gap. The top pill is **"Day N · <trip name>"** by design — the name-over-a-different-city case
  is an **accepted tradeoff**, not a bug to "fix" with a reverse-geocode.

## Environment / device verification

- **adb** is not on PATH: `C:\Users\agamr\AppData\Local\Android\Sdk\platform-tools\adb.exe`.
- **Device id** `00162352E001788` (product AsteroidsIND, model A059) is **flaky** — if "device not
  found," run `adb kill-server` → `adb start-server` → `adb wait-for-device`, then retry.
- **Screenshots:** `adb -s <id> exec-out screencap -p > /e/Projects/Trip/.verify/x.png`, then Read
  the Windows path `E:\Projects\Trip\.verify\x.png` (the `/tmp` path is not readable by the Read tool).
  `.verify/` is scratch (git-ignored) — clean it up after. Device is ~1080×2392; screenshots display
  at 903×2000 (multiply displayed coords by 1.20 for raw device pixels).

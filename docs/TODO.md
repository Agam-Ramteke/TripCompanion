# TODO.md

Work queue for Trip Companion. Living document — check items off, move them between sections, and add
new ones as they surface. Detailed *in-flight* status (what's half-done, what to do next step by
step) lives in [CURRENT_STATE.md](CURRENT_STATE.md); this file is the backlog overview.

**Last updated:** 2026-08-25 (post-commit)

---

## 🔴 Current (active now)

- [x] **Task 3 — Activity-card background customization — DONE & user-confirmed (2026-08-24).**
      Added `Event.backgroundImageUri` end-to-end (domain → `EventEntity` → `EntityMappers` → Room
      `MIGRATION_5_6` + exported `6.json` + `MigrationTest` 5→6 cases → `EventEditorViewModel`/State →
      a **compact** "Card background" picker in `EventEditorScreen`). `HomeViewModel.nextUpImageUri`
      now prefers it; `PhotoBackdrop` fade lightened (0.42/0.60/0.80) so the chosen photo shows
      through. Opacity ambiguity resolved (lightened the wash). Unit tests + `assembleDebug` green;
      committed on `main`.
- [x] **Task 3 follow-up — export/import round-trip — DONE (2026-08-24), unit-tested, committed
      on `main` (2026-08-25).** The hand-written trip-file manifest omitted the new `backgroundImageUri`,
      so it was silently dropped on a trip export→import (no crash — forgiving reader defaulted null —
      but the photo was lost). Fixed 4 points mirroring cover/place/stay images:
      `TripManifest.encode`/`decode` + `TripTransferServiceImpl` export `carry`/import `local`. New
      `TripManifestTest`; strengthened `EntityMapperTest`. `TripArchive.VERSION` unchanged (additive
      optional field).
- [ ] **Await the user's pick** for the next feature (below): **proximity-first search** (the one
      leftover Map-epic item), the new **Stay check-in/check-out** feature, or the deferred
      **Back-navigation fix**. *(All prior work — Task 3, Task 3 follow-up, and the full Map
      redesign — is committed on `main`. Working tree is clean. See
      [CURRENT_STATE.md](CURRENT_STATE.md).)*

## 🟠 Next (soon, after current)

- [x] **Commit the RailRadar + photo + docs work — DONE (2026-08-24).** Landed on `main` as 4
      commits (tip `f247c3f`): RailRadar; photo backdrop + shadow fix; portrait lock; project docs.
      Secrets confirmed git-ignored (`local.properties`, `docs/Trip/*.pdf`). `main` is ahead of
      `origin/main` — **not pushed**. Task 3 will be its own commit(s) when it lands.
- [ ] **Confirm the RailRadar API key.** Verify `rg_…` vs `rr_live_…` in the RailRadar dashboard;
      watch `logcat` for `401` on a live fetch. If rejected, re-issue. (See
      [API_CONTRACTS.md](API_CONTRACTS.md).)
- [ ] **Verify RailRadar time format** against a real live response (`HH:mm` vs ISO) and tighten the
      parser if needed (currently accepts both) — [API_CONTRACTS.md](API_CONTRACTS.md).

## 🗺️ Map & location epic (requested 2026-08-24)

Three of four sub-items **DONE** in the 2026-08-25 **Trip Map redesign** (plan
`.claude/plans/snug-skipping-tiger.md`; ADR-014…019). Code-complete + unit-tested + APK built +
**on-device verified 2026-08-25** + **committed on `main` (2026-08-25)**. Only **proximity-first
search** remains as a feature.

- [x] **Map redesign — DONE (2026-08-25), on-device verified, committed on `main`.** Superseded the original literal
      mockup with a **"unified premium travel HUD"**: keyed **MapTiler** pastel basemap that *recedes*
      (graceful **CARTO** fallback when unkeyed); **numbered status markers** (Completed/Upcoming/
      Current) whose numbers match the sheet, state from `TripStateEngine` — **not** place-typed glyph
      pins (ADR-004/017); refined-blue route with a darker **casing**; **fit-the-day** camera +
      animate-to-tapped-stop; minimal floating top bar with a **"Day N · <trip name>"** pill + day
      switcher; M3 draggable **itinerary sheet** (collapsed preview → expanded numbered stops with
      per-leg travel "min · km · 🚗", status) + **NEXT STOP** card. Lives in
      [OsmMap.kt](../app/src/main/java/com/tripcompanion/app/ui/components/OsmMap.kt) +
      [TripMapScreen.kt](../app/src/main/java/com/tripcompanion/app/ui/screens/TripMapScreen.kt) +
      [TripMapViewModel.kt](../app/src/main/java/com/tripcompanion/app/feature/map/TripMapViewModel.kt).
      (Per-leg travel added an ORS `segments`→`RouteLeg` contract extension — ADR-016.)
- [x] **Device location (GPS) — DONE (2026-08-25), on-device verified, committed on `main`.** Manifest now declares
      `ACCESS_FINE/COARSE_LOCATION`; runtime `RequestMultiplePermissions`; framework `LocationManager`
      behind the `DeviceLocationProvider` port (impl `AndroidDeviceLocationProvider`, **no Play
      Services** — ADR-015); live dot + accuracy ring + center-on-me on the map. Degrades gracefully
      when denied (no dot; center-on-me recentres on the trip). *(Verified on-device: permission
      dialog → grant → live dot + accuracy ring, center-on-me animated to the real fix, and a live
      distance appeared on the NEXT STOP card.) (This unblocks GPS as a search anchor for the proximity
      item below.)*
- [x] **Navigation → Google Maps — DONE (2026-08-25), on-device verified, committed on `main`.** The NEXT STOP /
      stop-card **Navigate** action fires `core/util/ExternalNavigator.navigateTo` →
      `google.navigation:q=<lat>,<lon>`, `geo:` fallback, silent no-op if neither resolves (ADR-018).
      No in-app turn-by-turn.
- [ ] **Proximity-first search — the one remaining epic item. FEASIBLE, plumbing already exists.**
      [NominatimLocationSearchProvider.kt](../app/src/main/java/com/tripcompanion/app/data/network/NominatimLocationSearchProvider.kt)
      already accepts a `SearchViewport` and, when given one, sends `viewbox=west,north,east,south`
      + `bounded=0` to **bias** toward that area (its KDoc: "the single biggest improvement"). Global
      results today mean **no viewport is being passed at the call site** (the search VM/service).
      Work: (a) supply a viewport/anchor — from the **trip's locations**, the **map camera**, or now
      **device GPS** (wired above); (b) for guaranteed closest-first, **sort results by distance** to
      the anchor using `core/util/GeoUtils`.

## 🏨 Stay check-in / check-out (requested 2026-08-24 — "future but essential")

A `STAY` activity currently uses the generic single **Date + Starts/Ends + "Ends the next day"**
model (as seen in the "Add an activity → Stay" editor). The user wants stays to carry **two explicit
datetime fields instead**:

- [ ] **Check-in (date + time)** and **check-out (date + time)** fields, surfaced when Kind = Stay.
- [ ] When the stay **spans multiple days**, the itinerary shows it as **two separate entries** — a
      **check-in** item on the arrival day and a **check-out** item on the departure day (each on its
      own day) — rather than one card that merely "ends the next day".
- [ ] **Design to settle first:** store two datetimes (on `Event`/`StayDetails`) and derive two
      itinerary rows at the grouping layer, *or* model two linked events. If persisted as new
      columns → another Room migration (**v7**) + exported schema + `MigrationTest` case (no
      destructive fallback — ADR-006). Keep the generic-engine principle: branch on
      `event.type == STAY`, never a place name.

## 🐞 Bugs

- [x] **Unwanted screen auto-rotation on app open — FIXED & device-verified (2026-08-24).**
      Root cause: `MainActivity` in [AndroidManifest.xml](../app/src/main/AndroidManifest.xml) had
      **no `android:screenOrientation`**, so the app followed the device and rotated. Fix applied:
      added `android:screenOrientation="portrait"` to the `<activity>`. Nothing calls
      `setRequestedOrientation` in code, so this is the complete cause. **Verified on-device
      2026-08-24:** with auto-rotate ON, forcing the display to landscape left the app portrait.
      Note: this locks the *whole* single-Activity app to portrait, including the map — revisit if
      the redesigned map should allow landscape. osmdroid's *map* rotation gestures in `OsmMap.kt` are
      unrelated (map orientation ≠ screen).
- **None other open.** Two were fixed & device-verified this session (kept here so they aren't
  reintroduced — see [CURRENT_STATE.md](CURRENT_STATE.md) *Do Not Redo*):
  - ✅ RailRadar schedule "MALFORMED" — station nested under `station:{}`, parser fixed.
  - ✅ Grey "shadow-slab" on the next-up card — transparent `Surface` + non-zero `shadow` elevation;
    `TrainCard` now takes `elevation`, Home passes `0.dp`.

## 🟡 Later (someday / not scheduled)

- [ ] **Back-navigation to Home (requested 2026-08-24, "for later").** System **Back** should
      navigate to the **Home** screen from anywhere, **except** the **Settings menu** and the
      **activity editor**, where Back keeps its normal behaviour. Today Back just pops the last
      screen. Touches back-stack handling in `AppNavHost`/`Routes` (single-Activity
      Navigation-Compose). Confirm the exact set of "excepted" screens before building.
- [ ] **Notifications / background refresh (WorkManager)** — spec future-work; no WorkManager dep yet
      ([ARCHITECTURE.md](ARCHITECTURE.md) → Background processing). Only add if a real need appears.
- [ ] **Proximity-based trip-state (unblocked by the 2026-08-25 GPS wiring).** Now that
      `DeviceLocationProvider` exists, `TripStateEngine`'s time-based "current event" could be
      complemented by device **proximity** (which stop you're actually at). Not built; keep the
      generic-engine principle (derive from geometry + `EventType`, never a place name).
- [ ] Broaden ticket import beyond IRCTC format if other formats are needed.

## 🧹 Technical debt / cleanups

- [x] **Migration test coverage 5→6 — DONE (Task 3).** `MigrationTest` has `LATEST_VERSION = 6`, a
      `seedVersion5()` helper, and two 5→6 cases (schema validates; an upgraded event has a null
      `backgroundImageUri`). Instrumented — run on a device to execute.
- [x] **Transfer layer now has round-trip coverage — DONE (2026-08-24).** `TripManifestTest` is the
      first test for `data/transfer` (previously none). It locks the hand-written manifest against
      the exact silent-drop class of bug (a domain field forgotten in `encodeEvent`/`decodeEvent`).
      Service-level `carry`/`local` is still only covered indirectly — a full zip round-trip test
      (real archive + temp image dir) would close that gap if the transfer layer grows.
- [ ] Confirm `Converters` stored representation for `LocalDateTime` is documented somewhere durable
      (currently `UNVERIFIED` in [ARCHITECTURE.md](ARCHITECTURE.md)).
- [x] **Map/routing test coverage — DONE (2026-08-25).** `TripMapViewModelTest` (haversine fallback /
      routed passthrough / partial-leg discard, over real repos + `InMemoryTripDatabase`) and the
      extended `OpenRouteServiceRouteProviderTest` (segment→leg parse, real `org.json`) are the first
      tests for the map/routing path.
- [ ] The implemented Room schema is a pragmatic subset of spec §18 (no `Traveler`/`Task` tables);
      keep [ARCHITECTURE.md](ARCHITECTURE.md) honest about the gap as the model evolves.

## 💡 Ideas (unfiltered, not commitments)

- Per-event "card background" could later extend to trains (currently trains are intentionally solid).
- Trip export could gain a shareable read-only view.

---

*When you finish something here, also update [CURRENT_STATE.md](CURRENT_STATE.md) if it changes the
"what's happening now" picture.*

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

---

*If you add a decision here, also enforce it where it lives (code/comment/test) — a decision only
recorded in this file but not in the code will rot.*

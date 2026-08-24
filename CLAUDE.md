# CLAUDE.md — Trip Companion (Android)

> **This file and `docs/` are the project's persistent memory.** They — not the chat
> history — are the source of truth for context. Conversation history is temporary working
> memory; the source code is the ultimate source of truth; these documents are the layer that
> survives compaction, `/compact`, context-window limits, new sessions, and interrupted work.
>
> **Before substantial work, read `docs/CURRENT_STATE.md` first** (it is the live handoff), then
> this file, then the relevant `docs/` files, then the actual source. See *Context-Preservation
> Workflow* below.

---

## Project Overview

**Trip Companion** is a **native Android** trip-planning and live-companion app. It turns a
user-entered itinerary into a glanceable travel HUD that answers, at any moment: *Where am I?
What am I doing now? Where next? What do I need there?* — combining the stored plan with the
current time (and, per spec, GPS) and honoring manual overrides when reality deviates from plan.

- **Primary purpose:** a *generic trip engine* — one data model + one set of screens render **any**
  trip. There is deliberately **no place-specific code path**; only `event.type` and
  `location.category` (data fields) drive behavior. "Udaipur", "Agra", etc. appear **only** in
  seed/sample data ([SampleTripSeeder.kt](app/src/main/java/com/tripcompanion/app/data/SampleTripSeeder.kt)),
  never as literals in logic. Grepping those names anywhere else is a defect (spec §3, §18).
- **Major functionality:** trip/itinerary CRUD; a Home "command center" with a *next-up* card; a
  timeline; an OpenStreetMap trip map; **train journeys** (booking editor, IRCTC e-ticket PDF
  import, live status / schedule via RailRadar, PNR lookup); hotel/stay details; photo inspiration
  & planned shots; light/dark theming; offline-first local storage; trip export/import.
- **Deliberately NOT:** a booking engine, social network, offline-maps replacement, or
  LLM-driven planner. Trip progression/schedule/state logic is **deterministic native code** (spec §1).
- **Product spec (numbered `§` sections referenced throughout the code):**
  [docs/Udaipur_Trip_Companion_Product_Spec_v2.md](docs/Udaipur_Trip_Companion_Product_Spec_v2.md).

## Technology Stack

| Concern | Choice |
|---|---|
| Language | Kotlin `2.0.21` (JDK 17) |
| UI | Jetpack Compose (BOM `2025.06.01`), Material 3, `material-icons-extended` |
| Navigation | Navigation-Compose `2.9.0` (single-Activity) |
| State | `ViewModel` + Coroutines `1.10.2` + `StateFlow` (MVVM) |
| DI | Hilt `2.56.2` (KSP) |
| Local DB | Room `2.7.1` (KSP), schema **v5**, schemas exported to `app/schemas/` |
| Preferences | DataStore Preferences `1.1.1` (theme) |
| Images | Coil `2.7.0`; images are app-private `file://` URIs only (no remote image URLs) |
| Maps | osmdroid `6.1.20` (OpenStreetMap, **no API key**) |
| Build | AGP `8.7.3`, Gradle Kotlin DSL, version catalog `gradle/libs.versions.toml` |
| Tests | JUnit4, `kotlinx-coroutines-test`, Turbine, `org.json` (real, for parser tests), Room `MigrationTestHelper`, Hilt testing |
| SDK | `minSdk 26`, `targetSdk 35`, `compileSdk 35` |
| App id / version | `com.tripcompanion.app`, `versionCode 3` / `versionName "3.0"` |

Single Gradle module `:app` (rootProject `TripCompanion`). No backend server — the only network
calls are to **RailRadar** (trains), **Nominatim** (geocoding search), and OSM tile servers.

## Architecture (high level)

Clean, layered, one-way dependencies. Full detail in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md); decisions in [docs/DECISIONS.md](docs/DECISIONS.md).

```
Compose UI (ui/screens, ui/components, ui/theme, ui/navigation)
        │  observes StateFlow, calls VM methods
Feature ViewModels (feature/*)                     ← MVVM, @HiltViewModel, StateFlow<UiState>
        │  call domain interfaces
Domain (domain/model, domain/repository, domain/service, domain/engine)   ← pure Kotlin, no Android/HTTP
        │  implemented by
Data (data/repository, data/network, data/local[Room], data/ticket, data/pdf, data/transfer, data/prefs)
        │
Room DB  •  RailRadar/Nominatim HTTP  •  OSM tiles  •  filesystem (images, archives)
```

- **The `§11` boundary is load-bearing:** nothing about HTTP, JSON, API keys, or *which* vendor
  is in use is visible above `data/`. Providers implement a domain **port** (e.g.
  [`TrainStatusProvider`](app/src/main/java/com/tripcompanion/app/domain/service/TrainStatusProvider.kt),
  `LocationSearchProvider`, `PnrLookupService`) and are selected in a Hilt module. Errors cross the
  boundary as classified enums/sealed outcomes, never as exceptions with HTTP vocabulary.
- **Data flow:** DAO `Flow` → repository (entity→domain mapping in
  [EntityMappers.kt](app/src/main/java/com/tripcompanion/app/data/local/EntityMappers.kt)) →
  ViewModel combines/derives `UiState` → Compose renders. User actions call VM → repository →
  Room; the `Flow` re-emits and the UI updates.
- **Current Trip State is computed, never stored** — derived in `domain/engine`
  ([TripStateEngine.kt](app/src/main/java/com/tripcompanion/app/domain/engine/TripStateEngine.kt),
  `TrainProgress.kt`, `TripStats.kt`) from events + time (+ GPS, per spec).
- **Train live status** has two swappable providers behind one port: **RailRadar** (live) when an
  API key is present, else an **offline schedule projection**. Selection lives in
  [TrainModule.kt](app/src/main/java/com/tripcompanion/app/di/TrainModule.kt). Cached snapshots in
  Room; refresh TTL 2 min; request timeout 12 s.
- **Persistence:** Room (`trip_companion.db`) with explicit migrations
  ([Migrations.kt](app/src/main/java/com/tripcompanion/app/data/local/Migrations.kt), `MIGRATION_1_2 … 4_5`).
  **No `fallbackToDestructiveMigration`** — an unhandled version must fail loudly.
- **Auth:** none (no accounts). The only secret is the RailRadar API key, read from
  `local.properties` (git-ignored) into `BuildConfig.RAILRADAR_API_KEY` at build time.

## Critical Constraints — do NOT change these without explicit instruction

1. **Preserve the generic-engine principle.** Never branch logic on a place's name; branch only on
   `event.type` / `location.category`. Never add "Udaipur"/"Agra"/etc. outside seed data (spec §3, §18).
2. **Preserve the `data/` boundary (§11).** Do not leak HTTP/JSON/vendor types above `data/`. New
   external capabilities go behind a `domain/service` interface with a Hilt-selected implementation.
3. **Preserve existing architecture.** Do not introduce a competing DI framework, a second HTTP
   client style, a second image loader, or an alternative navigation approach. Match the layered
   MVVM + Hilt + Room + StateFlow structure already here.
4. **Deleting a train must NOT delete its `JOURNEY` event.** The event outlives the booking; see
   [JourneyEventLinker.kt](app/src/main/java/com/tripcompanion/app/domain/service/JourneyEventLinker.kt).
5. **Do not re-add the removed IRCTC vendor** (`indianrailapi.com`). RailRadar replaced it
   deliberately; the app is now **TLS-only** (no cleartext exception in `network_security_config.xml`).
6. **Do not reintroduce `fallbackToDestructiveMigration`** in the shipping DB config. Schema changes
   require a real, tested `MIGRATION_n_(n+1)` + an exported schema JSON (see `.claude/rules/data-layer.md`).
7. **Do not break API contracts** (Room schema, `Routes` argument names, `domain/service`
   interfaces, `ParsedTicket`) without documenting the change in
   [docs/API_CONTRACTS.md](docs/API_CONTRACTS.md) and [docs/DECISIONS.md](docs/DECISIONS.md).
8. **Do not replace a working implementation** just because another is possible, or do a large
   refactor, without first explaining in `docs/DECISIONS.md` why it is necessary.
9. **Never commit secrets.** `local.properties`, real e-tickets (`docs/Trip/*.pdf`), and `.claude/`
   are git-ignored on purpose.

## Development Rules (project-specific)

- **Code voice / comments.** This codebase comments the *why*, not the *what*, in a distinctive
  prose voice, often citing spec sections (`§14`). Match the surrounding density and tone; do not
  strip existing comments or add narration of obvious code.
- **Naming.** Domain models are plain Kotlin data classes in `domain/model`; Room rows are
  `*Entity` in `data/local/entity`; interfaces in `domain/service` end in `Provider`/`Service`;
  ViewModels end in `ViewModel`; Compose screens end in `Screen`. Keep this.
- **Error handling.** Cross the `data/` boundary with classified enums / sealed `*Outcome` types
  (see `TrainStatusOutcome`, `TrainStatusError`). Failures the user routinely hits (offline on a
  train) are **values**, not thrown exceptions. Providers throw a classified exception internally;
  the service turns it into a value.
- **Networking.** HTTPS only. Header auth for RailRadar (`X-API-Key`). Respect the metered free
  tier (1000 req/month) — cache in Room, honor the TTL, never send `authoritative=true`.
- **Persistence / DB.** See constraint 6. Bump `@Database(version=…)`, add a `Migration`, export the
  schema, and add a `MigrationTest` case. Entity ⇄ domain mapping goes in `EntityMappers.kt`.
- **UI conventions.** Material 3 + the app's own theme tokens (`ui/theme`, `AppThemeExtended`,
  `AppMetrics`). Reuse `ui/components` primitives (`AppCard`, `AppMediaCard`, `PrimaryButton`,
  `AppImage`, `PhotoBackdrop`, etc.) rather than raw Material where a wrapper exists. Text stays
  legible on any user photo (scrims/backdrops), and copy is templated (`"…{eventName}"`), never a
  baked-in place name.
- **Testing.** Pure logic (parsers, engines, mappers, VMs) has JVM unit tests under `app/src/test`;
  DB migrations have instrumented tests under `app/src/androidTest`. Add/adjust tests with behavior
  changes. See `.claude/rules/testing-and-verify.md`.
- **Dependencies.** Add via the version catalog `gradle/libs.versions.toml` only. Avoid adding a
  library that duplicates one already present (Coil, osmdroid, Hilt, Room, DataStore).
- **Security.** No secrets in code or VCS; app-private storage for user media; TLS-only.

## Context-Preservation Rules

- Treat `CLAUDE.md` and `docs/` as **persistent state**. Do **not** assume the previous
  conversation is still available.
- **Before beginning substantial work**, read the relevant persistent docs (workflow below), then
  check them against the actual source — **assume docs may be stale** and correct them.
- **After** any architectural change, API/schema change, or important implementation decision,
  **update the relevant doc in the same session**.
- **Preserve, never summarize away:** unresolved bugs, failed approaches, important assumptions,
  API contracts, DB/schema decisions, the files currently being modified, and the exact next step of
  an incomplete task. Do not drop a critical technical detail merely because it is old.

**When context compaction occurs, prioritize preserving (in this order):**
1. Current objective
2. Current implementation state
3. Files being modified
4. Architectural decisions
5. API / database contracts
6. Known bugs
7. Failed approaches
8. Important constraints
9. Remaining work
10. Exact next action

These live in [docs/CURRENT_STATE.md](docs/CURRENT_STATE.md) — keep it current so the list above is
never lost.

## Context-Preservation Workflow

**Before major work**
1. Read `CLAUDE.md` (this file).
2. Read `docs/CURRENT_STATE.md` (live handoff — current goal, state, files, next action).
3. Read the relevant `docs/ARCHITECTURE.md` / `docs/DECISIONS.md` / `docs/API_CONTRACTS.md` sections.
4. Inspect the actual source for the area you're touching (targeted reads/searches — see below).
5. Decide whether the docs are stale; if so, correct them before/with your change.

**During work — update docs when any of these happen:**
- architecture changes • an API changes • the DB schema changes • an important implementation
  decision is made • a debugging approach succeeds or fails • a major bug is found • the active task
  changes. (Route each to `ARCHITECTURE`, `API_CONTRACTS`, `DECISIONS`, or `CURRENT_STATE`/`TODO`.)

**Before compaction / running low on context**
Ensure `docs/CURRENT_STATE.md` contains: current objective, work completed, work in progress, files
being modified, current bug/state, failed attempts, and the **next exact action**.

**After compaction / new session**
Immediately re-read `CLAUDE.md` and `docs/CURRENT_STATE.md`, then inspect the files relevant to the
active task. Do **not** assume the prior conversation is available.

**Before ending a session**
Update `docs/CURRENT_STATE.md` (and `docs/TODO.md`) so the next session can continue immediately.

## Session Handoff Protocol

Produce a **persistent** handoff whenever a session ends or the user asks for one. **Write it to
the project files — especially `docs/CURRENT_STATE.md` — never rely on a chat reply alone.** A
handoff must include:
- What was being built • What was completed • What changed • **Files changed** • Current
  implementation details • Bugs • Failed approaches • Decisions • Unresolved questions •
  **Exact next steps**.

Mirror committed decisions into `docs/DECISIONS.md`, contract changes into `docs/API_CONTRACTS.md`,
and remaining work into `docs/TODO.md`, so no single file has to carry everything.

## Protect Against Context Loss (token discipline)

- Prefer **targeted reads** and **searches** (Grep/Glob) over reading whole large files; search
  before reading, and read only the ranges you need.
- Do **not** re-read unchanged files without reason; rely on the persistent docs instead of
  re-deriving the same explanation in chat.
- For broad, read-only sweeps across many files, prefer returning a **concise conclusion**, not raw
  file dumps, into the main context. *(Note: the user has a standing instruction not to spawn
  sub-agents/workflows unless they explicitly ask — honor that; do the sweep inline and keep the
  summary tight.)*
- Never dump large logs/build output into context — filter to the relevant lines.
- **But never omit information that is necessary to continue the task correctly** merely to save
  tokens. Correctness first, brevity second.

## Persistent docs index

| File | Purpose |
|---|---|
| [docs/CURRENT_STATE.md](docs/CURRENT_STATE.md) | **Live handoff.** Current goal, status, files being modified, bugs, next exact action. Read first. |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Actual layered architecture, modules, data flow, patterns. |
| [docs/DECISIONS.md](docs/DECISIONS.md) | ADR-style record of why the code is the way it is. |
| [docs/API_CONTRACTS.md](docs/API_CONTRACTS.md) | RailRadar + Nominatim + OSM externals; internal contracts (Room schema, Routes, domain ports). |
| [docs/TODO.md](docs/TODO.md) | Current / Next / Later / Bugs / Tech debt / Ideas. |
| [docs/Udaipur_Trip_Companion_Product_Spec_v2.md](docs/Udaipur_Trip_Companion_Product_Spec_v2.md) | The product spec (numbered `§` sections cited in code). |
| [docs/railradar-api.md](docs/railradar-api.md) | RailRadar field-level API notes (as fetched from vendor). |
| `.claude/rules/*.md` | Focused, project-local guidance (`data-layer`, `ui-compose`, `testing-and-verify`). **Local-only** — `.claude/` is git-ignored, so committed truth lives in `docs/` + this file. |

## Build & verify (quick reference)

```bash
./gradlew :app:testDebugUnitTest
```
```bash
./gradlew :app:assembleDebug
```
Device install/verify (adb is not on PATH; path and device id in `docs/CURRENT_STATE.md`). To enable
live trains + PNR, set `RAILRADAR_API_KEY=…` in `local.properties`; without it the app uses the
offline projection and works fully otherwise.

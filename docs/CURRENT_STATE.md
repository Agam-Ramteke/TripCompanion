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

**DONE — Stay Check-In/Check-Out, Back-Navigation Fix, and Train Station Locations & Overnight Itinerary.**
Plan: `implementation_plan.md` (completed 2026-08-25). Proximity-first search stashed for later per user request.

1. **Stay check-in / check-out:**
   - Explicit **Check-in** (date + time) and **Check-out** (date + time) fields in the Event Editor for `STAY` activities.
   - Multi-day stays appear as **two** itinerary entries (check-in on arrival day, check-out on departure day).
2. **Back-navigation fix:**
   - System Back returns to **Home** from anywhere, **except** Settings, Event Editor, Location Picker, and Photo Editor (and Home which exits the app).
3. **Train station locations & overnight train itinerary:**
   - Adding/syncing trains automatically searches for station locations via `LocationSearchService` and inserts them into `LocationRepository` (category = `"Transit"`), linking `locationId` to the `JOURNEY` event.
   - Overnight trains appear on both Day 1 (departure) and Day 2 (arrival) in the itinerary.

## Current Status

- ✅ **Stay Check-in/out, Back-navigation & Train station/overnight updates — COMPLETE & TESTED (2026-08-25).**
  - `EventEditorViewModel` & `EventEditorScreen` updated for distinct check-in & check-out dates and times.
  - `TimelineViewModel` & `TimelineScreen` updated with `TimelineDayEvent` splitting multi-day stays and overnight trains across arrival/departure days.
  - `JourneyEventLinker` updated to resolve station location and link `locationId` to `JOURNEY` events.
  - `AppNavHost` updated with `BackHandler` returning to Home except for Settings, Event Editor, and sub-pickers.
  - Unit tests added: `EventEditorViewModelTest`, `TimelineViewModelTest`, `JourneyEventLinkerTest`. All 470+ tests pass (`./gradlew :app:testDebugUnitTest`).
  - Debug APK built cleanly (`./gradlew :app:assembleDebug`).
- ✅ **Trip Map redesign — COMMITTED on `main` (2026-08-25).** A premium travel-HUD redesign of the
  itinerary map (plan `.claude/plans/snug-skipping-tiger.md`, Workstreams A–J): keyed **MapTiler**
  pastel basemap with graceful **CARTO** fallback; **numbered status markers** (Completed/Upcoming/
  Current) whose numbers match the sheet, state derived from `TripStateEngine`; route **casing**
  under a refined-blue line; **fit-the-day camera** + animate-to-tapped-stop; **device GPS**
  (framework `LocationManager` behind `DeviceLocationProvider`, runtime permission, live dot +
  accuracy ring + center-on-me); **per-leg travel** (ORS `segments` → `RouteLeg`, haversine
  fallback) in a redesigned M3 draggable sheet; **NEXT STOP** card with Navigate
  (`ExternalNavigator` → Google Maps) / Details; **"Day N · <trip name>"** top pill.
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

## Modified Files

- `app/src/main/java/com/tripcompanion/app/feature/event/EventEditorViewModel.kt`
- `app/src/main/java/com/tripcompanion/app/ui/screens/EventEditorScreen.kt`
- `app/src/main/java/com/tripcompanion/app/feature/trip/TimelineViewModel.kt`
- `app/src/main/java/com/tripcompanion/app/ui/screens/TimelineScreen.kt`
- `app/src/main/java/com/tripcompanion/app/domain/service/JourneyEventLinker.kt`
- `app/src/main/java/com/tripcompanion/app/data/prefs/UserPreferencesStore.kt`
- `app/src/main/java/com/tripcompanion/app/ui/navigation/AppNavHost.kt`
- `app/src/test/java/com/tripcompanion/app/feature/event/EventEditorViewModelTest.kt`
- `app/src/test/java/com/tripcompanion/app/feature/trip/TimelineViewModelTest.kt`
- `app/src/test/java/com/tripcompanion/app/domain/service/JourneyEventLinkerTest.kt`
- `docs/CURRENT_STATE.md`
- `docs/TODO.md`

## Next Steps (exact, in order)

1. Review and commit changes to local `main`.
2. Ready for next backlog item or user feedback.

# TODO.md

Work queue for Trip Companion. Living document — check items off, move them between sections, and add
new ones as they surface. Detailed *in-flight* status (what's half-done, what to do next step by
step) lives in [CURRENT_STATE.md](CURRENT_STATE.md); this file is the backlog overview.

**Last updated:** 2026-08-25

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
- [x] **Stay check-in / check-out — DONE (2026-08-25).** Two explicit datetime fields
      (`startDate`+`startTime` for Check-in, `endDate`+`endTime` for Check-out) in Event Editor for `STAY`
      activities. Multi-day stays split into two itinerary entries (check-in on arrival day, check-out
      on departure day). Unit-tested in `EventEditorViewModelTest` and `TimelineViewModelTest`.
- [x] **Back-navigation to Home — DONE (2026-08-25).** System Back returns to Home from anywhere,
      except Settings, Event Editor, Location Picker, and Photo Editor (and Home which exits the app).
- [x] **Train station locations & overnight train itinerary — DONE (2026-08-25).** Auto-searches and
      inserts station location on train creation/sync in `JourneyEventLinker` (with category = `"Transit"`
      and coordinates), linking `locationId` to the `JOURNEY` event; overnight trains appear on both
      departure day (with departure time) and arrival day (with arrival time) on the itinerary.
- [ ] **Proximity-first place search — STASHED for later.** Use GPS/viewport anchor with Nominatim
      and sort by distance via GeoUtils.

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
      pins; route **casing** under a refined-blue line; **fit-the-day camera** + animate-to-tapped-stop;
      **device GPS** (framework `LocationManager` behind `DeviceLocationProvider`, runtime permission,
      live dot + accuracy ring + center-on-me); **per-leg travel** (ORS `segments` → `RouteLeg`,
      haversine fallback) in a redesigned M3 draggable sheet; **NEXT STOP** card with Navigate
      (`ExternalNavigator` → Google Maps) / Details; **"Day N · <trip name>"** top pill. Unit tests +
      `assembleDebug` green. On-device verified (Workstream J) 2026-08-25, all checks passed.
- [ ] **Proximity-first place search.** Stashed for later per user instruction.

## 🟢 Backlog (future milestones)

- [ ] **Offline tiles pre-caching** (stretch from original prompt) — pre-fetch OSM tiles along trip
      bounding box before travel. (See ADR-014 §"Out of Scope for initial ship".)
- [ ] **Export trip as GPX / KML** — for loading routes into Garmin / OsmAnd.
- [ ] **Trip statistics sheet** — total distance travelled, days on the road, count of stops by type.
- [ ] **Multi-passenger PNR tracking** — surface individual seat numbers and status changes per
      passenger on live status updates.
- [ ] **Train station platform numbers** — where reported by RailRadar, show the platform badge on the
      train card.

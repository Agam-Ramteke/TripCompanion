/**
 * Seed data — spec §4.
 *
 * This file is DATA. Udaipur is an example trip, not a feature. Nothing
 * anywhere else in the prototype may branch on a trip name, a city or a place:
 * no `if (trip === 'Udaipur')`, ever. Deleting this file should leave a working,
 * empty app.
 *
 * Two things are seeded on purpose:
 *   - A second, unrelated trip, so multi-trip isolation and genericity are
 *     visible rather than asserted.
 *   - Events declared in scrambled order with non-sequential ids, so the
 *     chronological sort in §10 is actually exercised instead of accidentally
 *     satisfied by insertion order.
 */

import type { Event, PlannedPhoto, Trip, TripLocation } from '../domain/types'

export const SEED_TRIPS: Trip[] = [
  { id: 1, name: 'Udaipur', startDate: '2026-09-08', endDate: '2026-09-11', status: 'PLANNING' },
  { id: 2, name: 'Spiti Valley Loop', startDate: '2026-10-02', endDate: '2026-10-06', status: 'PLANNING' },
]

export const SEED_LOCATIONS: TripLocation[] = [
  {
    id: 11, name: 'Udaipur City Railway Station', address: 'Station Road, Udaipur, Rajasthan 313001',
    latitude: 24.5726, longitude: 73.6919, category: 'railway station',
    providerPlaceId: 'osm:node:1', providerName: 'OpenStreetMap',
  },
  {
    id: 12, name: 'Jagat Niwas Palace Hotel', address: '23–25 Lal Ghat, Udaipur, Rajasthan 313001',
    latitude: 24.5793, longitude: 73.6822, category: 'hotel',
    providerPlaceId: 'osm:way:2', providerName: 'OpenStreetMap',
  },
  {
    id: 13, name: 'Ambrai Ghat', address: 'Outside Chandpole, Udaipur, Rajasthan 313001',
    latitude: 24.5768, longitude: 73.6789, category: 'viewpoint',
    providerPlaceId: 'osm:node:3', providerName: 'OpenStreetMap',
  },
  {
    id: 14, name: 'Ambrai Restaurant', address: 'Amet Haveli, Outside Chandpole, Udaipur',
    latitude: 24.5772, longitude: 73.6785, category: 'restaurant',
    providerPlaceId: 'osm:node:4', providerName: 'OpenStreetMap',
  },
  {
    id: 15, name: 'City Palace', address: 'City Palace Complex, Udaipur, Rajasthan 313001',
    latitude: 24.5764, longitude: 73.6835, category: 'attraction',
    providerPlaceId: 'osm:way:5', providerName: 'OpenStreetMap',
  },
  {
    id: 16, name: 'Jagdish Temple', address: 'Jagdish Chowk, Udaipur, Rajasthan 313001',
    latitude: 24.5793, longitude: 73.6836, category: 'temple',
    providerPlaceId: 'osm:way:6', providerName: 'OpenStreetMap',
  },
  {
    id: 17, name: 'Bagore Ki Haveli', address: 'Gangaur Ghat Marg, Udaipur, Rajasthan 313001',
    latitude: 24.5788, longitude: 73.6813, category: 'museum',
    providerPlaceId: 'osm:way:7', providerName: 'OpenStreetMap',
  },
  {
    id: 18, name: 'Lake Pichola Boat Jetty', address: 'Rameshwar Ghat, City Palace, Udaipur',
    latitude: 24.5744, longitude: 73.6817, category: 'jetty',
    providerPlaceId: 'osm:node:8', providerName: 'OpenStreetMap',
  },
  {
    id: 19, name: 'Saheliyon Ki Bari', address: 'Saheli Marg, Panchwati, Udaipur, Rajasthan 313001',
    latitude: 24.5977, longitude: 73.69, category: 'garden',
    providerPlaceId: 'osm:way:9', providerName: 'OpenStreetMap',
  },
  {
    id: 20, name: 'Monsoon Palace', address: 'Sajjangarh, Bari Lake Road, Udaipur',
    latitude: 24.5936, longitude: 73.6482, category: 'attraction',
    providerPlaceId: 'osm:way:10', providerName: 'OpenStreetMap',
  },
  {
    id: 21, name: 'Natraj Dining Hall', address: 'City Station Road, Udaipur, Rajasthan 313001',
    latitude: 24.5817, longitude: 73.6907, category: 'restaurant',
    providerPlaceId: 'osm:node:11', providerName: 'OpenStreetMap',
  },
  // Second trip — proves the app has no idea which city it is in.
  {
    id: 31, name: 'Manali Bus Stand', address: 'Mall Road, Manali, Himachal Pradesh 175131',
    latitude: 32.2432, longitude: 77.1892, category: 'bus station',
    providerPlaceId: 'osm:node:20', providerName: 'OpenStreetMap',
  },
  {
    id: 32, name: 'Kaza', address: 'Kaza, Lahaul and Spiti, Himachal Pradesh 172114',
    latitude: 32.2258, longitude: 78.0715, category: 'town',
    providerPlaceId: 'osm:node:21', providerName: 'OpenStreetMap',
  },
  {
    id: 33, name: 'Key Monastery', address: 'Kyi, Lahaul and Spiti, Himachal Pradesh 172114',
    latitude: 32.2977, longitude: 78.0119, category: 'monastery',
    providerPlaceId: 'osm:way:22', providerName: 'OpenStreetMap',
  },
]

/**
 * Declared in scrambled order with non-sequential ids on purpose — see the note
 * at the top of this file.
 *
 * `status` here is only ever UPCOMING (let the clock decide), COMPLETED or
 * SKIPPED (manual, terminal). ACTIVE / STARTING_SOON / MISSED are never stored;
 * they are always derived.
 */
export const SEED_EVENTS: Event[] = [
  // ── Day 2 (declared first, deliberately) ──────────────────────────────────
  {
    id: 204, tripId: 1, type: 'VISIT', title: 'Lake Pichola boat ride',
    startTime: '2026-09-09T15:00', endTime: '2026-09-09T17:00', locationId: 18,
    whatWeAreDoing:
      'Take the late-afternoon boat from Rameshwar Ghat, stop at Jag Mandir, and stay out for the light on the water.',
    notes: 'Tickets are sold at the jetty, cash only. Last boat back is 18:00.',
    status: 'UPCOMING', order: 0,
  },
  {
    id: 201, tripId: 1, type: 'VISIT', title: 'City Palace',
    startTime: '2026-09-09T08:30', endTime: '2026-09-09T10:15', locationId: 15,
    whatWeAreDoing:
      'Explore the palace, visit the museum, and take architecture/couple photos.',
    notes: '', status: 'COMPLETED', order: 0,
  },
  {
    id: 206, tripId: 1, type: 'FOOD', title: 'Dinner at Ambrai',
    startTime: '2026-09-09T18:30', endTime: '2026-09-09T20:30', locationId: 14,
    whatWeAreDoing: 'Have dinner by the lake and take some evening photos.',
    notes: 'Booked for two, lakeside table.', status: 'UPCOMING', order: 0,
  },
  {
    id: 202, tripId: 1, type: 'VISIT', title: 'Jagdish Temple',
    startTime: '2026-09-09T10:30', endTime: '2026-09-09T11:15', locationId: 16,
    whatWeAreDoing: 'Walk up from the palace gate and sit for a while.',
    notes: '', status: 'SKIPPED', order: 0,
  },
  {
    id: 203, tripId: 1, type: 'FOOD', title: 'Lunch at Bagore Ki Haveli',
    startTime: '2026-09-09T12:30', endTime: '2026-09-09T14:00', locationId: 17,
    whatWeAreDoing: 'Eat, then wander the haveli courtyards out of the heat.',
    notes: '', status: 'COMPLETED', order: 0,
  },

  // ── Day 1 ─────────────────────────────────────────────────────────────────
  {
    id: 101, tripId: 1, type: 'JOURNEY', title: 'Train to Udaipur',
    startTime: '2026-09-08T06:10', endTime: '2026-09-08T13:45', locationId: 11,
    whatWeAreDoing: 'Overnight bags only. Breakfast on board.',
    notes: 'Coach B4, seats 41 and 42.', status: 'COMPLETED', order: 0,
  },
  {
    id: 104, tripId: 1, type: 'FOOD', title: 'First dinner',
    startTime: '2026-09-08T19:30', endTime: '2026-09-08T21:00', locationId: 21,
    whatWeAreDoing: 'Thali, early night.', notes: '', status: 'COMPLETED', order: 0,
  },
  {
    id: 102, tripId: 1, type: 'STAY', title: 'Hotel check-in',
    startTime: '2026-09-08T14:30', endTime: '2026-09-08T15:30', locationId: 12,
    whatWeAreDoing: 'Check in, drop our bags, freshen up, and rest before dinner.',
    notes: '', status: 'COMPLETED', order: 0,
  },
  {
    // Left as UPCOMING so it computes to MISSED once the clock passes it —
    // the state we most need to see rendered somewhere.
    id: 103, tripId: 1, type: 'VISIT', title: 'Ambrai Ghat sunset',
    startTime: '2026-09-08T17:30', endTime: '2026-09-08T19:00', locationId: 13,
    whatWeAreDoing: 'Cross to the far bank for the view back at the palace.',
    notes: '', status: 'UPCOMING', order: 0,
  },

  // ── Day 3 — includes a deliberate overlap ─────────────────────────────────
  {
    id: 303, tripId: 1, type: 'VISIT', title: 'Monsoon Palace sunset',
    startTime: '2026-09-10T15:00', endTime: '2026-09-10T17:30', locationId: 20,
    whatWeAreDoing: 'Taxi up through the sanctuary gate and stay for the sun going down.',
    notes: 'Sanctuary entry closes at 18:00.', status: 'UPCOMING', order: 0,
  },
  {
    id: 304, tripId: 1, type: 'CUSTOM', title: 'Photo session on the ramparts',
    startTime: '2026-09-10T16:00', endTime: '2026-09-10T17:00', locationId: 20,
    whatWeAreDoing: 'The couple shots we actually want, while the light is low.',
    notes: 'Overlaps the visit on purpose — same place, nested block.',
    status: 'UPCOMING', order: 1,
  },
  {
    id: 301, tripId: 1, type: 'VISIT', title: 'Saheliyon Ki Bari',
    startTime: '2026-09-10T09:00', endTime: '2026-09-10T11:30', locationId: 19,
    whatWeAreDoing: 'Slow morning in the gardens before it gets hot.',
    notes: '', status: 'UPCOMING', order: 0,
  },
  {
    id: 302, tripId: 1, type: 'FOOD', title: 'Lunch',
    startTime: '2026-09-10T12:00', endTime: '2026-09-10T13:30', locationId: 21,
    whatWeAreDoing: '', notes: '', status: 'UPCOMING', order: 0,
  },

  // ── Day 4 ─────────────────────────────────────────────────────────────────
  {
    id: 402, tripId: 1, type: 'JOURNEY', title: 'Train home',
    startTime: '2026-09-11T11:20', endTime: '2026-09-11T19:40', locationId: 11,
    whatWeAreDoing: '', notes: 'Coach A1, seats 12 and 13.', status: 'UPCOMING', order: 0,
  },
  {
    id: 401, tripId: 1, type: 'STAY', title: 'Hotel checkout',
    startTime: '2026-09-11T09:00', endTime: '2026-09-11T10:00', locationId: 12,
    whatWeAreDoing: 'Pack, settle the bill, leave bags at reception.',
    notes: '', status: 'UPCOMING', order: 0,
  },

  // ── Second trip ───────────────────────────────────────────────────────────
  {
    id: 501, tripId: 2, type: 'JOURNEY', title: 'Overnight bus to Kaza',
    startTime: '2026-10-02T05:30', endTime: '2026-10-02T17:00', locationId: 31,
    whatWeAreDoing: 'Long day on the road. Layers, water, motion sickness tablets.',
    notes: '', status: 'UPCOMING', order: 0,
  },
  {
    id: 502, tripId: 2, type: 'VISIT', title: 'Key Monastery',
    startTime: '2026-10-03T08:00', endTime: '2026-10-03T11:00', locationId: 33,
    whatWeAreDoing: 'Walk up early, before the day tours arrive.',
    notes: '', status: 'UPCOMING', order: 0,
  },
  {
    id: 503, tripId: 2, type: 'STAY', title: 'Homestay in Kaza',
    startTime: '2026-10-03T17:00', endTime: '2026-10-03T18:00', locationId: 32,
    whatWeAreDoing: 'Acclimatise. Nothing strenuous on the first night at altitude.',
    notes: '', status: 'UPCOMING', order: 0,
  },
]

/**
 * Planned photos — the reference board, not a shot list (§15).
 * Titles are short or absent. A photo with no title at all is valid.
 *
 * Remote URLs are used only because the prototype has no photo library to draw
 * from; on Android these are app-private file paths (§16).
 */
const ref = (seed: string) => `https://picsum.photos/seed/${seed}/480/640`

export const SEED_PHOTOS: PlannedPhoto[] = [
  { id: 901, eventId: 201, title: 'Palace doorway', imageUri: ref('tc-doorway'), order: 0 },
  { id: 902, eventId: 201, title: 'Couple shot', imageUri: ref('tc-couple'), order: 1 },
  { id: 903, eventId: 201, title: '', imageUri: ref('tc-arches'), order: 2 },
  { id: 904, eventId: 201, title: 'Courtyard from above', imageUri: ref('tc-courtyard'), order: 3 },

  { id: 905, eventId: 204, title: 'Boat wake', imageUri: ref('tc-boat'), order: 0 },
  { id: 906, eventId: 204, title: '', imageUri: ref('tc-water'), order: 1 },

  { id: 907, eventId: 206, title: 'Table by the water', imageUri: ref('tc-table'), order: 0 },
  { id: 908, eventId: 206, title: 'Palace lit up', imageUri: ref('tc-lit'), order: 1 },

  { id: 909, eventId: 102, title: 'Check-in', imageUri: ref('tc-checkin'), order: 0 },

  { id: 910, eventId: 304, title: 'Wide, low sun', imageUri: ref('tc-lowsun'), order: 0 },
  { id: 911, eventId: 304, title: '', imageUri: ref('tc-rampart'), order: 1 },
  { id: 912, eventId: 304, title: 'Silhouette', imageUri: ref('tc-silhouette'), order: 2 },

  { id: 913, eventId: 502, title: 'Monastery on the ridge', imageUri: ref('tc-ridge'), order: 0 },
]

/** Simulated clock default: day 2 of the Udaipur trip, mid-afternoon. */
export const DEFAULT_SIM_NOW = '2026-09-09T15:40'

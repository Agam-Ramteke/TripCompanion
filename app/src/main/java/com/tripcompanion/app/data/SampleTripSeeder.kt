package com.tripcompanion.app.data

import com.tripcompanion.app.core.time.TimeProvider
import com.tripcompanion.app.domain.model.BerthType
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.domain.model.Location
import com.tripcompanion.app.domain.model.PassengerGender
import com.tripcompanion.app.domain.model.PlannedPhoto
import com.tripcompanion.app.domain.model.StayDetails
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.model.TrainAllotment
import com.tripcompanion.app.domain.model.TrainBookingStatus
import com.tripcompanion.app.domain.model.TrainPassenger
import com.tripcompanion.app.domain.model.TrainStop
import com.tripcompanion.app.domain.model.Trip
import com.tripcompanion.app.domain.model.TripStatus
import com.tripcompanion.app.domain.repository.EventRepository
import com.tripcompanion.app.domain.repository.LocationRepository
import com.tripcompanion.app.domain.repository.PlannedPhotoRepository
import com.tripcompanion.app.domain.repository.StayDetailsRepository
import com.tripcompanion.app.domain.repository.TrainRepository
import com.tripcompanion.app.domain.repository.TripRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes one fully-populated trip so a first launch has something to look at.
 */
@Singleton
class SampleTripSeeder @Inject constructor(
    private val tripRepository: TripRepository,
    private val eventRepository: EventRepository,
    private val locationRepository: LocationRepository,
    private val trainRepository: TrainRepository,
    private val stayDetailsRepository: StayDetailsRepository,
    private val photoRepository: PlannedPhotoRepository,
    private val timeProvider: TimeProvider
) {

    /**
     * Inserts the sample trips idempotently — never duplicates existing records.
     */
    suspend fun seed(): Long {
        val existingTrips = tripRepository.getAllTrips().first()
        val existingAgra = existingTrips.firstOrNull { it.name.contains("Agra", ignoreCase = true) }
        val existingUdaipur = existingTrips.firstOrNull { it.name.contains("Udaipur", ignoreCase = true) }

        if (existingAgra != null && existingUdaipur != null) {
            return existingAgra.id
        }

        val agraTripId = existingAgra?.id ?: seedAgraTrip()
        if (existingUdaipur == null) {
            val day1 = timeProvider.now().toLocalDate()
            val tripId = tripRepository.insertTrip(
                Trip(
                    name = "Udaipur Getaway",
                    startDate = day1,
                    endDate = day1.plusDays(4),
                    status = TripStatus.PLANNING
                )
            )

            val places = insertPlaces()
            val journeys = insertEvents(tripId, day1, places)
            insertTrains(tripId, day1, journeys)
        }

        return agraTripId
    }

    /**
     * Seeds the 1-Day Agra Itinerary (7 stops in sequence).
     */
    suspend fun seedAgraTrip(): Long {
        val today = timeProvider.now().toLocalDate()
        val tripId = tripRepository.insertTrip(
            Trip(
                name = "Day in Agra",
                startDate = today,
                endDate = today,
                status = TripStatus.ACTIVE
            )
        )

        val stationLocId = locationRepository.insertLocation(
            Location(
                name = "Agra Cantt Railway Station",
                address = "Idgah Colony, Agra, Uttar Pradesh",
                latitude = 27.1583,
                longitude = 77.9944,
                category = "Transit",
                rating = 4.2
            )
        )
        val hotelLocId = locationRepository.insertLocation(
            Location(
                name = "Tajview Hotel",
                address = "Fatehabad Road, Tajganj, Agra",
                latitude = 27.1612,
                longitude = 78.0384,
                category = "Hotel",
                rating = 4.6
            )
        )
        val tajLocId = locationRepository.insertLocation(
            Location(
                name = "Taj Mahal",
                address = "Dharmapuri, Forest Colony, Tajganj, Agra",
                latitude = 27.1751,
                longitude = 78.0421,
                category = "Monument",
                rating = 4.9,
                openingHours = "06:00 – 18:30 (Closed Fridays)",
                estimatedVisitMinutes = 150
            )
        )
        val lunchLocId = locationRepository.insertLocation(
            Location(
                name = "Pinch of Spice Restaurant",
                address = "1076/2, Fatehabad Road, Tajganj, Agra",
                latitude = 27.1648,
                longitude = 78.0460,
                category = "Restaurant",
                rating = 4.4,
                openingHours = "11:30 – 23:00",
                estimatedVisitMinutes = 60
            )
        )
        val fortLocId = locationRepository.insertLocation(
            Location(
                name = "Agra Fort",
                address = "Agra Fort, Rakabganj, Agra",
                latitude = 27.1795,
                longitude = 78.0211,
                category = "Monument",
                rating = 4.7,
                openingHours = "06:00 – 18:00 daily",
                estimatedVisitMinutes = 120
            )
        )
        val gardenLocId = locationRepository.insertLocation(
            Location(
                name = "Mehtab Bagh",
                address = "Opposite Taj Mahal, Nagla Devjit, Agra",
                latitude = 27.1800,
                longitude = 78.0445,
                category = "Garden",
                rating = 4.5,
                openingHours = "06:00 – 19:00",
                estimatedVisitMinutes = 120
            )
        )

        var order = 0
        suspend fun addAgraEvent(
            start: LocalTime,
            end: LocalTime,
            type: EventType,
            title: String,
            what: String,
            locationId: Long
        ): Long {
            return eventRepository.insertEvent(
                Event(
                    tripId = tripId,
                    type = type,
                    title = title,
                    startTime = today.atTime(start),
                    endTime = today.atTime(end),
                    locationId = locationId,
                    whatWeAreDoing = what,
                    order = order++
                )
            )
        }

        addAgraEvent(
            start = LocalTime.of(5, 0),
            end = LocalTime.of(5, 20),
            type = EventType.JOURNEY,
            title = "Agra Cantt Railway Station",
            what = "Arrival at Agra Cantt on morning express. Exit from Platform 1 to prepaid taxi booth.",
            locationId = stationLocId
        )
        addAgraEvent(
            start = LocalTime.of(5, 40),
            end = LocalTime.of(8, 0),
            type = EventType.STAY,
            title = "Hotel Rest & Breakfast",
            what = "Check in at Tajview Hotel, freshen up, and enjoy rooftop breakfast with distant Taj view.",
            locationId = hotelLocId
        )
        addAgraEvent(
            start = LocalTime.of(8, 30),
            end = LocalTime.of(11, 0),
            type = EventType.VISIT,
            title = "Taj Mahal",
            what = "Enter from East Gate for shorter morning queues. Guided visit through gardens, main mausoleum, and Yamuna terrace.",
            locationId = tajLocId
        )
        addAgraEvent(
            start = LocalTime.of(11, 30),
            end = LocalTime.of(12, 30),
            type = EventType.FOOD,
            title = "Lunch at Pinch of Spice",
            what = "Authentic North Indian lunch on Fatehabad Road. Famous for Mughlai cuisine and dal makhani.",
            locationId = lunchLocId
        )
        addAgraEvent(
            start = LocalTime.of(13, 0),
            end = LocalTime.of(15, 0),
            type = EventType.VISIT,
            title = "Agra Fort",
            what = "Explore Jahangiri Mahal, Diwan-i-Khas, and the Musamman Burj balcony where Shah Jahan gazed at the Taj.",
            locationId = fortLocId
        )
        addAgraEvent(
            start = LocalTime.of(16, 0),
            end = LocalTime.of(18, 0),
            type = EventType.VISIT,
            title = "Mehtab Bagh Sunset",
            what = "Watch the sunset reflecting on the white marble of the Taj Mahal across the Yamuna River from the Charbagh complex.",
            locationId = gardenLocId
        )
        addAgraEvent(
            start = LocalTime.of(18, 30),
            end = LocalTime.of(21, 0),
            type = EventType.STAY,
            title = "Hotel / End of Day",
            what = "Return to hotel, relax and evening dinner.",
            locationId = hotelLocId
        )

        return tripId
    }

    // ── Places ─────────────────────────────────────────────────────────────────────────

    /**
     * The places the itinerary points at, keyed by a short handle.
     *
     * A map rather than a list because [insertEvents] needs to link specific events to
     * specific locations, and positional indexes into a list of eleven places is the kind of
     * thing that silently breaks when one is added in the middle.
     */
    private suspend fun insertPlaces(): Map<String, Long> {
        val rows = listOf(
            "hotel" to Location(
                name = "Lake Pichola Heritage Hotel",
                address = "Outside Chandpole, Udaipur, Rajasthan",
                latitude = 24.5749,
                longitude = 73.6772,
                category = "Hotel",
                rating = 4.4,
                openingHours = "Reception open 24 hours"
            ),
            "cityPalace" to Location(
                name = "City Palace",
                address = "Old City, Udaipur, Rajasthan",
                latitude = 24.5764,
                longitude = 73.6835,
                category = "Monument",
                rating = 4.6,
                openingHours = "09:30 – 17:30 daily",
                estimatedVisitMinutes = 150
            ),
            "ambrai" to Location(
                name = "Ambrai Ghat",
                address = "Amet Haveli, Outside Chandpole, Udaipur",
                latitude = 24.5766,
                longitude = 73.6768,
                category = "Restaurant",
                rating = 4.5,
                openingHours = "07:00 – 23:00",
                estimatedVisitMinutes = 90
            ),
            "jagdish" to Location(
                name = "Jagdish Temple",
                address = "Jagdish Chowk, Udaipur",
                latitude = 24.5794,
                longitude = 73.6837,
                category = "Temple",
                rating = 4.5,
                openingHours = "05:15 – 14:00, 16:00 – 22:00",
                estimatedVisitMinutes = 45
            ),
            "pichola" to Location(
                name = "Lake Pichola",
                address = "Udaipur, Rajasthan",
                latitude = 24.5716,
                longitude = 73.6790,
                category = "Lake",
                rating = 4.7,
                openingHours = "Boat rides 10:00 – 18:00",
                estimatedVisitMinutes = 90
            ),
            "saheliyon" to Location(
                name = "Saheliyon Ki Bari",
                address = "Saheli Marg, Udaipur",
                latitude = 24.6021,
                longitude = 73.6885,
                category = "Garden",
                rating = 4.3,
                openingHours = "09:00 – 19:00",
                estimatedVisitMinutes = 60
            ),
            "bagore" to Location(
                name = "Bagore Ki Haveli",
                address = "Gangaur Ghat Marg, Udaipur",
                latitude = 24.5798,
                longitude = 73.6805,
                category = "Museum",
                rating = 4.4,
                openingHours = "Museum 09:30 – 17:30, show 19:00",
                estimatedVisitMinutes = 120
            ),
            "kumbhalgarh" to Location(
                name = "Kumbhalgarh Fort",
                address = "Rajsamand district, Rajasthan",
                latitude = 25.1485,
                longitude = 73.5871,
                category = "Fort",
                rating = 4.6,
                openingHours = "09:00 – 18:00",
                estimatedVisitMinutes = 180
            ),
            "fatehSagar" to Location(
                name = "Fateh Sagar Lake",
                address = "Rani Road, Udaipur",
                latitude = 24.5990,
                longitude = 73.6790,
                category = "Lake",
                rating = 4.5,
                openingHours = "Open all day",
                estimatedVisitMinutes = 75
            ),
            "monsoonPalace" to Location(
                name = "Monsoon Palace",
                address = "Sajjangarh, Udaipur",
                latitude = 24.5951,
                longitude = 73.6470,
                category = "Viewpoint",
                rating = 4.2,
                openingHours = "09:00 – 18:00",
                estimatedVisitMinutes = 90
            ),
            "shilpgram" to Location(
                name = "Shilpgram Crafts Village",
                address = "Havala village, Udaipur",
                latitude = 24.5905,
                longitude = 73.6469,
                category = "Crafts",
                rating = 4.1,
                openingHours = "11:00 – 19:00",
                estimatedVisitMinutes = 90
            )
        )

        return rows.associate { (key, location) -> key to locationRepository.insertLocation(location) }
    }

    // ── Itinerary ──────────────────────────────────────────────────────────────────────

    /**
     * The whole plan, returning the two JOURNEY events the trains fill.
     *
     * A train is a row on the itinerary rather than an entry in a tab of its own, so the sample
     * has to demonstrate that: [insertTrains] takes these ids and the two bookings open already
     * linked, the way a booking saved through the editor does.
     */
    private suspend fun insertEvents(
        tripId: Long,
        day1: LocalDate,
        places: Map<String, Long>
    ): SeededJourneys {
        // order is assigned by position in this list, so §10's comparator has a stable
        // tie-breaker for the two events that start at the same minute on day three.
        var order = 0

        suspend fun add(
            dayOffset: Long,
            start: LocalTime,
            end: LocalTime,
            type: EventType,
            title: String,
            what: String,
            placeKey: String? = null,
            endDayOffset: Long = dayOffset,
            notes: String = ""
        ): Long {
            val date = day1.plusDays(dayOffset)
            return eventRepository.insertEvent(
                Event(
                    tripId = tripId,
                    type = type,
                    title = title,
                    startTime = date.atTime(start),
                    endTime = day1.plusDays(endDayOffset).atTime(end),
                    locationId = placeKey?.let { places[it] },
                    whatWeAreDoing = what,
                    notes = notes,
                    order = order++
                )
            )
        }

        // Day 1 — arrive and settle in
        val outboundJourneyId = add(
            dayOffset = 0,
            start = LocalTime.of(6, 15),
            end = LocalTime.of(13, 5),
            type = EventType.JOURNEY,
            title = "Train to Udaipur",
            what = "Board 12991 at Jaipur Junction from platform 2. Coach B1, seats 41 and 42. " +
                "Breakfast is served after Ajmer, so keep the tickets handy for the TTE check " +
                "before then.",
            notes = "Reach the station 30 minutes early — the entry gate for platform 2 is at " +
                "the far end."
        )
        val stayId = add(
            dayOffset = 0,
            start = LocalTime.of(14, 0),
            end = LocalTime.of(10, 30),
            endDayOffset = 4,
            type = EventType.STAY,
            title = "Lake Pichola Heritage Hotel",
            what = "Check in, drop the bags in room 204 and take twenty minutes on the terrace " +
                "before heading out. Check-out is on the last morning at 10:30.",
            placeKey = "hotel"
        )
        val cityPalaceId = add(
            dayOffset = 0,
            start = LocalTime.of(15, 30),
            end = LocalTime.of(17, 30),
            type = EventType.VISIT,
            title = "City Palace",
            what = "Enter from Badi Pol and work upward through the courtyards to the Amar " +
                "Vilas terrace. The Crystal Gallery ticket is separate and worth it; the " +
                "queue is shortest in the last hour before closing.",
            placeKey = "cityPalace"
        )
        add(
            dayOffset = 0,
            start = LocalTime.of(19, 0),
            end = LocalTime.of(20, 30),
            type = EventType.FOOD,
            title = "Dinner at Ambrai Ghat",
            what = "Ask for a table at the water's edge facing the Lake Palace. Booked under " +
                "the trip name for 19:00 — the light on the palace is best in the first half hour.",
            placeKey = "ambrai"
        )

        // Day 2 — the old city on foot
        add(
            dayOffset = 1,
            start = LocalTime.of(8, 30),
            end = LocalTime.of(9, 30),
            type = EventType.VISIT,
            title = "Jagdish Temple",
            what = "Morning aarti at 08:45. Shoes come off at the base of the steps; the " +
                "carved elephants on the outer wall are the reason to walk the full circuit.",
            placeKey = "jagdish"
        )
        val picholaId = add(
            dayOffset = 1,
            start = LocalTime.of(10, 30),
            end = LocalTime.of(12, 0),
            type = EventType.VISIT,
            title = "Lake Pichola boat ride",
            what = "Boat from Rameshwar Ghat, one hour, stopping at Jag Mandir island. Tickets " +
                "are sold at the ghat and the morning boats are quieter than the sunset run.",
            placeKey = "pichola"
        )
        add(
            dayOffset = 1,
            start = LocalTime.of(16, 0),
            end = LocalTime.of(17, 0),
            type = EventType.VISIT,
            title = "Saheliyon Ki Bari",
            what = "The fountain garden built for the queen's companions. Small, flat and shaded " +
                "— a good hour between the lake and the evening show.",
            placeKey = "saheliyon"
        )
        add(
            dayOffset = 1,
            start = LocalTime.of(19, 0),
            end = LocalTime.of(20, 15),
            type = EventType.VISIT,
            title = "Dharohar show at Bagore Ki Haveli",
            what = "Folk dance and puppetry in the haveli courtyard. Seating is unreserved, so " +
                "arrive by 18:40 for the front rows. Camera tickets are sold separately at the gate.",
            placeKey = "bagore"
        )

        // Day 3 — a day out of the city
        add(
            dayOffset = 2,
            start = LocalTime.of(8, 0),
            end = LocalTime.of(19, 0),
            type = EventType.VISIT,
            title = "Kumbhalgarh Fort day trip",
            what = "Two hours each way by car. Walk the rampart from Hanuman Pol as far as " +
                "Badal Mahal — the wall runs for kilometres and the light is best on the way back " +
                "down. Carry water; there is nothing to buy inside.",
            placeKey = "kumbhalgarh"
        )

        // Day 4 — lakes and the ridge
        add(
            dayOffset = 3,
            start = LocalTime.of(9, 30),
            end = LocalTime.of(11, 0),
            type = EventType.VISIT,
            title = "Fateh Sagar Lake",
            what = "Walk the Rani Road promenade from the north end and stop at Nehru Island " +
                "if the boat is running. Cooler and emptier before eleven.",
            placeKey = "fatehSagar"
        )
        add(
            dayOffset = 3,
            start = LocalTime.of(16, 30),
            end = LocalTime.of(18, 30),
            type = EventType.VISIT,
            title = "Sunset at Monsoon Palace",
            what = "The last stretch up Sajjangarh hill is a shared jeep from the gate. Be at " +
                "the top terrace forty minutes before sunset for the view down over both lakes.",
            placeKey = "monsoonPalace"
        )
        add(
            dayOffset = 3,
            start = LocalTime.of(20, 0),
            end = LocalTime.of(21, 30),
            type = EventType.FOOD,
            title = "Last dinner in the old city",
            what = "Rooftop table near Gangaur Ghat. Nothing booked — walk up and take whatever " +
                "has a view of the ghat lights.",
            placeKey = "ambrai"
        )

        // Day 5 — crafts, then the train home
        add(
            dayOffset = 4,
            start = LocalTime.of(11, 30),
            end = LocalTime.of(13, 0),
            type = EventType.VISIT,
            title = "Shilpgram Crafts Village",
            what = "Open-air museum of Rajasthani and Gujarati village houses, with weavers and " +
                "potters working in the huts. The last stop before the station and the best place " +
                "to buy something that is actually made here.",
            placeKey = "shilpgram"
        )
        val homewardJourneyId = add(
            dayOffset = 4,
            start = LocalTime.of(14, 35),
            end = LocalTime.of(21, 25),
            type = EventType.JOURNEY,
            title = "Train home to Jaipur",
            what = "12992 from Udaipur City, coach B2, seats 15 and 16. Reach the station by " +
                "14:00 — the platform for the Jaipur train is the one furthest from the entrance.",
            notes = "Hotel drop to the station takes 25 minutes in afternoon traffic."
        )

        insertPhotoPlans(cityPalaceId, picholaId)
        insertStayDetails(stayId)

        return SeededJourneys(outbound = outboundJourneyId, homeward = homewardJourneyId)
    }

    /**
     * Photo plans on two events, so the Photo Plan section on Activity Detail and Home's
     * photo count are demonstrating something real (§15).
     */
    private suspend fun insertPhotoPlans(cityPalaceEventId: Long, picholaEventId: Long) {
        listOf(
            PlannedPhoto(
                eventId = cityPalaceEventId,
                title = "Mosaic peacock in Mor Chowk, shot square from the centre of the courtyard",
                order = 0
            ),
            PlannedPhoto(
                eventId = cityPalaceEventId,
                title = "Lake Palace framed through an Amar Vilas arch",
                order = 1
            ),
            PlannedPhoto(
                eventId = picholaEventId,
                title = "Jag Mandir from the water, low angle, boat rail in the foreground",
                order = 0
            )
        ).forEach { photoRepository.insertPhoto(it) }
    }

    private suspend fun insertStayDetails(stayEventId: Long) {
        stayDetailsRepository.save(
            StayDetails(
                eventId = stayEventId,
                bookingReference = "LPH-4472913",
                roomType = "Lake-facing double, room 204",
                guests = 2,
                contactPhone = "+91 294 242 0000",
                address = "Outside Chandpole, Brahmpuri, Udaipur, Rajasthan 313001",
                checkInInstructions = "Reception is through the courtyard on the left. Check-in " +
                    "from 14:00; the booking is held under the trip name and one photo ID per " +
                    "guest is needed at the desk."
            )
        )
    }

    // ── Trains ─────────────────────────────────────────────────────────────────────────

    private suspend fun insertTrains(tripId: Long, day1: LocalDate, journeys: SeededJourneys) {
        val outboundId = trainRepository.insertTrain(
            Train(
                tripId = tripId,
                eventId = journeys.outbound,
                number = "12991",
                name = "Jaipur – Udaipur City Express",
                originCode = "JP",
                originName = "Jaipur Junction",
                destinationCode = "UDZ",
                destinationName = "Udaipur City",
                departureTime = day1.atTime(6, 15),
                arrivalTime = day1.atTime(13, 5),
                travelClass = "AC 3 Tier (3A)",
                pnr = "8472019365",
                bookingStatus = TrainBookingStatus.CONFIRMED,
                platform = "2",
                quota = "GENERAL",
                distanceKm = 431,
                passengers = sampleParty(coach = "B1", berths = listOf("41" to BerthType.LOWER, "42" to BerthType.MIDDLE))
            )
        )
        trainRepository.replaceSchedule(outboundId, outboundSchedule())

        val returnId = trainRepository.insertTrain(
            Train(
                tripId = tripId,
                eventId = journeys.homeward,
                number = "12992",
                name = "Udaipur City – Jaipur Express",
                originCode = "UDZ",
                originName = "Udaipur City",
                destinationCode = "JP",
                destinationName = "Jaipur Junction",
                departureTime = day1.plusDays(4).atTime(14, 35),
                arrivalTime = day1.plusDays(4).atTime(21, 25),
                travelClass = "AC 3 Tier (3A)",
                pnr = "8472019366",
                bookingStatus = TrainBookingStatus.CONFIRMED,
                quota = "GENERAL",
                distanceKm = 431,
                passengers = sampleParty(coach = "B2", berths = listOf("15" to BerthType.SIDE_LOWER, "16" to BerthType.SIDE_UPPER))
            )
        )
        trainRepository.replaceSchedule(returnId, returnSchedule())
    }

    /**
     * Two travellers, so the sample trip demonstrates a booking that covers a party.
     *
     * The same two people on both trains with different berths, which is the ordinary case and
     * the one a single coach-and-berth pair on the booking could never show. `bookingStatusText`
     * is filled in so the ticket screen has the verbatim column an imported ticket would give it.
     */
    private fun sampleParty(coach: String, berths: List<Pair<String, BerthType>>): List<TrainPassenger> {
        val people = listOf(
            Triple("Asha Menon", 34, PassengerGender.FEMALE),
            Triple("Rohan Menon", 36, PassengerGender.MALE)
        )
        return people.mapIndexed { index, (name, age, gender) ->
            val (berth, berthType) = berths[index]
            val allotment = TrainAllotment(
                status = TrainBookingStatus.CONFIRMED,
                coach = coach,
                berth = berth,
                berthType = berthType
            )
            TrainPassenger(
                serialNo = index + 1,
                name = name,
                age = age,
                gender = gender,
                allotment = allotment,
                bookingStatusText = allotment.text,
                currentStatusText = allotment.text
            )
        }
    }

    /**
     * Jaipur to Udaipur City, eleven halts.
     *
     * Origin arrival and terminus departure are left null, which is what
     * [com.tripcompanion.app.data.network.railradar.RailRadarParser] produces from a real response:
     * a train does not arrive at the station it starts from.
     */
    private fun outboundSchedule(): List<TrainStop> = listOf(
        stop(1, "JP", "Jaipur Junction", null, LocalTime.of(6, 15), 0),
        stop(2, "DPA", "Durgapura", LocalTime.of(6, 27), LocalTime.of(6, 29), 8),
        stop(3, "PHD", "Phulera Junction", LocalTime.of(7, 8), LocalTime.of(7, 10), 55),
        stop(4, "KSG", "Kishangarh", LocalTime.of(7, 58), LocalTime.of(8, 0), 105),
        stop(5, "AII", "Ajmer Junction", LocalTime.of(8, 35), LocalTime.of(8, 55), 135),
        stop(6, "NAD", "Nasirabad", LocalTime.of(9, 23), LocalTime.of(9, 25), 160),
        stop(7, "VG", "Vijaynagar", LocalTime.of(10, 5), LocalTime.of(10, 7), 205),
        stop(8, "BHL", "Bhilwara", LocalTime.of(10, 45), LocalTime.of(10, 50), 240),
        stop(9, "COR", "Chittaurgarh Junction", LocalTime.of(11, 45), LocalTime.of(11, 55), 297),
        stop(10, "MVJ", "Mavli Junction", LocalTime.of(12, 32), LocalTime.of(12, 34), 350),
        stop(11, "UDZ", "Udaipur City", LocalTime.of(13, 5), null, 383)
    )

    /** The same route reversed, which is how the return service actually runs. */
    private fun returnSchedule(): List<TrainStop> = listOf(
        stop(1, "UDZ", "Udaipur City", null, LocalTime.of(14, 35), 0),
        stop(2, "MVJ", "Mavli Junction", LocalTime.of(15, 8), LocalTime.of(15, 10), 33),
        stop(3, "COR", "Chittaurgarh Junction", LocalTime.of(15, 55), LocalTime.of(16, 5), 86),
        stop(4, "BHL", "Bhilwara", LocalTime.of(16, 58), LocalTime.of(17, 3), 143),
        stop(5, "VG", "Vijaynagar", LocalTime.of(17, 38), LocalTime.of(17, 40), 178),
        stop(6, "NAD", "Nasirabad", LocalTime.of(18, 18), LocalTime.of(18, 20), 223),
        stop(7, "AII", "Ajmer Junction", LocalTime.of(18, 50), LocalTime.of(19, 10), 248),
        stop(8, "KSG", "Kishangarh", LocalTime.of(19, 40), LocalTime.of(19, 42), 278),
        stop(9, "PHD", "Phulera Junction", LocalTime.of(20, 25), LocalTime.of(20, 27), 328),
        stop(10, "DPA", "Durgapura", LocalTime.of(21, 5), LocalTime.of(21, 7), 375),
        stop(11, "JP", "Jaipur Junction", LocalTime.of(21, 25), null, 383)
    )

    private fun stop(
        serial: Int,
        code: String,
        name: String,
        arrival: LocalTime?,
        departure: LocalTime?,
        distanceKm: Int
    ) = TrainStop(
        serialNo = serial,
        stationCode = code,
        stationName = name,
        scheduledArrival = arrival,
        scheduledDeparture = departure,
        distanceKm = distanceKm
    )

    /** The two itinerary rows the sample's trains fill, handed from the events to the bookings. */
    private data class SeededJourneys(val outbound: Long, val homeward: Long)
}

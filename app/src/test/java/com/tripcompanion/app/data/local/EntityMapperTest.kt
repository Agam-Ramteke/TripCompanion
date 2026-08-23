package com.tripcompanion.app.data.local

import com.tripcompanion.app.data.local.converter.Converters
import com.tripcompanion.app.domain.model.Activity
import com.tripcompanion.app.domain.model.ActivityStatus
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.EventStatus
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.domain.model.Location
import com.tripcompanion.app.domain.model.PlannedPhoto
import com.tripcompanion.app.domain.model.StayDetails
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.model.TrainBookingStatus
import com.tripcompanion.app.domain.model.TrainRunSource
import com.tripcompanion.app.domain.model.TrainRunStatus
import com.tripcompanion.app.domain.model.TrainStop
import com.tripcompanion.app.domain.model.TrainStopStatus
import com.tripcompanion.app.domain.model.Trip
import com.tripcompanion.app.domain.model.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Every domain model has to survive the round trip to storage and back unchanged.
 *
 * The assertion that matters in each case is the last one — `assertEquals(domain,
 * entity.toDomain())` compares the whole data class, so a field added to the model
 * and forgotten in the mapper fails here rather than silently reading back as null
 * on someone's phone.
 */
class EntityMapperTest {

    private val converters = Converters()

    // ---- Trip --------------------------------------------------------------

    @Test
    fun `a trip round-trips through its entity`() {
        val domain = Trip(
            id = 10,
            name = "Tokyo Autumn Journey",
            startDate = LocalDate.of(2026, 11, 1),
            endDate = LocalDate.of(2026, 11, 10),
            status = TripStatus.PLANNING,
            createdAt = LocalDateTime.of(2026, 8, 1, 10, 0),
            updatedAt = LocalDateTime.of(2026, 8, 2, 12, 0)
        )

        val entity = domain.toEntity()

        assertEquals(domain.id, entity.id)
        assertEquals(domain.name, entity.name)
        assertEquals(domain.status.name, entity.status)
        assertEquals(domain, entity.toDomain())
    }

    @Test
    fun `every trip status survives the string column`() {
        TripStatus.entries.forEach { status ->
            val domain = Trip(
                id = 1,
                name = "Trip",
                startDate = LocalDate.of(2026, 1, 1),
                endDate = LocalDate.of(2026, 1, 2),
                status = status
            )
            assertEquals(status, domain.toEntity().toDomain().status)
        }
    }

    @Test
    fun `a trip with no cover photo keeps a null rather than an empty path`() {
        val domain = Trip(
            id = 11,
            name = "Trip",
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 1, 2)
        )
        val restored = domain.toEntity().toDomain()

        assertNull(restored.coverImageUri)
        assertEquals(domain, restored)
    }

    @Test
    fun `a trip cover photo path survives storage`() {
        val path = "/data/user/0/com.tripcompanion.app/files/covers/trip_11.jpg"
        val domain = Trip(
            id = 11,
            name = "Trip",
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 1, 2),
            coverImageUri = path
        )

        assertEquals(path, domain.toEntity().coverImageUri)
        assertEquals(domain, domain.toEntity().toDomain())
    }

    // ---- Event -------------------------------------------------------------

    @Test
    fun `an event round-trips through its entity`() {
        val domain = Event(
            id = 25,
            tripId = 10,
            type = EventType.VISIT,
            title = "Museum Tour",
            startTime = LocalDateTime.of(2026, 11, 2, 14, 0),
            endTime = LocalDateTime.of(2026, 11, 2, 17, 0),
            locationId = 5,
            whatWeAreDoing = "Explore ancient art galleries and view special samurai exhibitions.",
            notes = "Meet guide at main lobby",
            status = EventStatus.UPCOMING,
            order = 1
        )

        val entity = domain.toEntity()

        assertEquals(domain.id, entity.id)
        assertEquals(domain.tripId, entity.tripId)
        assertEquals(domain.type.name, entity.type)
        assertEquals(domain.whatWeAreDoing, entity.whatWeAreDoing)
        assertEquals(domain.status.name, entity.status)
        assertEquals(domain, entity.toDomain())
    }

    @Test
    fun `every event type survives the string column`() {
        // Includes FOOD (§8). A type added to the enum but not persisted correctly
        // would throw IllegalArgumentException on the way back through valueOf.
        EventType.entries.forEach { type ->
            val domain = sampleEvent(type = type)
            assertEquals(type, domain.toEntity().toDomain().type)
        }
    }

    @Test
    fun `every event status survives the string column`() {
        EventStatus.entries.forEach { status ->
            val domain = sampleEvent(status = status)
            assertEquals(status, domain.toEntity().toDomain().status)
        }
    }

    @Test
    fun `an event with no location keeps its null rather than becoming zero`() {
        val domain = sampleEvent().copy(locationId = null)
        assertNull(domain.toEntity().locationId)
        assertNull(domain.toEntity().toDomain().locationId)
    }

    @Test
    fun `a multi-line 'what we are doing' survives intact`() {
        // §14: this is a paragraph the user typed, not a label. Newlines included.
        val prose = "Walk the ghats at first light.\nCoffee at the corner stall.\n" +
            "Back before the heat."
        val domain = sampleEvent().copy(whatWeAreDoing = prose)
        assertEquals(prose, domain.toEntity().toDomain().whatWeAreDoing)
    }

    @Test
    fun `an overnight event keeps both of its dates`() {
        val domain = sampleEvent(
            start = LocalDateTime.of(2026, 11, 2, 23, 0),
            end = LocalDateTime.of(2026, 11, 3, 7, 30)
        )
        val restored = domain.toEntity().toDomain()

        assertEquals(LocalDate.of(2026, 11, 2), restored.startTime.toLocalDate())
        assertEquals(LocalDate.of(2026, 11, 3), restored.endTime.toLocalDate())
    }

    // ---- Location ----------------------------------------------------------

    @Test
    fun `a location round-trips with its coordinates and provider fields`() {
        val domain = Location(
            id = 5,
            name = "National Museum",
            address = "1-1 Ueno Park, Taito, Tokyo",
            latitude = 35.7188,
            longitude = 139.7765,
            category = "Tourism / Museum",
            providerPlaceId = "osm_12345",
            providerName = "OpenStreetMap"
        )

        val entity = domain.toEntity()

        assertEquals(domain.id, entity.id)
        assertEquals(domain.name, entity.name)
        assertEquals(domain.latitude, entity.latitude, 0.0000001)
        assertEquals(domain.longitude, entity.longitude, 0.0000001)
        assertEquals(domain.providerPlaceId, entity.providerPlaceId)
        assertEquals(domain.providerName, entity.providerName)
        assertEquals(domain, entity.toDomain())
    }

    @Test
    fun `coordinate precision is not rounded away in storage`() {
        // §13 saves the coordinate so the map can be drawn offline. Six decimal
        // places is roughly a tenth of a metre; losing them moves the marker.
        val domain = Location(
            id = 7,
            name = "City Palace",
            address = "Udaipur",
            latitude = 24.5760123,
            longitude = 73.6832456
        )
        val restored = domain.toEntity().toDomain()

        assertEquals(24.5760123, restored.latitude, 0.0000001)
        assertEquals(73.6832456, restored.longitude, 0.0000001)
    }

    @Test
    fun `southern and western coordinates keep their sign`() {
        val domain = Location(
            id = 8,
            name = "Somewhere else entirely",
            address = "",
            latitude = -33.8688,
            longitude = -70.6693
        )
        val restored = domain.toEntity().toDomain()

        assertEquals(-33.8688, restored.latitude, 0.0000001)
        assertEquals(-70.6693, restored.longitude, 0.0000001)
    }

    /**
     * The columns behind Place Detail and the three Places tabs.
     *
     * These were added in the 3 → 4 migration, so every one of them is a field that exists in
     * the model and could quietly be missing from the mapper. The whole-object comparison at
     * the end is what catches that.
     */
    @Test
    fun `a place round-trips with its photo, rating, hours and flags`() {
        val domain = Location(
            id = 9,
            name = "City Palace",
            address = "Old City, Udaipur",
            latitude = 24.5760,
            longitude = 73.6832,
            category = "Historic",
            photoUri = "/data/user/0/com.tripcompanion.app/files/places/loc_9.jpg",
            rating = 4.6,
            openingHours = "09:30 – 17:30, daily",
            estimatedVisitMinutes = 150,
            isVisited = true,
            isSaved = true
        )

        val entity = domain.toEntity()

        assertEquals(domain.photoUri, entity.photoUri)
        assertEquals(4.6, entity.rating!!, 0.0000001)
        assertEquals(domain.openingHours, entity.openingHours)
        assertEquals(150, entity.estimatedVisitMinutes)
        assertTrue(entity.isVisited)
        assertTrue(entity.isSaved)
        assertEquals(domain, entity.toDomain())
    }

    /**
     * Unrated is not the same claim as rated zero.
     *
     * Most places the user adds by hand have no rating at all. Storing that as 0.0 would put
     * an empty five-star row on every one of them.
     */
    @Test
    fun `an unrated place keeps a null rating rather than becoming zero`() {
        val domain = Location(id = 10, name = "A corner stall")
        val restored = domain.toEntity().toDomain()

        assertNull(restored.rating)
        assertNull(restored.estimatedVisitMinutes)
        assertNull(restored.photoUri)
        assertEquals(domain, restored)
    }

    /**
     * Saved and visited are independent, because they back different tabs.
     *
     * Somewhere can be bookmarked and not yet seen, or seen and never bookmarked; collapsing
     * the two would empty one tab into the other.
     */
    @Test
    fun `saved and visited are stored independently`() {
        val savedOnly = Location(id = 11, name = "Not yet", isSaved = true).toEntity().toDomain()
        assertTrue(savedOnly.isSaved)
        assertFalse(savedOnly.isVisited)

        val visitedOnly = Location(id = 12, name = "Been", isVisited = true).toEntity().toDomain()
        assertFalse(visitedOnly.isSaved)
        assertTrue(visitedOnly.isVisited)
    }

    // ---- Train -------------------------------------------------------------

    @Test
    fun `a train round-trips through its entity`() {
        val domain = Train(
            id = 3,
            tripId = 10,
            eventId = 25,
            number = "12992",
            name = "Udaipur City Superfast Express",
            originCode = "ADI",
            originName = "Ahmedabad Jn",
            destinationCode = "UDZ",
            destinationName = "Udaipur City",
            departureTime = LocalDateTime.of(2026, 11, 3, 22, 40),
            arrivalTime = LocalDateTime.of(2026, 11, 4, 5, 15),
            travelClass = "CC",
            pnr = "8412345678",
            bookingStatus = TrainBookingStatus.CONFIRMED,
            platform = "3",
            knownDelayMinutes = 14,
            notes = "Pantry car on this rake.",
            createdAt = LocalDateTime.of(2026, 8, 1, 10, 0),
            updatedAt = LocalDateTime.of(2026, 8, 2, 12, 0)
        )

        val entity = domain.toEntity()

        assertEquals(domain.number, entity.number)
        assertEquals(domain.pnr, entity.pnr)
        assertEquals(domain.bookingStatus.name, entity.bookingStatus)
        assertEquals(domain.platform, entity.platform)
        // No passengers on this one, so the round trip is exact. The party lives in its own
        // table and comes back through `toDomain(passengers)`, tested separately below.
        assertEquals(domain, entity.toDomain())
    }

    @Test
    fun `every booking status survives the string column`() {
        TrainBookingStatus.entries.forEach { status ->
            val domain = sampleTrain().copy(bookingStatus = status)
            assertEquals(status, domain.toEntity().toDomain().bookingStatus)
        }
    }

    /** The link to an itinerary event is optional — a ticket can be added before the plan is. */
    @Test
    fun `a train with no event linked keeps its null`() {
        val domain = sampleTrain().copy(eventId = null)

        assertNull(domain.toEntity().eventId)
        assertNull(domain.toEntity().toDomain().eventId)
    }

    // ---- TrainStop ---------------------------------------------------------

    @Test
    fun `a timetable stop round-trips through its entity`() {
        val domain = TrainStop(
            id = 40,
            trainId = 3,
            serialNo = 2,
            stationCode = "AII",
            stationName = "Ajmer Jn",
            scheduledArrival = LocalTime.of(1, 20),
            scheduledDeparture = LocalTime.of(1, 30),
            distanceKm = 200,
            dayOffset = 1
        )

        val entity = domain.toEntity()

        assertEquals(domain.serialNo, entity.serialNo)
        assertEquals(domain.scheduledArrival, entity.scheduledArrival)
        assertEquals(domain.dayOffset, entity.dayOffset)
        assertEquals(domain, entity.toDomain())
    }

    /**
     * The ends of a route are defined by absent times, so those nulls carry meaning.
     *
     * `isOrigin` and `isTerminus` are derived from them, and a null read back as midnight
     * would put a 00:00 arrival on the first row of every route screen.
     */
    @Test
    fun `the origin's missing arrival and the terminus' missing departure stay missing`() {
        val origin = TrainStop(
            serialNo = 1,
            stationCode = "ADI",
            stationName = "Ahmedabad Jn",
            scheduledDeparture = LocalTime.of(22, 40)
        ).toEntity(3).toDomain()

        assertNull(origin.scheduledArrival)
        assertTrue(origin.isOrigin)

        val terminus = TrainStop(
            serialNo = 9,
            stationCode = "UDZ",
            stationName = "Udaipur City",
            scheduledArrival = LocalTime.of(5, 15)
        ).toEntity(3).toDomain()

        assertNull(terminus.scheduledDeparture)
        assertTrue(terminus.isTerminus)
    }

    /** A stop fetched for one train is written against that train, not whatever it carried. */
    @Test
    fun `an explicit train id wins over the stop's own`() {
        val domain = TrainStop(
            trainId = 3,
            serialNo = 1,
            stationCode = "ADI",
            stationName = "Ahmedabad Jn",
            scheduledDeparture = LocalTime.of(22, 40)
        )

        assertEquals(3L, domain.toEntity().trainId)
        assertEquals(9L, domain.toEntity(9L).trainId)
    }

    // ---- TrainRunStatus ----------------------------------------------------

    /**
     * The snapshot spans two tables, so the round trip is asserted across both.
     *
     * `toEntity` drops the stop rows and `toDomain` takes them back as an argument, which is
     * the one mapper in the app whose two halves have to be reassembled by the caller.
     */
    @Test
    fun `a running snapshot round-trips across both of its tables`() {
        val domain = TrainRunStatus(
            trainId = 3,
            fetchedAt = LocalDateTime.of(2026, 11, 4, 1, 25),
            runDate = LocalDate.of(2026, 11, 3),
            source = TrainRunSource.LIVE,
            currentStationCode = "AII",
            currentStationName = "Ajmer Jn",
            delayMinutes = 14,
            lastDepartedSerial = 1,
            progressFraction = 0.52f,
            nextStopCode = "COR",
            nextStopName = "Chittaurgarh Jn",
            nextStopEta = LocalTime.of(3, 44),
            averageSpeedKmph = 72.5,
            message = "SUCCESS",
            stops = listOf(
                TrainStopStatus(
                    serialNo = 1,
                    stationCode = "ADI",
                    stationName = "Ahmedabad Jn",
                    scheduledDeparture = LocalTime.of(22, 40),
                    actualDeparture = LocalTime.of(22, 54),
                    departureDelayMinutes = 14,
                    distanceKm = 0,
                    isDeparted = true
                ),
                TrainStopStatus(
                    serialNo = 2,
                    stationCode = "AII",
                    stationName = "Ajmer Jn",
                    scheduledArrival = LocalTime.of(1, 20),
                    actualArrival = LocalTime.of(1, 34),
                    scheduledDeparture = LocalTime.of(1, 30),
                    arrivalDelayMinutes = 14,
                    distanceKm = 200,
                    dayOffset = 1,
                    isCurrent = true
                )
            )
        )

        val header = domain.toEntity()
        val rows = domain.stops.map { it.toEntity(domain.trainId) }

        assertEquals(domain.source.name, header.source)
        assertEquals(domain.nextStopEta, header.nextStopEta)
        assertEquals(listOf(3L, 3L), rows.map { it.trainId })
        assertEquals(domain, header.toDomain(rows))
    }

    @Test
    fun `every run source survives the string column`() {
        TrainRunSource.entries.forEach { source ->
            val domain = sampleSnapshot().copy(source = source)
            assertEquals(source, domain.toEntity().toDomain().source)
        }
    }

    /**
     * A figure the provider could not derive stays absent.
     *
     * The API carries no speed field, so this is null more often than not, and zero would be
     * read as a stopped train.
     */
    @Test
    fun `an undetermined speed and ETA stay null through storage`() {
        val domain = sampleSnapshot().copy(averageSpeedKmph = null, nextStopEta = null)
        val restored = domain.toEntity().toDomain()

        assertNull(restored.averageSpeedKmph)
        assertNull(restored.nextStopEta)
    }

    /** A snapshot header with no stop rows is a header, not a crash. */
    @Test
    fun `a snapshot with no stop rows reads back with an empty route`() {
        val restored = sampleSnapshot().toEntity().toDomain()
        assertEquals(emptyList<TrainStopStatus>(), restored.stops)
    }

    // ---- StayDetails -------------------------------------------------------

    @Test
    fun `hotel details round-trip through their entity`() {
        val domain = StayDetails(
            eventId = 25,
            bookingReference = "HTL-8842291",
            roomType = "Lake-view double",
            guests = 2,
            contactPhone = "+91 294 000 0000",
            address = "Gangaur Ghat Marg, Udaipur 313001",
            checkInInstructions = "Ask for the boat jetty entrance after 20:00.",
            photoUri = "/data/user/0/com.tripcompanion.app/files/stays/e_25.jpg"
        )

        val entity = domain.toEntity()

        assertEquals(domain.eventId, entity.eventId)
        assertEquals(domain.bookingReference, entity.bookingReference)
        assertEquals(domain.guests, entity.guests)
        assertEquals(domain, entity.toDomain())
    }

    /**
     * A stay with nothing filled in but the event it belongs to is still storable.
     *
     * The hotel screen is reachable as soon as a STAY event exists, which is usually before
     * any of the paperwork has arrived.
     */
    @Test
    fun `a stay with no paperwork yet is a valid row`() {
        val domain = StayDetails(eventId = 25)
        val restored = domain.toEntity().toDomain()

        assertEquals("", restored.bookingReference)
        assertEquals(1, restored.guests)
        assertNull(restored.photoUri)
        assertEquals(domain, restored)
    }

    // ---- Activity (legacy, kept for pre-V2 rows — §3) ----------------------

    @Test
    fun `an activity still round-trips so pre-V2 rows survive`() {
        val domain = Activity(
            id = 101,
            eventId = 25,
            title = "Ancient Artifacts Gallery",
            category = "Sightseeing",
            order = 0,
            isOptional = false,
            completionStatus = ActivityStatus.PENDING,
            notes = "Audio guide #4"
        )

        val entity = domain.toEntity()

        assertEquals(domain.id, entity.id)
        assertEquals(domain.title, entity.title)
        assertEquals(domain.completionStatus.name, entity.completionStatus)
        assertEquals(domain, entity.toDomain())
    }

    // ---- PlannedPhoto ------------------------------------------------------

    @Test
    fun `a planned photo round-trips through its entity`() {
        val domain = PlannedPhoto(
            id = 201,
            eventId = 25,
            title = "Grand entrance arch",
            referenceImageUri = "/data/user/0/com.tripcompanion.app/files/planned_photos/p_123.jpg",
            order = 0
        )

        val entity = domain.toEntity()

        assertEquals(domain.id, entity.id)
        assertEquals(domain.title, entity.title)
        assertEquals(domain.referenceImageUri, entity.referenceImageUri)
        assertEquals(domain, entity.toDomain())
    }

    @Test
    fun `a photo with no title at all is a valid row`() {
        // §15: "The user must be able to save a photo with no text at all."
        val domain = PlannedPhoto(
            id = 202,
            eventId = 25,
            referenceImageUri = "/data/user/0/com.tripcompanion.app/files/planned_photos/p_9.jpg"
        )

        val restored = domain.toEntity().toDomain()

        assertEquals("", restored.title)
        assertEquals(domain, restored)
    }

    @Test
    fun `a photo with no image yet keeps a null reference`() {
        // §16 persists a path, never bytes, so "no image" has to be representable
        // as an absent path rather than an empty blob.
        val domain = PlannedPhoto(id = 203, eventId = 25, title = "Find something here")
        val restored = domain.toEntity().toDomain()

        assertNull(restored.referenceImageUri)
        assertEquals(domain, restored)
    }

    // ---- Type converters ---------------------------------------------------

    @Test
    fun `LocalDateTime converts to a string and back`() {
        val moment = LocalDateTime.of(2026, 8, 21, 14, 20)
        val stored = converters.fromLocalDateTime(moment)

        // Note this is not LocalDateTime.toString(), which drops a zero seconds
        // field. Formatting through ISO_LOCAL_DATE_TIME always writes the seconds,
        // so every row is the same width — which is what the ORDER BY below relies on.
        assertEquals("2026-08-21T14:20:00", stored)
        assertEquals(moment, converters.toLocalDateTime(stored))
    }

    @Test
    fun `a stored date-time never carries a fractional second`() {
        // §9: seconds and nanoseconds are not part of trip planning. Values are
        // truncated to the minute on the way in, so the column should only ever hold
        // a ":00" tail — never the "…:00.517484" that §9 calls out by name.
        listOf(
            LocalDateTime.of(2026, 8, 21, 0, 0),
            LocalDateTime.of(2026, 8, 21, 14, 20),
            LocalDateTime.of(2026, 8, 21, 23, 59)
        ).forEach { moment ->
            val stored = converters.fromLocalDateTime(moment)!!

            assertFalse("a fraction reached the column: $stored", stored.contains("."))
            assertEquals("every row should be the same width: $stored", 19, stored.length)
        }
    }

    @Test
    fun `LocalDate converts to a string and back`() {
        val date = LocalDate.of(2026, 8, 21)
        val stored = converters.fromLocalDate(date)

        assertEquals("2026-08-21", stored)
        assertEquals(date, converters.toLocalDate(stored))
    }

    @Test
    fun `LocalTime converts to a string and back`() {
        val time = LocalTime.of(22, 40)
        val stored = converters.fromLocalTime(time)

        assertEquals("22:40", stored)
        assertEquals(time, converters.toLocalTime(stored))
    }

    /**
     * A timetable time is a time to the minute, and the column enforces that.
     *
     * `ISO_LOCAL_TIME` would write "22:40:55" here and hand a stray second back on the way
     * out, which §9 rules out — so the converter uses an explicit `HH:mm` pattern instead.
     */
    @Test
    fun `a stored time is truncated to the minute`() {
        val stored = converters.fromLocalTime(LocalTime.of(22, 40, 55))

        assertEquals("22:40", stored)
        assertEquals(LocalTime.of(22, 40), converters.toLocalTime(stored))
    }

    @Test
    fun `midnight stores as a time rather than as nothing`() {
        val stored = converters.fromLocalTime(LocalTime.MIDNIGHT)

        assertEquals("00:00", stored)
        assertEquals(LocalTime.MIDNIGHT, converters.toLocalTime(stored))
    }

    /**
     * Every stored time is the same five characters wide.
     *
     * A single-digit hour written as "4:27" would sort after "22:40" as a plain string, and
     * the route DAO orders on `serialNo` precisely so it never has to depend on that — but a
     * ragged column would still render ragged in the route list.
     */
    @Test
    fun `stored times are all the same width`() {
        listOf(
            LocalTime.of(0, 0),
            LocalTime.of(4, 27),
            LocalTime.of(9, 5),
            LocalTime.of(23, 59)
        ).forEach { time ->
            val stored = converters.fromLocalTime(time)!!

            assertEquals("ragged time column: $stored", 5, stored.length)
            assertEquals(time, converters.toLocalTime(stored))
        }
    }

    @Test
    fun `nulls pass through the converters untouched`() {
        assertNull(converters.fromLocalDateTime(null))
        assertNull(converters.toLocalDateTime(null))
        assertNull(converters.fromLocalDate(null))
        assertNull(converters.toLocalDate(null))
        assertNull(converters.fromLocalTime(null))
        assertNull(converters.toLocalTime(null))
    }

    @Test
    fun `stored date-times sort chronologically as plain strings`() {
        // SQLite orders these lexicographically. The ISO form is chosen precisely
        // so that string order and clock order are the same thing — an ORDER BY on
        // the column has to agree with the app's comparator (§10).
        val moments = listOf(
            LocalDateTime.of(2026, 8, 21, 9, 5),
            LocalDateTime.of(2026, 8, 21, 10, 0),
            LocalDateTime.of(2026, 8, 21, 23, 59),
            LocalDateTime.of(2026, 8, 22, 0, 0),
            LocalDateTime.of(2026, 9, 1, 8, 0),
            LocalDateTime.of(2027, 1, 1, 0, 0)
        )
        val asStrings = moments.map { converters.fromLocalDateTime(it)!! }

        assertEquals(asStrings.sorted(), asStrings)
    }

    @Test
    fun `midnight is stored without a truncated time component`() {
        // A bare "2026-08-22" would parse back as a LocalDate, not a LocalDateTime,
        // and would sort before every time on the previous day.
        val midnight = LocalDateTime.of(2026, 8, 22, 0, 0)
        val stored = converters.fromLocalDateTime(midnight)!!

        assertTrue("midnight lost its time component: $stored", stored.contains("T00:00"))
        assertEquals(midnight, converters.toLocalDateTime(stored))
    }

    private fun sampleTrain() = Train(
        id = 3,
        tripId = 10,
        eventId = 25,
        number = "12992",
        name = "A train",
        originCode = "ADI",
        originName = "Ahmedabad Jn",
        destinationCode = "UDZ",
        destinationName = "Udaipur City",
        departureTime = LocalDateTime.of(2026, 11, 3, 22, 40),
        arrivalTime = LocalDateTime.of(2026, 11, 4, 5, 15)
    )

    private fun sampleSnapshot() = TrainRunStatus(
        trainId = 3,
        fetchedAt = LocalDateTime.of(2026, 11, 4, 1, 25),
        runDate = LocalDate.of(2026, 11, 3),
        source = TrainRunSource.LIVE,
        currentStationCode = "AII",
        currentStationName = "Ajmer Jn",
        delayMinutes = 14,
        lastDepartedSerial = 1,
        progressFraction = 0.52f,
        nextStopCode = "COR",
        nextStopName = "Chittaurgarh Jn",
        nextStopEta = LocalTime.of(3, 44),
        averageSpeedKmph = 72.5,
        message = "SUCCESS"
    )

    private fun sampleEvent(
        type: EventType = EventType.VISIT,
        status: EventStatus = EventStatus.UPCOMING,
        start: LocalDateTime = LocalDateTime.of(2026, 11, 2, 14, 0),
        end: LocalDateTime = LocalDateTime.of(2026, 11, 2, 17, 0)
    ) = Event(
        id = 1,
        tripId = 10,
        type = type,
        title = "A stop",
        startTime = start,
        endTime = end,
        locationId = 5,
        whatWeAreDoing = "",
        notes = "",
        status = status,
        order = 0
    )
}

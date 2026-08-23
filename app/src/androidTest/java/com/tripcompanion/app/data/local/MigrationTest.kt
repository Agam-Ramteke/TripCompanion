package com.tripcompanion.app.data.local

import android.content.ContentValues
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tripcompanion.app.data.local.entity.StayDetailsEntity
import com.tripcompanion.app.data.local.entity.TrainEntity
import com.tripcompanion.app.data.local.entity.TrainPassengerEntity
import com.tripcompanion.app.data.local.entity.TrainRunStatusEntity
import com.tripcompanion.app.data.local.entity.TrainRunStopEntity
import com.tripcompanion.app.data.local.entity.TrainStopEntity
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.domain.model.TrainRunSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The upgrade path from the first shipped schema to the current one (§28).
 *
 * A trip plan is typed by hand over days and cannot be regenerated, which is why
 * `DatabaseModule` refuses `fallbackToDestructiveMigration`. That refusal is only
 * worth anything if the migrations actually work, and a migration bug is invisible
 * in development — a developer's device is wiped and reinstalled constantly, so the
 * upgrade path is the one code path that never runs locally.
 *
 * Version 2 was never released and so was never exported to `schemas/2.json`; it
 * exists only as a step in this chain. That is why the tests that start at version 1
 * run the whole chain rather than each hop separately, and it is also honest about
 * what shipped. Versions 3 and 4 *did* ship, so each gets its own seed and its own
 * tests: those are the upgrades an installed copy of the app will actually take.
 *
 * `runMigrationsAndValidate` compares the migrated database against the exported
 * schema for the current version column by column, including indices and foreign
 * keys, so a rebuilt table that silently lost its `ON DELETE CASCADE` fails without
 * needing an assertion of its own.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TripDatabase::class.java
    )

    /**
     * Seeds a version 1 database with one of everything, then migrates.
     *
     * The rows are written as raw SQL on purpose: using today's entities would test
     * the migration against the schema it produces rather than the one it starts from.
     */
    private fun seedVersion1(): SupportSQLiteDatabase = helper.createDatabase(TEST_DB, 1).apply {
        insert(
            "trips",
            ContentValues().apply {
                put("id", 1L)
                put("name", "Udaipur, four days")
                put("startDate", "2026-11-03")
                put("endDate", "2026-11-06")
                put("status", "PLANNING")
                put("createdAt", "2026-08-01T09:00:00")
                put("updatedAt", "2026-08-01T09:00:00")
            }
        )
        insert(
            "locations",
            ContentValues().apply {
                put("id", 1L)
                put("name", "City Palace")
                put("address", "Old City, Udaipur")
                put("latitude", 24.5760)
                put("longitude", 73.6832)
                put("category", "Historic")
                put("createdAt", "2026-08-01T09:00:00")
                put("updatedAt", "2026-08-01T09:00:00")
            }
        )
        insert(
            "events",
            ContentValues().apply {
                put("id", 1L)
                put("tripId", 1L)
                put("type", "VISIT")
                put("title", "Palace tour")
                put("startTime", "2026-11-03T10:00:00")
                put("endTime", "2026-11-03T13:00:00")
                put("locationId", 1L)
                put("notes", "Buy tickets at the north gate.")
                put("status", "UPCOMING")
                put("order", 0)
                put("createdAt", "2026-08-01T09:00:00")
                put("updatedAt", "2026-08-01T09:00:00")
            }
        )
        insert(
            "planned_photos",
            ContentValues().apply {
                put("id", 1L)
                put("eventId", 1L)
                put("title", "Palace doorway")
                put("description", "The painted doorway on the second courtyard")
                put("referenceImageUri", "/files/planned_photos/p_1.jpg")
                put("poseOrShotDescription", "Stand centred, shoot from waist height, 35mm")
                put("status", "PLANNED")
                put("order", 0)
            }
        )
        insert(
            "activities",
            ContentValues().apply {
                put("id", 1L)
                put("eventId", 1L)
                put("title", "Buy tickets")
                put("category", "ADMIN")
                put("order", 0)
                put("isOptional", 0)
                put("completionStatus", "PENDING")
                put("notes", "")
            }
        )
        close()
    }

    /**
     * Seeds a version 3 database — the one that shipped before the redesign.
     *
     * 3 → 4 gets its own seed because that is the hop a real installed copy takes. Going
     * through 1 → 4 exercises the same code but starts from a database no user has.
     */
    private fun seedVersion3(): SupportSQLiteDatabase = helper.createDatabase(TEST_DB, 3).apply {
        insert(
            "trips",
            ContentValues().apply {
                put("id", 1L)
                put("name", "Udaipur, four days")
                put("startDate", "2026-11-03")
                put("endDate", "2026-11-06")
                put("status", "PLANNING")
                put("createdAt", "2026-08-01T09:00:00")
                put("updatedAt", "2026-08-01T09:00:00")
            }
        )
        insert(
            "locations",
            ContentValues().apply {
                put("id", 1L)
                put("name", "City Palace")
                put("address", "Old City, Udaipur")
                put("latitude", 24.5760)
                put("longitude", 73.6832)
                put("category", "Historic")
                put("createdAt", "2026-08-01T09:00:00")
                put("updatedAt", "2026-08-01T09:00:00")
            }
        )
        insert(
            "events",
            ContentValues().apply {
                put("id", 1L)
                put("tripId", 1L)
                put("type", "STAY")
                put("title", "Lake-view hotel")
                put("startTime", "2026-11-03T14:00:00")
                put("endTime", "2026-11-06T11:00:00")
                put("locationId", 1L)
                put("whatWeAreDoing", "Check in, drop the bags, walk to the ghat.")
                put("notes", "")
                put("status", "UPCOMING")
                put("order", 0)
                put("createdAt", "2026-08-01T09:00:00")
                put("updatedAt", "2026-08-01T09:00:00")
            }
        )
        insert(
            "planned_photos",
            ContentValues().apply {
                put("id", 1L)
                put("eventId", 1L)
                put("title", "Palace doorway")
                put("referenceImageUri", "/files/planned_photos/p_1.jpg")
                put("order", 0)
            }
        )
        close()
    }

    /**
     * Seeds a version 4 database — the one that shipped with a single coach and seat per train.
     *
     * Three trains, because 4 → 5 has to treat them differently: one with a berth allotted, one
     * waitlisted with nothing allotted yet, and one merely planned. The first also gets a
     * timetable and a cached running snapshot, which are the rows most at risk when the `trains`
     * table is rebuilt underneath them.
     *
     * Times are written as `HH:mm` because that is the only shape [Converters] will read back.
     */
    private fun seedVersion4(): SupportSQLiteDatabase = helper.createDatabase(TEST_DB, 4).apply {
        insert(
            "trips",
            ContentValues().apply {
                put("id", 1L)
                put("name", "Udaipur, four days")
                put("startDate", "2026-11-03")
                put("endDate", "2026-11-06")
                put("status", "PLANNING")
                put("createdAt", "2026-08-01T09:00:00")
                put("updatedAt", "2026-08-01T09:00:00")
            }
        )
        insert("trains", version4Train(id = 1L, coach = "S4", seat = "8", status = "CONFIRMED"))
        insert("trains", version4Train(id = 2L, coach = "", seat = "", status = "WAITLISTED"))
        insert("trains", version4Train(id = 3L, coach = "", seat = "", status = "NOT_BOOKED"))
        insert(
            "train_stops",
            ContentValues().apply {
                put("id", 1L)
                put("trainId", 1L)
                put("serialNo", 1)
                put("stationCode", "AAA")
                put("stationName", "Alpha Jn")
                put("scheduledDeparture", "22:40")
                put("distanceKm", 0)
                put("dayOffset", 0)
            }
        )
        insert(
            "train_stops",
            ContentValues().apply {
                put("id", 2L)
                put("trainId", 1L)
                put("serialNo", 2)
                put("stationCode", "BBB")
                put("stationName", "Beta")
                put("scheduledArrival", "05:15")
                put("distanceKm", 383)
                put("dayOffset", 1)
            }
        )
        insert(
            "train_run_status",
            ContentValues().apply {
                put("trainId", 1L)
                put("fetchedAt", "2026-11-04T01:25:00")
                put("runDate", "2026-11-03")
                put("source", "LIVE")
                put("currentStationCode", "AAA")
                put("currentStationName", "Alpha Jn")
                put("delayMinutes", 14)
                put("lastDepartedSerial", 1)
                put("progressFraction", 0.0)
                put("nextStopCode", "BBB")
                put("nextStopName", "Beta")
                put("nextStopEta", "05:29")
                put("averageSpeedKmph", 72.5)
                put("message", "")
            }
        )
        insert(
            "train_run_stops",
            ContentValues().apply {
                put("id", 1L)
                put("trainId", 1L)
                put("serialNo", 1)
                put("stationCode", "AAA")
                put("stationName", "Alpha Jn")
                put("scheduledDeparture", "22:40")
                put("actualDeparture", "22:54")
                put("departureDelayMinutes", 14)
                put("distanceKm", 0)
                put("dayOffset", 0)
                put("isDeparted", 1)
                put("isCurrent", 1)
            }
        )
        close()
    }

    /**
     * One row of the version 4 `trains` table, whose `coach` and `seat` no longer exist.
     *
     * Station and train names here are invented placeholders. §4 forbids code that recognises a
     * real one, and a fixture that used real names would make such code easy to write by mistake.
     */
    private fun version4Train(id: Long, coach: String, seat: String, status: String) =
        ContentValues().apply {
            put("id", id)
            put("tripId", 1L)
            put("number", "1000$id")
            put("name", "SOME EXPRESS")
            put("originCode", "AAA")
            put("originName", "Alpha Jn")
            put("destinationCode", "BBB")
            put("destinationName", "Beta")
            put("departureTime", "2026-11-03T22:40:00")
            put("arrivalTime", "2026-11-04T05:15:00")
            put("travelClass", "SL")
            put("coach", coach)
            put("seat", seat)
            put("pnr", "100000000$id")
            put("bookingStatus", status)
            put("platform", "3")
            put("knownDelayMinutes", 0)
            put("notes", "")
            put("createdAt", "2026-08-01T09:00:00")
            put("updatedAt", "2026-08-01T09:00:00")
        }

    private fun SupportSQLiteDatabase.insert(table: String, values: ContentValues) {
        val columns = values.keySet().joinToString(", ") { "`$it`" }
        val placeholders = values.keySet().joinToString(", ") { "?" }
        execSQL(
            "INSERT INTO `$table` ($columns) VALUES ($placeholders)",
            values.keySet().map { values.get(it) }.toTypedArray()
        )
    }

    /**
     * Runs the whole chain and hands the file back closed.
     *
     * Closing matters: the tests below reopen the same file through Room, and leaving
     * the helper's write connection open alongside it invites a lock rather than a
     * useful failure.
     */
    private fun migrateToCurrent() {
        helper.runMigrationsAndValidate(TEST_DB, LATEST_VERSION, true, *Migrations.ALL).close()
    }

    /** Reopens the migrated file through Room, which re-validates the identity hash. */
    private fun openAtCurrentVersion(): TripDatabase =
        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            TripDatabase::class.java,
            TEST_DB
        )
            .addMigrations(*Migrations.ALL)
            .build()
            .also { helper.closeWhenFinished(it) }

    @Test
    @Throws(IOException::class)
    fun migrating1ToLatestProducesTheSchemaRoomExpects() {
        seedVersion1()

        // Throws if the migrated schema differs from the exported one in any column,
        // index or foreign key. The `true` also asserts that no table was left behind.
        migrateToCurrent()
    }

    @Test
    @Throws(IOException::class)
    fun migrating1ToLatestKeepsTheTripThePlanAndThePhoto() = runBlocking {
        seedVersion1()
        migrateToCurrent()

        val db = openAtCurrentVersion()

        val trip = db.tripDao().getTripById(1L).first()
        assertNotNull("the trip did not survive the upgrade", trip)
        assertEquals("Udaipur, four days", trip!!.name)

        val events = db.eventDao().getEventsForTrip(1L).first()
        assertEquals(1, events.size)
        assertEquals("Palace tour", events.single().title)
        assertEquals("Buy tickets at the north gate.", events.single().notes)
        assertEquals(1L, events.single().locationId)

        val location = db.locationDao().getLocationByIdOnce(1L)
        assertNotNull(location)
        assertEquals("City Palace", location!!.name)
        assertEquals(24.5760, location.latitude, 0.0000001)

        val photos = db.plannedPhotoDao().getPhotosForEvent(1L).first()
        assertEquals(1, photos.size)
        assertEquals("Palace doorway", photos.single().title)
        assertEquals("/files/planned_photos/p_1.jpg", photos.single().referenceImageUri)

        // §3 keeps the Activity table for compatibility, so pre-V2 rows must still read.
        assertEquals(1, db.activityDao().getActivitiesForEvent(1L).first().size)
    }

    @Test
    @Throws(IOException::class)
    fun theNewColumnsArriveWithUsableValues() = runBlocking {
        seedVersion1()
        migrateToCurrent()

        val db = openAtCurrentVersion()
        val event = db.eventDao().getEventsForTrip(1L).first().single()
        val location = db.locationDao().getLocationByIdOnce(1L)!!

        // §14's field is NOT NULL, so an upgraded row needs an empty string rather than
        // a null the entity mapper would refuse to read.
        assertEquals("", event.whatWeAreDoing)

        // §13's provenance columns are genuinely unknown for a place saved before they
        // existed, so null is the right answer here rather than an invented value.
        assertNull(location.providerPlaceId)
        assertNull(location.providerName)
    }

    @Test
    @Throws(IOException::class)
    fun thePhotographyAssignmentColumnsAreGone() {
        seedVersion1()
        val migrated = helper.runMigrationsAndValidate(TEST_DB, LATEST_VERSION, true, *Migrations.ALL)

        val columns = migrated.query("PRAGMA table_info(`planned_photos`)").use { cursor ->
            val names = mutableListOf<String>()
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) names += cursor.getString(nameIndex)
            names
        }
        migrated.close()

        // §15: the photo card is a visual reference, not a photography assignment.
        assertEquals(listOf("id", "eventId", "title", "referenceImageUri", "order"), columns)
    }

    @Test
    @Throws(IOException::class)
    fun theRebuiltPhotoTableStillCascades() = runBlocking {
        // MIGRATION_2_3 rebuilds planned_photos because SQLite on API 26 has no DROP
        // COLUMN. A rebuild is exactly where a foreign key gets quietly lost, and the
        // symptom would be orphaned rows keeping image files alive forever.
        seedVersion1()
        migrateToCurrent()

        val db = openAtCurrentVersion()
        assertEquals(1, db.plannedPhotoDao().getPhotosForEvent(1L).first().size)

        db.eventDao().deleteEventById(1L)

        assertTrue(
            "a photo outlived the event it belonged to",
            db.plannedPhotoDao().getPhotosForEvent(1L).first().isEmpty()
        )
        assertTrue(
            "an activity outlived the event it belonged to",
            db.activityDao().getActivitiesForEvent(1L).first().isEmpty()
        )
    }

    @Test
    @Throws(IOException::class)
    fun anUpgradedDatabaseAcceptsNewWork() = runBlocking {
        // The migration is only finished if the database is writable afterwards — a
        // rebuilt table with a broken AUTOINCREMENT would read fine and fail on insert.
        seedVersion1()
        migrateToCurrent()

        val db = openAtCurrentVersion()

        val newEventId = db.eventDao().insertEvent(
            db.eventDao().getEventsForTrip(1L).first().single().copy(
                id = 0,
                title = "Rooftop dinner",
                type = EventType.FOOD.name,
                startTime = LocalDateTime.of(2026, 11, 3, 20, 0),
                endTime = LocalDateTime.of(2026, 11, 3, 22, 0),
                whatWeAreDoing = "Book the corner table facing the lake.",
                order = 1
            )
        )

        assertTrue("the upgraded database would not take a new event", newEventId > 1L)
        assertEquals(2, db.eventDao().getEventsForTrip(1L).first().size)
        assertEquals(2, db.eventDao().getNextOrder(1L))
    }

    // ---- 3 → 4: trains, hotel paperwork, covers and place state ------------

    @Test
    @Throws(IOException::class)
    fun migrating3ToLatestProducesTheSchemaRoomExpects() {
        seedVersion3()

        // Five hand-written CREATE TABLEs have to match what Room generates for the new
        // entities exactly. This is the assertion that says they do.
        migrateToCurrent()
    }

    @Test
    @Throws(IOException::class)
    fun migrating3ToLatestKeepsTheTripThePlanAndThePhoto() = runBlocking {
        seedVersion3()
        migrateToCurrent()

        val db = openAtCurrentVersion()

        assertEquals("Udaipur, four days", db.tripDao().getTripById(1L).first()!!.name)

        val event = db.eventDao().getEventsForTrip(1L).first().single()
        assertEquals("Lake-view hotel", event.title)
        assertEquals("Check in, drop the bags, walk to the ghat.", event.whatWeAreDoing)

        assertEquals("City Palace", db.locationDao().getLocationByIdOnce(1L)!!.name)
        assertEquals(1, db.plannedPhotoDao().getPhotosForEvent(1L).first().size)
    }

    /**
     * A place saved before this version has no photo and no rating, and says so.
     *
     * Zero would render as an empty five-star row on every place the user typed in by hand,
     * and an empty string for a rating column would not parse at all. Null is the only
     * honest value for a fact nobody has entered yet.
     */
    @Test
    @Throws(IOException::class)
    fun aPlaceFromBeforeTheUpgradeIsUnratedRatherThanRatedZero() = runBlocking {
        seedVersion3()
        migrateToCurrent()

        val db = openAtCurrentVersion()
        val location = db.locationDao().getLocationByIdOnce(1L)!!

        assertNull(location.photoUri)
        assertNull(location.rating)
        assertNull(location.estimatedVisitMinutes)
        assertEquals("", location.openingHours)

        // Both Places tabs start empty for an upgraded install rather than claiming
        // everything has already been seen.
        assertFalse(location.isVisited)
        assertFalse(location.isSaved)

        assertNull(db.tripDao().getTripById(1L).first()!!.coverImageUri)
    }

    @Test
    @Throws(IOException::class)
    fun theTrainAndHotelTablesArriveEmptyAndUsable() = runBlocking {
        seedVersion3()
        migrateToCurrent()

        val db = openAtCurrentVersion()

        assertTrue("an upgraded database invented a train", db.trainDao().getAllTrains().first().isEmpty())
        assertNull(db.stayDetailsDao().getForEventOnce(1L))

        val trainId = db.trainDao().insertTrain(
            TrainEntity(
                tripId = 1L,
                eventId = 1L,
                number = "12992",
                name = "Udaipur City Superfast Express",
                originCode = "ADI",
                originName = "Ahmedabad Jn",
                destinationCode = "UDZ",
                destinationName = "Udaipur City",
                departureTime = LocalDateTime.of(2026, 11, 3, 22, 40),
                arrivalTime = LocalDateTime.of(2026, 11, 4, 5, 15),
                pnr = "8412345678",
                platform = "3"
            )
        )
        assertTrue("the upgraded database would not take a train", trainId > 0L)

        db.trainStopDao().insertStops(
            listOf(
                TrainStopEntity(
                    trainId = trainId,
                    serialNo = 1,
                    stationCode = "ADI",
                    stationName = "Ahmedabad Jn",
                    scheduledDeparture = LocalTime.of(22, 40),
                    distanceKm = 0
                ),
                TrainStopEntity(
                    trainId = trainId,
                    serialNo = 2,
                    stationCode = "UDZ",
                    stationName = "Udaipur City",
                    scheduledArrival = LocalTime.of(5, 15),
                    distanceKm = 383,
                    dayOffset = 1
                )
            )
        )
        assertEquals(2, db.trainStopDao().getStopsForTrainOnce(trainId).size)

        db.trainRunStatusDao().replaceSnapshot(
            status = TrainRunStatusEntity(
                trainId = trainId,
                fetchedAt = LocalDateTime.of(2026, 11, 4, 1, 25),
                runDate = LocalDate.of(2026, 11, 3),
                source = TrainRunSource.LIVE.name,
                currentStationCode = "ADI",
                delayMinutes = 14,
                nextStopEta = LocalTime.of(5, 29),
                averageSpeedKmph = 72.5
            ),
            stops = listOf(
                TrainRunStopEntity(
                    trainId = trainId,
                    serialNo = 1,
                    stationCode = "ADI",
                    stationName = "Ahmedabad Jn",
                    actualDeparture = LocalTime.of(22, 54),
                    departureDelayMinutes = 14,
                    isDeparted = true
                )
            )
        )
        val snapshot = db.trainRunStatusDao().getStatus(trainId).first()!!
        assertEquals(14, snapshot.delayMinutes)
        assertEquals(LocalTime.of(5, 29), snapshot.nextStopEta)
        assertEquals(72.5, snapshot.averageSpeedKmph!!, 0.0001)

        db.stayDetailsDao().upsert(
            StayDetailsEntity(
                eventId = 1L,
                bookingReference = "HTL-8842291",
                roomType = "Lake-view double",
                guests = 2,
                address = "Gangaur Ghat Marg, Udaipur 313001"
            )
        )
        assertEquals("HTL-8842291", db.stayDetailsDao().getForEventOnce(1L)!!.bookingReference)
    }

    /**
     * The new tables hang off the trip and the event, so deleting either has to clear them.
     *
     * A hand-written `CREATE TABLE` is exactly where an `ON DELETE CASCADE` gets forgotten,
     * and the symptom is a phantom train that outlives the trip it belonged to and shows up
     * on the Trains tab with nowhere to go.
     */
    @Test
    @Throws(IOException::class)
    fun theNewTablesCascadeFromTheTripAndTheEvent() = runBlocking {
        seedVersion3()
        migrateToCurrent()

        val db = openAtCurrentVersion()

        val trainId = db.trainDao().insertTrain(
            TrainEntity(
                tripId = 1L,
                number = "12992",
                departureTime = LocalDateTime.of(2026, 11, 3, 22, 40),
                arrivalTime = LocalDateTime.of(2026, 11, 4, 5, 15)
            )
        )
        db.trainStopDao().insertStops(
            listOf(
                TrainStopEntity(
                    trainId = trainId,
                    serialNo = 1,
                    stationCode = "ADI",
                    stationName = "Ahmedabad Jn",
                    scheduledDeparture = LocalTime.of(22, 40)
                )
            )
        )
        db.trainRunStatusDao().replaceSnapshot(
            status = TrainRunStatusEntity(
                trainId = trainId,
                fetchedAt = LocalDateTime.of(2026, 11, 4, 1, 25),
                runDate = LocalDate.of(2026, 11, 3)
            ),
            stops = listOf(
                TrainRunStopEntity(
                    trainId = trainId,
                    serialNo = 1,
                    stationCode = "ADI",
                    stationName = "Ahmedabad Jn"
                )
            )
        )
        db.stayDetailsDao().upsert(StayDetailsEntity(eventId = 1L, roomType = "Lake-view double"))

        // The hotel paperwork belongs to the event, not the trip.
        db.eventDao().deleteEventById(1L)
        assertNull("hotel paperwork outlived its event", db.stayDetailsDao().getForEventOnce(1L))

        db.tripDao().deleteTripById(1L)

        assertNull("a train outlived its trip", db.trainDao().getTrainByIdOnce(trainId))
        assertTrue(
            "a timetable outlived its train",
            db.trainStopDao().getStopsForTrainOnce(trainId).isEmpty()
        )
        assertNull(
            "a running snapshot outlived its train",
            db.trainRunStatusDao().getStatus(trainId).first()
        )
        assertTrue(
            "snapshot stop rows outlived their train",
            db.trainRunStatusDao().getRunStops(trainId).first().isEmpty()
        )
    }

    // ---- 4 → 5: a booking is a party, not a berth ---------------------------

    @Test
    @Throws(IOException::class)
    fun migrating4To5ProducesTheSchemaRoomExpects() {
        seedVersion4()

        // `trains` is rebuilt by hand here, column for column, so this validation is the
        // assertion that the rebuilt table is byte-identical to the one Room generates —
        // including the foreign key and the index that have to be recreated afterwards.
        migrateToCurrent()
    }

    /**
     * The one coach and seat the old schema held become the first passenger on the booking.
     *
     * The row has to exist, or a booking someone entered before the upgrade loses the only
     * record of where they sit. It also has to stay honest: the old schema never held a name,
     * an age or a gender, so those stay empty rather than becoming "Passenger 1", and the two
     * verbatim allotment strings stay empty because no e-ticket was ever read for this booking.
     */
    @Test
    @Throws(IOException::class)
    fun theOldCoachAndSeatBecomeTheFirstPassenger() = runBlocking {
        seedVersion4()
        migrateToCurrent()

        val db = openAtCurrentVersion()
        val party = db.trainPassengerDao().getPassengersForTrainOnce(1L)

        assertEquals("the allotment did not survive as a passenger", 1, party.size)
        val passenger = party.single()
        assertEquals(1, passenger.serialNo)
        assertEquals("S4", passenger.coach)
        assertEquals("8", passenger.berth)
        assertEquals("CONFIRMED", passenger.status)

        assertEquals("a name was invented for a passenger nobody named", "", passenger.name)
        assertNull(passenger.age)
        assertEquals("UNSPECIFIED", passenger.gender)
        assertEquals("UNKNOWN", passenger.berthType)
        assertNull(passenger.queuePosition)
        assertEquals("", passenger.queueKind)

        // Both verbatim columns belong to an e-ticket, and there was never one behind this row.
        assertEquals("", passenger.bookingStatusText)
        assertEquals("", passenger.currentStatusText)
    }

    /** A waitlisted booking has a status worth keeping even with no berth to keep with it. */
    @Test
    @Throws(IOException::class)
    fun aWaitlistedBookingWithNoBerthStillBecomesAPassenger() = runBlocking {
        seedVersion4()
        migrateToCurrent()

        val passenger = openAtCurrentVersion().trainPassengerDao()
            .getPassengersForTrainOnce(2L)
            .single()

        assertEquals("WAITLISTED", passenger.status)
        assertEquals("", passenger.coach)
        assertEquals("", passenger.berth)
    }

    /**
     * A train someone had merely planned gets no passenger at all.
     *
     * An empty row would show up on the ticket screen as a person with no name and no seat,
     * which is a passenger the user then has to delete. Nothing booked means nobody to list.
     */
    @Test
    @Throws(IOException::class)
    fun aTrainNobodyBookedGetsNoPassenger() = runBlocking {
        seedVersion4()
        migrateToCurrent()

        val db = openAtCurrentVersion()

        assertTrue(
            "a planned train invented a passenger",
            db.trainPassengerDao().getPassengersForTrainOnce(3L).isEmpty()
        )
        // Two of the three trains had something allotted, and only those two got a row.
        assertEquals(2, db.trainPassengerDao().getAllPassengers().first().size)
    }

    @Test
    @Throws(IOException::class)
    fun theSingleSeatColumnsAreGoneFromTheTrain() {
        seedVersion4()
        val migrated = helper.runMigrationsAndValidate(TEST_DB, LATEST_VERSION, true, *Migrations.ALL)

        val columns = migrated.query("PRAGMA table_info(`trains`)").use { cursor ->
            val names = mutableListOf<String>()
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) names += cursor.getString(nameIndex)
            names
        }
        migrated.close()

        // The pair that could only ever describe one person.
        assertFalse("coach outlived the rebuild", columns.contains("coach"))
        assertFalse("seat outlived the rebuild", columns.contains("seat"))

        // What the e-ticket columns replaced them with.
        assertTrue(
            "the rebuilt train is missing an e-ticket column",
            columns.containsAll(
                listOf("quota", "boardingCode", "bookedAt", "agentBookingId", "fareTotal")
            )
        )
        // The booking-wide status stays: it is the fallback for a train with no party.
        assertTrue(columns.contains("bookingStatus"))
    }

    /**
     * The timetable and the cached run survive the `trains` table being dropped.
     *
     * This is the specific hazard in this migration. `train_stops`, `train_run_status` and
     * `train_run_stops` all cascade from `trains`, so `DROP TABLE trains` would take every one
     * of their rows with it — except that Room issues `PRAGMA foreign_keys = ON` in `onOpen`,
     * which runs *after* `onUpgrade`. The migration depends on that ordering, so it is asserted
     * rather than trusted: if a future Room turns keys on earlier, this test fails instead of a
     * user's saved timetable quietly disappearing.
     */
    @Test
    @Throws(IOException::class)
    fun theTimetableAndTheCachedRunSurviveTheTrainsRebuild() = runBlocking {
        seedVersion4()
        migrateToCurrent()

        val db = openAtCurrentVersion()

        val stops = db.trainStopDao().getStopsForTrainOnce(1L)
        assertEquals("the timetable was cascaded away by the rebuild", 2, stops.size)
        assertEquals("AAA", stops.first().stationCode)
        assertEquals(LocalTime.of(22, 40), stops.first().scheduledDeparture)
        assertEquals(383, stops.last().distanceKm)
        assertEquals(1, stops.last().dayOffset)

        val snapshot = db.trainRunStatusDao().getStatus(1L).first()
        assertNotNull("the cached running status was cascaded away", snapshot)
        assertEquals(14, snapshot!!.delayMinutes)
        assertEquals(LocalTime.of(5, 29), snapshot.nextStopEta)
        assertEquals(72.5, snapshot.averageSpeedKmph!!, 0.0001)

        val runStops = db.trainRunStatusDao().getRunStops(1L).first()
        assertEquals(1, runStops.size)
        assertEquals(LocalTime.of(22, 54), runStops.single().actualDeparture)

        // Ids are preserved by the copy, which is the reason the children still point at a train.
        assertEquals("10001", db.trainDao().getTrainByIdOnce(1L)!!.number)
    }

    /**
     * The e-ticket columns arrive empty rather than filled in with plausible values.
     *
     * None of this was ever recorded before version 5, and a zero fare or a boarding station
     * copied from the origin would read on screen as a fact somebody entered.
     */
    @Test
    @Throws(IOException::class)
    fun theNewTicketColumnsArriveEmptyRatherThanInvented() = runBlocking {
        seedVersion4()
        migrateToCurrent()

        val train = openAtCurrentVersion().trainDao().getTrainByIdOnce(1L)!!

        assertEquals("", train.quota)
        assertEquals(0, train.distanceKm)
        assertEquals("", train.boardingCode)
        assertEquals("", train.boardingName)
        assertNull(train.bookedAt)
        assertEquals("", train.agentName)
        assertEquals("", train.agentBookingId)
        assertEquals("", train.transactionId)
        assertNull(train.fareTicket)
        assertNull(train.fareTotal)

        // What version 4 did hold is still there, unchanged.
        assertEquals("1000000001", train.pnr)
        assertEquals("CONFIRMED", train.bookingStatus)
        assertEquals("SL", train.travelClass)
        assertEquals("3", train.platform)
        assertEquals(LocalDateTime.of(2026, 11, 3, 22, 40), train.departureTime)
    }

    @Test
    @Throws(IOException::class)
    fun anUpgradedTrainTakesAWholePartyAndLetsItGoWithTheTrain() = runBlocking {
        seedVersion4()
        migrateToCurrent()

        val db = openAtCurrentVersion()

        db.trainPassengerDao().replacePassengers(
            trainId = 1L,
            passengers = listOf(
                TrainPassengerEntity(
                    trainId = 1L,
                    serialNo = 2,
                    name = "Rohit Bansal",
                    age = 34,
                    gender = "MALE",
                    status = "CONFIRMED",
                    coach = "S4",
                    berth = "9",
                    berthType = "LOWER",
                    bookingStatusText = "CNF/S4/9/LB",
                    currentStatusText = "CNF/S4/9"
                ),
                TrainPassengerEntity(
                    trainId = 1L,
                    serialNo = 1,
                    name = "Ananya Kapoor",
                    age = 21,
                    gender = "FEMALE",
                    status = "CONFIRMED",
                    coach = "S4",
                    berth = "8",
                    berthType = "SIDE_UPPER",
                    bookingStatusText = "CNF/S4/8/SU",
                    currentStatusText = "CNF/S4/8"
                )
            )
        )

        // §10's ordering: the railway's own serial, not insertion order.
        assertEquals(
            listOf("Ananya Kapoor", "Rohit Bansal"),
            db.trainPassengerDao().getPassengersForTrainOnce(1L).map { it.name }
        )

        db.trainDao().deleteTrainById(1L)

        assertTrue(
            "a passenger outlived the booking they were on",
            db.trainPassengerDao().getPassengersForTrainOnce(1L).isEmpty()
        )
    }

    private companion object {
        const val TEST_DB = "migration-test.db"

        /**
         * The schema every seed is migrated up to.
         *
         * One constant rather than a literal per call site: the next migration moves this line
         * and every test in the file follows, instead of quietly continuing to validate against
         * a version that is no longer current.
         */
        const val LATEST_VERSION = 5
    }
}

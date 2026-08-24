package com.tripcompanion.app.feature.train

import androidx.lifecycle.SavedStateHandle
import com.tripcompanion.app.core.time.FixedTimeProvider
import com.tripcompanion.app.data.local.fake.InMemoryTripDatabase
import com.tripcompanion.app.data.repository.EventRepositoryImpl
import com.tripcompanion.app.data.repository.TrainRepositoryImpl
import com.tripcompanion.app.data.repository.TripRepositoryImpl
import com.tripcompanion.app.domain.model.BerthType
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.domain.model.ParsedTicket
import com.tripcompanion.app.domain.model.PassengerGender
import com.tripcompanion.app.domain.model.TicketImportWarning
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.model.TrainAllotment
import com.tripcompanion.app.domain.model.TrainBookingStatus
import com.tripcompanion.app.domain.model.TrainFare
import com.tripcompanion.app.domain.model.TrainPassenger
import com.tripcompanion.app.domain.model.Trip
import com.tripcompanion.app.domain.service.JourneyEventLinker
import com.tripcompanion.app.domain.service.PnrLookupError
import com.tripcompanion.app.domain.service.PnrLookupOutcome
import com.tripcompanion.app.domain.service.PnrLookupService
import com.tripcompanion.app.domain.service.TicketImportError
import com.tripcompanion.app.domain.service.TicketImportOutcome
import com.tripcompanion.app.domain.service.TicketImportService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The train editor: what a ticket fills in, what it must not overwrite, and what survives a save.
 *
 * Two entry paths lead into this one form — typing a booking by hand and importing an e-ticket —
 * and the whole design rests on the second being a head start on the first rather than a separate
 * flow. That is only true if importing behaves like a careful assistant filling in a paper form:
 * it writes what the document says, it leaves alone what the document is silent about, and it
 * never quietly erases a column the form has no field for. Each of those is a test below.
 *
 * Everything runs against the real repositories over [InMemoryTripDatabase], so a save here goes
 * through the real mappers and comes back out of a real query. The only fake is the PDF reader,
 * because [com.tripcompanion.app.data.ticket.IrctcTicketParserTest] already runs the real one
 * against real tickets and what this class needs is control over the *outcome*, including the
 * failures a real file makes awkward to produce on demand.
 */
class TrainEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /** Weeks before the trip, which is when a ticket is actually bought. */
    private val clock = FixedTimeProvider(LocalDateTime.of(2026, 8, 23, 10, 0))

    private lateinit var db: InMemoryTripDatabase
    private lateinit var trips: TripRepositoryImpl
    private lateinit var events: EventRepositoryImpl
    private lateinit var trains: TrainRepositoryImpl
    private lateinit var linker: JourneyEventLinker
    private lateinit var importer: FakeTicketImportService
    private lateinit var pnrLookup: FakePnrLookupService

    private var tripId = 0L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = InMemoryTripDatabase()
        trips = TripRepositoryImpl(db.tripDao)
        events = EventRepositoryImpl(db.eventDao)
        trains = TrainRepositoryImpl(
            db.trainDao, db.trainPassengerDao, db.trainStopDao, db.trainRunStatusDao
        )
        importer = FakeTicketImportService()
        // No key configured by default: the PNR affordance is hidden, which is the state every
        // test here but the PNR ones runs in. The PNR tests flip isAvailable and set an outcome.
        pnrLookup = FakePnrLookupService()
        // The real linker over the real repositories: saving a booking here writes the itinerary
        // row too, which is the point — a train that is not on the plan is the bug this prevents.
        linker = JourneyEventLinker(events, trains)
        tripId = 0L
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- Opening the form --------------------------------------------------

    /**
     * A new booking opens on the trip's first day, not on today.
     *
     * Tickets are bought weeks ahead. Opening on today would mean correcting the date by hand
     * every single time, and a date nobody corrected is a countdown against the wrong moment.
     */
    @Test
    fun `a new train opens on the trip's first day`() = runTest(dispatcher) {
        val vm = editor()

        assertEquals(LocalDate.of(2026, 11, 3), vm.state.value.journeyDate)
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.trainId)
        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun `an existing booking opens with its party already in the form`() =
        runTest(dispatcher) {
            val id = trains.insertTrain(booking().copy(passengers = listOf(travellerOne())))

            val state = editor(trainId = id).state.value

            assertEquals(id, state.trainId)
            assertEquals("12345", state.number)
            assertEquals(LocalDate.of(2026, 11, 3), state.journeyDate)
            assertEquals(LocalTime.of(22, 40), state.departureTime)
            assertEquals(LocalTime.of(5, 20), state.arrivalTime)
            assertTrue(state.arrivesNextDay)

            assertEquals(1, state.passengers.size)
            val draft = state.passengers.single()
            assertEquals("A Traveller", draft.name)
            assertEquals("21", draft.age)
            assertEquals(PassengerGender.FEMALE, draft.gender)
            assertEquals("S4", draft.coach)
            assertEquals("8", draft.berth)
            assertEquals(BerthType.SIDE_UPPER, draft.berthType)
            assertEquals(TrainBookingStatus.CONFIRMED, draft.status)
            // The railway's own two columns, carried into the form untouched.
            assertEquals("CNF/S4/8/SU", draft.bookingStatusText)
            assertEquals("CNF/S4/8", draft.currentStatusText)
        }

    // ---- What an imported ticket fills in ----------------------------------

    @Test
    fun `a parsed ticket fills the whole form`() = runTest(dispatcher) {
        val vm = editor()
        importer.outcome = TicketImportOutcome.Parsed(ticket())

        vm.importTicket { ByteArray(0) }
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("12345", state.number)
        assertEquals("SOME EXPRESS", state.name)
        assertEquals("AAA", state.originCode)
        assertEquals("Alpha Jn", state.originName)
        assertEquals("BBB", state.destinationCode)
        assertEquals("Beta", state.destinationName)
        assertEquals("SL", state.travelClass)
        assertEquals("1000000001", state.pnr)
        assertEquals(LocalDate.of(2026, 11, 3), state.journeyDate)
        assertEquals(LocalTime.of(22, 40), state.departureTime)
        assertEquals(LocalTime.of(5, 20), state.arrivalTime)
        assertTrue(state.arrivesNextDay)
        assertEquals(2, state.passengers.size)
        assertEquals(listOf("A Traveller", "Another Traveller"), state.passengers.map { it.name })
        assertEquals(listOf("8", "6"), state.passengers.map { it.berth })
        assertFalse(state.isImporting)
        assertTrue(state.canSave)
    }

    /** The one sentence that tells the user the form just rearranged itself under them. */
    @Test
    fun `the import says what it did and who it found`() = runTest(dispatcher) {
        val vm = editor()
        importer.outcome = TicketImportOutcome.Parsed(ticket())

        vm.importTicket { ByteArray(0) }
        advanceUntilIdle()

        assertEquals(
            "Filled in from the Some Agent e-ticket — 2 passengers.",
            vm.state.value.importSummary
        )
        assertTrue(vm.state.value.importWarnings.isEmpty())
        assertNull(vm.state.value.importError)
    }

    /** One passenger is one passenger, not "1 passengers". */
    @Test
    fun `the import counts a single passenger in the singular`() = runTest(dispatcher) {
        val vm = editor()
        importer.outcome = TicketImportOutcome.Parsed(
            ticket().copy(agentName = "", passengers = listOf(travellerOne()))
        )

        vm.importTicket { ByteArray(0) }
        advanceUntilIdle()

        assertEquals("Filled in from the e-ticket — 1 passenger.", vm.state.value.importSummary)
    }

    /**
     * A blank on the ticket never overwrites something already typed.
     *
     * Someone who filled the number in by hand and then imported the PDF to save typing the
     * party keeps their number when the parse missed it. This is the difference between the
     * import being a help and it being a gamble.
     */
    @Test
    fun `a blank field on the ticket does not overwrite what was typed`() = runTest(dispatcher) {
        val vm = editor()
        vm.setName("The one I always take")
        vm.setPnr("9999999999")
        vm.setTravelClass("3A")
        vm.setOriginName("Somewhere")
        importer.outcome = TicketImportOutcome.Parsed(
            ticket().copy(trainName = "", pnr = "", travelClass = "", originName = "")
        )

        vm.importTicket { ByteArray(0) }
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("The one I always take", state.name)
        assertEquals("9999999999", state.pnr)
        assertEquals("3A", state.travelClass)
        assertEquals("Somewhere", state.originName)
        // What the ticket did say still lands.
        assertEquals("12345", state.number)
        assertEquals("BBB", state.destinationCode)
    }

    /**
     * A ticket with no arrival time leaves the arrival alone and refuses the save.
     *
     * The older layout prints the destination's *name* where the arrival time belongs. The right
     * behaviour is to say so and block: a countdown on the Home screen against a moment nobody
     * chose is worse than a form that will not save yet.
     */
    @Test
    fun `a ticket with no arrival leaves the arrival alone and blocks the save`() =
        runTest(dispatcher) {
            val vm = editor()
            importer.outcome = TicketImportOutcome.Parsed(
                ticket().copy(
                    arrival = null,
                    warnings = listOf(TicketImportWarning.ARRIVAL_TIME_MISSING)
                )
            )

            vm.importTicket { ByteArray(0) }
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals(LocalTime.of(22, 40), state.departureTime)
            // Untouched: the default the form opened with, not a guess derived from the departure.
            assertEquals(LocalTime.of(14, 0), state.arrivalTime)
            assertFalse(state.arrivesNextDay)
            assertFalse(state.canSave)
            assertEquals(
                listOf(TicketImportWarning.ARRIVAL_TIME_MISSING),
                state.importWarnings
            )

            // And it saves the moment the user supplies the one thing the document could not.
            vm.setArrivalTime(LocalTime.of(5, 20))
            vm.setArrivesNextDay(true)
            assertTrue(vm.state.value.canSave)
        }

    /**
     * The columns this form has no control for ride along to the save; the record's own do not.
     *
     * The fare, the quota, the agent's booking id and the date the ticket was bought are on the
     * imported booking and nowhere on screen. The row's identity — its id, when it was created,
     * and the delay someone entered off a platform announcement — belongs to the record instead,
     * and an import is not a new record.
     */
    @Test
    fun `the ticket's own columns ride along while the record keeps its identity`() =
        runTest(dispatcher) {
            val stamped = LocalDateTime.of(2026, 8, 1, 9, 0)
            val id = trains.insertTrain(
                booking().copy(createdAt = stamped, updatedAt = stamped, knownDelayMinutes = 25)
            )
            val vm = editor(trainId = id)
            importer.outcome = TicketImportOutcome.Parsed(ticket())

            vm.importTicket { ByteArray(0) }
            advanceUntilIdle()

            val carried = vm.state.value.source!!
            assertEquals(id, carried.id)
            assertEquals(stamped, carried.createdAt)
            assertEquals(25, carried.knownDelayMinutes)
            assertEquals("General", carried.quota)
            assertEquals(610, carried.distanceKm)
            assertEquals("CCC", carried.boardingCode)
            assertEquals("REF123", carried.agentBookingId)
            assertEquals("TX456", carried.transactionId)
            assertEquals(LocalDateTime.of(2026, 10, 1, 9, 15, 30), carried.bookedAt)
            assertEquals(828.6, carried.fare.totalFare!!, 0.001)

            var savedId = -1L
            vm.save { savedId = it }
            advanceUntilIdle()

            assertEquals(id, savedId)
            val saved = trains.getTrainByIdOnce(id)!!
            assertEquals("General", saved.quota)
            assertEquals(610, saved.distanceKm)
            assertEquals("TX456", saved.transactionId)
            assertEquals(828.6, saved.fare.totalFare!!, 0.001)
            assertEquals(17.7, saved.fare.convenienceFee!!, 0.001)
            assertEquals(stamped, saved.createdAt)
            assertEquals(25, saved.knownDelayMinutes)
            assertEquals(2, saved.passengers.size)
        }

    /** Importing over a booking that had a party replaces it rather than appending to it. */
    @Test
    fun `importing over an existing party replaces it`() = runTest(dispatcher) {
        val id = trains.insertTrain(booking().copy(passengers = listOf(travellerOne())))
        val vm = editor(trainId = id)
        importer.outcome = TicketImportOutcome.Parsed(ticket())

        vm.importTicket { ByteArray(0) }
        advanceUntilIdle()
        vm.save { }
        advanceUntilIdle()

        val saved = trains.getTrainByIdOnce(id)!!
        assertEquals(2, saved.passengers.size)
        assertEquals(listOf(1, 2), saved.passengers.map { it.serialNo })
    }

    // ---- While it is reading, and when it cannot ---------------------------

    @Test
    fun `the button waits while the ticket is being read`() = runTest(dispatcher) {
        val vm = editor()
        importer.outcome = TicketImportOutcome.Parsed(ticket())

        vm.importTicket { ByteArray(0) }

        // Set before the coroutine is dispatched, so the tap has an immediate effect.
        assertTrue(vm.state.value.isImporting)

        advanceUntilIdle()
        assertFalse(vm.state.value.isImporting)
    }

    /** A second tap while the first read is in flight does nothing at all. */
    @Test
    fun `a second import is ignored while one is running`() = runTest(dispatcher) {
        val vm = editor()
        importer.outcome = TicketImportOutcome.Parsed(ticket())
        var reads = 0

        vm.importTicket { reads++; ByteArray(0) }
        vm.importTicket { reads++; ByteArray(0) }
        advanceUntilIdle()

        assertEquals(1, reads)
        assertEquals(1, importer.readCount)
    }

    @Test
    fun `a file that is not a ticket is reported and changes nothing`() = runTest(dispatcher) {
        val vm = editor()
        vm.setNumber("12345")
        vm.setName("Typed by hand")
        importer.outcome = TicketImportOutcome.Failed(TicketImportError.NOT_AN_ETICKET)

        vm.importTicket { ByteArray(0) }
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(TicketImportError.NOT_AN_ETICKET, state.importError)
        assertNull(state.importSummary)
        assertTrue(state.importWarnings.isEmpty())
        assertFalse(state.isImporting)
        // The form is exactly as it was left.
        assertEquals("12345", state.number)
        assertEquals("Typed by hand", state.name)
        assertTrue(state.passengers.isEmpty())
    }

    /** A picker that returns nothing readable, and a read that throws, are the same thing. */
    @Test
    fun `a file that cannot be read at all is reported as unreadable`() = runTest(dispatcher) {
        val vm = editor()

        vm.importTicket { null }
        advanceUntilIdle()
        assertEquals(TicketImportError.UNREADABLE, vm.state.value.importError)
        assertEquals(0, importer.readCount)

        vm.dismissImportNotice()
        vm.importTicket { throw IllegalStateException("gone") }
        advanceUntilIdle()
        assertEquals(TicketImportError.UNREADABLE, vm.state.value.importError)
        assertEquals(0, importer.readCount)
        assertFalse(vm.state.value.isImporting)
    }

    @Test
    fun `dismissing the notice clears it but keeps what was filled in`() = runTest(dispatcher) {
        val vm = editor()
        importer.outcome = TicketImportOutcome.Parsed(
            ticket().copy(warnings = listOf(TicketImportWarning.STATION_CODES_MISSING))
        )

        vm.importTicket { ByteArray(0) }
        advanceUntilIdle()
        assertEquals(1, vm.state.value.importWarnings.size)

        vm.dismissImportNotice()

        val state = vm.state.value
        assertNull(state.importSummary)
        assertNull(state.importError)
        assertTrue(state.importWarnings.isEmpty())
        assertEquals("12345", state.number)
        assertEquals(2, state.passengers.size)
    }

    // ---- The party, on the way back out -----------------------------------

    /**
     * A save that changed nothing leaves the railway's own status strings byte for byte.
     *
     * `"CNF/S4/8"` in the current-status column and `"CNF/S4/8/SU"` in the booked one describe
     * one allotment: the berth type was only printed once. Rewriting the shorter string on a
     * save that touched nothing would destroy the app's one copy of what was actually printed.
     */
    @Test
    fun `a save that changed nothing keeps both status strings verbatim`() = runTest(dispatcher) {
        val id = trains.insertTrain(booking().copy(passengers = listOf(travellerOne())))
        val vm = editor(trainId = id)

        vm.save { }
        advanceUntilIdle()

        val saved = trains.getTrainByIdOnce(id)!!.passengers.single()
        assertEquals("CNF/S4/8/SU", saved.bookingStatusText)
        assertEquals("CNF/S4/8", saved.currentStatusText)
        assertEquals(BerthType.SIDE_UPPER, saved.allotment.berthType)
        assertFalse(saved.allotmentChanged)
    }

    /** The same, for the newer layout's way of saying the berth type is not repeated. */
    @Test
    fun `an NA berth type in the current status is not a change either`() = runTest(dispatcher) {
        val id = trains.insertTrain(
            booking().copy(
                passengers = listOf(
                    travellerOne().copy(
                        bookingStatusText = "CNF/S3/56/SU",
                        currentStatusText = "CNF/S3/56/NA",
                        allotment = TrainAllotment(
                            status = TrainBookingStatus.CONFIRMED,
                            coach = "S3",
                            berth = "56",
                            berthType = BerthType.SIDE_UPPER
                        )
                    )
                )
            )
        )
        val vm = editor(trainId = id)

        vm.save { }
        advanceUntilIdle()

        assertEquals(
            "CNF/S3/56/NA",
            trains.getTrainByIdOnce(id)!!.passengers.single().currentStatusText
        )
    }

    /** Move someone to a different berth and the current status is rewritten to match. */
    @Test
    fun `moving a berth rewrites the current status and keeps the booked one`() =
        runTest(dispatcher) {
            val id = trains.insertTrain(booking().copy(passengers = listOf(travellerOne())))
            val vm = editor(trainId = id)

            vm.setPassengerBerth(0, "9")
            vm.save { }
            advanceUntilIdle()

            val saved = trains.getTrainByIdOnce(id)!!.passengers.single()
            assertEquals("CNF/S4/9/SU", saved.currentStatusText)
            // What was booked is a matter of record and does not move with them.
            assertEquals("CNF/S4/8/SU", saved.bookingStatusText)
            assertTrue(saved.allotmentChanged)
        }

    /**
     * The booking's status is the least settled of its party.
     *
     * One person still waitlisted is what the trip has to be planned around, even when everyone
     * else is confirmed, so the stored column agrees with the list rather than going stale the
     * first time somebody gets a berth.
     */
    @Test
    fun `the saved booking status is the least settled of the party`() = runTest(dispatcher) {
        val vm = editor()
        vm.setNumber("12345")
        vm.setArrivalTime(LocalTime.of(14, 0))

        vm.addPassenger()
        vm.setPassengerName(0, "A Traveller")
        vm.setPassengerCoach(0, "s4")
        vm.setPassengerBerth(0, "8")

        vm.addPassenger()
        vm.setPassengerName(1, "Another Traveller")
        vm.setPassengerCoach(1, "")
        vm.setPassengerStatus(1, TrainBookingStatus.WAITLISTED)
        vm.setPassengerQueuePosition(1, "24")

        var id = -1L
        vm.save { id = it }
        advanceUntilIdle()

        val saved = trains.getTrainByIdOnce(id)!!
        assertEquals(TrainBookingStatus.WAITLISTED, saved.bookingStatus)
        assertEquals(TrainBookingStatus.WAITLISTED, saved.effectiveBookingStatus)
        // Coach codes are shouted whatever the keyboard did.
        assertEquals("S4", saved.passengers[0].allotment.coach)
        assertEquals(24, saved.passengers[1].allotment.queuePosition)
        assertEquals("WL/24", saved.passengers[1].currentStatusText)
    }

    /** Leaving the queue takes the queue position with it, rather than hiding it. */
    @Test
    fun `a passenger confirmed off the waitlist loses their queue position`() =
        runTest(dispatcher) {
            val vm = editor()
            vm.setNumber("12345")
            vm.addPassenger()
            vm.setPassengerName(0, "A Traveller")
            vm.setPassengerStatus(0, TrainBookingStatus.WAITLISTED)
            vm.setPassengerQueuePosition(0, "24")
            vm.setPassengerStatus(0, TrainBookingStatus.CONFIRMED)
            vm.setPassengerCoach(0, "S4")
            vm.setPassengerBerth(0, "8")

            var id = -1L
            vm.save { id = it }
            advanceUntilIdle()

            val saved = trains.getTrainByIdOnce(id)!!.passengers.single()
            assertNull(saved.allotment.queuePosition)
            assertEquals("CNF/S4/8", saved.currentStatusText)
        }

    /** A stray tap on "Add passenger" must not save a blank traveller or block the save. */
    @Test
    fun `an untouched passenger row is dropped on save`() = runTest(dispatcher) {
        val vm = editor()
        vm.setNumber("12345")
        vm.addPassenger()

        assertTrue(vm.state.value.canSave)

        var id = -1L
        vm.save { id = it }
        advanceUntilIdle()

        assertTrue(trains.getTrainByIdOnce(id)!!.passengers.isEmpty())
    }

    /**
     * Serial numbers are counted from the list, so a deleted row does not leave a gap.
     *
     * A party of three that loses its second member is numbered 1, 2 — the chart position the
     * railway allotted is not a number this form is free to invent, but it is also not one that
     * can survive a row disappearing from the middle.
     */
    @Test
    fun `removing a passenger renumbers the rest`() = runTest(dispatcher) {
        val vm = editor()
        vm.setNumber("12345")
        listOf("First", "Second", "Third").forEachIndexed { index, name ->
            vm.addPassenger()
            vm.setPassengerName(index, name)
        }

        vm.removePassenger(1)

        var id = -1L
        vm.save { id = it }
        advanceUntilIdle()

        val party = trains.getTrainByIdOnce(id)!!.passengers
        assertEquals(listOf("First", "Third"), party.map { it.name })
        assertEquals(listOf(1, 2), party.map { it.serialNo })
    }

    /** An index that is not in the list is a no-op, not a crash. */
    @Test
    fun `editing a passenger that is not there does nothing`() = runTest(dispatcher) {
        val vm = editor()

        vm.setPassengerName(3, "Nobody")
        vm.removePassenger(-1)

        assertTrue(vm.state.value.passengers.isEmpty())
    }

    // ---- The itinerary row -------------------------------------------------

    /**
     * Saving a booking puts it on the itinerary.
     *
     * This is the whole point of §4: a journey is part of the plan, not a thing beside it. Before
     * this, six hours on a train left a hole in the day and Home could never say "next up: the
     * 22:40" — the train lived in a tab of its own that the rest of the app could not see.
     */
    @Test
    fun `saving a booking writes its journey onto the itinerary`() = runTest(dispatcher) {
        val vm = editor()
        vm.setNumber("12345")
        vm.setDestinationName("Beta")
        vm.setDepartureTime(LocalTime.of(22, 40))
        vm.setArrivalTime(LocalTime.of(5, 20))
        vm.setArrivesNextDay(true)

        var id = -1L
        vm.save { id = it }
        advanceUntilIdle()

        val journeys = events.getEventsForTrip(tripId).first()
        assertEquals(1, journeys.size)
        val journey = journeys.single()
        assertEquals(EventType.JOURNEY, journey.type)
        assertEquals("Train to Beta", journey.title)
        assertEquals(LocalDateTime.of(2026, 11, 3, 22, 40), journey.startTime)
        assertEquals(LocalDateTime.of(2026, 11, 4, 5, 20), journey.endTime)
        // The link is written with the booking, in one save — a train that reached the database
        // without its eventId would be invisible to the itinerary until something else fixed it.
        assertEquals(journey.id, trains.getTrainByIdOnce(id)!!.eventId)
    }

    /** Editing a booking edits its journey. A second save is not a second journey. */
    @Test
    fun `saving the same booking twice leaves one journey`() = runTest(dispatcher) {
        val first = editor()
        first.setNumber("12345")
        first.setDestinationName("Beta")
        var id = -1L
        first.save { id = it }
        advanceUntilIdle()

        val again = editor(trainId = id)
        again.setPlatform("4")
        again.save { }
        advanceUntilIdle()

        assertEquals(1, events.getEventsForTrip(tripId).first().size)
    }

    /**
     * Moving the departure moves the journey.
     *
     * The ticket is the one source for when the journey is. Two records disagreeing about that is
     * how an itinerary ends up contradicting the ticket in the traveller's hand.
     */
    @Test
    fun `moving the departure moves the journey`() = runTest(dispatcher) {
        val first = editor()
        first.setNumber("12345")
        first.setDestinationName("Beta")
        var id = -1L
        first.save { id = it }
        advanceUntilIdle()

        val again = editor(trainId = id)
        again.setDepartureTime(LocalTime.of(6, 15))
        again.setArrivalTime(LocalTime.of(12, 30))
        again.save { }
        advanceUntilIdle()

        val journey = events.getEventsForTrip(tripId).first().single()
        assertEquals(LocalDateTime.of(2026, 11, 3, 6, 15), journey.startTime)
        assertEquals(LocalDateTime.of(2026, 11, 3, 12, 30), journey.endTime)
    }

    /**
     * A journey the user has renamed keeps its name, however often the booking changes.
     *
     * The generated title is a starting point, not a claim on the row. Regenerating it on every
     * save would quietly undo an edit the user made on a different screen, which reads as the app
     * losing their work.
     */
    @Test
    fun `a renamed journey keeps its title`() = runTest(dispatcher) {
        val first = editor()
        first.setNumber("12345")
        first.setDestinationName("Beta")
        var id = -1L
        first.save { id = it }
        advanceUntilIdle()
        val journey = events.getEventsForTrip(tripId).first().single()
        events.updateEvent(journey.copy(title = "The overnight to Beta"))

        val again = editor(trainId = id)
        // The arrival moves with it. A departure later than the stored arrival is not a journey,
        // and `canSave` refuses it — so a test that moved only the departure would be asserting
        // against a save that never happened.
        again.setDepartureTime(LocalTime.of(23, 55))
        again.setArrivalTime(LocalTime.of(6, 30))
        again.setArrivesNextDay(true)
        again.save { }
        advanceUntilIdle()

        val after = events.getEventsForTrip(tripId).first().single()
        assertEquals("The overnight to Beta", after.title)
        assertEquals(LocalDateTime.of(2026, 11, 3, 23, 55), after.startTime)
        assertEquals(LocalDateTime.of(2026, 11, 4, 6, 30), after.endTime)
    }

    /**
     * A journey the user planned by hand is adopted, not duplicated.
     *
     * Someone writes "Train to Beta" on their itinerary in June and enters the ticket in October.
     * Those are one journey. Two rows for it is the failure this prevents — and the title stays
     * theirs, because matching a row to a ticket is not licence to rename it.
     */
    @Test
    fun `a hand-written journey is adopted rather than duplicated`() = runTest(dispatcher) {
        val vm = editor()
        val planned = events.insertEvent(
            Event(
                tripId = tripId,
                type = EventType.JOURNEY,
                title = "The long ride south",
                startTime = LocalDateTime.of(2026, 11, 3, 21, 0),
                endTime = LocalDateTime.of(2026, 11, 4, 6, 0)
            )
        )
        vm.setNumber("12345")
        vm.setDestinationName("Beta")
        vm.setDepartureTime(LocalTime.of(22, 40))
        vm.setArrivalTime(LocalTime.of(5, 20))
        vm.setArrivesNextDay(true)

        var id = -1L
        vm.save { id = it }
        advanceUntilIdle()

        val journey = events.getEventsForTrip(tripId).first().single()
        assertEquals(planned, journey.id)
        assertEquals("The long ride south", journey.title)
        assertEquals(LocalDateTime.of(2026, 11, 3, 22, 40), journey.startTime)
        assertEquals(planned, trains.getTrainByIdOnce(id)!!.eventId)
    }

    /**
     * A journey another booking already sits on is left alone.
     *
     * Two trains an hour apart — a connection — are two rows. Adoption looks for a row nobody has
     * claimed, so the second booking makes its own instead of stealing the first one's. The
     * ninety minutes between them is deliberate: close enough that the window alone would match.
     */
    @Test
    fun `a journey another booking holds is not adopted`() = runTest(dispatcher) {
        val first = editor()
        first.setNumber("12345")
        first.setDestinationName("Beta")
        first.setDepartureTime(LocalTime.of(8, 0))
        first.setArrivalTime(LocalTime.of(9, 0))
        first.save { }
        advanceUntilIdle()

        val second = editor()
        second.setNumber("54321")
        second.setDestinationName("Gamma")
        second.setDepartureTime(LocalTime.of(9, 30))
        second.setArrivalTime(LocalTime.of(15, 0))
        second.save { }
        advanceUntilIdle()

        val journeys = events.getEventsForTrip(tripId).first()
        assertEquals(2, journeys.size)
        assertEquals(
            listOf("Train to Beta", "Train to Gamma"),
            journeys.sortedBy { it.startTime }.map { it.title }
        )
    }

    /**
     * A booking whose journey was deleted gets a new one instead of a dangling link.
     *
     * [Train.eventId] is deliberately not a foreign key, so the column can outlive the row it
     * names. The next save has to notice and put the journey back rather than write the booking
     * with a link to nothing.
     */
    @Test
    fun `a booking whose journey was deleted is put back on the itinerary`() = runTest(dispatcher) {
        val first = editor()
        first.setNumber("12345")
        first.setDestinationName("Beta")
        var id = -1L
        first.save { id = it }
        advanceUntilIdle()
        val gone = trains.getTrainByIdOnce(id)!!.eventId!!
        events.deleteEvent(gone)

        val again = editor(trainId = id)
        again.setPlatform("2")
        again.save { }
        advanceUntilIdle()

        val journey = events.getEventsForTrip(tripId).first().single()
        assertTrue(journey.id != gone)
        assertEquals(journey.id, trains.getTrainByIdOnce(id)!!.eventId)
    }

    // ---- Fixtures ----------------------------------------------------------

    private suspend fun TestScope.editor(trainId: Long? = null): TrainEditorViewModel {
        if (tripId == 0L) {
            tripId = trips.insertTrip(
                Trip(
                    name = "A trip",
                    startDate = LocalDate.of(2026, 11, 3),
                    endDate = LocalDate.of(2026, 11, 8)
                )
            )
        }
        val keys = mutableMapOf<String, Any?>("tripId" to tripId.toString())
        if (trainId != null) keys["trainId"] = trainId.toString()
        val vm = TrainEditorViewModel(
            savedStateHandle = SavedStateHandle(keys),
            trainRepository = trains,
            eventRepository = events,
            tripRepository = trips,
            ticketImportService = importer,
            journeyEventLinker = linker,
            pnrLookupService = pnrLookup,
            timeProvider = clock
        )
        advanceUntilIdle()
        return vm
    }

    /**
     * A booking as the database holds one, with the two times the form splits into three controls.
     *
     * Station names are placeholders. Nothing in this feature may recognise a real one (§4), so
     * the fixtures deliberately do not contain any.
     */
    private fun booking() = Train(
        tripId = tripId,
        number = "12345",
        name = "SOME EXPRESS",
        originCode = "AAA",
        originName = "Alpha Jn",
        destinationCode = "BBB",
        destinationName = "Beta",
        departureTime = LocalDateTime.of(2026, 11, 3, 22, 40),
        arrivalTime = LocalDateTime.of(2026, 11, 4, 5, 20),
        travelClass = "SL",
        pnr = "1000000001"
    )

    /** The shape the parser produces from the older layout: a berth type printed only once. */
    private fun travellerOne() = TrainPassenger(
        serialNo = 1,
        name = "A Traveller",
        age = 21,
        gender = PassengerGender.FEMALE,
        allotment = TrainAllotment(
            status = TrainBookingStatus.CONFIRMED,
            coach = "S4",
            berth = "8",
            berthType = BerthType.SIDE_UPPER
        ),
        bookingStatusText = "CNF/S4/8/SU",
        currentStatusText = "CNF/S4/8"
    )

    private fun travellerTwo() = TrainPassenger(
        serialNo = 2,
        name = "Another Traveller",
        age = 24,
        gender = PassengerGender.MALE,
        allotment = TrainAllotment(
            status = TrainBookingStatus.CONFIRMED,
            coach = "S4",
            berth = "6",
            berthType = BerthType.UPPER
        ),
        bookingStatusText = "CNF/S4/6/UB",
        currentStatusText = "CNF/S4/6"
    )

    /** A complete reading, of the kind that needs nothing filled in by hand. */
    private fun ticket() = ParsedTicket(
        pnr = "1000000001",
        trainNumber = "12345",
        trainName = "SOME EXPRESS",
        travelClass = "SL",
        quota = "General",
        distanceKm = 610,
        originCode = "AAA",
        originName = "Alpha Jn",
        destinationCode = "BBB",
        destinationName = "Beta",
        boardingCode = "CCC",
        boardingName = "Gamma",
        departure = LocalDateTime.of(2026, 11, 3, 22, 40),
        arrival = LocalDateTime.of(2026, 11, 4, 5, 20),
        bookedAt = LocalDateTime.of(2026, 10, 1, 9, 15, 30),
        agentName = "Some Agent",
        agentBookingId = "REF123",
        transactionId = "TX456",
        fare = TrainFare(
            ticketFare = 790.0,
            convenienceFee = 17.7,
            insurancePremium = 0.9,
            agentServiceCharge = 20.0,
            paymentGatewayCharge = 0.0,
            totalFare = 828.6
        ),
        passengers = listOf(travellerOne(), travellerTwo())
    )

    /**
     * The reader, with the outcome decided by the test.
     *
     * [readCount] is what proves the guard against a double tap actually guards: the second call
     * has to not reach here, and the only way to see that is to count arrivals.
     */
    private class FakeTicketImportService : TicketImportService {
        var outcome: TicketImportOutcome =
            TicketImportOutcome.Failed(TicketImportError.NOT_AN_ETICKET)
        var readCount = 0

        override suspend fun readTicket(bytes: ByteArray): TicketImportOutcome {
            readCount++
            return outcome
        }
    }

    /**
     * The PNR lookup, stubbed the way [FakeTicketImportService] stubs the importer.
     *
     * [isAvailable] defaults to false so the editor behaves as it does with no API key — the state
     * almost every test here runs in. A PNR test sets it true and points [outcome] at a booking or
     * a failure. [lookupCount] proves the double-tap guard, the same role [readCount] plays.
     */
    private class FakePnrLookupService : PnrLookupService {
        override var isAvailable: Boolean = false
        var outcome: PnrLookupOutcome = PnrLookupOutcome.Failed(PnrLookupError.NOT_CONFIGURED)
        var lookupCount = 0

        override suspend fun lookup(pnr: String): PnrLookupOutcome {
            lookupCount++
            return outcome
        }
    }
}

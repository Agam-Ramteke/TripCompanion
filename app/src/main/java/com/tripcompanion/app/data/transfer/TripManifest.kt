package com.tripcompanion.app.data.transfer

import com.tripcompanion.app.domain.model.Activity
import com.tripcompanion.app.domain.model.ActivityStatus
import com.tripcompanion.app.domain.model.BerthType
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.EventStatus
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.domain.model.Location
import com.tripcompanion.app.domain.model.PassengerGender
import com.tripcompanion.app.domain.model.PlannedPhoto
import com.tripcompanion.app.domain.model.StayDetails
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.model.TrainAllotment
import com.tripcompanion.app.domain.model.TrainBookingStatus
import com.tripcompanion.app.domain.model.TrainFare
import com.tripcompanion.app.domain.model.TrainPassenger
import com.tripcompanion.app.domain.model.TrainStop
import com.tripcompanion.app.domain.model.Trip
import com.tripcompanion.app.domain.model.TripStatus
import com.tripcompanion.app.domain.service.TripImportError
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Everything one trip is made of, in one value.
 *
 * The ids inside are the *exporting* device's, kept only so the parts can find each other:
 * [Event.locationId] names a place in [places], [Train.eventId] names an event in [events], and
 * [activities], [photos], [stays] and [stops] are keyed by the id of the row they hang off. None
 * of them survive the import — [TripTransferServiceImpl] assigns new ones and rewrites every
 * reference, because the receiving phone's database has its own idea of what id 4 is.
 *
 * Every image field — the trip's cover, a place's photo, a photo plan's reference, a stay's
 * picture — holds an *archive entry name* here, not a URI. `"images/img_3.jpg"` means nothing to
 * the app and everything to the zip; the importer swaps each one for the local file it wrote.
 */
internal data class TripBundle(
    val trip: Trip,
    val places: List<Location>,
    val events: List<Event>,
    val activities: Map<Long, List<Activity>>,
    val photos: Map<Long, List<PlannedPhoto>>,
    val stays: Map<Long, StayDetails>,
    val trains: List<Train>,
    val stops: Map<Long, List<TrainStop>>
)

/**
 * The trip file's manifest, read and written by hand.
 *
 * By hand rather than with a serialisation library because the format is a *contract with other
 * copies of this app*, including future ones, and a reflective encoder makes that contract a
 * side effect of whatever the domain classes happen to look like today. Renaming a field or
 * reordering a constructor would silently change the file format. Here, changing the format takes
 * editing this file, which is the point.
 *
 * Reading is deliberately more forgiving than writing. A missing optional field falls back to the
 * same default the domain model has, and an enum this version has never heard of falls back to
 * the tamest value rather than failing the import — the alternative is a trip that will not open
 * at all because one activity was marked with a status added in a later release. What is *not*
 * forgiven is a missing format marker, an unknown version, or a manifest that is not JSON: those
 * mean this is not a file we can read, and reading it hopefully would produce a trip with pieces
 * quietly missing.
 */
internal object TripManifest {

    /** The manifest text for [bundle]. Pretty-printed: it is a file people may end up opening. */
    fun encode(bundle: TripBundle, exportedAt: LocalDateTime): String {
        val root = JSONObject()
        root.put("format", TripArchive.FORMAT)
        root.put("version", TripArchive.VERSION)
        root.put("exportedAt", exportedAt.toString())
        root.put("trip", encodeTrip(bundle.trip))
        root.put("places", bundle.places.map(::encodePlace).toJsonArray())
        root.put(
            "events",
            bundle.events.map { event ->
                encodeEvent(
                    event = event,
                    activities = bundle.activities[event.id].orEmpty(),
                    photos = bundle.photos[event.id].orEmpty(),
                    stay = bundle.stays[event.id]
                )
            }.toJsonArray()
        )
        root.put(
            "trains",
            bundle.trains.map { train ->
                encodeTrain(train, bundle.stops[train.id].orEmpty())
            }.toJsonArray()
        )
        return root.toString(2)
    }

    /**
     * The trip in [manifest], or a [TripArchiveException] saying why not.
     *
     * The version check comes before anything else is read. A newer file may have restructured
     * the very fields this would otherwise start pulling out, and "damaged" is the wrong thing to
     * tell someone whose only problem is an out-of-date app on the receiving phone.
     */
    fun decode(manifest: String): TripBundle {
        val root = try {
            JSONObject(manifest)
        } catch (_: JSONException) {
            throw TripArchiveException(TripImportError.NOT_A_TRIP_FILE)
        }
        if (root.optString("format") != TripArchive.FORMAT) {
            throw TripArchiveException(TripImportError.NOT_A_TRIP_FILE)
        }
        val version = root.optInt("version", 0)
        if (version > TripArchive.VERSION) {
            throw TripArchiveException(TripImportError.NEWER_VERSION)
        }

        return try {
            decodeBody(root)
        } catch (_: JSONException) {
            throw TripArchiveException(TripImportError.DAMAGED)
        } catch (_: IllegalArgumentException) {
            // A date that is not a date, most often — `LocalDateTime.parse` on a truncated file.
            throw TripArchiveException(TripImportError.DAMAGED)
        }
    }

    private fun decodeBody(root: JSONObject): TripBundle {
        val places = root.optJSONArray("places").objects().map(::decodePlace)
        val eventObjects = root.optJSONArray("events").objects()
        val events = eventObjects.map(::decodeEvent)
        val activities = mutableMapOf<Long, List<Activity>>()
        val photos = mutableMapOf<Long, List<PlannedPhoto>>()
        val stays = mutableMapOf<Long, StayDetails>()

        eventObjects.forEachIndexed { index, json ->
            val eventId = events[index].id
            json.optJSONArray("activities").objects()
                .takeIf { it.isNotEmpty() }
                ?.let { rows -> activities[eventId] = rows.map { decodeActivity(it, eventId) } }
            json.optJSONArray("photos").objects()
                .takeIf { it.isNotEmpty() }
                ?.let { rows -> photos[eventId] = rows.map { decodePhoto(it, eventId) } }
            json.objectOrNull("stay")?.let { stays[eventId] = decodeStay(it, eventId) }
        }

        val trainObjects = root.optJSONArray("trains").objects()
        val trains = trainObjects.map(::decodeTrain)
        val stops = mutableMapOf<Long, List<TrainStop>>()
        trainObjects.forEachIndexed { index, json ->
            val trainId = trains[index].id
            json.optJSONArray("stops").objects()
                .takeIf { it.isNotEmpty() }
                ?.let { rows -> stops[trainId] = rows.map { decodeStop(it, trainId) } }
        }

        return TripBundle(
            trip = decodeTrip(root.getJSONObject("trip")),
            places = places,
            events = events,
            activities = activities,
            photos = photos,
            stays = stays,
            trains = trains,
            stops = stops
        )
    }

    // ---- Trip --------------------------------------------------------------

    private fun encodeTrip(trip: Trip) = JSONObject().apply {
        put("name", trip.name)
        put("startDate", trip.startDate.toString())
        put("endDate", trip.endDate.toString())
        put("status", trip.status.name)
        putIfPresent("coverImage", trip.coverImageUri)
        put("createdAt", trip.createdAt.toString())
        put("updatedAt", trip.updatedAt.toString())
    }

    private fun decodeTrip(json: JSONObject) = Trip(
        name = json.optString("name"),
        startDate = LocalDate.parse(json.getString("startDate")),
        endDate = LocalDate.parse(json.getString("endDate")),
        status = json.enum("status", TripStatus.PLANNING),
        coverImageUri = json.stringOrNull("coverImage"),
        // Kept rather than stamped with now(): the trips list is ordered by when a trip was
        // planned, and a moved trip that claims to have been created today sorts as if it were
        // the newest thing the user has thought of.
        createdAt = json.dateTime("createdAt"),
        updatedAt = json.dateTime("updatedAt")
    )

    // ---- Places ------------------------------------------------------------

    private fun encodePlace(place: Location) = JSONObject().apply {
        put("id", place.id)
        put("name", place.name)
        put("address", place.address)
        put("latitude", place.latitude)
        put("longitude", place.longitude)
        put("category", place.category)
        putIfPresent("providerPlaceId", place.providerPlaceId)
        putIfPresent("providerName", place.providerName)
        putIfPresent("photo", place.photoUri)
        putIfPresent("rating", place.rating)
        put("openingHours", place.openingHours)
        putIfPresent("estimatedVisitMinutes", place.estimatedVisitMinutes)
        put("isVisited", place.isVisited)
        put("isSaved", place.isSaved)
        put("createdAt", place.createdAt.toString())
        put("updatedAt", place.updatedAt.toString())
    }

    private fun decodePlace(json: JSONObject) = Location(
        id = json.optLong("id"),
        name = json.optString("name"),
        address = json.optString("address"),
        latitude = json.optDouble("latitude", 0.0),
        longitude = json.optDouble("longitude", 0.0),
        category = json.optString("category"),
        providerPlaceId = json.stringOrNull("providerPlaceId"),
        providerName = json.stringOrNull("providerName"),
        photoUri = json.stringOrNull("photo"),
        rating = json.doubleOrNull("rating"),
        openingHours = json.optString("openingHours"),
        estimatedVisitMinutes = json.intOrNull("estimatedVisitMinutes"),
        isVisited = json.optBoolean("isVisited"),
        isSaved = json.optBoolean("isSaved"),
        createdAt = json.dateTime("createdAt"),
        updatedAt = json.dateTime("updatedAt")
    )

    // ---- Events and what hangs off them ------------------------------------

    private fun encodeEvent(
        event: Event,
        activities: List<Activity>,
        photos: List<PlannedPhoto>,
        stay: StayDetails?
    ) = JSONObject().apply {
        put("id", event.id)
        put("type", event.type.name)
        put("title", event.title)
        put("startTime", event.startTime.toString())
        put("endTime", event.endTime.toString())
        putIfPresent("placeId", event.locationId)
        put("whatWeAreDoing", event.whatWeAreDoing)
        put("notes", event.notes)
        put("status", event.status.name)
        put("order", event.order)
        put("createdAt", event.createdAt.toString())
        put("updatedAt", event.updatedAt.toString())
        if (activities.isNotEmpty()) put("activities", activities.map(::encodeActivity).toJsonArray())
        if (photos.isNotEmpty()) put("photos", photos.map(::encodePhoto).toJsonArray())
        stay?.let { put("stay", encodeStay(it)) }
    }

    private fun decodeEvent(json: JSONObject) = Event(
        id = json.optLong("id"),
        tripId = 0L,
        type = json.enum("type", EventType.CUSTOM),
        title = json.optString("title"),
        startTime = json.dateTime("startTime"),
        endTime = json.dateTime("endTime"),
        locationId = json.longOrNull("placeId"),
        whatWeAreDoing = json.optString("whatWeAreDoing"),
        notes = json.optString("notes"),
        // The user's own ruling on a stop travels; the engine derives every other status from the
        // clock anyway (§21), so a stored UPCOMING is simply what it will recompute on arrival.
        status = json.enum("status", EventStatus.UPCOMING),
        order = json.optInt("order"),
        createdAt = json.dateTime("createdAt"),
        updatedAt = json.dateTime("updatedAt")
    )

    private fun encodeActivity(activity: Activity) = JSONObject().apply {
        put("title", activity.title)
        put("category", activity.category)
        put("order", activity.order)
        put("isOptional", activity.isOptional)
        put("completionStatus", activity.completionStatus.name)
        put("notes", activity.notes)
    }

    private fun decodeActivity(json: JSONObject, eventId: Long) = Activity(
        eventId = eventId,
        title = json.optString("title"),
        category = json.optString("category"),
        order = json.optInt("order"),
        isOptional = json.optBoolean("isOptional"),
        completionStatus = json.enum("completionStatus", ActivityStatus.PENDING),
        notes = json.optString("notes")
    )

    private fun encodePhoto(photo: PlannedPhoto) = JSONObject().apply {
        put("title", photo.title)
        putIfPresent("image", photo.referenceImageUri)
        put("order", photo.order)
    }

    private fun decodePhoto(json: JSONObject, eventId: Long) = PlannedPhoto(
        eventId = eventId,
        title = json.optString("title"),
        referenceImageUri = json.stringOrNull("image"),
        order = json.optInt("order")
    )

    private fun encodeStay(stay: StayDetails) = JSONObject().apply {
        put("bookingReference", stay.bookingReference)
        put("roomType", stay.roomType)
        put("guests", stay.guests)
        put("contactPhone", stay.contactPhone)
        put("address", stay.address)
        put("checkInInstructions", stay.checkInInstructions)
        putIfPresent("photo", stay.photoUri)
    }

    private fun decodeStay(json: JSONObject, eventId: Long) = StayDetails(
        eventId = eventId,
        bookingReference = json.optString("bookingReference"),
        roomType = json.optString("roomType"),
        guests = json.optInt("guests", 1),
        contactPhone = json.optString("contactPhone"),
        address = json.optString("address"),
        checkInInstructions = json.optString("checkInInstructions"),
        photoUri = json.stringOrNull("photo")
    )

    // ---- Trains ------------------------------------------------------------

    private fun encodeTrain(train: Train, stops: List<TrainStop>) = JSONObject().apply {
        put("id", train.id)
        putIfPresent("eventId", train.eventId)
        put("number", train.number)
        put("name", train.name)
        put("originCode", train.originCode)
        put("originName", train.originName)
        put("destinationCode", train.destinationCode)
        put("destinationName", train.destinationName)
        put("departureTime", train.departureTime.toString())
        put("arrivalTime", train.arrivalTime.toString())
        put("travelClass", train.travelClass)
        put("pnr", train.pnr)
        put("bookingStatus", train.bookingStatus.name)
        put("platform", train.platform)
        put("knownDelayMinutes", train.knownDelayMinutes)
        put("notes", train.notes)
        put("quota", train.quota)
        put("distanceKm", train.distanceKm)
        put("boardingCode", train.boardingCode)
        put("boardingName", train.boardingName)
        putIfPresent("bookedAt", train.bookedAt?.toString())
        put("agentName", train.agentName)
        put("agentBookingId", train.agentBookingId)
        put("transactionId", train.transactionId)
        if (!train.fare.isEmpty) put("fare", encodeFare(train.fare))
        if (train.passengers.isNotEmpty()) {
            put("passengers", train.passengers.map(::encodePassenger).toJsonArray())
        }
        if (stops.isNotEmpty()) put("stops", stops.map(::encodeStop).toJsonArray())
        put("createdAt", train.createdAt.toString())
        put("updatedAt", train.updatedAt.toString())
    }

    private fun decodeTrain(json: JSONObject) = Train(
        id = json.optLong("id"),
        tripId = 0L,
        eventId = json.longOrNull("eventId"),
        number = json.optString("number"),
        name = json.optString("name"),
        originCode = json.optString("originCode"),
        originName = json.optString("originName"),
        destinationCode = json.optString("destinationCode"),
        destinationName = json.optString("destinationName"),
        departureTime = json.dateTime("departureTime"),
        arrivalTime = json.dateTime("arrivalTime"),
        travelClass = json.optString("travelClass"),
        pnr = json.optString("pnr"),
        bookingStatus = json.enum("bookingStatus", TrainBookingStatus.NOT_BOOKED),
        platform = json.optString("platform"),
        knownDelayMinutes = json.optInt("knownDelayMinutes"),
        notes = json.optString("notes"),
        quota = json.optString("quota"),
        distanceKm = json.optInt("distanceKm"),
        boardingCode = json.optString("boardingCode"),
        boardingName = json.optString("boardingName"),
        bookedAt = json.stringOrNull("bookedAt")?.let(LocalDateTime::parse),
        agentName = json.optString("agentName"),
        agentBookingId = json.optString("agentBookingId"),
        transactionId = json.optString("transactionId"),
        fare = json.objectOrNull("fare")?.let(::decodeFare) ?: TrainFare(),
        passengers = json.optJSONArray("passengers").objects().map(::decodePassenger),
        createdAt = json.dateTime("createdAt"),
        updatedAt = json.dateTime("updatedAt")
    )

    /**
     * The fare, with absent and zero kept apart.
     *
     * A ticket with no agent genuinely has a `0.00` agent service charge, so a field that is
     * simply not there has to come back null rather than as a zero the receiving phone would
     * then display as a real charge of nothing.
     */
    private fun encodeFare(fare: TrainFare) = JSONObject().apply {
        putIfPresent("ticketFare", fare.ticketFare)
        putIfPresent("convenienceFee", fare.convenienceFee)
        putIfPresent("insurancePremium", fare.insurancePremium)
        putIfPresent("agentServiceCharge", fare.agentServiceCharge)
        putIfPresent("paymentGatewayCharge", fare.paymentGatewayCharge)
        putIfPresent("totalFare", fare.totalFare)
    }

    private fun decodeFare(json: JSONObject) = TrainFare(
        ticketFare = json.doubleOrNull("ticketFare"),
        convenienceFee = json.doubleOrNull("convenienceFee"),
        insurancePremium = json.doubleOrNull("insurancePremium"),
        agentServiceCharge = json.doubleOrNull("agentServiceCharge"),
        paymentGatewayCharge = json.doubleOrNull("paymentGatewayCharge"),
        totalFare = json.doubleOrNull("totalFare")
    )

    private fun encodePassenger(passenger: TrainPassenger) = JSONObject().apply {
        put("serialNo", passenger.serialNo)
        put("name", passenger.name)
        putIfPresent("age", passenger.age)
        put("gender", passenger.gender.name)
        put("allotment", encodeAllotment(passenger.allotment))
        put("bookingStatusText", passenger.bookingStatusText)
        put("currentStatusText", passenger.currentStatusText)
    }

    private fun decodePassenger(json: JSONObject) = TrainPassenger(
        serialNo = json.optInt("serialNo", 1),
        name = json.optString("name"),
        age = json.intOrNull("age"),
        gender = json.enum("gender", PassengerGender.UNSPECIFIED),
        allotment = json.objectOrNull("allotment")?.let(::decodeAllotment) ?: TrainAllotment(),
        bookingStatusText = json.optString("bookingStatusText"),
        currentStatusText = json.optString("currentStatusText")
    )

    private fun encodeAllotment(allotment: TrainAllotment) = JSONObject().apply {
        put("status", allotment.status.name)
        put("coach", allotment.coach)
        put("berth", allotment.berth)
        put("berthType", allotment.berthType.name)
        putIfPresent("queuePosition", allotment.queuePosition)
        put("queueKind", allotment.queueKind)
    }

    private fun decodeAllotment(json: JSONObject) = TrainAllotment(
        status = json.enum("status", TrainBookingStatus.NOT_BOOKED),
        coach = json.optString("coach"),
        berth = json.optString("berth"),
        berthType = json.enum("berthType", BerthType.UNKNOWN),
        queuePosition = json.intOrNull("queuePosition"),
        queueKind = json.optString("queueKind")
    )

    private fun encodeStop(stop: TrainStop) = JSONObject().apply {
        put("serialNo", stop.serialNo)
        put("stationCode", stop.stationCode)
        put("stationName", stop.stationName)
        putIfPresent("scheduledArrival", stop.scheduledArrival?.toString())
        putIfPresent("scheduledDeparture", stop.scheduledDeparture?.toString())
        put("distanceKm", stop.distanceKm)
        put("dayOffset", stop.dayOffset)
    }

    private fun decodeStop(json: JSONObject, trainId: Long) = TrainStop(
        trainId = trainId,
        serialNo = json.optInt("serialNo", 1),
        stationCode = json.optString("stationCode"),
        stationName = json.optString("stationName"),
        scheduledArrival = json.stringOrNull("scheduledArrival")?.let(LocalTime::parse),
        scheduledDeparture = json.stringOrNull("scheduledDeparture")?.let(LocalTime::parse),
        distanceKm = json.optInt("distanceKm"),
        dayOffset = json.optInt("dayOffset")
    )
}

// ---- Reading and writing JSON without the sharp edges ----------------------

/**
 * Writes [value] only when there is one.
 *
 * `org.json` treats putting a null as *removing* the key, which is the behaviour we want but not
 * a thing to rely on a reader of this file knowing. Said out loud instead.
 */
private fun JSONObject.putIfPresent(key: String, value: Any?) {
    if (value != null) put(key, value)
}

/** Missing and JSON-null are the same absence to us; `optString` would call both `""`. */
private fun JSONObject.stringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null

private fun JSONObject.intOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) getInt(key) else null

private fun JSONObject.longOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) getLong(key) else null

private fun JSONObject.doubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) getDouble(key) else null

private fun JSONObject.objectOrNull(key: String): JSONObject? =
    if (has(key) && !isNull(key)) getJSONObject(key) else null

/**
 * A timestamp, falling back to *now* when the file never had one.
 *
 * Only reached for `createdAt` and `updatedAt` on a file written before this app tracked them.
 * Every timestamp the app actually shows — a departure, a check-in — is required and read with
 * [JSONObject.getString], so a missing one fails the import rather than defaulting to today.
 */
private fun JSONObject.dateTime(key: String): LocalDateTime =
    stringOrNull(key)?.let(LocalDateTime::parse) ?: LocalDateTime.now()

/**
 * An enum by name, or [default] for one this version has never heard of.
 *
 * Forgiving on purpose: a trip exported by a later release that added, say, a FERRY event type
 * should still import, with that one day reading as a custom entry, rather than refusing to open
 * because of a word in one field.
 */
private inline fun <reified T : Enum<T>> JSONObject.enum(key: String, default: T): T {
    val raw = stringOrNull(key) ?: return default
    return enumValues<T>().firstOrNull { it.name == raw } ?: default
}

private fun List<JSONObject>.toJsonArray(): JSONArray =
    JSONArray().also { array -> forEach { array.put(it) } }

/** A JSON array's objects, with a missing array and an empty one meaning the same thing. */
private fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it) }
}

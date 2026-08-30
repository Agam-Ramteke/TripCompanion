package com.tripcompanion.app.data.local

import com.tripcompanion.app.data.local.entity.*
import com.tripcompanion.app.domain.model.*

// ── Trip ──

fun TripEntity.toDomain() = Trip(
    id = id,
    name = name,
    startDate = startDate,
    endDate = endDate,
    status = TripStatus.valueOf(status),
    coverImageUri = coverImageUri,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Trip.toEntity() = TripEntity(
    id = id,
    name = name,
    startDate = startDate,
    endDate = endDate,
    status = status.name,
    coverImageUri = coverImageUri,
    createdAt = createdAt,
    updatedAt = updatedAt
)

// ── Event ──

fun EventEntity.toDomain() = Event(
    id = id,
    tripId = tripId,
    type = EventType.valueOf(type),
    title = title,
    startTime = startTime,
    endTime = endTime,
    locationId = locationId,
    whatWeAreDoing = whatWeAreDoing,
    notes = notes,
    backgroundImageUri = backgroundImageUri,
    status = EventStatus.valueOf(status),
    order = order,
    actualStartTime = actualStartTime,
    actualEndTime = actualEndTime,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Event.toEntity() = EventEntity(
    id = id,
    tripId = tripId,
    type = type.name,
    title = title,
    startTime = startTime,
    endTime = endTime,
    locationId = locationId,
    whatWeAreDoing = whatWeAreDoing,
    notes = notes,
    backgroundImageUri = backgroundImageUri,
    status = status.name,
    order = order,
    actualStartTime = actualStartTime,
    actualEndTime = actualEndTime,
    createdAt = createdAt,
    updatedAt = updatedAt
)

// ── Location ──

fun LocationEntity.toDomain() = Location(
    id = id,
    name = name,
    address = address,
    latitude = latitude,
    longitude = longitude,
    category = category,
    providerPlaceId = providerPlaceId,
    providerName = providerName,
    photoUri = photoUri,
    rating = rating,
    openingHours = openingHours,
    estimatedVisitMinutes = estimatedVisitMinutes,
    isVisited = isVisited,
    isSaved = isSaved,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Location.toEntity() = LocationEntity(
    id = id,
    name = name,
    address = address,
    latitude = latitude,
    longitude = longitude,
    category = category,
    providerPlaceId = providerPlaceId,
    providerName = providerName,
    photoUri = photoUri,
    rating = rating,
    openingHours = openingHours,
    estimatedVisitMinutes = estimatedVisitMinutes,
    isVisited = isVisited,
    isSaved = isSaved,
    createdAt = createdAt,
    updatedAt = updatedAt
)

// ── Activity ──

fun ActivityEntity.toDomain() = Activity(
    id = id,
    eventId = eventId,
    title = title,
    category = category,
    order = order,
    isOptional = isOptional,
    completionStatus = ActivityStatus.valueOf(completionStatus),
    notes = notes
)

fun Activity.toEntity() = ActivityEntity(
    id = id,
    eventId = eventId,
    title = title,
    category = category,
    order = order,
    isOptional = isOptional,
    completionStatus = completionStatus.name,
    notes = notes
)

// ── PlannedPhoto ──

fun PlannedPhotoEntity.toDomain() = PlannedPhoto(
    id = id,
    eventId = eventId,
    title = title,
    referenceImageUri = referenceImageUri,
    order = order
)

fun PlannedPhoto.toEntity() = PlannedPhotoEntity(
    id = id,
    eventId = eventId,
    title = title,
    referenceImageUri = referenceImageUri,
    order = order
)

// ── Train ──

/**
 * [passengers] is a parameter because it comes from a different table.
 *
 * The repository reads the party separately and hands it in, which keeps this a pure function of
 * its inputs and keeps the mapper free of a DAO. Defaulting to empty is not a shortcut: a train
 * with no passengers recorded is the ordinary state of one added by hand before the ticket
 * arrives, so an empty list is a real answer rather than a missing one.
 */
fun TrainEntity.toDomain(passengers: List<TrainPassenger> = emptyList()) = Train(
    id = id,
    tripId = tripId,
    eventId = eventId,
    number = number,
    name = name,
    originCode = originCode,
    originName = originName,
    destinationCode = destinationCode,
    destinationName = destinationName,
    departureTime = departureTime,
    arrivalTime = arrivalTime,
    actualBoardingTime = actualBoardingTime,
    actualDepartureTime = actualDepartureTime,
    actualArrivalTime = actualArrivalTime,
    arrivalSource = arrivalSource,
    travelClass = travelClass,
    pnr = pnr,
    bookingStatus = TrainBookingStatus.valueOf(bookingStatus),
    platform = platform,
    knownDelayMinutes = knownDelayMinutes,
    notes = notes,
    quota = quota,
    distanceKm = distanceKm,
    boardingCode = boardingCode,
    boardingName = boardingName,
    bookedAt = bookedAt,
    agentName = agentName,
    agentBookingId = agentBookingId,
    transactionId = transactionId,
    fare = TrainFare(
        ticketFare = fareTicket,
        convenienceFee = fareConvenience,
        insurancePremium = fareInsurance,
        agentServiceCharge = fareAgentService,
        paymentGatewayCharge = farePaymentGateway,
        totalFare = fareTotal
    ),
    passengers = passengers,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/**
 * Drops [Train.passengers], the way `TrainRunStatus.toEntity` drops its stop rows.
 *
 * The party is a second table, so writing it is a second call — see
 * `TrainPassengerDao.replacePassengers`. Silently flattening a list into a row here would give
 * a caller that only saved the entity a train whose passengers had vanished.
 */
fun Train.toEntity() = TrainEntity(
    id = id,
    tripId = tripId,
    eventId = eventId,
    number = number,
    name = name,
    originCode = originCode,
    originName = originName,
    destinationCode = destinationCode,
    destinationName = destinationName,
    departureTime = departureTime,
    arrivalTime = arrivalTime,
    actualBoardingTime = actualBoardingTime,
    actualDepartureTime = actualDepartureTime,
    actualArrivalTime = actualArrivalTime,
    arrivalSource = arrivalSource,
    travelClass = travelClass,
    pnr = pnr,
    bookingStatus = bookingStatus.name,
    platform = platform,
    knownDelayMinutes = knownDelayMinutes,
    notes = notes,
    quota = quota,
    distanceKm = distanceKm,
    boardingCode = boardingCode,
    boardingName = boardingName,
    bookedAt = bookedAt,
    agentName = agentName,
    agentBookingId = agentBookingId,
    transactionId = transactionId,
    fareTicket = fare.ticketFare,
    fareConvenience = fare.convenienceFee,
    fareInsurance = fare.insurancePremium,
    fareAgentService = fare.agentServiceCharge,
    farePaymentGateway = fare.paymentGatewayCharge,
    fareTotal = fare.totalFare,
    createdAt = createdAt,
    updatedAt = updatedAt
)

// ── TrainPassenger ──

/**
 * The stored columns are the truth about the allotment; the verbatim strings are kept beside
 * them and never re-parsed here.
 *
 * Reading [TrainAllotment] back out of `bookingStatusText` instead would make a hand-edited
 * ticket display whatever the regex guessed rather than what the user typed.
 */
fun TrainPassengerEntity.toDomain() = TrainPassenger(
    id = id,
    trainId = trainId,
    serialNo = serialNo,
    name = name,
    age = age,
    gender = try { PassengerGender.valueOf(gender) } catch (e: Exception) { PassengerGender.UNSPECIFIED },
    allotment = TrainAllotment(
        coach = coach,
        berth = berth,
        berthType = try { BerthType.valueOf(berthType) } catch (e: Exception) { BerthType.UNKNOWN },
        status = try { TrainBookingStatus.valueOf(status) } catch (e: Exception) { TrainBookingStatus.NOT_BOOKED },
        queuePosition = queuePosition,
        queueKind = queueKind
    ),
    bookingStatusText = bookingStatusText,
    currentStatusText = currentStatusText
)

/**
 * [trainId] can be given explicitly, following `TrainStop.toEntity`: passengers are built
 * before the train they belong to has an id, so the repository supplies it on insert.
 */
fun TrainPassenger.toEntity(trainId: Long = this.trainId) = TrainPassengerEntity(
    id = id,
    trainId = trainId,
    serialNo = serialNo,
    name = name,
    age = age,
    gender = gender.name,
    status = allotment.status.name,
    coach = allotment.coach,
    berth = allotment.berth,
    berthType = allotment.berthType.name,
    queuePosition = allotment.queuePosition,
    queueKind = allotment.queueKind,
    bookingStatusText = bookingStatusText,
    currentStatusText = currentStatusText
)

// ── TrainStop ──

fun TrainStopEntity.toDomain() = TrainStop(
    id = id,
    trainId = trainId,
    serialNo = serialNo,
    stationCode = stationCode,
    stationName = stationName,
    scheduledArrival = scheduledArrival,
    scheduledDeparture = scheduledDeparture,
    distanceKm = distanceKm,
    dayOffset = dayOffset
)

fun TrainStop.toEntity(trainId: Long = this.trainId) = TrainStopEntity(
    id = id,
    trainId = trainId,
    serialNo = serialNo,
    stationCode = stationCode,
    stationName = stationName,
    scheduledArrival = scheduledArrival,
    scheduledDeparture = scheduledDeparture,
    distanceKm = distanceKm,
    dayOffset = dayOffset
)

// ── TrainRunStatus ──
//
// The snapshot spans two tables, so it maps in two halves. `toDomain` takes the stop rows as
// an argument rather than reading them itself — mappers stay pure, and the DAO already had to
// fetch both to hand them over.

fun TrainRunStatusEntity.toDomain(stops: List<TrainRunStopEntity> = emptyList()) = TrainRunStatus(
    trainId = trainId,
    fetchedAt = fetchedAt,
    runDate = runDate,
    source = TrainRunSource.valueOf(source),
    currentStationCode = currentStationCode,
    currentStationName = currentStationName,
    delayMinutes = delayMinutes,
    lastDepartedSerial = lastDepartedSerial,
    progressFraction = progressFraction,
    nextStopCode = nextStopCode,
    nextStopName = nextStopName,
    nextStopEta = nextStopEta,
    averageSpeedKmph = averageSpeedKmph,
    message = message,
    stops = stops.map { it.toDomain() }
)

fun TrainRunStatus.toEntity(trainId: Long = this.trainId) = TrainRunStatusEntity(
    trainId = trainId,
    fetchedAt = fetchedAt,
    runDate = runDate,
    source = source.name,
    currentStationCode = currentStationCode,
    currentStationName = currentStationName,
    delayMinutes = delayMinutes,
    lastDepartedSerial = lastDepartedSerial,
    progressFraction = progressFraction,
    nextStopCode = nextStopCode,
    nextStopName = nextStopName,
    nextStopEta = nextStopEta,
    averageSpeedKmph = averageSpeedKmph,
    message = message
)

fun TrainRunStopEntity.toDomain() = TrainStopStatus(
    serialNo = serialNo,
    stationCode = stationCode,
    stationName = stationName,
    scheduledArrival = scheduledArrival,
    actualArrival = actualArrival,
    scheduledDeparture = scheduledDeparture,
    actualDeparture = actualDeparture,
    arrivalDelayMinutes = arrivalDelayMinutes,
    departureDelayMinutes = departureDelayMinutes,
    distanceKm = distanceKm,
    dayOffset = dayOffset,
    isDeparted = isDeparted,
    isCurrent = isCurrent
)

/** Stop rows always take the train id from the snapshot they belong to, never their own. */
fun TrainStopStatus.toEntity(trainId: Long) = TrainRunStopEntity(
    trainId = trainId,
    serialNo = serialNo,
    stationCode = stationCode,
    stationName = stationName,
    scheduledArrival = scheduledArrival,
    actualArrival = actualArrival,
    scheduledDeparture = scheduledDeparture,
    actualDeparture = actualDeparture,
    arrivalDelayMinutes = arrivalDelayMinutes,
    departureDelayMinutes = departureDelayMinutes,
    distanceKm = distanceKm,
    dayOffset = dayOffset,
    isDeparted = isDeparted,
    isCurrent = isCurrent
)

// ── StayDetails ──

fun StayDetailsEntity.toDomain() = StayDetails(
    eventId = eventId,
    bookingReference = bookingReference,
    roomType = roomType,
    guests = guests,
    contactPhone = contactPhone,
    address = address,
    checkInInstructions = checkInInstructions,
    photoUri = photoUri,
    actualCheckIn = actualCheckIn,
    actualCheckOut = actualCheckOut
)

fun StayDetails.toEntity() = StayDetailsEntity(
    eventId = eventId,
    bookingReference = bookingReference,
    roomType = roomType,
    guests = guests,
    contactPhone = contactPhone,
    address = address,
    checkInInstructions = checkInInstructions,
    photoUri = photoUri,
    actualCheckIn = actualCheckIn,
    actualCheckOut = actualCheckOut
)

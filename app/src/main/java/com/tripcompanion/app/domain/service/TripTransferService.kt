package com.tripcompanion.app.domain.service

import java.io.InputStream
import java.io.OutputStream

/** Why a trip could not be written out. */
enum class TripExportError {
    /** The trip is gone — deleted between picking it and saving the file. */
    TRIP_NOT_FOUND,

    /** The file could not be written: no space, a revoked permission, a removed SD card. */
    WRITE_FAILED
}

/**
 * Why a trip file could not be read.
 *
 * The user's categories, not the archive's — the same distinction [TicketImportError] draws.
 * "This is not a trip file" and "this trip file is damaged" both mean the import failed, but
 * only the first has an obvious next step for the person holding the phone.
 */
enum class TripImportError {
    /** A readable file that is not one of ours — the wrong zip, a photo, a document. */
    NOT_A_TRIP_FILE,

    /**
     * Written by a newer version of the app than this one.
     *
     * Its own error because the answer is specific and encouraging: update this phone's app and
     * try again. Guessing at a format we do not know would import a trip with pieces missing
     * and no way to tell which.
     */
    NEWER_VERSION,

    /** Ours, but truncated or corrupt — an interrupted transfer, usually. */
    DAMAGED,

    /** The file could not be opened or read at all. */
    UNREADABLE
}

/**
 * The outcome of writing a trip out.
 *
 * [Written] reports what went in so the confirmation can be specific. "Saved 34 activities and
 * 12 photos" is checkable by the person who just pressed the button; "Export complete" is not.
 */
sealed interface TripExportOutcome {
    data class Written(
        val tripName: String,
        val eventCount: Int,
        val trainCount: Int,
        val imageCount: Int
    ) : TripExportOutcome

    data class Failed(val error: TripExportError) : TripExportOutcome
}

/** The outcome of reading a trip file. [Imported.tripId] is the new local trip, ready to open. */
sealed interface TripImportOutcome {
    data class Imported(
        val tripId: Long,
        val tripName: String,
        val eventCount: Int,
        val trainCount: Int,
        val imageCount: Int
    ) : TripImportOutcome

    data class Failed(val error: TripImportError) : TripImportOutcome
}

/**
 * Moves a whole trip between devices as one file.
 *
 * The boundary that keeps the archive's internals out of the UI, the way [TicketImportService]
 * keeps PDF internals out of it. Nothing about zip entries or JSON keys is visible here, so the
 * format can grow a field — or change entirely behind a version check — without a screen
 * changing.
 *
 * Streams rather than a `Uri` on purpose, and `java.io` streams rather than Android ones: opening
 * the user's chosen file is the caller's job (see
 * [com.tripcompanion.app.data.transfer.TripArchiveFiles]), which keeps every implementation
 * testable on a plain JVM against a real archive in memory. A trip with a dozen photographs is
 * megabytes, so it is streamed in and out rather than passed around as a `ByteArray`.
 *
 * What travels is the trip and everything that belongs to it: its days, the places they happen
 * at, their activities, photo plans and hotel paperwork, its train bookings with their parties
 * and timetables, and every photograph any of those point at. What does not travel is anything
 * the receiving phone can work out for itself — a train's live running status is fetched and
 * thrown away, so carrying yesterday's copy of it across would only be wrong on arrival.
 */
interface TripTransferService {

    /**
     * Writes [tripId] to [sink] as a trip file.
     *
     * Never throws, and never closes [sink] — the caller opened it and owns it. A failure is a
     * value because running out of space is an ordinary thing to do, not an exceptional one.
     */
    suspend fun export(tripId: Long, sink: OutputStream): TripExportOutcome

    /**
     * Reads a trip file from [source] and adds it as a new trip.
     *
     * Never throws, and never closes [source]. Always a *new* trip: importing the same file twice
     * gives two trips, the same way loading the sample trip twice does. Merging would mean
     * guessing which of two edited copies of a day is the one the user meant, and a duplicate is
     * deletable while a wrong merge is not.
     */
    suspend fun import(source: InputStream): TripImportOutcome

    /**
     * A file name to offer the save dialog for [tripId], e.g. `"Udaipur.zip"`.
     *
     * Here rather than in the screen because the extension is part of the format, and the trip's
     * name has to survive being a file name on a phone, a laptop and whatever is in between.
     */
    suspend fun suggestedFileName(tripId: Long): String
}

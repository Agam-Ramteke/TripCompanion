package com.tripcompanion.app.domain.model

/**
 * A photo the user wants to recreate (spec §15).
 *
 * The image is the information. Everything else is optional labelling, so this
 * model deliberately holds no description, no pose or framing instructions and
 * no capture status — that earlier shape turned a visual reference board into a
 * photography assignment. A photo with an image and nothing else is valid.
 */
data class PlannedPhoto(
    val id: Long = 0,
    val eventId: Long,
    /** Optional short label such as "Palace doorway". May be blank. */
    val title: String = "",
    /** `file://`-style path into app-private storage, or null while unset. */
    val referenceImageUri: String? = null,
    val order: Int = 0
)

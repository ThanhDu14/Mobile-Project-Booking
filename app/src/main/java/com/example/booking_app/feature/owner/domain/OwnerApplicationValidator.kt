package com.example.booking_app.feature.owner.domain

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

enum class OwnerApplicationField {
    VENUE_NAME,
    ADDRESS,
    DISTRICT,
    LOCATION,
    VENUE_PHONE,
    COURT_COUNT,
    OPENING_TIME,
    CLOSING_TIME,
    REPRESENTATIVE_NAME,
    REPRESENTATIVE_PHONE,
    VENUE_PHOTOS,
    DOCUMENTS,
}

data class OwnerApplicationValidation(
    val errors: Map<OwnerApplicationField, String>,
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/** Client-side feedback only; authorization and final validation remain server responsibilities. */
object OwnerApplicationValidator {
    fun validate(draft: OwnerApplicationDraft): OwnerApplicationValidation {
        val errors = linkedMapOf<OwnerApplicationField, String>()
        val venue = draft.venue

        fun requireText(value: String, field: OwnerApplicationField) {
            if (value.isBlank()) errors[field] = "required"
        }

        requireText(venue.name, OwnerApplicationField.VENUE_NAME)
        requireText(venue.address, OwnerApplicationField.ADDRESS)
        requireText(venue.districtId, OwnerApplicationField.DISTRICT)
        requireText(venue.phone, OwnerApplicationField.VENUE_PHONE)
        requireText(draft.representativeName, OwnerApplicationField.REPRESENTATIVE_NAME)
        requireText(draft.representativePhone, OwnerApplicationField.REPRESENTATIVE_PHONE)

        val latitude = venue.latitude
        val longitude = venue.longitude
        if (latitude == null || longitude == null || !latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) {
            errors[OwnerApplicationField.LOCATION] = "invalid_location"
        }

        if (venue.courtCount == null || venue.courtCount <= 0) {
            errors[OwnerApplicationField.COURT_COUNT] = "positive_integer_required"
        }
        if (!venue.openTime.isValidTime()) errors[OwnerApplicationField.OPENING_TIME] = "invalid_time"
        if (!venue.closeTime.isValidTime()) errors[OwnerApplicationField.CLOSING_TIME] = "invalid_time"
        if (venue.photoPaths.none(String::isNotBlank)) errors[OwnerApplicationField.VENUE_PHOTOS] = "required"
        if (draft.documentPaths.none(String::isNotBlank)) errors[OwnerApplicationField.DOCUMENTS] = "required"

        return OwnerApplicationValidation(errors)
    }

    private fun String.isValidTime(): Boolean = runCatching { LocalTime.parse(this, TIME_FORMAT) }.isSuccess

    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HHmm").withResolverStyle(ResolverStyle.STRICT)
}

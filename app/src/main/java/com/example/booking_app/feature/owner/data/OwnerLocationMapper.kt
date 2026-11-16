package com.example.booking_app.feature.owner.data

import com.google.firebase.firestore.GeoPoint
import com.google.firebase.Timestamp
import java.time.Instant
import java.util.Date
import com.google.firebase.firestore.DocumentSnapshot

/** Accept legacy venues created by the REST adapter before GeoPoint encoding was added. */
internal fun ownerLocation(value: Any?): GeoPoint? = when (value) {
    is GeoPoint -> value
    is Map<*, *> -> {
        val latitude = (value["latitude"] as? Number)?.toDouble()
        val longitude = (value["longitude"] as? Number)?.toDouble()
        if (latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
        ) GeoPoint(latitude, longitude) else null
    }
    else -> null
}

internal fun ownerTimestamp(value: Any?): Timestamp? = when (value) {
    is Timestamp -> value
    is Date -> Timestamp(value)
    is String -> runCatching { Timestamp(Date.from(Instant.parse(value))) }.getOrNull()
    else -> null
}

internal fun ownerSlotTimestamp(doc: DocumentSnapshot, field: String): Timestamp? {
    ownerTimestamp(doc.get(field))?.let { return it }
    val date = doc.getString("date") ?: return null
    val time = doc.getString(if (field == "startAt") "startTime" else "endTime") ?: return null
    if (!date.matches(Regex("[0-9]{8}")) || !time.matches(Regex("[0-9]{4}"))) return null
    return ownerTimestamp("${date.substring(0,4)}-${date.substring(4,6)}-${date.substring(6,8)}T${time.substring(0,2)}:${time.substring(2,4)}:00+07:00")
}

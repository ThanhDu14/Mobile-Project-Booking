package com.example.booking_app.feature.owner.data

import com.google.firebase.firestore.GeoPoint
import org.junit.Assert.*
import org.junit.Test

class OwnerLocationMapperTest {
    @Test fun acceptsFirestoreAndLegacyRestLocations() {
        val point = GeoPoint(10.77, 106.7)
        assertEquals(point, ownerLocation(point))
        assertEquals(point, ownerLocation(mapOf("latitude" to 10.77, "longitude" to 106.7)))
    }

    @Test fun rejectsMissingMalformedAndOutOfRangeCoordinates() {
        listOf(null, "location", emptyMap<String, Any>(), mapOf("latitude" to 91, "longitude" to 106),
            mapOf("latitude" to Double.NaN, "longitude" to 106)).forEach { assertNull(ownerLocation(it)) }
    }
}

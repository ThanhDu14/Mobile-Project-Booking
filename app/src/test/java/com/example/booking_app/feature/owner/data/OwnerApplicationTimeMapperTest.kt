package com.example.booking_app.feature.owner.data

import com.example.booking_app.feature.owner.domain.OwnerApplicationDraft
import com.example.booking_app.feature.owner.domain.OwnerApplicationValidation
import com.example.booking_app.feature.owner.domain.OwnerVenueDraft
import com.google.firebase.firestore.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class OwnerApplicationTimeMapperTest {
    @Test fun serverHoursCanBeValidatedAndResubmittedWithoutRetyping() {
        val draft = OwnerApplicationDraft(
            OwnerVenueDraft("AUDIT", "AUDIT", "q1", GeoPoint(10.77, 106.7), "0900000000", 1,
                ownerApplicationFormTime("0530"), ownerApplicationFormTime("2230"), listOf("photo.png")),
            "AUDIT", "0900000000", listOf("document.png"))
        assertEquals(emptyList<Any>(), OwnerApplicationValidation.validate(draft))
        assertEquals("05:30", ownerApplicationFormTime("05:30"))
        assertEquals("2960", ownerApplicationFormTime("2960"))
    }
}

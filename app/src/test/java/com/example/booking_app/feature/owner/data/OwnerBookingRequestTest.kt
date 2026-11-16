package com.example.booking_app.feature.owner.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class OwnerBookingRequestTest {
    @Test fun rejectionReasonIsAlwaysIncludedInTheWirePayload() {
        val payload = Json.encodeToJsonElement(RejectBookingWire("v1", "b1", "AUDIT reason")).jsonObject
        assertEquals("AUDIT reason", payload.getValue("reason").jsonPrimitive.content)
    }
}

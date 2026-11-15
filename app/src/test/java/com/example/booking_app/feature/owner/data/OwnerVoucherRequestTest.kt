package com.example.booking_app.feature.owner.data

import com.example.booking_app.feature.owner.domain.voucher.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class OwnerVoucherRequestTest {
    @Test fun serializesBothToggleDirectionsAndIncludesTheFullLastLocalDay() {
        val draft=OwnerVoucherDraft("AUDIT10",OwnerVoucherType.PERCENT,10,20000,100000,
            LocalDate.parse("2026-11-15"),LocalDate.parse("2026-11-16"),1,1,true)
        val request=draft.toRequest("CREATE","venue")
        assertEquals("2026-11-14T17:00:00Z",request.validFrom)
        assertEquals("2026-11-16T16:59:59.999Z",request.validTo)
        assertTrue(Json.encodeToString(request).contains("\"isActive\":true"))
        assertTrue(Json.encodeToString(request.copy(isActive=false)).contains("\"isActive\":false"))
        assertFalse(Json.encodeToString(request).contains("usedCount"))
    }
}

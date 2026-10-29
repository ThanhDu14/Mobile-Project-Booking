package com.example.booking_app.feature.owner

import com.example.booking_app.feature.owner.domain.OwnerApplicationReviewStatus
import com.example.booking_app.feature.owner.domain.OwnerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OwnerApplicationStatusTest {
    @Test
    fun `doc du cac gia tri ownerStatus da quy dinh`() {
        for (status in OwnerStatus.entries) {
            assertEquals(status, OwnerStatus.fromWire(status.name))
        }
    }

    @Test
    fun `khong dien giai gia tri status khong duoc quy dinh`() {
        assertNull(OwnerStatus.fromWire(null))
        assertNull(OwnerStatus.fromWire("pending"))
        assertNull(OwnerStatus.fromWire("UNKNOWN"))
    }

    @Test
    fun `status cua application khong bao gom NONE`() {
        for (status in OwnerApplicationReviewStatus.entries) {
            assertEquals(status, OwnerApplicationReviewStatus.fromWire(status.name))
        }
        assertNull(OwnerApplicationReviewStatus.fromWire("NONE"))
    }
}

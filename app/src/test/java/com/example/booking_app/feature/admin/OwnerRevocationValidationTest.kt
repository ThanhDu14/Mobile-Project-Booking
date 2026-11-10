package com.example.booking_app.feature.admin

import com.example.booking_app.feature.admin.domain.model.OwnerRevocationDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OwnerRevocationValidationTest {

    @Test
    fun validDecision_returnsNullError() {
        val decision = OwnerRevocationDecision(
            uid = "user-123",
            reason = "Vi phạm nhiều lần hợp đồng dịch vụ",
        )
        assertNull(decision.validate())
    }

    @Test
    fun blankUid_returnsError() {
        val decision = OwnerRevocationDecision(
            uid = "   ",
            reason = "Vi phạm hợp đồng",
        )
        val error = decision.validate()
        assertNotNull(error)
        assertEquals("Mã người dùng không được để trống", error)
    }

    @Test
    fun emptyReason_returnsError() {
        val decision = OwnerRevocationDecision(
            uid = "user-123",
            reason = "",
        )
        val error = decision.validate()
        assertNotNull(error)
        assertEquals("Lý do thu hồi không được để trống", error)
    }

    @Test
    fun whitespaceOnlyReason_returnsError() {
        val decision = OwnerRevocationDecision(
            uid = "user-123",
            reason = "    \n\t   ",
        )
        val error = decision.validate()
        assertNotNull(error)
        assertEquals("Lý do thu hồi không được để trống", error)
    }
}

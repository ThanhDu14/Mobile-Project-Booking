package com.example.booking_app.feature.payment.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class OrderItemsTest {

    @Test
    fun `courtDetails cua 040 bo dau gach doc thanh dong de doc`() {
        assertEquals(
            "Sân 1 · Thảm PVC · T6, 20/11 · 18:00 – 19:00 · 120.000đ",
            OrderItems.fromCourtDetail("S1|Sân 1|Thảm PVC|T6, 20/11|18:00 – 19:00|120000"),
        )
        assertEquals("Sân 2 · T6, 20/11 · 18:00 – 19:00", OrderItems.fromCourtDetail("S2|Sân 2||T6, 20/11|18:00 – 19:00|"))
        assertEquals("chuỗi khác dạng", OrderItems.fromCourtDetail("chuỗi khác dạng"))
    }

    @Test
    fun `041 khong co courtDetails thi moi ngay mot dong`() {
        assertEquals(
            listOf("27/11/2026 · 18:00 – 19:00", "04/12/2026 · 18:00 – 19:00"),
            OrderItems.fromSlotIds(listOf("c1_20261204_1800", "c1_20261127_1830", "c1_20261127_1800", "c1_20261204_1830")),
        )
    }
}

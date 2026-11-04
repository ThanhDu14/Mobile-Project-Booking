package com.example.booking_app.feature.courtdetail.domain

import com.example.booking_app.feature.courtdetail.domain.model.Court
import com.example.booking_app.feature.courtdetail.domain.model.SurfaceType
import org.junit.Assert.assertEquals
import org.junit.Test

class CourtSummaryTest {

    private fun court(id: String, surface: SurfaceType, active: Boolean = true) = Court(id, surface, active)

    @Test
    fun `chi dem san dang hoat dong`() {
        val s = CourtSummaries.from(
            listOf(
                court("1", SurfaceType.MAT),
                court("2", SurfaceType.MAT),
                court("3", SurfaceType.MAT),
                court("4", SurfaceType.MAT, active = false),
            ),
        )
        assertEquals(3, s.activeCount)
        assertEquals(listOf(SurfaceType.MAT to 3), s.bySurface)
    }

    @Test
    fun `sap theo so luong giam dan`() {
        val s = CourtSummaries.from(
            listOf(court("1", SurfaceType.MAT), court("2", SurfaceType.WOOD), court("3", SurfaceType.WOOD)),
        )
        assertEquals(listOf(SurfaceType.WOOD to 2, SurfaceType.MAT to 1), s.bySurface)
    }

    @Test
    fun `khong co san hoat dong`() {
        val s = CourtSummaries.from(listOf(court("1", SurfaceType.PU, active = false)))
        assertEquals(0, s.activeCount)
        assertEquals(emptyList<Pair<SurfaceType, Int>>(), s.bySurface)
    }

    @Test
    fun `surfaceType la thanh OTHER`() {
        assertEquals(SurfaceType.OTHER, SurfaceType.from("CONCRETE"))
        assertEquals(SurfaceType.OTHER, SurfaceType.from(null))
        assertEquals(SurfaceType.PU, SurfaceType.from("PU"))
    }
}

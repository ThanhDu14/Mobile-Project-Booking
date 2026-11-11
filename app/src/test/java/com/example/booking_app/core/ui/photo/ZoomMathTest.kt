package com.example.booking_app.core.ui.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomMathTest {

    // Khung 1000×2000, ảnh vừa khung (fit center) 1000×500 nằm giữa theo chiều dọc.
    private val viewW = 1000f
    private val viewH = 2000f
    private val contentW = 1000f
    private val contentH = 500f

    @Test
    fun `ti le bi kep trong 1x den 4x`() {
        assertEquals(1f, ZoomMath.clampScale(0.3f), 0f)
        assertEquals(4f, ZoomMath.clampScale(9f), 0f)
        assertEquals(2f, ZoomMath.clampScale(2f), 0f)
        val zoomed = ZoomMath.zoomAround(ZoomMath.Transform.IDENTITY, 10f, 500f, 1000f)
        assertEquals(4f, zoomed.scale, 0f)
    }

    @Test
    fun `phong quanh tieu diem giu dung diem duoi ngon tay`() {
        val start = ZoomMath.Transform(1.5f, -100f, -50f)
        val fx = 320f
        val fy = 870f
        val before = start.toContent(fx, fy)
        val after = ZoomMath.zoomAround(start, 2f, fx, fy)
        val point = after.toContent(fx, fy)
        assertEquals(before.first, point.first, 0.001f)
        assertEquals(before.second, point.second, 0.001f)
        assertEquals(3f, after.scale, 0f)
    }

    @Test
    fun `keo khi anh lon hon khung bi kep khong lo nen`() {
        val zoomed = ZoomMath.Transform(2f, 0f, 0f)
        // Kéo sang phải quá mép trái
        val right = ZoomMath.clampTranslation(zoomed.copy(tx = 500f), viewW, viewH, contentW, contentH)
        assertEquals(0f, right.tx, 0.001f) // mép trái ảnh = 0
        // Kéo sang trái quá mép phải: ảnh rộng 2000, mép phải phải = 1000 → tx = -1000
        val left = ZoomMath.clampTranslation(zoomed.copy(tx = -5000f), viewW, viewH, contentW, contentH)
        assertEquals(-1000f, left.tx, 0.001f)
    }

    @Test
    fun `chieu nho hon khung thi can giua`() {
        // Ảnh cao 500 × 2 = 1000 < 2000 → luôn ở giữa theo chiều dọc dù kéo.
        val t = ZoomMath.clampTranslation(ZoomMath.Transform(2f, 0f, 999f), viewW, viewH, contentW, contentH)
        val top = ((viewH - contentH) / 2f) * t.scale + t.ty
        assertEquals((viewH - contentH * 2f) / 2f, top, 0.001f)
    }

    @Test
    fun `o 1x khong keo ngang de ViewPager2 chuyen anh`() {
        assertFalse(ZoomMath.canPanHorizontally(ZoomMath.Transform.IDENTITY, viewW, contentW))
        assertTrue(ZoomMath.canPanHorizontally(ZoomMath.Transform(2f, 0f, 0f), viewW, contentW))
    }

    @Test
    fun `cham hai lan 1x len 2_5x roi ve 1x`() {
        assertEquals(2.5f, ZoomMath.nextDoubleTapScale(1f), 0f)
        assertEquals(1f, ZoomMath.nextDoubleTapScale(2.5f), 0f)
        assertEquals(1f, ZoomMath.nextDoubleTapScale(3.7f), 0f)
    }
}

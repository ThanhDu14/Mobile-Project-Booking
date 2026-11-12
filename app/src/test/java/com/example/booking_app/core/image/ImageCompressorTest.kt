package com.example.booking_app.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageCompressorTest {

    @Test
    fun `thong so dung hop dong`() {
        assertEquals(1600, ImageCompressionRules.MAX_SIDE)
        assertEquals(80, ImageCompressionRules.JPEG_QUALITY)
        assertEquals(2_097_152L, ImageCompressionRules.MAX_BYTES)
    }

    @Test
    fun `anh nho hon gioi han giu nguyen kich thuoc`() {
        assertEquals(800 to 600, ImageCompressionRules.targetSize(800, 600))
        assertEquals(1600 to 1200, ImageCompressionRules.targetSize(1600, 1200))
    }

    @Test
    fun `anh ngang thu ve canh dai 1600 giu ti le`() {
        assertEquals(1600 to 1200, ImageCompressionRules.targetSize(4000, 3000))
    }

    @Test
    fun `anh doc thu ve canh dai 1600 giu ti le`() {
        assertEquals(900 to 1600, ImageCompressionRules.targetSize(1800, 3200))
    }

    @Test
    fun `anh panorama cuc dai khong cho canh ngan bang 0`() {
        val (w, h) = ImageCompressionRules.targetSize(20000, 2)
        assertEquals(1600, w)
        assertEquals(1, h)
    }

    @Test
    fun `dung 2 MB hop le, hon 1 byte la qua lon`() {
        assertFalse(ImageCompressionRules.isTooLarge(2 * 1024 * 1024))
        assertTrue(ImageCompressionRules.isTooLarge(2 * 1024 * 1024 + 1))
    }
}

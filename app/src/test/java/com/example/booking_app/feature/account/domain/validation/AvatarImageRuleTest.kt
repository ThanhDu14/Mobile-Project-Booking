package com.example.booking_app.feature.account.domain.validation

import com.example.booking_app.feature.account.domain.model.AccountError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvatarImageRuleTest {

    @Test
    fun `jpeg png webp hop le`() {
        listOf("image/jpeg", "image/png", "image/webp").forEach { assertNull(AvatarImageRule.mimeError(it)) }
    }

    @Test
    fun `heic gif null la UnsupportedImage`() {
        listOf("image/heic", "image/gif", null).forEach {
            assertEquals(AccountError.UnsupportedImage, AvatarImageRule.mimeError(it))
        }
    }

    @Test
    fun `qua 2 MB sau nen la ImageTooLarge`() {
        assertNull(AvatarImageRule.sizeError(2_097_152))
        assertEquals(AccountError.ImageTooLarge, AvatarImageRule.sizeError(2_097_153))
    }

    @Test
    fun `thu ve toi da 512 px, giu ti le, khong phong to anh nho`() {
        assertEquals(512 to 512, AvatarImageRule.targetSize(2048, 2048))
        assertEquals(512 to 384, AvatarImageRule.targetSize(4000, 3000))
        assertEquals(288 to 512, AvatarImageRule.targetSize(1080, 1920))
        assertEquals(300 to 200, AvatarImageRule.targetSize(300, 200))
    }

    @Test
    fun `chat luong bat dau 85, giam 10 moi lan, toi thieu 50`() {
        assertEquals(listOf(85, 75, 65, 55, 50), AvatarImageRule.QUALITY_STEPS)
    }
}

package com.example.booking_app.feature.community.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R5: chuẩn hóa từ khóa tìm nhóm, cùng quy tắc `_shared/keywords.ts`. */
class GroupSearchKeywordsTest {

    @Test
    fun `chuan hoa bo dau doi d chu thuong va tach tu`() {
        assertEquals(listOf("cau", "long", "da"), GroupSearchKeywords.tokens("  Cầu-Lông, ĐÀ!! "))
        assertEquals("cau long da", GroupSearchKeywords.normalizeQuery("  Cầu   Lông ĐÀ "))
        assertEquals("dong da", GroupSearchKeywords.normalize("Đống Đa"))
    }

    @Test
    fun `tu khoa trung chi giu mot lan va trong thi khong co tu`() {
        assertEquals(listOf("a", "b"), GroupSearchKeywords.tokens("a b A"))
        assertEquals(emptyList<String>(), GroupSearchKeywords.tokens("  !? "))
    }

    @Test
    fun `tu dai nhat la tu array-contains va cat toi 20 ky tu`() {
        assertEquals("long", GroupSearchKeywords.primary(listOf("cau", "long", "q7")))
        assertEquals("cau", GroupSearchKeywords.primary(listOf("cau", "lon", "q7")))
        assertEquals("a".repeat(20), GroupSearchKeywords.primary(listOf("a".repeat(30))))
        assertNull(GroupSearchKeywords.primary(emptyList()))
    }

    @Test
    fun `loc client theo cac tu con lai`() {
        val keywords = listOf("c", "cl", "clb", "ca", "cau", "l", "lo", "lon", "long")
        assertTrue(GroupSearchKeywords.matches(keywords, listOf("cau", "lo")))
        assertFalse(GroupSearchKeywords.matches(keywords, listOf("cau", "tennis")))
        assertTrue(GroupSearchKeywords.matches(keywords, emptyList()))
    }
}

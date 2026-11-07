package com.example.booking_app.feature.courtdetail.data

import com.example.booking_app.core.media.PhotoUrlResolver
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** T053 (phần ánh xạ): document `reviews` → `ReviewPreview` theo data-model §1.4 và research R3. */
class ReviewPreviewMapperTest {

    private val resolver = PhotoUrlResolver("https://x.supabase.co")

    private fun doc(
        displayName: String? = "Minh Anh",
        authorDeleted: Boolean = false,
        ownerReply: Map<String, Any?>? = null,
        overall: Any? = 4.3,
        photoPaths: List<String> = emptyList(),
        avatarPath: String? = null,
    ): Map<String, Any?> = mapOf(
        "userSnapshot" to mapOf("displayName" to displayName, "avatarPath" to avatarPath),
        "authorDeleted" to authorDeleted,
        "overall" to overall,
        "comment" to "Sân đẹp, chủ sân thân thiện",
        "photoPaths" to photoPaths,
        "ownerReply" to ownerReply,
        "createdAt" to Timestamp(1_793_329_200L, 0),
        "status" to "VISIBLE",
    )

    @Test
    fun `anh xa day du`() {
        val review = ReviewPreviewMapper.map(
            "r1",
            doc(photoPaths = listOf("p1/r1/photo-1.jpg"), avatarPath = "p1/avatar.jpg"),
            resolver,
        )
        assertEquals("r1", review.id)
        assertEquals("Minh Anh", review.authorName)
        assertEquals("https://x.supabase.co/storage/v1/object/public/avatars/p1/avatar.jpg", review.authorAvatarUrl)
        assertEquals(4, review.overall)
        assertEquals("Sân đẹp, chủ sân thân thiện", review.comment)
        assertEquals(listOf("https://x.supabase.co/storage/v1/object/public/review-photos/p1/r1/photo-1.jpg"), review.photoUrls)
        assertNull(review.reply)
        assertEquals(Instant.ofEpochSecond(1_793_329_200L), review.createdAt)
    }

    @Test
    fun `nguoi viet da xoa tai khoan thi khong co ten va anh`() {
        val review = ReviewPreviewMapper.map("r1", doc(authorDeleted = true, avatarPath = "p1/avatar.jpg"), resolver)
        assertNull(review.authorName)
        assertNull(review.authorAvatarUrl)
    }

    @Test
    fun `thieu displayName cung la nguoi dung da xoa`() {
        assertNull(ReviewPreviewMapper.map("r1", doc(displayName = null), resolver).authorName)
        assertNull(ReviewPreviewMapper.map("r1", doc(displayName = "  "), resolver).authorName)
    }

    @Test
    fun `co phan hoi cua chu san`() {
        val review = ReviewPreviewMapper.map("r1", doc(ownerReply = mapOf("text" to "Cảm ơn bạn đã ủng hộ")), resolver)
        assertEquals("Cảm ơn bạn đã ủng hộ", review.reply)
    }

    @Test
    fun `phan hoi rong coi nhu khong co`() {
        assertNull(ReviewPreviewMapper.map("r1", doc(ownerReply = mapOf("text" to " ")), resolver).reply)
    }

    @Test
    fun `diem lam tron va gioi han 1 den 5`() {
        assertEquals(5, ReviewPreviewMapper.map("r1", doc(overall = 4.7), resolver).overall)
        assertEquals(3, ReviewPreviewMapper.map("r1", doc(overall = 3L), resolver).overall)
        assertEquals(5, ReviewPreviewMapper.map("r1", doc(overall = 9.0), resolver).overall)
        assertEquals(1, ReviewPreviewMapper.map("r1", doc(overall = null), resolver).overall)
    }
}

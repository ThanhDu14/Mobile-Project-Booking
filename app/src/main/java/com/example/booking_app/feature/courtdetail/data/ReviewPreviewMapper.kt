package com.example.booking_app.feature.courtdetail.data

import com.example.booking_app.core.media.PhotoUrlResolver
import com.example.booking_app.feature.courtdetail.domain.model.ReviewPreview
import com.google.firebase.Timestamp
import kotlin.math.roundToInt

/**
 * Document `reviews` → [ReviewPreview] (data-model §1.4, research R3). Hàm thuần để unit test.
 *
 * - `authorDeleted == true` hoặc `userSnapshot.displayName` trống → `authorName = null`, không ảnh (giao diện hiện
 *   "Người dùng đã xóa" và ảnh mặc định, 070 research R9).
 * - `overall` (Double 1–5) làm tròn thành số sao, kẹp trong 1…5.
 * - `ownerReply.text` trống coi như không có phản hồi.
 */
object ReviewPreviewMapper {

    fun map(id: String, data: Map<String, Any?>, resolver: PhotoUrlResolver): ReviewPreview {
        val snapshot = data["userSnapshot"] as? Map<*, *>
        val deleted = data["authorDeleted"] == true
        val name = (snapshot?.get("displayName") as? String)?.takeIf { it.isNotBlank() && !deleted }
        val reply = ((data["ownerReply"] as? Map<*, *>)?.get("text") as? String)?.takeIf { it.isNotBlank() }
        return ReviewPreview(
            id = id,
            authorName = name,
            authorAvatarUrl = if (name == null) null else resolver.avatar(snapshot?.get("avatarPath") as? String),
            overall = ((data["overall"] as? Number)?.toDouble() ?: 0.0).roundToInt().coerceIn(1, 5),
            comment = (data["comment"] as? String)?.takeIf { it.isNotBlank() },
            photoUrls = (data["photoPaths"] as? List<*>).orEmpty().mapNotNull { resolver.reviewPhoto(it as? String) },
            reply = reply,
            createdAt = (data["createdAt"] as? Timestamp)?.toInstant(),
        )
    }
}

package com.example.booking_app.feature.review.data.dto

import com.example.booking_app.feature.review.data.ReviewPhotoUrls
import com.example.booking_app.feature.review.domain.model.FavoriteVenue
import com.example.booking_app.feature.review.domain.model.FavoriteVenue.Availability
import com.example.booking_app.feature.review.domain.ReviewEligibility
import com.example.booking_app.feature.review.domain.model.OwnerReply
import com.example.booking_app.feature.review.domain.model.Ratings
import com.example.booking_app.feature.review.domain.model.Review
import com.example.booking_app.feature.review.domain.model.ReviewAuthor
import com.example.booking_app.feature.review.domain.model.ReviewTarget
import com.example.booking_app.feature.review.domain.model.ReviewTargetType
import com.example.booking_app.feature.review.domain.model.VenueRatingSummary
import java.time.Instant
import kotlin.math.roundToLong

/**
 * Ghép favorite với trạng thái hiện tại của cơ sở (research R6). [venue] là `null` khi không đọc
 * được cơ sở (offline, chưa có cache): hiển thị theo `venueSnapshot`, coi như đang hoạt động.
 */
fun FavoriteDto.toDomain(
    venue: FavoriteVenueStateDto?,
    photoUrl: (String?) -> String? = { ReviewPhotoUrls.venuePhoto(it) },
): FavoriteVenue {
    val availability = when {
        venue == null -> Availability.ACTIVE
        !venue.exists -> Availability.GONE
        venue.status == STATUS_ACTIVE -> Availability.ACTIVE
        else -> Availability.SUSPENDED
    }
    // Sân đang hoạt động lấy tên, ảnh, địa chỉ mới nhất; các trường hợp còn lại dùng snapshot lúc lưu.
    val latest = venue?.takeIf { availability == Availability.ACTIVE }
    return FavoriteVenue(
        venueId = venueId,
        name = latest?.name ?: name,
        photoUrl = photoUrl(latest?.photoPath ?: photoPath),
        address = latest?.address ?: address,
        ratingAvg = latest?.ratingAvg,
        availability = availability,
    )
}

private const val STATUS_ACTIVE = "ACTIVE"

/**
 * [photoUrl] ghép URL bucket `review-photos`, [avatarUrl] ghép URL bucket `avatars` (để test thay được).
 * Tác giả đã xóa tài khoản (FR-016): bỏ tên và ảnh, UI hiện "Người dùng đã xóa".
 */
fun ReviewDto.toDomain(
    photoUrl: (String) -> String = { ReviewPhotoUrls.reviewPhoto(it) },
    avatarUrl: (String) -> String = { ReviewPhotoUrls.avatar(it) },
): Review {
    val deleted = authorDeleted || userId == null
    val created = createdAt ?: Instant.EPOCH
    return Review(
        id = id,
        venueId = venueId,
        author = ReviewAuthor(
            displayName = displayName.takeUnless { deleted },
            avatarUrl = avatarPath?.takeIf { !deleted && it.isNotBlank() }?.let(avatarUrl),
            isDeleted = deleted,
        ),
        ratings = Ratings(court, cleanliness, service),
        overall = overall,
        comment = comment,
        photoUrls = photoPaths.map(photoUrl),
        ownerReply = ownerReplyText?.let { text ->
            OwnerReply(text, ownerRepliedAt ?: created, isEdited = ownerReplyEditedAt != null)
        },
        isHidden = status == STATUS_HIDDEN,
        isEdited = editedAt != null,
        createdAt = created,
        // Dữ liệu cũ thiếu completedAt: tính từ createdAt, server vẫn là nơi quyết định.
        canEditUntil = ReviewEligibility.canEditUntil(completedAt ?: created),
        photoPaths = photoPaths,
        targetType = if (targetType == TARGET_DROP_IN) ReviewTargetType.DROP_IN else ReviewTargetType.BOOKING,
    )
}

/**
 * Trung bình từng tiêu chí = `ratingSums.<tiêu chí> / ratingCount` (research R2). Dữ liệu cũ chưa có
 * `ratingSums` thì chỉ hiện `ratingAvg`, các tiêu chí bằng 0.
 */
fun VenueRatingFieldsDto.toSummary(): VenueRatingSummary {
    val count = (ratingCount ?: 0L).coerceAtLeast(0L)
    if (count == 0L) return VenueRatingSummary(0.0, 0, 0.0, 0.0, 0.0)
    fun avg(key: String) = ratingSums?.get(key)?.let { round1(it.toDouble() / count) } ?: 0.0
    val average = ratingSums?.get("overall10")?.let { round1(it / 10.0 / count) } ?: (ratingAvg ?: 0.0)
    return VenueRatingSummary(average, count.toInt(), avg("court"), avg("cleanliness"), avg("service"))
}

fun ReviewBookingDto.toTarget(photoUrl: (String?) -> String? = { it }): ReviewTarget = ReviewTarget(
    bookingId = id,
    venueId = venueId,
    venueName = venueName.orEmpty(),
    venuePhotoUrl = photoUrl(venuePhotoUrl),
    startAt = startAt,
    endAt = endAt,
    courtNames = courtNames.distinct(),
    status = status,
    completedAt = completedAt,
)

private fun round1(x: Double): Double = (x * 10).roundToLong() / 10.0

private const val STATUS_HIDDEN = "HIDDEN"
private const val TARGET_DROP_IN = "DROP_IN"

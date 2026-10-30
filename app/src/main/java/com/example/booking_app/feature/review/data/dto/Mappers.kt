package com.example.booking_app.feature.review.data.dto

import com.example.booking_app.feature.review.data.ReviewPhotoUrls
import com.example.booking_app.feature.review.domain.model.FavoriteVenue
import com.example.booking_app.feature.review.domain.model.FavoriteVenue.Availability

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

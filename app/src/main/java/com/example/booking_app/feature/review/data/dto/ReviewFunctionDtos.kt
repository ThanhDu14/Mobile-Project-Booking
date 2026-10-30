@file:OptIn(ExperimentalSerializationApi::class)

package com.example.booking_app.feature.review.data.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Body và response của Edge Function `review` (contracts/edge-function-review.md). Thời gian là chuỗi ISO-8601. */
val ReviewFunctionJson = Json { ignoreUnknownKeys = true }

@Serializable
data class RatingsDto(val court: Int, val cleanliness: Int, val service: Int)

// ---------- Request: trường `action` luôn được gửi (EncodeDefault) ----------

@Serializable
data class CreateReviewRequest(
    val bookingId: String,
    val ratings: RatingsDto,
    val comment: String,
    val photoPaths: List<String>,
    @EncodeDefault val action: String = "create",
)

@Serializable
data class UpdateReviewRequest(
    val reviewId: String,
    val ratings: RatingsDto,
    val comment: String,
    val photoPaths: List<String>,
    @EncodeDefault val action: String = "update",
)

@Serializable
data class DeleteReviewRequest(
    val reviewId: String,
    @EncodeDefault val action: String = "delete",
)

@Serializable
data class ReplyReviewRequest(
    val reviewId: String,
    val text: String,
    @EncodeDefault val action: String = "reply",
)

@Serializable
data class DiscardPhotosRequest(
    val bookingId: String,
    val photoPaths: List<String>,
    @EncodeDefault val action: String = "discardPhotos",
)

// ---------- Response ----------

@Serializable
data class VenueRatingDto(val ratingAvg: Double, val ratingCount: Int)

@Serializable
data class ReviewResultDto(val id: String, val overall: Double, val createdAt: String, val canEditUntil: String)

/** `create` (201, hoặc 200 khi gửi lại cùng nội dung) và `update` (200). */
@Serializable
data class ReviewWriteResponse(val review: ReviewResultDto, val venue: VenueRatingDto)

@Serializable
data class DeleteReviewResponse(val venue: VenueRatingDto)

@Serializable
data class OwnerReplyResultDto(val text: String, val repliedAt: String, val editedAt: String? = null)

@Serializable
data class ReplyReviewResponse(val ownerReply: OwnerReplyResultDto)

@Serializable
data class DiscardPhotosResponse(val deleted: Int)

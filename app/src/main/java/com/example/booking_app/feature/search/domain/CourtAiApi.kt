package com.example.booking_app.feature.search.domain

import kotlinx.serialization.Serializable

enum class ReviewPreference { GRIP, HIGH_CEILING, QUIET, CLEAN, GOOD_LIGHT, VENTILATED, GOOD_SERVICE }

@Serializable
data class ReviewSource(val reviewId: String, val version: String)

/** Câu khớp trong đánh giá (đã che liên hệ), hiển thị trên thẻ đề xuất. */
@Serializable
data class ReviewQuote(val reviewId: String, val text: String, val supports: Boolean)

@Serializable
data class ReviewRecommendation(
    val venueId: String,
    val sources: List<ReviewSource>,
    val quotes: List<ReviewQuote> = emptyList(),
)

@Serializable
data class ReviewClaim(val source: ReviewSource, val excerpt: String, val supports: Boolean)

@Serializable
data class AiSearchUsage(val used: Int, val attempts: Int, val limit: Int, val lockedUntil: String? = null) {
    val remaining: Int get() = (limit - maxOf(used, attempts)).coerceAtLeast(0)
}

@Serializable
data class AiCourtFilter(
    val type: String = "COURT",
    val keyword: String? = null,
    val districtId: String? = null,
    val date: String? = null,
    val dateChoices: List<String> = emptyList(),
    val partOfDay: String? = null,
    val start: String? = null,
    val end: String? = null,
    val priceMin: Long? = null,
    val priceMax: Long? = null,
    val amenityIds: List<String> = emptyList(),
    val sort: String? = null,
    val level: String? = null,
)

@Serializable
data class AiCourtParse(
    val intent: String,
    val faqTopic: String? = null,
    val filter: AiCourtFilter? = null,
    val preference: String? = null,
    val dropped: List<String> = emptyList(),
)

@Serializable
data class AiParseResponse(val result: AiCourtParse, val usage: AiSearchUsage)

@Serializable
/** Explanation is built from review observations without an LLM, so it carries no usage (spec 022 FR-029). */
data class AiExplanationResponse(val claims: List<ReviewClaim>, val usage: AiSearchUsage? = null)

interface CourtAiApi {
    suspend fun usage(): AiSearchUsage
    suspend fun parse(query: String): AiParseResponse
    suspend fun recommend(preference: ReviewPreference, venueIds: List<String>): List<ReviewRecommendation>
    suspend fun source(venueId: String, source: ReviewSource): String
    suspend fun explain(venueId: String, preference: ReviewPreference, sources: List<ReviewSource>): AiExplanationResponse
}

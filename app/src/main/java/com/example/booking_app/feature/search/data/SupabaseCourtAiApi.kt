package com.example.booking_app.feature.search.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.FunctionErrors
import com.example.booking_app.feature.search.domain.AiExplanationResponse
import com.example.booking_app.feature.search.domain.AiParseResponse
import com.example.booking_app.feature.search.domain.AiSearchUsage
import com.example.booking_app.feature.search.domain.CourtAiApi
import com.example.booking_app.feature.search.domain.ReviewPreference
import com.example.booking_app.feature.search.domain.ReviewRecommendation
import com.example.booking_app.feature.search.domain.ReviewSource
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

class SupabaseCourtAiApi @Inject constructor(
    private val supabase: SupabaseClient,
    private val errors: FunctionErrors,
) : CourtAiApi {
    override suspend fun usage(): AiSearchUsage = call<UsageResponse>(Request("USAGE")).usage

    override suspend fun parse(query: String): AiParseResponse = call(Request("PARSE", query = maskQuery(query)))

    override suspend fun recommend(preference: ReviewPreference, venueIds: List<String>): List<ReviewRecommendation> =
        call<RecommendationsResponse>(Request("RECOMMEND", preference = preference.name, venueIds = venueIds)).recommendations

    override suspend fun source(venueId: String, source: ReviewSource): String =
        call<SourceResponse>(Request("SOURCE", venueId = venueId, source = source)).comment

    override suspend fun explain(
        venueId: String,
        preference: ReviewPreference,
        sources: List<ReviewSource>,
    ): AiExplanationResponse = call(Request("EXPLAIN", venueId = venueId, preference = preference.name, sources = sources))

    private suspend inline fun <reified T> call(request: Request): T =
        withTimeoutOrNull(10_000) {
            errors.invokeWithAuthRetry {
                json.decodeFromString<T>(supabase.functions.invoke("ai-search-parse", body = request).bodyAsText())
            }
        } ?: throw AppError.Timeout

    @Serializable
    private data class Request(
        val action: String,
        val query: String? = null,
        val venueId: String? = null,
        val venueIds: List<String>? = null,
        val preference: String? = null,
        val source: ReviewSource? = null,
        val sources: List<ReviewSource>? = null,
    )

    @Serializable private data class UsageResponse(val usage: AiSearchUsage)
    @Serializable private data class RecommendationsResponse(val recommendations: List<ReviewRecommendation>)
    @Serializable private data class SourceResponse(val comment: String)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val email = Regex("[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}.-]+\\.[\\p{L}]{2,}")
        val phone = Regex("(?<![\\p{L}\\p{N}])(?:\\+?84|0)(?:[\\s.()-]*[0-9]){8,10}(?![0-9])")

        fun maskQuery(query: String): String = phone.replace(email.replace(query, "[email]"), "[phone]")
    }
}

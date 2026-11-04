package com.example.booking_app.feature.booking.data.cache

import com.example.booking_app.core.util.AppClock
import com.example.booking_app.core.util.SystemAppClock
import com.example.booking_app.feature.booking.domain.model.Court
import com.example.booking_app.feature.booking.domain.model.PriceRule
import com.example.booking_app.feature.booking.domain.model.VenueInfo
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache in-memory cho thông tin metadata của cơ sở (venue, courts, priceRules).
 * TTL = 5 phút (300.000 ms), hỗ trợ injectable clock để dễ dàng kiểm thử.
 */
@Singleton
class VenueMetadataCache @Inject constructor(
    private val clock: AppClock
) {
    companion object {
        const val DEFAULT_TTL_MILLIS: Long = 5 * 60 * 1000L // 5 phút
    }

    private data class CacheEntry<T>(
        val data: T,
        val timestamp: Long
    ) {
        fun isExpired(now: Long, ttlMillis: Long): Boolean = now - timestamp > ttlMillis
    }

    private val venueCache = ConcurrentHashMap<String, CacheEntry<VenueInfo>>()
    private val courtsCache = ConcurrentHashMap<String, CacheEntry<List<Court>>>()
    private val priceRulesCache = ConcurrentHashMap<String, CacheEntry<List<PriceRule>>>()

    fun getVenueInfo(venueId: String, forceRefresh: Boolean = false): VenueInfo? {
        if (forceRefresh) return null
        val entry = venueCache[venueId] ?: return null
        return if (entry.isExpired(clock.now(), DEFAULT_TTL_MILLIS)) {
            venueCache.remove(venueId)
            null
        } else {
            entry.data
        }
    }

    fun putVenueInfo(venueId: String, info: VenueInfo) {
        venueCache[venueId] = CacheEntry(info, clock.now())
    }

    fun getCourts(venueId: String, forceRefresh: Boolean = false): List<Court>? {
        if (forceRefresh) return null
        val entry = courtsCache[venueId] ?: return null
        return if (entry.isExpired(clock.now(), DEFAULT_TTL_MILLIS)) {
            courtsCache.remove(venueId)
            null
        } else {
            entry.data
        }
    }

    fun putCourts(venueId: String, courts: List<Court>) {
        courtsCache[venueId] = CacheEntry(courts, clock.now())
    }

    fun getPriceRules(venueId: String, forceRefresh: Boolean = false): List<PriceRule>? {
        if (forceRefresh) return null
        val entry = priceRulesCache[venueId] ?: return null
        return if (entry.isExpired(clock.now(), DEFAULT_TTL_MILLIS)) {
            priceRulesCache.remove(venueId)
            null
        } else {
            entry.data
        }
    }

    fun putPriceRules(venueId: String, rules: List<PriceRule>) {
        priceRulesCache[venueId] = CacheEntry(rules, clock.now())
    }

    fun clear(venueId: String? = null) {
        if (venueId != null) {
            venueCache.remove(venueId)
            courtsCache.remove(venueId)
            priceRulesCache.remove(venueId)
        } else {
            venueCache.clear()
            courtsCache.clear()
            priceRulesCache.clear()
        }
    }
}

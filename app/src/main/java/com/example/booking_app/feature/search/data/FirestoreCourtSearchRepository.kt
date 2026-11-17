package com.example.booking_app.feature.search.data

import com.example.booking_app.core.catalog.CatalogRepository
import com.example.booking_app.core.location.GeoDistance
import com.example.booking_app.core.media.PhotoUrlResolver
import com.example.booking_app.core.network.awaitWithTimeout
import com.example.booking_app.core.pricing.PriceRuleMapper
import com.example.booking_app.core.pricing.SlotPricing
import com.example.booking_app.core.schedule.BookingSearchWindow
import com.example.booking_app.core.schedule.BookingWindow
import com.example.booking_app.core.schedule.TimeOfDay
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.search.domain.CourtSearchCriteria
import com.example.booking_app.feature.search.domain.CourtSearchPage
import com.example.booking_app.feature.search.domain.CourtSearchRepository
import com.example.booking_app.feature.search.domain.CourtSearchSort
import com.example.booking_app.feature.search.domain.OccupiedSlot
import com.example.booking_app.feature.search.domain.hasContinuousCourt
import com.example.booking_app.feature.search.domain.matchesVenueName
import com.example.booking_app.feature.search.domain.model.HomeVenue
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Source
import com.example.booking_app.core.pricing.PriceRule
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flow
import java.time.Instant
import javax.inject.Inject

class FirestoreCourtSearchRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val catalog: CatalogRepository,
    private val photoResolver: PhotoUrlResolver,
    private val clock: AppClock,
) : CourtSearchRepository {

    override fun search(criteria: CourtSearchCriteria, origin: GeoPoint?) = flow {
        val venues = firestore.collection("venues").whereEqualTo("status", "ACTIVE")
        val query = criteria.districtId?.let { venues.whereEqualTo("districtId", it) } ?: venues
        // Danh sách mặc định phải duyệt hết các cơ sở hoạt động, kể cả khi vượt giới hạn tìm kiếm có lọc.
        val browseAll = !criteria.hasFilters() && criteria.sort != CourtSearchSort.NEAREST
        val snapshots = if (browseAll) {
            val all = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()
            var page = query.orderBy(FieldPath.documentId()).limit(BROWSE_PAGE_SIZE)
                .get(Source.SERVER).awaitWithTimeout().documents
            while (page.isNotEmpty()) {
                all.addAll(page)
                if (page.size < BROWSE_PAGE_SIZE) break
                page = query.orderBy(FieldPath.documentId()).startAfter(page.last()).limit(BROWSE_PAGE_SIZE)
                    .get(Source.SERVER).awaitWithTimeout().documents
            }
            all
        } else query.limit(MAX_VENUES + 1L).get(Source.SERVER).awaitWithTimeout().documents
        val districts = catalog.districts()
        val candidates = (if (browseAll) snapshots else snapshots.take(MAX_VENUES)).mapNotNull { document ->
            val data = document.data ?: return@mapNotNull null
            val venue = HomeVenueMapper.map(document.id, data, districts, photoResolver)
            if (!matchesVenueName(criteria.keyword, venue.name)) return@mapNotNull null
            val amenities = (data["amenityIds"] as? List<*>)?.filterIsInstance<String>().orEmpty()
            if (!amenities.containsAll(criteria.amenityIds)) return@mapNotNull null
            val location = data["location"] as? GeoPoint
            val distance = if (origin != null && location != null) GeoDistance.meters(origin, location) else null
            Candidate(venue.copy(distanceMeters = distance), data["openTime"] as? String, data["closeTime"] as? String)
        }
        val capped = !browseAll && snapshots.size > MAX_VENUES
        val comparator = comparator(criteria.sort)
        val window = criteria.window
        if (window == null) {
            emit(CourtSearchPage(candidates.map { it.venue }.filter { criteria.acceptsPrice(it.minPricePerHour) }
                .sortedWith(comparator), complete = true, capped = capped))
            return@flow
        }
        if (!window.isBookableAt(Instant.ofEpochMilli(clock.now()).atZone(BookingWindow.ZONE))) {
            emit(CourtSearchPage(emptyList(), complete = true, capped = capped))
            return@flow
        }

        val matches = mutableListOf<HomeVenue>()
        val sortByWindowPrice = criteria.sort == CourtSearchSort.PRICE_ASC
        val ordered = candidates.sortedWith(compareBy(comparator) { it.venue })
        for (batch in ordered.chunked(AVAILABILITY_BATCH_SIZE)) {
            val found = coroutineScope {
                batch.map { candidate -> async { matchWindow(candidate, window, criteria) } }.awaitAll().filterNotNull()
            }
            matches += found
            if (!sortByWindowPrice && found.isNotEmpty()) {
                emit(CourtSearchPage(matches.toList(), complete = false, capped = capped))
            }
        }
        emit(CourtSearchPage(matches.sortedWith(comparator), complete = true, capped = capped))
    }

    private suspend fun matchWindow(
        candidate: Candidate,
        window: BookingSearchWindow,
        criteria: CourtSearchCriteria,
    ): HomeVenue? = coroutineScope {
        val opens = TimeOfDay.parse(candidate.opens) ?: return@coroutineScope null
        val closes = TimeOfDay.parseEnd(candidate.closes) ?: return@coroutineScope null
        if (window.start < opens || window.end > closes) return@coroutineScope null
        val venue = firestore.collection("venues").document(candidate.venue.id)
        val rules = async { venue.collection("priceRules").get(Source.SERVER).awaitWithTimeout() }
        val courts = async { venue.collection("courts").whereEqualTo("isActive", true).get(Source.SERVER).awaitWithTimeout() }
        val slots = async {
            firestore.collection("slots").whereEqualTo("venueId", venue.id).whereEqualTo("date", window.dateKey())
                .whereIn("status", listOf("BOOKED", "BLOCKED", "HELD")).get(Source.SERVER).awaitWithTimeout()
        }
        val prices = rules.await().documents.mapNotNull { doc -> PriceRuleMapper.fromFirestore(doc.id, doc.data.orEmpty()) }
        val total = SlotPricing.priceForWindow(prices, window.date, window.start, window.end) ?: return@coroutineScope null
        val perHour = SlotPricing.perHour(total, window.start, window.end)
        if (!criteria.acceptsPrice(perHour)) return@coroutineScope null
        val courtIds = courts.await().documents.mapTo(mutableSetOf()) { it.id }
        val occupied = slots.await().documents.mapNotNull { doc ->
            val courtId = doc.getString("courtId") ?: return@mapNotNull null
            val start = TimeOfDay.parse(doc.getString("startTime")) ?: return@mapNotNull null
            val status = doc.getString("status") ?: return@mapNotNull null
            OccupiedSlot(courtId, start, status, (doc.get("holdExpiresAt") as? Timestamp)?.toDate()?.time)
        }
        if (!hasContinuousCourt(courtIds, window, occupied, clock.now())) return@coroutineScope null
        candidate.venue.copy(windowPricePerHour = perHour, window = window)
    }

    override suspend fun variantPrices(variants: List<CourtSearchCriteria>): List<List<Long?>> = coroutineScope {
        if (variants.isEmpty()) return@coroutineScope emptyList()
        val scope = this
        val districts = catalog.districts()
        val now = Instant.ofEpochMilli(clock.now()).atZone(BookingWindow.ZONE)
        // Đọc chung giữa các biến thể: danh sách sân theo quận, bảng giá + sân con theo sân, lịch bận theo sân và ngày.
        val pools = ConcurrentHashMap<String, Deferred<List<Probe>>>()
        val statics = ConcurrentHashMap<String, Deferred<VenueStatic>>()
        val busy = ConcurrentHashMap<String, Deferred<List<OccupiedSlot>>>()

        fun pool(districtId: String?): Deferred<List<Probe>> = pools.computeIfAbsent(districtId.orEmpty()) {
            scope.async(start = CoroutineStart.LAZY) {
                val venues = firestore.collection("venues").whereEqualTo("status", "ACTIVE")
                val query = districtId?.let { venues.whereEqualTo("districtId", it) } ?: venues
                query.limit(MAX_VENUES.toLong()).get(Source.SERVER).awaitWithTimeout().documents.mapNotNull { document ->
                    val data = document.data ?: return@mapNotNull null
                    Probe(
                        HomeVenueMapper.map(document.id, data, districts, photoResolver),
                        (data["amenityIds"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
                        data["openTime"] as? String,
                        data["closeTime"] as? String,
                    )
                }
            }
        }

        suspend fun windowPrice(probe: Probe, window: BookingSearchWindow, variant: CourtSearchCriteria): Long? {
            val opens = TimeOfDay.parse(probe.opens) ?: return null
            val closes = TimeOfDay.parseEnd(probe.closes) ?: return null
            if (window.start < opens || window.end > closes) return null
            val id = probe.venue.id
            val static = statics.computeIfAbsent(id) {
                scope.async(start = CoroutineStart.LAZY) { loadStatic(id) }
            }.await()
            val total = SlotPricing.priceForWindow(static.rules, window.date, window.start, window.end) ?: return null
            val perHour = SlotPricing.perHour(total, window.start, window.end)
            if (!variant.acceptsPrice(perHour)) return null
            val occupied = busy.computeIfAbsent("$id|${window.dateKey()}") {
                scope.async(start = CoroutineStart.LAZY) { loadBusy(id, window.dateKey()) }
            }.await()
            return perHour.takeIf { hasContinuousCourt(static.courtIds, window, occupied, clock.now()) }
        }

        variants.map { variant ->
            val matching = pool(variant.districtId).await().filter { probe ->
                matchesVenueName(variant.keyword, probe.venue.name) && probe.amenities.containsAll(variant.amenityIds)
            }
            val window = variant.window
            when {
                window == null -> matching.map { it.venue.minPricePerHour }.filter(variant::acceptsPrice)
                !window.isBookableAt(now) -> emptyList()
                else -> matching.chunked(AVAILABILITY_BATCH_SIZE).flatMap { batch ->
                    batch.map { probe -> async { windowPrice(probe, window, variant) } }.awaitAll().filterNotNull()
                }
            }
        }
    }

    private suspend fun loadStatic(venueId: String): VenueStatic = coroutineScope {
        val venue = firestore.collection("venues").document(venueId)
        val rules = async { venue.collection("priceRules").get(Source.SERVER).awaitWithTimeout() }
        val courts = async { venue.collection("courts").whereEqualTo("isActive", true).get(Source.SERVER).awaitWithTimeout() }
        VenueStatic(
            rules.await().documents.mapNotNull { doc -> PriceRuleMapper.fromFirestore(doc.id, doc.data.orEmpty()) },
            courts.await().documents.mapTo(mutableSetOf()) { it.id },
        )
    }

    private suspend fun loadBusy(venueId: String, dateKey: String): List<OccupiedSlot> =
        firestore.collection("slots").whereEqualTo("venueId", venueId).whereEqualTo("date", dateKey)
            .whereIn("status", listOf("BOOKED", "BLOCKED", "HELD")).get(Source.SERVER).awaitWithTimeout()
            .documents.mapNotNull { doc ->
                val courtId = doc.getString("courtId") ?: return@mapNotNull null
                val start = TimeOfDay.parse(doc.getString("startTime")) ?: return@mapNotNull null
                val status = doc.getString("status") ?: return@mapNotNull null
                OccupiedSlot(courtId, start, status, (doc.get("holdExpiresAt") as? Timestamp)?.toDate()?.time)
            }

    private fun comparator(sort: CourtSearchSort): Comparator<HomeVenue> {
        val rating = compareByDescending<HomeVenue> { it.ratingCount > 0 }.thenByDescending { it.ratingAvg }
            .thenByDescending { it.ratingCount }.thenBy { it.id }
        return when (sort) {
            CourtSearchSort.RATING -> rating
            CourtSearchSort.PRICE_ASC -> compareBy<HomeVenue> { it.windowPricePerHour ?: it.minPricePerHour ?: Long.MAX_VALUE }
                .then(rating)
            CourtSearchSort.NEAREST -> compareBy<HomeVenue> { it.distanceMeters ?: Double.POSITIVE_INFINITY }.then(rating)
        }
    }

    private data class Candidate(val venue: HomeVenue, val opens: String?, val closes: String?)

    /** Sân ứng viên khi đếm biến thể: giữ tiện ích để lọc lại theo từng biến thể. */
    private data class Probe(val venue: HomeVenue, val amenities: List<String>, val opens: String?, val closes: String?)

    private class VenueStatic(val rules: List<PriceRule>, val courtIds: Set<String>)

    private companion object {
        const val MAX_VENUES = 150
        const val BROWSE_PAGE_SIZE = 100L
        const val AVAILABILITY_BATCH_SIZE = 6
    }
}

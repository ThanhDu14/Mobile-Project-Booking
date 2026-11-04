package com.example.booking_app.feature.courtdetail.data

import com.example.booking_app.core.media.PhotoUrlResolver
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.DEFAULT_TIMEOUT_MS
import com.example.booking_app.core.network.awaitWithTimeout
import com.example.booking_app.core.pricing.PriceRule
import com.example.booking_app.core.pricing.PriceRuleMapper
import com.example.booking_app.feature.courtdetail.data.dto.CourtDto
import com.example.booking_app.feature.courtdetail.domain.VenueDetailRepository
import com.example.booking_app.feature.courtdetail.domain.model.Court
import com.example.booking_app.feature.courtdetail.domain.model.VenueAvailability
import com.example.booking_app.feature.courtdetail.domain.model.VenueCatalog
import com.example.booking_app.feature.courtdetail.domain.model.VenueResult
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Đọc Firestore cho chi tiết sân (research R2): listener cho `venues/{id}`, `get()` một lần cho `courts`,
 * `priceRules`. Mọi lời gọi tối đa 15 giây.
 */
class FirestoreVenueDetailRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val resolver: PhotoUrlResolver,
) : VenueDetailRepository {

    private fun venueRef(id: String) = firestore.collection(VENUES).document(id)

    override fun observeVenue(venueId: String, catalog: VenueCatalog?): Flow<VenueResult> = callbackFlow {
        var received = false
        // Cache trống khi offline: snapshot "không tồn tại" từ cache chưa chắc đúng, chờ server tối đa 15 giây.
        val timeout = launch {
            delay(DEFAULT_TIMEOUT_MS)
            if (!received) trySend(VenueResult.Failed(AppError.Timeout))
        }
        val registration = venueRef(venueId).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                received = true
                timeout.cancel()
                trySend(VenueErrorMapper.map(error))
                close()
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener
            val fromCache = snapshot.metadata.isFromCache
            if (!snapshot.exists() && fromCache) return@addSnapshotListener
            received = true
            timeout.cancel()
            trySend(VenueDetailMapper.map(venueId, snapshot.data, snapshot.exists(), fromCache, catalog, resolver))
        }
        awaitClose { registration.remove() }
    }.distinctUntilChanged()

    override suspend fun loadCourts(venueId: String): List<Court> =
        venueRef(venueId).collection(COURTS).get().awaitWithTimeout().documents
            .map { CourtDto.toDomain(it.id, it.data.orEmpty()) }

    override suspend fun loadPriceRules(venueId: String): List<PriceRule> =
        venueRef(venueId).collection(PRICE_RULES).get().awaitWithTimeout().documents
            .mapNotNull { PriceRuleMapper.fromFirestore(it.id, it.data.orEmpty()) }

    override suspend fun fetchStatusFromServer(venueId: String): VenueAvailability? = try {
        val snapshot = venueRef(venueId).get(Source.SERVER).awaitWithTimeout()
        if (!snapshot.exists()) VenueAvailability.GONE else VenueDetailMapper.availabilityOf(snapshot.getString("status"))
    } catch (e: AppError) {
        when {
            VenueErrorMapper.isPermissionDenied(e) -> VenueAvailability.SUSPENDED
            else -> null // mất mạng, quá hạn: dùng trạng thái mới nhất từ listener
        }
    }

    private companion object {
        const val VENUES = "venues"
        const val COURTS = "courts"
        const val PRICE_RULES = "priceRules"
    }
}

package com.example.booking_app.feature.search.data

import com.example.booking_app.core.catalog.CatalogRepository
import com.example.booking_app.core.catalog.District
import com.example.booking_app.core.media.PhotoUrlResolver
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.awaitWithTimeout
import com.example.booking_app.feature.search.domain.HomeVenueRepository
import com.example.booking_app.feature.search.domain.model.HomeVenue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * Đọc `venues` với `status == ACTIVE` (Rules cho khách đọc được, FR-026). Chỉ dùng index một trường nên chạy được
 * trên project thật mà không phải deploy index ghép. Danh mục quận lỗi thì thẻ chỉ thiếu tên quận.
 */
class FirestoreHomeVenueRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val catalog: CatalogRepository,
    private val resolver: PhotoUrlResolver,
) : HomeVenueRepository {

    override suspend fun activeVenues(): List<HomeVenue> {
        val snapshot = firestore.collection("venues")
            .whereEqualTo("status", "ACTIVE")
            .limit(HomeVenueRepository.MAX_VENUES)
            .get()
            .awaitWithTimeout()
        val districts = districtsOrEmpty()
        return snapshot.documents.map { doc -> HomeVenueMapper.map(doc.id, doc.data.orEmpty(), districts, resolver) }
    }

    private suspend fun districtsOrEmpty(): List<District> = try {
        catalog.districts()
    } catch (e: CancellationException) {
        throw e
    } catch (e: AppError) {
        emptyList()
    }
}

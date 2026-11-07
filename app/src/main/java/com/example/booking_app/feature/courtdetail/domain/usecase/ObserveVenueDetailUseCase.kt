package com.example.booking_app.feature.courtdetail.domain.usecase

import com.example.booking_app.core.catalog.CatalogRepository
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.courtdetail.domain.VenueDetailRepository
import com.example.booking_app.feature.courtdetail.domain.model.VenueCatalog
import com.example.booking_app.feature.courtdetail.domain.model.VenueResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Kết quả đã quyết theo phiên đăng nhập (research R10). */
sealed interface VenueView {
    data class Found(val result: VenueResult.Found) : VenueView
    data object Gone : VenueView

    /** Khách mở cơ sở tạm ngừng (`PERMISSION_DENIED`): màn hình rút gọn kèm "Đăng nhập". */
    data object SuspendedGuest : VenueView
    data class Failed(val error: AppError) : VenueView
}

/**
 * Lắng nghe cơ sở kèm danh mục quận/tiện ích. **Chỉ ở đây** `PERMISSION_DENIED` được đổi thành
 * [VenueView.SuspendedGuest] khi đang là khách; đã đăng nhập thì là lỗi chung.
 */
class ObserveVenueDetailUseCase @Inject constructor(
    private val repository: VenueDetailRepository,
    private val catalogRepository: CatalogRepository,
    private val sessionManager: SessionManager,
) {
    operator fun invoke(venueId: String): Flow<VenueView> = flow {
        val catalog = try {
            VenueCatalog(catalogRepository.districts(), catalogRepository.amenities())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // mục "Tiện ích" hiện lỗi kèm "Thử lại", các mục khác vẫn hiện
        }
        emitAll(
            repository.observeVenue(venueId, catalog).map { result ->
                when (result) {
                    is VenueResult.Found -> VenueView.Found(result)
                    VenueResult.Gone -> VenueView.Gone
                    VenueResult.PermissionDenied ->
                        if (sessionManager.isGuest.value) VenueView.SuspendedGuest
                        else VenueView.Failed(AppError.Unknown(null))
                    is VenueResult.Failed -> VenueView.Failed(result.error)
                }
            },
        )
    }
}

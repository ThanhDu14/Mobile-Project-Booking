package com.example.booking_app.feature.courtdetail.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.toAppError
import com.example.booking_app.feature.courtdetail.domain.model.VenueResult
import com.google.firebase.firestore.FirebaseFirestoreException

/**
 * Lỗi đọc `venues/{id}`. Tầng data không biết người dùng là khách hay đã đăng nhập: `PERMISSION_DENIED` chỉ là
 * [VenueResult.PermissionDenied]; `ObserveVenueDetailUseCase` mới quyết định (research R10).
 */
object VenueErrorMapper {

    fun map(error: Throwable): VenueResult {
        val firestore = (error as? FirebaseFirestoreException) ?: (error.cause as? FirebaseFirestoreException)
        return when (firestore?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> VenueResult.PermissionDenied
            FirebaseFirestoreException.Code.UNAVAILABLE -> VenueResult.Failed(AppError.Network)
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> VenueResult.Failed(AppError.Timeout)
            else -> VenueResult.Failed(if (error is AppError) error else error.toAppError())
        }
    }

    fun isPermissionDenied(error: Throwable): Boolean = map(error) == VenueResult.PermissionDenied
}

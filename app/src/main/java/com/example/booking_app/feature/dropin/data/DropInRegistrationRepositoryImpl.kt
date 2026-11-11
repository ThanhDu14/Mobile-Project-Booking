package com.example.booking_app.feature.dropin.data

import com.example.booking_app.core.network.toAppError
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.dropin.data.dto.RegisterDropInRequest
import com.example.booking_app.feature.dropin.data.dto.RegistrationDto
import com.example.booking_app.feature.dropin.data.dto.toDomain
import com.example.booking_app.feature.dropin.data.dto.toDropInError
import com.example.booking_app.feature.dropin.domain.DropInRegistrationReader
import com.example.booking_app.feature.dropin.domain.DropInRegistrationRepository
import com.example.booking_app.feature.dropin.domain.model.CancelQuote
import com.example.booking_app.feature.dropin.domain.model.DropInCursor
import com.example.booking_app.feature.dropin.domain.model.DropInError
import com.example.booking_app.feature.dropin.domain.model.DropInRegistration
import com.example.booking_app.feature.dropin.domain.model.Page
import com.example.booking_app.feature.dropin.domain.model.RegisterRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Đăng ký/hủy qua Edge Function, đọc đăng ký của mình qua Firestore (T035). Lỗi trả
 * `Result.failure(DropInError)`; mất mạng là [DropInError.Network] để ViewModel giữ lựa chọn và
 * cho thử lại cùng `requestId` (AC 2.8).
 */
@Singleton
class DropInRegistrationRepositoryImpl @Inject constructor(
    private val api: DropInApi,
    private val firestore: FirebaseFirestore,
    private val sessionManager: SessionManager,
) : DropInRegistrationRepository, DropInRegistrationReader {

    override suspend fun register(req: RegisterRequest): Result<DropInRegistration> {
        val uid = sessionManager.currentUid() ?: return Result.failure(DropInError.Unauthenticated())
        return try {
            val response = api.register(
                RegisterDropInRequest(
                    sessionId = req.sessionId,
                    partySize = req.partySize,
                    paymentMethod = req.paymentMethod.name,
                    requestId = req.requestId,
                ),
            )
            Result.success(response.registration.toDomain(fallbackSessionId = req.sessionId, uid = uid))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.toDropInError())
        }
    }

    // tuần 7, spec 080 US4
    override suspend fun quoteCancel(sessionId: String, requestId: String): Result<CancelQuote> =
        Result.failure(UnsupportedOperationException("quoteCancel: tuần 7, spec 080 US4"))

    // tuần 7, spec 080 US4
    override suspend fun cancel(sessionId: String, requestId: String): Result<DropInRegistration> =
        Result.failure(UnsupportedOperationException("cancel: tuần 7, spec 080 US4"))

    /**
     * Nghe `dropInSessions/{id}/registrations/{uid}`. Khách → `flowOf(null)`. Bị Rules từ chối
     * (ví dụ vừa đăng xuất) → phát `null`; lỗi khác đóng flow bằng [DropInError].
     */
    override fun observeMine(sessionId: String): Flow<DropInRegistration?> {
        val uid = sessionManager.currentUid() ?: return flowOf(null)
        return callbackFlow {
            val registration = firestore.collection(COLLECTION_SESSIONS).document(sessionId)
                .collection(COLLECTION_REGISTRATIONS).document(uid)
                .addSnapshotListener { snapshot, error ->
                    when {
                        error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> trySend(null)
                        error != null -> close(error.toAppError().toDropInError())
                        else -> trySend(
                            snapshot?.takeIf { it.exists() }
                                ?.let { RegistrationDto.fromMap(sessionId, it.id, it.data.orEmpty()).toDomain() },
                        )
                    }
                }
            awaitClose { registration.remove() }
        }.distinctUntilChanged()
    }

    // tuần 7, spec 080 US3
    override fun observeUpcoming(uid: String): Flow<List<DropInRegistration>> = emptyFlow()

    // tuần 7, spec 080 US3
    override suspend fun pagePast(uid: String, cursor: DropInCursor?): Result<Page<DropInRegistration>> =
        Result.failure(UnsupportedOperationException("pagePast: tuần 7, spec 080 US3"))

    private companion object {
        const val COLLECTION_SESSIONS = "dropInSessions"
        const val COLLECTION_REGISTRATIONS = "registrations"
    }
}

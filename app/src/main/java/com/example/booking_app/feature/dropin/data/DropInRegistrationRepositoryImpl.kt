package com.example.booking_app.feature.dropin.data

import com.example.booking_app.core.network.awaitWithTimeout
import com.example.booking_app.core.network.toAppError
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.dropin.data.dto.AddPartyRequest
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
import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.util.Date
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

    override suspend fun addParty(sessionId: String, additional: Int, requestId: String): Result<DropInRegistration> {
        val uid = sessionManager.currentUid() ?: return Result.failure(DropInError.Unauthenticated())
        return try {
            val response = api.addParty(AddPartyRequest(sessionId = sessionId, additional = additional, requestId = requestId))
            Result.success(response.registration.toDomain(fallbackSessionId = sessionId, uid = uid))
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

    /**
     * T045 (research R7): collection group `registrations` `where userId == uid`, `status in [REGISTERED, WAITLISTED]`,
     * `sessionStartAt >= now`, `orderBy sessionStartAt`. Mốc `now` lấy lúc bắt đầu nghe. Index
     * `(userId, status, sessionStartAt)` có sẵn trong `firestore.indexes.json`. Cũng là [DropInRegistrationReader]
     * cho nhóm khác.
     */
    override fun observeUpcoming(uid: String): Flow<List<DropInRegistration>> {
        if (uid.isBlank()) return flowOf(emptyList())
        return callbackFlow {
            val now = Instant.now()
            val registration = firestore.collectionGroup(COLLECTION_REGISTRATIONS)
                .whereEqualTo(FIELD_USER_ID, uid)
                .whereIn(FIELD_STATUS, UPCOMING_STATUSES)
                .whereGreaterThanOrEqualTo(FIELD_SESSION_START_AT, now.toTimestamp())
                .orderBy(FIELD_SESSION_START_AT, Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    when {
                        error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> trySend(emptyList())
                        error != null -> close(error.toAppError().toDropInError())
                        else -> trySend(snapshot?.documents.orEmpty().mapNotNull { it.toRegistration() })
                    }
                }
            awaitClose { registration.remove() }
        }.distinctUntilChanged()
    }

    /**
     * T045 (research R7): `where userId == uid`, `orderBy sessionStartAt desc` (kèm đường dẫn document để không trùng
     * khi hai lượt cùng giờ), `limit 20`; bỏ những lượt thuộc "Sắp tới". Con trỏ theo document thô cuối trang, để
     * trang bị lọc bớt không làm sót trang sau. Index `(userId, sessionStartAt desc)` có sẵn.
     */
    override suspend fun pagePast(uid: String, cursor: DropInCursor?): Result<Page<DropInRegistration>> {
        if (uid.isBlank()) return Result.failure(DropInError.Unauthenticated())
        return try {
            val now = Instant.now()
            var query: Query = firestore.collectionGroup(COLLECTION_REGISTRATIONS)
                .whereEqualTo(FIELD_USER_ID, uid)
                .orderBy(FIELD_SESSION_START_AT, Query.Direction.DESCENDING)
                .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
            // Truy vấn collection group: giá trị documentId của con trỏ phải là đường dẫn đầy đủ.
            cursor?.let { query = query.startAfter(it.startAt.toTimestamp(), it.id) }
            val docs = query.limit(Page.PAGE_SIZE.toLong()).get().awaitWithTimeout().documents
            val items = docs.mapNotNull { it.toRegistration() }.filterNot { it.isUpcoming(now) }
            val next = docs.lastOrNull()
                ?.takeIf { docs.size >= Page.PAGE_SIZE }
                ?.let { last ->
                    last.getTimestamp(FIELD_SESSION_START_AT)?.toInstant()?.let { DropInCursor(it, last.reference.path) }
                }
            Result.success(Page(items, next))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.toDropInError())
        }
    }

    /** Lượt nằm ở tab "Sắp tới" (cùng điều kiện với [observeUpcoming]). */
    private fun DropInRegistration.isUpcoming(now: Instant): Boolean {
        val start = sessionStartAt ?: return false
        return status.name in UPCOMING_STATUSES && !start.isBefore(now)
    }

    /** Document trong collection group: ID buổi là document cha của `registrations`. */
    private fun DocumentSnapshot.toRegistration(): DropInRegistration? {
        val sessionId = reference.parent.parent?.id ?: return null
        return RegistrationDto.fromMap(sessionId, id, data.orEmpty()).toDomain()
    }

    private fun Instant.toTimestamp(): Timestamp = Timestamp(Date.from(this))

    private companion object {
        const val COLLECTION_SESSIONS = "dropInSessions"
        const val COLLECTION_REGISTRATIONS = "registrations"
        const val FIELD_USER_ID = "userId"
        const val FIELD_STATUS = "status"
        const val FIELD_SESSION_START_AT = "sessionStartAt"
        val UPCOMING_STATUSES = listOf(RegistrationStatus.REGISTERED.name, RegistrationStatus.WAITLISTED.name)
    }
}

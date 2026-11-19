package com.example.booking_app.feature.chat.data

import com.example.booking_app.core.chat.UnreadChatsReader
import com.example.booking_app.core.session.AppMode
import com.example.booking_app.core.session.AppModeController
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.feature.chat.data.dto.asLong
import com.example.booking_app.feature.chat.domain.model.ChatStatus
import com.example.booking_app.feature.chat.domain.model.ChatType
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Đếm số cuộc trò chuyện còn tin chưa đọc (`unreadCounts[uid] > 0`, không phải tổng số tin)
 * theo đúng chế độ hiện tại (`PLAYER` / `OWNER`, FR-005, R6, R15, T039) qua listener realtime
 * `chats where memberIds array-contains uid`.
 * Khi người dùng đăng xuất thì tự hủy listener và phát `0`.
 */
@Singleton
class UnreadChatsReaderImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val sessionManager: SessionManager,
    private val appModeController: AppModeController,
) : UnreadChatsReader {

    private data class UnreadCandidate(
        val type: String?,
        val ownerId: String?,
        val unreadCount: Long,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeUnreadChatCount(): Flow<Int> = sessionManager.sessionState
        .map { state -> (state as? SessionState.SignedIn)?.uid }
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) {
                flowOf(0)
            } else {
                combine(observeUnreadCandidatesForUser(uid), appModeController.mode) { candidates, mode ->
                    candidates.count { doc ->
                        if (doc.unreadCount <= 0L) return@count false
                        when (mode) {
                            AppMode.OWNER -> doc.ownerId == uid && doc.type == ChatType.DIRECT.name
                            AppMode.PLAYER -> doc.ownerId != uid
                        }
                    }
                }
            }
        }
        .distinctUntilChanged()

    private fun observeUnreadCandidatesForUser(uid: String): Flow<List<UnreadCandidate>> = callbackFlow {
        val registration = firestore.collection(COLLECTION_CHATS)
            .whereArrayContains("memberIds", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val items = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    val status = (doc.get("status") as? String)?.uppercase()
                    if (status == ChatStatus.DELETED.name) return@mapNotNull null
                    val counts = doc.get("unreadCounts") as? Map<*, *> ?: return@mapNotNull null
                    val unread = counts[uid].asLong() ?: 0L
                    UnreadCandidate(
                        type = (doc.get("type") as? String)?.uppercase(),
                        ownerId = doc.get("ownerId") as? String,
                        unreadCount = unread,
                    )
                }
                trySend(items)
            }
        awaitClose { registration.remove() }
    }

    private companion object {
        const val COLLECTION_CHATS = "chats"
    }
}

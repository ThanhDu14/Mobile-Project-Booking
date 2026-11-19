package com.example.booking_app.feature.chat.data

import com.example.booking_app.core.chat.UnreadChatsReader
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.feature.chat.data.dto.asLong
import com.example.booking_app.feature.chat.domain.model.ChatStatus
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Đếm số cuộc trò chuyện còn tin chưa đọc (`unreadCounts[uid] > 0`, không phải tổng số tin)
 * qua listener realtime `chats where memberIds array-contains uid` (T039, research R6).
 * Khi người dùng đăng xuất thì tự hủy listener và phát `0`.
 */
@Singleton
class UnreadChatsReaderImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val sessionManager: SessionManager,
) : UnreadChatsReader {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeUnreadChatCount(): Flow<Int> = sessionManager.sessionState
        .map { state -> (state as? SessionState.SignedIn)?.uid }
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) flowOf(0) else observeUnreadCountForUser(uid)
        }
        .distinctUntilChanged()

    private fun observeUnreadCountForUser(uid: String): Flow<Int> = callbackFlow {
        val registration = firestore.collection(COLLECTION_CHATS)
            .whereArrayContains("memberIds", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(0)
                    return@addSnapshotListener
                }
                val unreadChats = snapshot?.documents.orEmpty().count { doc ->
                    val status = (doc.get("status") as? String)?.uppercase()
                    if (status == ChatStatus.DELETED.name) return@count false
                    val counts = doc.get("unreadCounts") as? Map<*, *> ?: return@count false
                    (counts[uid].asLong() ?: 0L) > 0L
                }
                trySend(unreadChats)
            }
        awaitClose { registration.remove() }
    }

    private companion object {
        const val COLLECTION_CHATS = "chats"
    }
}

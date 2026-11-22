package com.example.booking_app.feature.chat.data

import com.example.booking_app.core.network.awaitWithTimeout
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.chat.data.dto.ChatActionRequest
import com.example.booking_app.feature.chat.data.dto.ChatDto
import com.example.booking_app.feature.chat.data.dto.MessageDto
import com.example.booking_app.feature.chat.data.dto.ParticipantDto
import com.example.booking_app.feature.chat.data.dto.groupHeaderFromMap
import com.example.booking_app.feature.chat.data.dto.toDetail
import com.example.booking_app.feature.chat.data.dto.toDomain
import com.example.booking_app.feature.chat.data.dto.toOpenDirectResult
import com.example.booking_app.feature.chat.data.dto.toSummary
import com.example.booking_app.feature.chat.domain.ChatError
import com.example.booking_app.feature.chat.domain.ChatRepository
import com.example.booking_app.feature.chat.domain.model.ChatDetail
import com.example.booking_app.feature.chat.domain.model.ChatMessage
import com.example.booking_app.feature.chat.domain.model.ChatMode
import com.example.booking_app.feature.chat.domain.model.ChatStatus
import com.example.booking_app.feature.chat.domain.model.ChatSummary
import com.example.booking_app.feature.chat.domain.model.ChatType
import com.example.booking_app.feature.chat.domain.model.DirectChatResult
import com.example.booking_app.feature.chat.domain.model.DiscardResult
import com.example.booking_app.feature.chat.domain.model.GroupHeaderInfo
import com.example.booking_app.feature.chat.domain.model.OutboxEntry
import com.example.booking_app.feature.chat.domain.model.Participant
import com.example.booking_app.feature.chat.domain.model.ReportReason
import com.example.booking_app.feature.chat.domain.toChatError
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kho dữ liệu cuộc trò chuyện và tin nhắn trên Cloud Firestore + Edge Functions (T027, T035, T051).
 */
@Singleton
class FirestoreChatRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val actionApi: ChatActionApi,
    private val outbox: ChatOutbox,
    private val sessionManager: SessionManager,
    private val clock: AppClock,
) : ChatRepository {

    /** Độ lệch ước lượng `serverMs - localMs` cập nhật sau mỗi lần ghi `lastReadAt = serverTimestamp()`. */
    private val serverOffsetMs = AtomicLong(0L)

    override fun estimatedServerOffsetMs(): Long = serverOffsetMs.get()

    override fun observeChats(mode: ChatMode): Flow<List<ChatSummary>> {
        val uid = sessionManager.currentUid() ?: return flowOf(emptyList())
        val chatsFlow = observeChatDocs(uid, mode)
        val participantsFlow = observeMyParticipants(uid)
        return combine(chatsFlow, participantsFlow) { chatsWithGroups, myParts ->
            chatsWithGroups.map { (dto, groupInfo) ->
                dto.toSummary(
                    currentUid = uid,
                    groupInfo = groupInfo,
                    myParticipant = myParts[dto.id],
                )
            }
        }.distinctUntilChanged()
    }

    private fun observeChatDocs(
        uid: String,
        mode: ChatMode,
    ): Flow<List<Pair<ChatDto, GroupHeaderInfo?>>> = callbackFlow {
        var groupListeners: List<ListenerRegistration> = emptyList()
        var lastGroupIds: List<String> = emptyList()
        var latestDtos: List<ChatDto> = emptyList()
        var latestGroupsById: Map<String, GroupHeaderInfo> = emptyMap()
        var latestHadGroupError = false
        var groupsLoaded = false
        var groupGeneration = 0

        fun emitCombined() {
            val visible = latestDtos.mapNotNull { dto ->
                val isGroup = dto.type?.uppercase() == ChatType.GROUP.name || dto.groupId != null
                if (!isGroup) {
                    dto to null
                } else {
                    val info = dto.groupId?.let(latestGroupsById::get)
                    if (info == null && !latestHadGroupError) {
                        // Nhóm đã bị xóa (không thuộc [ACTIVE, LOCKED]): loại bỏ khỏi danh sách (AC 2.5).
                        null
                    } else {
                        dto to info
                    }
                }
            }
            trySend(visible)
        }

        val baseQuery: Query = when (mode) {
            ChatMode.OWNER -> firestore.collection(COLLECTION_CHATS)
                .whereArrayContains("memberIds", uid)
                .whereEqualTo("ownerId", uid)
                .whereEqualTo("type", ChatType.DIRECT.name)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
            ChatMode.PLAYER -> firestore.collection(COLLECTION_CHATS)
                .whereArrayContains("memberIds", uid)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
        }

        val chatsListener = baseQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                groupGeneration++
                groupListeners.forEach { it.remove() }
                groupListeners = emptyList()
                lastGroupIds = emptyList()
                groupsLoaded = false
                if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    close(ChatError.NotParticipant())
                } else {
                    close(error.toChatError())
                }
                return@addSnapshotListener
            }

            val dtos = snapshot?.documents.orEmpty()
                .map { ChatDto.fromMap(it.id, it.data.orEmpty()) }
                .filter { dto ->
                    val status = dto.status?.uppercase()
                    if (status == ChatStatus.DELETED.name) return@filter false
                    if (mode == ChatMode.PLAYER && dto.ownerId == uid) return@filter false
                    true
                }

            latestDtos = dtos
            val groupIds = dtos.mapNotNull { it.groupId?.takeIf(String::isNotBlank) }.distinct()

            if (groupIds.isEmpty()) {
                groupGeneration++
                groupListeners.forEach { it.remove() }
                groupListeners = emptyList()
                lastGroupIds = emptyList()
                latestGroupsById = emptyMap()
                latestHadGroupError = false
                groupsLoaded = false
                trySend(dtos.map { it to null })
                return@addSnapshotListener
            }

            if (groupIds == lastGroupIds && groupListeners.isNotEmpty()) {
                if (groupsLoaded) emitCombined()
                return@addSnapshotListener
            }

            val gen = ++groupGeneration
            groupListeners.forEach { it.remove() }
            groupListeners = emptyList()
            lastGroupIds = groupIds
            groupsLoaded = false

            // Bài học F1 của spec 090 (T035): đọc groups theo lô ≤ 10 bằng whereIn(documentId) KÈM status in [ACTIVE, LOCKED].
            val chunks = groupIds.chunked(WHERE_IN_LIMIT)
            val chunkResults = arrayOfNulls<Map<String, GroupHeaderInfo>>(chunks.size)
            val chunkErrors = BooleanArray(chunks.size)
            groupListeners = chunks.mapIndexed { index, chunk ->
                firestore.collection(COLLECTION_GROUPS)
                    .whereIn(FieldPath.documentId(), chunk)
                    .whereIn("status", VISIBLE_GROUP_STATUSES)
                    .addSnapshotListener { groupSnapshot, groupError ->
                        if (gen != groupGeneration) return@addSnapshotListener
                        if (groupError != null) {
                            chunkErrors[index] = true
                            chunkResults[index] = emptyMap()
                        } else {
                            chunkErrors[index] = false
                            chunkResults[index] = groupSnapshot?.documents.orEmpty().associate { doc ->
                                doc.id to groupHeaderFromMap(doc.id, doc.data.orEmpty())
                            }
                        }
                        if (chunkResults.all { it != null }) {
                            latestGroupsById = buildMap {
                                chunkResults.forEach { it?.let(::putAll) }
                            }
                            latestHadGroupError = chunkErrors.any { it }
                            groupsLoaded = true
                            emitCombined()
                        }
                    }
            }
        }

        awaitClose {
            chatsListener.remove()
            groupListeners.forEach { it.remove() }
        }
    }

    private fun observeMyParticipants(uid: String): Flow<Map<String, Participant>> = callbackFlow {
        val listener = firestore.collectionGroup(COLLECTION_PARTICIPANTS)
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Không làm hỏng danh sách chat nếu participants chưa tải được.
                    trySend(emptyMap())
                    return@addSnapshotListener
                }
                val map = buildMap<String, Participant> {
                    snapshot?.documents.orEmpty().forEach { doc ->
                        val dto = ParticipantDto.fromMap(doc.id, doc.data.orEmpty())
                        val chatId = dto.chatId ?: doc.reference.parent.parent?.id ?: return@forEach
                        put(chatId, dto.toDomain())
                    }
                }
                trySend(map)
            }
        awaitClose { listener.remove() }
    }

    override fun observeChat(chatId: String): Flow<ChatDetail?> {
        val uid = sessionManager.currentUid() ?: return flowOf(null)
        val chatFlow = callbackFlow<ChatDto?> {
            val listener = firestore.collection(COLLECTION_CHATS).document(chatId)
                .addSnapshotListener { snapshot, error ->
                    when {
                        error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                            close(ChatError.NotParticipant())
                        error != null -> close(error.toChatError())
                        snapshot == null || !snapshot.exists() -> trySend(null)
                        else -> {
                            val dto = ChatDto.fromMap(snapshot.id, snapshot.data.orEmpty())
                            if (dto.status?.uppercase() == ChatStatus.DELETED.name) {
                                trySend(null)
                            } else {
                                trySend(dto)
                            }
                        }
                    }
                }
            awaitClose { listener.remove() }
        }
        val participantFlow = callbackFlow<Participant> {
            val listener = firestore.collection(COLLECTION_CHATS).document(chatId)
                .collection(COLLECTION_PARTICIPANTS).document(uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            trySend(Participant())
                        }
                        return@addSnapshotListener
                    }
                    val dto = snapshot?.takeIf { it.exists() }
                        ?.let { ParticipantDto.fromMap(it.id, it.data.orEmpty()) }
                    trySend(dto.toDomain())
                }
            awaitClose { listener.remove() }
        }
        return combine(chatFlow, participantFlow) { chatDto, participant ->
            chatDto?.toDetail(currentUid = uid, myParticipant = participant)
        }.distinctUntilChanged()
    }

    override fun observeGroupHeader(groupId: String): Flow<GroupHeaderInfo?> = callbackFlow {
        val listener = firestore.collection(COLLECTION_GROUPS).document(groupId)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> trySend(null)
                    snapshot == null || !snapshot.exists() -> trySend(null)
                    else -> {
                        val data = snapshot.data.orEmpty()
                        if ((data["status"] as? String)?.uppercase() == "DELETED") {
                            trySend(null)
                        } else {
                            trySend(groupHeaderFromMap(snapshot.id, data))
                        }
                    }
                }
            }
        awaitClose { listener.remove() }
    }.distinctUntilChanged()

    override suspend fun openDirect(venueId: String): Result<DirectChatResult> = try {
        val response = actionApi.invoke(ChatActionRequest.openDirect(venueId))
        val parsed = response.toOpenDirectResult()
            ?: return Result.failure(ChatError.Unknown("INVALID_OPEN_DIRECT_RESPONSE"))
        Result.success(parsed)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.toChatError())
    }

    override fun observeMessages(chatId: String): Flow<List<ChatMessage>> {
        val uid = sessionManager.currentUid() ?: return flowOf(emptyList())
        return callbackFlow {
            val listener = firestore.collection(COLLECTION_CHATS).document(chatId)
                .collection(COLLECTION_MESSAGES)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE)
                .addSnapshotListener { snapshot, error ->
                    when {
                        error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                            close(ChatError.NotParticipant())
                        error != null -> close(error.toChatError())
                        else -> {
                            val list = snapshot?.documents.orEmpty()
                                .map { MessageDto.fromMap(it.id, it.data.orEmpty()).toDomain(uid) }
                                .reversed()
                            trySend(list)
                        }
                    }
                }
            awaitClose { listener.remove() }
        }
    }

    override suspend fun loadOlder(chatId: String, before: Instant): Result<List<ChatMessage>> {
        val uid = sessionManager.currentUid() ?: return Result.failure(ChatError.Unauthenticated())
        return try {
            val snapshot = firestore.collection(COLLECTION_CHATS).document(chatId)
                .collection(COLLECTION_MESSAGES)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .startAfter(Timestamp(before.epochSecond, before.nano))
                .limit(PAGE_SIZE)
                .get()
                .awaitWithTimeout()
            val messages = snapshot.documents
                .map { MessageDto.fromMap(it.id, it.data.orEmpty()).toDomain(uid) }
                .reversed()
            Result.success(messages)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.toChatError())
        }
    }

    override fun observeOutbox(chatId: String): Flow<List<OutboxEntry>> = outbox.observe(chatId)

    override suspend fun send(entry: OutboxEntry): Result<Unit> = try {
        outbox.enqueue(entry)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.toChatError())
    }

    override suspend fun retry(chatId: String, messageId: String): Result<Unit> = try {
        outbox.retry(chatId, messageId)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.toChatError())
    }

    override suspend fun discard(chatId: String, messageId: String): Result<DiscardResult> = try {
        Result.success(outbox.discard(chatId, messageId))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.toChatError())
    }

    override suspend fun markRead(chatId: String): Result<Unit> {
        val uid = sessionManager.currentUid() ?: return Result.failure(ChatError.Unauthenticated())
        return try {
            val chatRef = firestore.collection(COLLECTION_CHATS).document(chatId)
            val participantRef = chatRef.collection(COLLECTION_PARTICIPANTS).document(uid)
            val batch = firestore.batch()
            batch.update(chatRef, FieldPath.of("unreadCounts", uid), 0)
            batch.update(participantRef, "lastReadAt", FieldValue.serverTimestamp())
            batch.commit().awaitWithTimeout()
            // Ước lượng độ lệch giờ server từ serverTimestamp vừa ghi (research R7).
            val localAfter = clock.now()
            try {
                val doc = participantRef.get().awaitWithTimeout()
                val serverTs = doc.getTimestamp("lastReadAt")?.toInstant()?.toEpochMilli()
                if (serverTs != null) {
                    val diff = serverTs - localAfter
                    if (kotlin.math.abs(diff) < MAX_CLOCK_SKEW_SANITY_MS) {
                        serverOffsetMs.set(diff)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Đọc lại serverTimestamp là thao tác best-effort để ước lượng độ lệch giờ.
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.toChatError())
        }
    }

    override suspend fun setViewing(chatId: String, until: Instant?): Result<Unit> {
        val uid = sessionManager.currentUid() ?: return Result.failure(ChatError.Unauthenticated())
        return try {
            val value = until?.let { Timestamp(it.epochSecond, it.nano) }
            firestore.collection(COLLECTION_CHATS).document(chatId)
                .collection(COLLECTION_PARTICIPANTS).document(uid)
                .update("viewingUntil", value)
                .awaitWithTimeout()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.toChatError())
        }
    }

    override suspend fun setMuted(chatId: String, muted: Boolean): Result<Unit> {
        val uid = sessionManager.currentUid() ?: return Result.failure(ChatError.Unauthenticated())
        return try {
            firestore.collection(COLLECTION_CHATS).document(chatId)
                .collection(COLLECTION_PARTICIPANTS).document(uid)
                .update("muted", muted)
                .awaitWithTimeout()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.toChatError())
        }
    }

    override suspend fun setBlocked(chatId: String, blocked: Boolean): Result<Unit> {
        val uid = sessionManager.currentUid() ?: return Result.failure(ChatError.Unauthenticated())
        return try {
            firestore.collection(COLLECTION_CHATS).document(chatId)
                .collection(COLLECTION_PARTICIPANTS).document(uid)
                .update("blockedOther", blocked)
                .awaitWithTimeout()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.toChatError())
        }
    }

    override suspend fun recall(chatId: String, messageId: String): Result<Unit> = try {
        actionApi.invoke(ChatActionRequest.recall(chatId, messageId))
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.toChatError())
    }

    override suspend fun report(
        chatId: String,
        messageId: String,
        reason: ReportReason,
        detail: String?,
    ): Result<String> = try {
        val response = actionApi.invoke(ChatActionRequest.report(chatId, messageId, reason.name, detail))
        val reportId = response.reportId?.takeIf { it.isNotBlank() }
            ?: return Result.failure(ChatError.Unknown("INVALID_REPORT_RESPONSE"))
        Result.success(reportId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.toChatError())
    }

    companion object {
        const val PAGE_SIZE = 30L
        private const val WHERE_IN_LIMIT = 10
        private const val COLLECTION_CHATS = "chats"
        private const val COLLECTION_PARTICIPANTS = "participants"
        private const val COLLECTION_MESSAGES = "messages"
        private const val COLLECTION_GROUPS = "groups"
        private val VISIBLE_GROUP_STATUSES = listOf("ACTIVE", "LOCKED")
        private const val MAX_CLOCK_SKEW_SANITY_MS = 24L * 60 * 60 * 1000
    }
}

package com.example.booking_app.feature.chat.data

import com.example.booking_app.feature.chat.data.dto.ChatSendRequest
import com.example.booking_app.feature.chat.domain.ChatError
import com.example.booking_app.feature.chat.domain.ChatMediaRepository
import com.example.booking_app.feature.chat.domain.model.OutboxEntry
import com.example.booking_app.feature.chat.domain.model.UploadState
import com.example.booking_app.feature.chat.domain.toChatError
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cầu nối giữa [ChatOutbox] với `chat-send`, `chat-media-url` và Firestore kiểm tra tin tồn tại.
 * Tách interface để `ChatOutboxTest` test hoàn toàn trên JVM không cần Firebase/Supabase thật.
 */
interface ChatOutboxTransport {
    suspend fun send(entry: OutboxEntry): Result<Unit>
    suspend fun uploadImage(chatId: String, messageId: String, localUri: String): Result<String>
    suspend fun messageExistsOnServer(chatId: String, messageId: String): Boolean
    suspend fun discardMedia(chatId: String, messageId: String)
}

@Singleton
class DefaultChatOutboxTransport @Inject constructor(
    private val sendApi: ChatSendApi,
    private val mediaRepository: ChatMediaRepository,
    private val firestore: FirebaseFirestore,
) : ChatOutboxTransport {

    override suspend fun send(entry: OutboxEntry): Result<Unit> = try {
        sendApi.invoke(
            ChatSendRequest(
                chatId = entry.chatId,
                messageId = entry.messageId,
                text = entry.text,
                imagePath = entry.imagePath,
            ),
        )
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.toChatError())
    }

    override suspend fun uploadImage(chatId: String, messageId: String, localUri: String): Result<String> = try {
        when (val finalState = mediaRepository.upload(chatId, messageId, localUri).last()) {
            is UploadState.Uploaded -> Result.success(finalState.path)
            is UploadState.Failed -> Result.failure(finalState.error)
            UploadState.Uploading -> Result.failure(ChatError.Unknown("UPLOAD_INCOMPLETE"))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.toChatError())
    }

    override suspend fun messageExistsOnServer(chatId: String, messageId: String): Boolean = try {
        firestore.collection("chats").document(chatId)
            .collection("messages").document(messageId)
            .get(Source.SERVER)
            .await()
            .exists()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    override suspend fun discardMedia(chatId: String, messageId: String) {
        runCatching { mediaRepository.discard(chatId, messageId) }
    }
}

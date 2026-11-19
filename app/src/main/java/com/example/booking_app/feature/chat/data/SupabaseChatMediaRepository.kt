package com.example.booking_app.feature.chat.data

import com.example.booking_app.core.image.ImageCompressor
import com.example.booking_app.core.image.ImageTooLargeException
import com.example.booking_app.core.image.ImageUnreadableException
import com.example.booking_app.core.network.toAppError
import com.example.booking_app.feature.chat.data.dto.ChatMediaRequest
import com.example.booking_app.feature.chat.data.dto.toSignedUpload
import com.example.booking_app.feature.chat.data.dto.toSignedUrls
import com.example.booking_app.feature.chat.domain.ChatError
import com.example.booking_app.feature.chat.domain.ChatMediaRepository
import com.example.booking_app.feature.chat.domain.model.ChatSignedUrls
import com.example.booking_app.feature.chat.domain.model.UploadState
import com.example.booking_app.feature.chat.domain.toChatError
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kho ảnh riêng tư của chat trên Supabase Storage bucket `chat-media` (T045, research R8).
 *
 * Luồng tải lên: nén JPEG bằng [ImageCompressor] (≤ 1600px, ≤ 2 MB) → xin signed upload URL qua
 * Edge Function `chat-media-url` → PUT lên `chat-media` → phát [UploadState.Uploaded].
 */
@Singleton
class SupabaseChatMediaRepository @Inject constructor(
    private val imageCompressor: ImageCompressor,
    private val supabase: SupabaseClient,
    private val mediaApi: ChatMediaUrlApi,
) : ChatMediaRepository {

    override fun upload(chatId: String, messageId: String, uri: String): Flow<UploadState> = flow {
        emit(UploadState.Uploading)

        val jpeg = try {
            imageCompressor.compress(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (_: ImageTooLargeException) {
            emit(UploadState.Failed(ChatError.ImageTooLarge()))
            return@flow
        } catch (_: ImageUnreadableException) {
            emit(UploadState.Failed(ChatError.ImageUnreadable()))
            return@flow
        } catch (_: Exception) {
            emit(UploadState.Failed(ChatError.ImageUnreadable()))
            return@flow
        }

        val target = try {
            val response = mediaApi.invoke(
                ChatMediaRequest.upload(
                    chatId = chatId,
                    messageId = messageId,
                    contentType = CONTENT_TYPE_JPEG,
                    size = jpeg.size.toLong(),
                ),
            )
            response.toSignedUpload() ?: run {
                emit(UploadState.Failed(ChatError.Unknown("INVALID_UPLOAD_RESPONSE")))
                return@flow
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(UploadState.Failed(e.toChatError()))
            return@flow
        }

        try {
            supabase.storage.from(BUCKET_CHAT_MEDIA).uploadToSignedUrl(target.path, target.token, jpeg) {
                upsert = true
                contentType = ContentType.Image.JPEG
            }
            emit(UploadState.Uploaded(target.path))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(UploadState.Failed(e.toAppError().toChatError()))
        }
    }

    override suspend fun signedUrls(paths: List<String>): Result<ChatSignedUrls> {
        if (paths.isEmpty()) return Result.success(ChatSignedUrls(emptyMap(), null))
        return try {
            val response = mediaApi.invoke(ChatMediaRequest.download(paths))
            Result.success(response.toSignedUrls())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.toChatError())
        }
    }

    override suspend fun discard(chatId: String, messageId: String): Result<Unit> = try {
        mediaApi.invoke(ChatMediaRequest.discard(chatId, messageId))
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.toChatError())
    }

    private companion object {
        const val BUCKET_CHAT_MEDIA = "chat-media"
        const val CONTENT_TYPE_JPEG = "image/jpeg"
    }
}

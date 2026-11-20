package com.example.booking_app.feature.chat.data

import com.example.booking_app.core.image.ImageCompressionRules
import com.example.booking_app.core.image.ImageCompressor
import com.example.booking_app.core.image.ImageTooLargeException
import com.example.booking_app.core.image.ImageUnreadableException
import com.example.booking_app.core.network.DEFAULT_TIMEOUT_MS
import com.example.booking_app.core.network.toAppError
import com.example.booking_app.feature.chat.data.dto.ChatMediaRequest
import com.example.booking_app.feature.chat.data.dto.toSignedUpload
import com.example.booking_app.feature.chat.data.dto.toSignedUrls
import com.example.booking_app.feature.chat.domain.ChatError
import com.example.booking_app.feature.chat.domain.ChatLocalMediaStore
import com.example.booking_app.feature.chat.domain.ChatMediaRepository
import com.example.booking_app.feature.chat.domain.model.ChatSignedUrls
import com.example.booking_app.feature.chat.domain.model.UploadState
import com.example.booking_app.feature.chat.domain.toChatError
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/** Giao diện mỏng bọc `storage.from(bucket).uploadToSignedUrl(...)` để unit test JVM trực tiếp. */
interface ChatStorageUploader {
    suspend fun uploadToSignedUrl(bucket: String, path: String, token: String, bytes: ByteArray)
}

@Singleton
class SupabaseChatStorageUploader @Inject constructor(
    private val supabase: SupabaseClient,
) : ChatStorageUploader {
    override suspend fun uploadToSignedUrl(bucket: String, path: String, token: String, bytes: ByteArray) {
        supabase.storage.from(bucket).uploadToSignedUrl(path, token, bytes) {
            upsert = true
            contentType = ContentType.Image.JPEG
        }
    }
}

/**
 * Kho ảnh riêng tư của chat trên Supabase Storage bucket `chat-media` (T045, research R8).
 *
 * Luồng tải lên: dùng bản JPEG đã nén sẵn từ [ChatLocalMediaStore] (hoặc nén bằng [ImageCompressor]
 * nếu chưa nén) → xin signed upload URL qua Edge Function `chat-media-url` → PUT lên `chat-media`
 * → phát [UploadState.Uploaded].
 */
@Singleton
class SupabaseChatMediaRepository @Inject constructor(
    private val imageCompressor: ImageCompressor,
    private val localMediaStore: ChatLocalMediaStore,
    private val storageUploader: ChatStorageUploader,
    private val mediaApi: ChatMediaUrlApi,
) : ChatMediaRepository {

    override fun upload(chatId: String, messageId: String, uri: String): Flow<UploadState> = flow {
        emit(UploadState.Uploading)

        val jpeg = try {
            val precompressed = localMediaStore.readPrecompressed(uri)
            if (precompressed != null && !ImageCompressionRules.isTooLarge(precompressed.size)) {
                precompressed
            } else {
                imageCompressor.compress(uri)
            }
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
            withTimeout(DEFAULT_TIMEOUT_MS) {
                storageUploader.uploadToSignedUrl(BUCKET_CHAT_MEDIA, target.path, target.token, jpeg)
            }
            emit(UploadState.Uploaded(target.path))
        } catch (_: TimeoutCancellationException) {
            emit(UploadState.Failed(ChatError.Network()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Lỗi deserialize của supabase-kt 3.6.0 (BucketApiImpl.kt:311-312): yêu cầu trường "Id",
            // trong khi Supabase Storage trả `{ "Key": "..." }` sau khi PUT thành công.
            if (e.message?.contains("Expected an id", ignoreCase = true) == true) {
                emit(UploadState.Uploaded(target.path))
            } else {
                emit(UploadState.Failed(e.toAppError().toChatError()))
            }
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

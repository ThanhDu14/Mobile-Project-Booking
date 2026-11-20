package com.example.booking_app.feature.community.data

import com.example.booking_app.core.image.ImageCompressor
import com.example.booking_app.core.image.ImageTooLargeException
import com.example.booking_app.core.image.ImageUnreadableException
import com.example.booking_app.core.network.toAppError
import com.example.booking_app.feature.community.data.dto.GroupMediaRequest
import com.example.booking_app.feature.community.data.dto.UploadFileDto
import com.example.booking_app.feature.community.data.dto.toDomain
import com.example.booking_app.feature.community.data.dto.toSignedUrls
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.GroupMediaRepository
import com.example.booking_app.feature.community.domain.model.DraftPhoto
import com.example.booking_app.feature.community.domain.model.SignedUpload
import com.example.booking_app.feature.community.domain.model.SignedUrls
import com.example.booking_app.feature.community.domain.model.UploadFile
import com.example.booking_app.feature.community.domain.toCommunityError
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Ảnh của nhóm (T025 + T047): ảnh đại diện ở bucket public `group-avatars`, ảnh bài đăng ở bucket private
 * `group-media`. Ảnh được nén bằng [ImageCompressor] rồi tải thẳng lên signed upload URL (`uploadToSignedUrl`).
 */
class SupabaseGroupMediaRepository @Inject constructor(
    private val imageCompressor: ImageCompressor,
    private val supabase: SupabaseClient,
    private val mediaApi: GroupMediaApi,
) : GroupMediaRepository {

    override suspend fun uploadAvatar(localUri: String, target: SignedUpload): Result<Unit> {
        val jpeg = try {
            imageCompressor.compress(localUri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ImageTooLargeException) {
            return Result.failure(CommunityError.ImageTooLarge())
        } catch (e: ImageUnreadableException) {
            return Result.failure(CommunityError.ImageUnreadable())
        } catch (e: Exception) {
            return Result.failure(CommunityError.ImageUnreadable())
        }
        return try {
            uploadJpeg(BUCKET_AVATARS, target, jpeg)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.toAppError().toCommunityError())
        }
    }

    override suspend fun requestUploads(
        groupId: String,
        postId: String,
        files: List<UploadFile>,
    ): Result<List<SignedUpload>> {
        if (files.isEmpty()) return Result.success(emptyList())
        return try {
            val dtoFiles = files.map { UploadFileDto(it.index, it.contentType, it.size) }
            val response = mediaApi.invoke(GroupMediaRequest.upload(groupId, postId, dtoFiles))
            Result.success(response.uploads.map { it.toDomain() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.toCommunityError())
        }
    }

    override fun upload(localUri: String, target: SignedUpload): Flow<DraftPhoto> = flow {
        emit(DraftPhoto(localUri = localUri, remotePath = null, state = DraftPhoto.State.UPLOADING))
        val jpeg = try {
            imageCompressor.compress(localUri)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emit(DraftPhoto(localUri = localUri, remotePath = null, state = DraftPhoto.State.FAILED))
            return@flow
        }
        emitAll(uploadCompressed(localUri, jpeg, target))
    }

    override fun uploadCompressed(localUri: String, jpeg: ByteArray, target: SignedUpload): Flow<DraftPhoto> = flow {
        emit(DraftPhoto(localUri = localUri, remotePath = null, state = DraftPhoto.State.UPLOADING))
        try {
            uploadJpeg(BUCKET_MEDIA, target, jpeg)
            emit(DraftPhoto(localUri = localUri, remotePath = target.path, state = DraftPhoto.State.UPLOADED))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emit(DraftPhoto(localUri = localUri, remotePath = null, state = DraftPhoto.State.FAILED))
        }
    }

    private suspend fun uploadJpeg(bucket: String, target: SignedUpload, jpeg: ByteArray) {
        try {
            supabase.storage.from(bucket).uploadToSignedUrl(target.path, target.token, jpeg) {
                upsert = true
                contentType = ContentType.Image.JPEG
            }
        } catch (e: Exception) {
            // supabase-kt 3.6.0 đòi trường "Id" trong phản hồi, Storage trả `{ "Key": ... }` dù PUT đã thành công
            // (cùng cách xử lý với OwnerVenuePhotoApi).
            if (e.message?.contains("Expected an id", ignoreCase = true) != true) throw e
        }
    }

    override suspend fun signedUrls(paths: List<String>): Result<SignedUrls> {
        if (paths.isEmpty()) return Result.success(SignedUrls(emptyMap(), null))
        return try {
            val response = mediaApi.invoke(GroupMediaRequest.download(paths))
            Result.success(response.toSignedUrls())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.toCommunityError())
        }
    }

    override suspend fun discard(groupId: String, postId: String): Result<Unit> = try {
        mediaApi.invoke(GroupMediaRequest.discard(groupId, postId))
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.toCommunityError())
    }

    private companion object {
        const val BUCKET_AVATARS = "group-avatars"
        const val BUCKET_MEDIA = "group-media"
    }
}

package com.example.booking_app.feature.community.domain.usecase

import com.example.booking_app.core.image.ImageCompressionRules
import com.example.booking_app.core.image.ImageCompressor
import com.example.booking_app.feature.community.domain.GroupMediaRepository
import com.example.booking_app.feature.community.domain.model.DraftPhoto
import com.example.booking_app.feature.community.domain.model.UploadFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * T048: nén ảnh trước bằng [ImageCompressor], sau đó xin signed upload URL với `size` thật rồi tải mảng JPEG
 * đã nén lên `group-media` (không nén lại lần hai). Nén lỗi hoặc > 2 MB → `FAILED` mà không gọi mạng.
 */
class UploadPostPhotos @Inject constructor(
    private val media: GroupMediaRepository,
    private val imageCompressor: ImageCompressor,
) {
    /** Nén, xin đích tải với `size` thật và tải một ảnh ở vị trí [index] (`0..3`) của bài [postId]. */
    fun uploadOne(groupId: String, postId: String, index: Int, localUri: String): Flow<DraftPhoto> = flow {
        emit(DraftPhoto(localUri = localUri, remotePath = null, state = DraftPhoto.State.UPLOADING))
        val jpeg = try {
            imageCompressor.compress(localUri)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emit(DraftPhoto(localUri = localUri, remotePath = null, state = DraftPhoto.State.FAILED))
            return@flow
        }
        if (jpeg.isEmpty() || ImageCompressionRules.isTooLarge(jpeg.size)) {
            emit(DraftPhoto(localUri = localUri, remotePath = null, state = DraftPhoto.State.FAILED))
            return@flow
        }
        val target = media.requestUploads(
            groupId = groupId,
            postId = postId,
            files = listOf(UploadFile(index = index, contentType = JPEG_MIME, size = jpeg.size.toLong())),
        ).getOrNull()?.firstOrNull()
        if (target == null) {
            emit(DraftPhoto(localUri = localUri, remotePath = null, state = DraftPhoto.State.FAILED))
            return@flow
        }
        emitAll(media.uploadCompressed(localUri, jpeg, target))
    }

    /** Hủy bản nháp: xóa ảnh đã tải khi bài chưa được tạo. */
    suspend fun discard(groupId: String, postId: String): Result<Unit> = media.discard(groupId, postId)

    companion object {
        const val JPEG_MIME = "image/jpeg"
    }
}

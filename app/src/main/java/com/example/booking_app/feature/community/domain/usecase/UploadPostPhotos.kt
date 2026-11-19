package com.example.booking_app.feature.community.domain.usecase

import com.example.booking_app.feature.community.domain.GroupMediaRepository
import com.example.booking_app.feature.community.domain.model.DraftPhoto
import com.example.booking_app.feature.community.domain.model.UploadFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * T048: xin signed upload URL cho ảnh bài đăng và nén/tải lên `group-media`.
 * Phát `UPLOADING` → `UPLOADED` (kèm `remotePath`) hoặc `FAILED`; không ném lỗi.
 */
class UploadPostPhotos @Inject constructor(
    private val media: GroupMediaRepository,
) {
    /** Xin đích tải và tải một ảnh ở vị trí [index] (`0..3`) của bài [postId]. */
    fun uploadOne(groupId: String, postId: String, index: Int, localUri: String): Flow<DraftPhoto> = flow {
        emit(DraftPhoto(localUri = localUri, remotePath = null, state = DraftPhoto.State.UPLOADING))
        val target = media.requestUploads(
            groupId = groupId,
            postId = postId,
            files = listOf(UploadFile(index = index, contentType = JPEG_MIME, size = DEFAULT_SIZE_BYTES)),
        ).getOrNull()?.firstOrNull()
        if (target == null) {
            emit(DraftPhoto(localUri = localUri, remotePath = null, state = DraftPhoto.State.FAILED))
            return@flow
        }
        emitAll(media.upload(localUri, target))
    }

    /** Hủy bản nháp: xóa ảnh đã tải khi bài chưa được tạo. */
    suspend fun discard(groupId: String, postId: String): Result<Unit> = media.discard(groupId, postId)

    companion object {
        const val JPEG_MIME = "image/jpeg"
        const val DEFAULT_SIZE_BYTES = 1L
    }
}

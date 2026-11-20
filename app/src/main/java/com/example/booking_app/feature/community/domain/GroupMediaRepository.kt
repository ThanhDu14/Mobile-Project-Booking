package com.example.booking_app.feature.community.domain

import com.example.booking_app.feature.community.domain.model.DraftPhoto
import com.example.booking_app.feature.community.domain.model.SignedUpload
import com.example.booking_app.feature.community.domain.model.SignedUrls
import com.example.booking_app.feature.community.domain.model.UploadFile
import kotlinx.coroutines.flow.Flow

/**
 * Ảnh nhóm (T018): `group-media-url` cấp signed URL; ảnh được nén bằng `core/image/ImageCompressor` rồi PUT thẳng lên
 * Storage. Ảnh bài đăng ở bucket private `group-media`, ảnh đại diện ở bucket public `group-avatars`.
 */
interface GroupMediaRepository {
    /** Xin đích tải lên cho các ảnh của bài đang soạn [postId] (tối đa 4); kết quả cùng thứ tự [files]. */
    suspend fun requestUploads(groupId: String, postId: String, files: List<UploadFile>): Result<List<SignedUpload>>

    /**
     * Nén [localUri] rồi tải lên [target]. Phát `UPLOADING`, sau đó `UPLOADED` (kèm `remotePath = target.path`) hoặc
     * `FAILED`; không ném lỗi. Nhận cả [SignedUpload] thay vì chỉ `signedUrl` để biết `path` điền vào `DraftPhoto`.
     */
    fun upload(localUri: String, target: SignedUpload): Flow<DraftPhoto>

    /**
     * Tải trực tiếp mảng [jpeg] đã nén trước lên [target] (không nén lại lần hai).
     * Phát `UPLOADING`, sau đó `UPLOADED` (kèm `remotePath = target.path`) hoặc `FAILED`; không ném lỗi.
     */
    fun uploadCompressed(localUri: String, jpeg: ByteArray, target: SignedUpload): Flow<DraftPhoto>

    /** URL xem ảnh cho [paths] (tối đa 40 mỗi lần); path của nhóm mình không là thành viên bị bỏ qua. */
    suspend fun signedUrls(paths: List<String>): Result<SignedUrls>

    /** Xóa ảnh của bài đang soạn khi người dùng bỏ bài (chỉ khi chưa có document `posts/{postId}`). */
    suspend fun discard(groupId: String, postId: String): Result<Unit>

    /** Nén [localUri] rồi tải lên [target] do `group-manage` cấp; sau đó ViewModel gọi `update(avatar = CONFIRM)`. */
    suspend fun uploadAvatar(localUri: String, target: SignedUpload): Result<Unit>
}

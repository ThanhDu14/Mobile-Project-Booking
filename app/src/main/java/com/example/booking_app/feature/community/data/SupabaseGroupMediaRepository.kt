package com.example.booking_app.feature.community.data

import com.example.booking_app.core.image.ImageCompressor
import com.example.booking_app.core.image.ImageTooLargeException
import com.example.booking_app.core.image.ImageUnreadableException
import com.example.booking_app.core.network.toAppError
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.GroupMediaRepository
import com.example.booking_app.feature.community.domain.model.DraftPhoto
import com.example.booking_app.feature.community.domain.model.SignedUpload
import com.example.booking_app.feature.community.domain.model.SignedUrls
import com.example.booking_app.feature.community.domain.model.UploadFile
import com.example.booking_app.feature.community.domain.toCommunityError
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Ảnh của nhóm (T025, US1: chỉ ảnh đại diện). Ảnh được nén bằng [ImageCompressor] rồi tải thẳng lên signed upload
 * URL do `group-manage` cấp (`uploadToSignedUrl`, token nằm trong [SignedUpload.token]). Phần ảnh bài đăng
 * (`requestUploads`, `upload`, `signedUrls`, `discard`) làm ở US3, hiện trả lỗi `NOT_IMPLEMENTED`.
 */
class SupabaseGroupMediaRepository @Inject constructor(
    private val imageCompressor: ImageCompressor,
    private val supabase: SupabaseClient,
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
            supabase.storage.from(BUCKET_AVATARS).uploadToSignedUrl(target.path, target.token, jpeg)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.toAppError().toCommunityError())
        }
    }

    // TODO US3 (T038 trở đi): ảnh bài đăng ở bucket private `group-media`.
    override suspend fun requestUploads(groupId: String, postId: String, files: List<UploadFile>): Result<List<SignedUpload>> =
        notImplemented()

    override fun upload(localUri: String, target: SignedUpload): Flow<DraftPhoto> =
        flowOf(DraftPhoto(localUri = localUri, state = DraftPhoto.State.FAILED))

    override suspend fun signedUrls(paths: List<String>): Result<SignedUrls> = notImplemented()

    override suspend fun discard(groupId: String, postId: String): Result<Unit> = notImplemented()

    private fun <T> notImplemented(): Result<T> = Result.failure(CommunityError.Unknown("NOT_IMPLEMENTED"))

    private companion object {
        const val BUCKET_AVATARS = "group-avatars"
    }
}

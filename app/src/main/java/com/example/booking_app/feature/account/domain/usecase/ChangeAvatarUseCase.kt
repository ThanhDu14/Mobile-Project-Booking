package com.example.booking_app.feature.account.domain.usecase

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.account.domain.AccountRepository
import com.example.booking_app.feature.account.domain.AvatarRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * Đổi ảnh đại diện theo thứ tự của research R3 để không có trạng thái nửa vời:
 * 1. tải object mới `{uid}/avatar_{ms}.jpg`; lỗi thì ảnh cũ giữ nguyên;
 * 2. ghi `avatarPath`; lỗi thì xóa object vừa tải (bỏ qua lỗi) rồi báo lỗi;
 * 3. xóa object cũ (bỏ qua lỗi).
 */
class ChangeAvatarUseCase @Inject constructor(
    private val avatarRepository: AvatarRepository,
    private val accountRepository: AccountRepository,
    private val sessionManager: SessionManager,
    private val clock: AppClock,
) {
    /** Trả đường dẫn mới. */
    suspend operator fun invoke(jpeg: ByteArray, oldPath: String?): String {
        val uid = sessionManager.currentUid() ?: throw AppError.NotSignedIn
        val path = "$uid/avatar_${clock.now()}.jpg"
        avatarRepository.upload(path, jpeg)
        try {
            accountRepository.setAvatarPath(path)
        } catch (e: Exception) {
            if (e !is CancellationException) avatarRepository.deleteQuietly(path)
            throw e
        }
        if (oldPath != null && oldPath != path) avatarRepository.deleteQuietly(oldPath)
        return path
    }
}

/** Xóa ảnh (FR-003): ghi `avatarPath = null` rồi xóa object (bỏ qua lỗi xóa). */
class RemoveAvatarUseCase @Inject constructor(
    private val avatarRepository: AvatarRepository,
    private val accountRepository: AccountRepository,
) {
    suspend operator fun invoke(oldPath: String?) {
        accountRepository.setAvatarPath(null)
        if (oldPath != null) avatarRepository.deleteQuietly(oldPath)
    }
}

/** Object mồ côi (nếu có) nằm trong thư mục `{uid}/` và bị xóa khi xóa tài khoản (research R11). */
internal suspend fun AvatarRepository.deleteQuietly(path: String) {
    try {
        delete(path)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // Bỏ qua theo research R3.
    }
}

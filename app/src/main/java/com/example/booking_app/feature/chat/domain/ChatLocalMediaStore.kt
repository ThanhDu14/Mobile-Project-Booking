package com.example.booking_app.feature.chat.domain

/**
 * Lưu và dọn các file ảnh tạm nội bộ của chat trong `cacheDir/chat/` (US4, review Argon #2):
 * - Lưu ảnh đã nén sẵn (`outbox_<messageId>.jpg`) để không mất quyền đọc URI Photo Picker khi mở
 *   lại app lúc offline và tránh nén cùng một ảnh 2 lần.
 * - Dọn file tạm sau khi gửi xong, xóa tin lỗi hoặc hủy chụp ảnh.
 */
interface ChatLocalMediaStore {
    suspend fun saveCompressed(messageId: String, jpegBytes: ByteArray): String
    suspend fun readPrecompressed(uri: String): ByteArray?
    fun deleteIfOwned(uri: String?)
}

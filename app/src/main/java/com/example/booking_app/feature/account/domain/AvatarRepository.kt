package com.example.booking_app.feature.account.domain

/** Object trong bucket `avatars` (contracts/storage-avatars.md §3). Lỗi mạng là `AccountError.Network`. */
interface AvatarRepository {
    /** Tải lên object mới (`upsert = false`). */
    suspend fun upload(path: String, jpeg: ByteArray)

    suspend fun delete(path: String)
}

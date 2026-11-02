package com.example.booking_app.feature.account.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import javax.inject.Inject

/** Bucket `avatars`. Token là Firebase ID token do `SupabaseModule` (010) cấp. */
class SupabaseAvatarDataSource @Inject constructor(
    private val client: SupabaseClient,
) {
    private val storage get() = client.storage

    suspend fun upload(path: String, jpeg: ByteArray) {
        storage.from(BUCKET).upload(path, jpeg) {
            upsert = false
            contentType = ContentType.Image.JPEG
        }
    }

    suspend fun delete(path: String) {
        storage.from(BUCKET).delete(path)
    }

    private companion object {
        const val BUCKET = "avatars"
    }
}

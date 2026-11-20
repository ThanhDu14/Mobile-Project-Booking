package com.example.booking_app.feature.chat.data

import android.content.Context
import android.net.Uri
import com.example.booking_app.core.di.IoDispatcher
import com.example.booking_app.core.image.ImageCompressionRules
import com.example.booking_app.feature.chat.domain.ChatLocalMediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Cài đặt [ChatLocalMediaStore] lưu ảnh nén sẵn và dọn ảnh chụp tạm trong `cacheDir/chat/`. */
@Singleton
class AndroidChatLocalMediaStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ChatLocalMediaStore {

    private fun chatDir(): File = File(context.cacheDir, CHAT_CACHE_DIR).apply { mkdirs() }

    override suspend fun saveCompressed(messageId: String, jpegBytes: ByteArray): String = withContext(ioDispatcher) {
        val safeId = messageId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(chatDir(), "${OUTBOX_PREFIX}${safeId}.jpg")
        file.writeBytes(jpegBytes)
        file.toURI().toString()
    }

    override suspend fun readPrecompressed(uri: String): ByteArray? = withContext(ioDispatcher) {
        val file = resolveOwnedFile(uri) ?: return@withContext null
        if (!file.name.startsWith(OUTBOX_PREFIX) || !file.isFile) return@withContext null
        val len = file.length()
        if (len <= 0L || len > ImageCompressionRules.MAX_BYTES) return@withContext null
        runCatching { file.readBytes() }.getOrNull()
    }

    override fun deleteIfOwned(uri: String?) {
        if (uri.isNullOrBlank()) return
        runCatching {
            val file = resolveOwnedFile(uri) ?: return
            if (file.isFile) file.delete()
        }
    }

    private fun resolveOwnedFile(uriString: String): File? {
        val baseDir = File(context.cacheDir, CHAT_CACHE_DIR)
        val baseCanonical = runCatching { baseDir.canonicalPath }.getOrNull() ?: return null
        val parsed = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        val candidate = when (parsed.scheme) {
            "file" -> parsed.path?.let(::File)
            "content" -> {
                val expectedAuthority = "${context.packageName}.fileprovider"
                val name = parsed.lastPathSegment
                if (parsed.authority == expectedAuthority && !name.isNullOrBlank() && !name.contains('/') && !name.contains("..")) {
                    File(baseDir, name)
                } else {
                    null
                }
            }
            else -> null
        } ?: return null

        val candidateCanonical = runCatching { candidate.canonicalPath }.getOrNull() ?: return null
        return if (candidateCanonical.startsWith(baseCanonical + File.separator)) candidate else null
    }

    companion object {
        const val CHAT_CACHE_DIR = "chat"
        const val OUTBOX_PREFIX = "outbox_"
    }
}

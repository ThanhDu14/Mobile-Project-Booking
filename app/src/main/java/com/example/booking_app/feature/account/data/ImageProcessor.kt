package com.example.booking_app.feature.account.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.example.booking_app.core.di.DefaultDispatcher
import com.example.booking_app.feature.account.domain.AvatarImageProcessor
import com.example.booking_app.feature.account.domain.model.AccountError
import com.example.booking_app.feature.account.domain.validation.AvatarImageRule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Đọc ảnh đã cắt, thu về tối đa 512 × 512 px và nén JPEG (research R4). Phần tính toán thuần nằm ở [AvatarImageRule].
 * Ảnh tạm trong `cacheDir/avatar/` (ảnh chụp, ảnh cắt) bị xóa khi xong.
 */
class ImageProcessor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : AvatarImageProcessor {

    override suspend fun process(uri: String): ByteArray = withContext(dispatcher) {
        try {
            val source = uri.toUri()
            AvatarImageRule.mimeError(mimeOf(source))?.let { throw it }
            val bitmap = decode(source)
            compress(bitmap).also { bitmap.recycle() }
        } finally {
            clearTempFiles(context)
        }
    }

    private fun mimeOf(uri: Uri): String? = when (uri.scheme) {
        ContentResolver.SCHEME_FILE ->
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(uri.toString()))
        else -> context.contentResolver.getType(uri)
    }

    private fun decode(uri: Uri): Bitmap = try {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val (width, height) = AvatarImageRule.targetSize(info.size.width, info.size.height)
            decoder.setTargetSize(width, height)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (_: IOException) {
        // Không giải mã được (ảnh hỏng hoặc định dạng giả đuôi) cũng báo "Chỉ hỗ trợ ảnh JPG, PNG, WebP".
        throw AccountError.UnsupportedImage
    }

    /** Giảm chất lượng tới khi ≤ 2 MB; vẫn quá ở mức 50 thì báo `ImageTooLarge`. */
    private fun compress(bitmap: Bitmap): ByteArray {
        for (quality in AvatarImageRule.QUALITY_STEPS) {
            val bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
            if (AvatarImageRule.sizeError(bytes.size) == null) return bytes
        }
        throw AccountError.ImageTooLarge
    }

    companion object {
        /** Thư mục ảnh tạm, khớp `res/xml/file_paths.xml` (`<cache-path name="avatar" path="avatar/"/>`). */
        fun tempDir(context: Context): File = File(context.cacheDir, "avatar").apply { mkdirs() }

        fun clearTempFiles(context: Context) {
            File(context.cacheDir, "avatar").listFiles()?.forEach { it.delete() }
        }
    }
}

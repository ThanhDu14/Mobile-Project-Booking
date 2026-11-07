package com.example.booking_app.core.media

import com.example.booking_app.BuildConfig

/**
 * URL công khai của object trên Supabase Storage (spec 011 R1). Dùng cho ảnh đại diện ở mọi màn hình:
 * `StorageUrls.publicUrl("avatars", path)`; `path == null` thì vẽ chữ cái đầu tên.
 */
object StorageUrls {

    fun publicUrl(bucket: String, path: String): String = publicUrl(BuildConfig.SUPABASE_URL, bucket, path)

    internal fun publicUrl(baseUrl: String, bucket: String, path: String): String {
        val encodedPath = path.trim('/').split('/').joinToString("/") { encodeSegment(it) }
        return "${baseUrl.trimEnd('/')}/storage/v1/object/public/${encodeSegment(bucket)}/$encodedPath"
    }

    /** Mã hóa phần trăm theo RFC 3986, chỉ giữ ký tự không cần mã hóa (không dùng `Uri` để test được trên JVM). */
    private fun encodeSegment(segment: String): String = buildString {
        for (byte in segment.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt() and 0xFF
            val ch = c.toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch in "-._~") {
                append(ch)
            } else {
                append('%').append("%02X".format(c))
            }
        }
    }
}

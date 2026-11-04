package com.example.booking_app.core.media

/**
 * URL ảnh cơ sở (spec 030 research R8). Firestore lưu path trong bucket `venue-photos` của Supabase; dữ liệu demo
 * (scripts/seed) lưu thẳng URL đầy đủ nên giữ nguyên. Dùng chung cho thẻ kết quả (020), bản đồ (021), yêu thích (070),
 * chủ sân (111). Inject qua Hilt (`MediaModule`); test truyền [supabaseUrl] trực tiếp.
 */
class PhotoUrlResolver(private val supabaseUrl: String) {

    fun venuePhoto(path: String?): String? {
        val trimmed = path?.trim()
        if (trimmed.isNullOrEmpty()) return null
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return "${supabaseUrl.trimEnd('/')}/storage/v1/object/public/$BUCKET_VENUE_PHOTOS/${trimmed.trimStart('/')}"
    }

    private companion object {
        const val BUCKET_VENUE_PHOTOS = "venue-photos"
    }
}

package com.example.booking_app.core.media

/**
 * URL ảnh công khai trên Supabase Storage: cơ sở (spec 030 research R8), đánh giá và ảnh đại diện (R3). Firestore lưu path trong bucket `venue-photos` của Supabase; dữ liệu demo
 * (scripts/seed) lưu thẳng URL đầy đủ nên giữ nguyên. Dùng chung cho thẻ kết quả (020), bản đồ (021), yêu thích (070),
 * chủ sân (111). Inject qua Hilt (`MediaModule`); test truyền [supabaseUrl] trực tiếp.
 */
class PhotoUrlResolver(private val supabaseUrl: String) {

    fun venuePhoto(path: String?): String? = publicUrl(BUCKET_VENUE_PHOTOS, path)

    /** Ảnh đánh giá, bucket `review-photos` (`{reviewId}/{tên file}`, đặc tả CSDL §8). */
    fun reviewPhoto(path: String?): String? = publicUrl(BUCKET_REVIEW_PHOTOS, path)

    /** Ảnh đại diện, bucket `avatars` (`{uid}/avatar.jpg`). */
    fun avatar(path: String?): String? = publicUrl(BUCKET_AVATARS, path)

    private fun publicUrl(bucket: String, path: String?): String? {
        val trimmed = path?.trim()
        if (trimmed.isNullOrEmpty()) return null
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return "${supabaseUrl.trimEnd('/')}/storage/v1/object/public/$bucket/${trimmed.trimStart('/')}"
    }

    private companion object {
        const val BUCKET_VENUE_PHOTOS = "venue-photos"
        const val BUCKET_REVIEW_PHOTOS = "review-photos"
        const val BUCKET_AVATARS = "avatars"
    }
}

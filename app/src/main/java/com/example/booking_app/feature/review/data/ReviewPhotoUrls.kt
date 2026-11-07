package com.example.booking_app.feature.review.data

import com.example.booking_app.BuildConfig

/**
 * Firestore chỉ lưu path của ảnh (đặc tả CSDL mục 8); URL công khai ghép ở tầng data.
 * Dữ liệu mẫu (scripts/seed) đang lưu thẳng URL `http(s)`, nên URL được giữ nguyên.
 */
object ReviewPhotoUrls {

    fun reviewPhoto(path: String, baseUrl: String = BuildConfig.SUPABASE_URL): String =
        publicUrl(baseUrl, BUCKET_REVIEW_PHOTOS, path)

    fun venuePhoto(path: String?, baseUrl: String = BuildConfig.SUPABASE_URL): String? =
        path?.takeIf { it.isNotBlank() }?.let { publicUrl(baseUrl, BUCKET_VENUE_PHOTOS, it) }

    private fun publicUrl(baseUrl: String, bucket: String, path: String): String =
        if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            "${baseUrl.trimEnd('/')}/storage/v1/object/public/$bucket/${path.trimStart('/')}"
        }

    private const val BUCKET_REVIEW_PHOTOS = "review-photos"
    private const val BUCKET_VENUE_PHOTOS = "venue-photos"
}

package com.example.booking_app.feature.owner.data

import com.example.booking_app.core.network.FunctionErrors
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.storage.storage
import io.ktor.client.call.body
import io.ktor.http.ContentType
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
private data class VenuePhotoUploadRequest(val action: String, val venueId: String, val contentType: String, val sizeBytes: Int)
@Serializable
private data class PublishVenuePhotosRequest(val action: String, val venueId: String)
@Serializable
private data class PublishedVenuePhotos(val paths: Map<String,String>)

class OwnerVenuePhotoApi @Inject constructor(private val supabase: SupabaseClient, private val errors: FunctionErrors) {
    suspend fun publishLegacy(venueId: String): Map<String,String> = errors.invokeWithAuthRetry {
        supabase.functions.invoke("owner-docs",body=PublishVenuePhotosRequest(action="PUBLISH_VENUE_PHOTOS",venueId=venueId)).body<PublishedVenuePhotos>().paths
    }
    suspend fun upload(venueId: String, mime: String, bytes: ByteArray): String {
        require(mime in setOf("image/jpeg","image/png","image/webp") && bytes.isNotEmpty() && bytes.size <= 2*1024*1024) { "Chọn ảnh JPEG, PNG hoặc WebP tối đa 2 MB" }
        val ticket = errors.invokeWithAuthRetry {
            supabase.functions.invoke("owner-docs",body=VenuePhotoUploadRequest(action="SIGN_VENUE_UPLOAD",venueId=venueId,contentType=mime,sizeBytes=bytes.size)).body<OwnerFileResponse>()
        }
        val path = requireNotNull(ticket.path)
        try {
            supabase.storage.from("venue-photos").uploadToSignedUrl(path,requireNotNull(ticket.token),bytes) { contentType=ContentType.parse(mime) }
        } catch (error: Exception) {
            if (error.message?.contains("Expected an id",true) != true) throw error
        }
        return path
    }
}

package com.example.booking_app.feature.owner.domain

import com.google.firebase.firestore.GeoPoint

/** Applicant-owned fields used by Feature 110. Server-controlled review fields are intentionally absent. */
data class OwnerApplicationDraft(
    val venue: OwnerVenueDraft,
    val representativeName: String,
    val representativePhone: String,
    val documentPaths: List<String>,
)

data class OwnerVenueDraft(
    val name: String,
    val address: String,
    val districtId: String,
    val location: GeoPoint?,
    val phone: String,
    val courtCount: Int?,
    val openTime: String,
    val closeTime: String,
    val photoPaths: List<String>,
)

data class OwnerApplicationFieldError(val field: String, val message: String)

data class OwnerEvidenceFile(val mimeType: String?, val sizeBytes: Long)

/** Pure validation for documented Feature 110 constraints. No undocumented phone/quantity limits are added. */
object OwnerApplicationValidation {
    private val timePattern = Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$")
    // Venue contact may be a landline (as in the approved wireframe); preserve input and validate format only.
    private val phonePattern = Regex("^(?:0\\d{9,10}|\\+84\\d{9,10})$")
    private val allowedEvidenceTypes = setOf("image/jpeg", "image/png", "image/webp")
    private const val MAX_EVIDENCE_BYTES = 2L * 1024 * 1024

    fun validate(draft: OwnerApplicationDraft): List<OwnerApplicationFieldError> = buildList {
        val venue = draft.venue
        required(venue.name, "venueName")
        required(venue.address, "address")
        required(venue.districtId, "districtId")
        val location = venue.location
        if (location == null || location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) {
            add(OwnerApplicationFieldError("location", "Vui lòng chọn vị trí hợp lệ trên bản đồ"))
        }
        phone(venue.phone, "venuePhone")
        if (venue.courtCount == null || venue.courtCount <= 0) {
            add(OwnerApplicationFieldError("courtCount", "Số sân phải là số nguyên dương"))
        }
        validTime(venue.openTime, "openTime")
        validTime(venue.closeTime, "closeTime")
        if (timePattern.matches(venue.openTime) && timePattern.matches(venue.closeTime) && venue.closeTime <= venue.openTime) {
            add(OwnerApplicationFieldError("closeTime", "Giờ đóng cửa phải sau giờ mở cửa"))
        }
        required(draft.representativeName, "representativeName")
        phone(draft.representativePhone, "representativePhone")
        if (venue.photoPaths.none(String::isNotBlank)) {
            add(OwnerApplicationFieldError("photoPaths", "Cần ít nhất một ảnh cơ sở đã tải lên"))
        }
        if (draft.documentPaths.none(String::isNotBlank)) {
            add(OwnerApplicationFieldError("documentPaths", "Cần ít nhất một giấy tờ đã tải lên"))
        }
    }

    fun validateEvidence(file: OwnerEvidenceFile): List<OwnerApplicationFieldError> = buildList {
        if (file.mimeType?.lowercase() !in allowedEvidenceTypes) {
            add(OwnerApplicationFieldError("evidence", "Chỉ hỗ trợ ảnh JPEG, PNG hoặc WebP"))
        }
        if (file.sizeBytes <= 0 || file.sizeBytes > MAX_EVIDENCE_BYTES) {
            add(OwnerApplicationFieldError("evidence", "Mỗi giấy tờ phải có dung lượng tối đa 2 MB"))
        }
    }

    private fun MutableList<OwnerApplicationFieldError>.required(value: String, field: String) {
        if (value.isBlank()) add(OwnerApplicationFieldError(field, "Trường này là bắt buộc"))
    }

    private fun MutableList<OwnerApplicationFieldError>.phone(value: String, field: String) {
        if (!phonePattern.matches(value.filterNot(Char::isWhitespace))) {
            add(OwnerApplicationFieldError(field, "Số điện thoại Việt Nam không hợp lệ"))
        }
    }

    private fun MutableList<OwnerApplicationFieldError>.validTime(value: String, field: String) {
        if (!timePattern.matches(value)) add(OwnerApplicationFieldError(field, "Nhập giờ theo định dạng HH:mm"))
    }
}

package com.example.booking_app.feature.dropin.data.dto

import java.time.Instant

/**
 * Document `dropInSessions/{id}` đã đọc thô (data-model mục 1). Mọi trường đều nullable để dữ liệu
 * thiếu hoặc sai kiểu không làm app crash; [toDomain] quyết định bỏ hay dùng giá trị mặc định.
 */
data class SessionDto(
    val id: String,
    val venueId: String? = null,
    val ownerId: String? = null,
    val districtId: String? = null,
    val courtIds: List<String> = emptyList(),
    /** `yyyyMMdd` theo giờ Việt Nam. */
    val date: String? = null,
    val startAt: Instant? = null,
    val endAt: Instant? = null,
    /** Dự phòng cho buổi cũ chỉ có `startTime`/`endTime` dạng `HH:mm` (bản đầu của `owner-create-session`). */
    val startTime: String? = null,
    val endTime: String? = null,
    val timeSlot: String? = null,
    val venueName: String? = null,
    val venueAddress: String? = null,
    val districtName: String? = null,
    val level: String? = null,
    val capacity: Long? = null,
    val registeredCount: Long? = null,
    val pricePerPerson: Long? = null,
    val status: String? = null,
    val note: String? = null,
) {
    companion object {
        /** Đọc từ `DocumentSnapshot.data`; không ném lỗi với kiểu lạ. */
        fun fromMap(id: String, data: Map<String, Any?>): SessionDto {
            val snapshot = data["venueSnapshot"] as? Map<*, *>
            val courtIds = (data["courtIds"] as? List<*>)?.mapNotNull { it as? String }?.filter { it.isNotBlank() }
                ?.takeIf { it.isNotEmpty() }
                ?: listOfNotNull((data["courtId"] as? String)?.takeIf { it.isNotBlank() })
            return SessionDto(
                id = id,
                venueId = data.string("venueId"),
                ownerId = data.string("ownerId"),
                districtId = data.string("districtId"),
                courtIds = courtIds,
                date = data.string("date"),
                startAt = data["startAt"].asInstant(),
                endAt = data["endAt"].asInstant(),
                startTime = data.string("startTime"),
                endTime = data.string("endTime"),
                timeSlot = data.string("timeSlot"),
                venueName = snapshot?.get("name") as? String,
                venueAddress = snapshot?.get("address") as? String,
                districtName = snapshot?.get("districtName") as? String,
                level = data.string("level"),
                capacity = data["capacity"].asLong(),
                registeredCount = data["registeredCount"].asLong(),
                pricePerPerson = data["pricePerPerson"].asLong(),
                status = data.string("status"),
                note = data.string("note"),
            )
        }
    }
}

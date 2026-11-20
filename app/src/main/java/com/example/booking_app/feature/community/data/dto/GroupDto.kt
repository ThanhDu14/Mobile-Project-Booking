package com.example.booking_app.feature.community.data.dto

import kotlinx.serialization.Serializable
import java.time.Instant

/*
 * Document Firestore của spec 090 đã đọc thô (data-model mục 1–6), cùng cách `SessionDto` của spec 080: đọc bằng
 * `DocumentSnapshot.data` rồi `fromMap`, mọi trường nullable để dữ liệu thiếu hoặc sai kiểu không làm app crash;
 * `toDomain` (Mappers.kt) quyết định bỏ hay dùng giá trị mặc định. Không dùng `toObject` (cần constructor rỗng, che lỗi kiểu).
 */

/** `userSnapshot`/`authorSnapshot`: `displayName`, `avatarPath` chép từ `users` lúc vào nhóm (R18). */
@Serializable
data class UserSnapshotDto(val displayName: String? = null, val avatarPath: String? = null) {
    companion object {
        fun fromAny(raw: Any?): UserSnapshotDto? {
            val map = raw as? Map<*, *> ?: return null
            return UserSnapshotDto(map["displayName"] as? String, map["avatarPath"] as? String)
        }
    }
}

/** `groups/{groupId}`. */
data class GroupDto(
    val id: String,
    val name: String? = null,
    val description: String? = null,
    val avatarPath: String? = null,
    val districtId: String? = null,
    val ownerId: String? = null,
    val visibility: String? = null,
    val memberCount: Long? = null,
    val pendingRequestCount: Long? = null,
    val status: String? = null,
    val createdAt: Instant? = null,
) {
    companion object {
        fun fromMap(id: String, data: Map<String, Any?>) = GroupDto(
            id = id,
            name = data.string("name"),
            description = data.string("description"),
            avatarPath = data.string("avatarPath")?.takeIf { it.isNotBlank() },
            districtId = data.string("districtId"),
            ownerId = data.string("ownerId"),
            visibility = data.string("visibility"),
            memberCount = data["memberCount"].asLong(),
            pendingRequestCount = data["pendingRequestCount"].asLong(),
            status = data.string("status"),
            createdAt = data["createdAt"].asInstant(),
        )
    }
}

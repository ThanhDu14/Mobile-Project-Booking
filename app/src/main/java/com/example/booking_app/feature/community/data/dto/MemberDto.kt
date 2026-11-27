package com.example.booking_app.feature.community.data.dto

import java.time.Instant

/** `groups/{groupId}/members/{uid}`. */
data class MemberDto(
    val userId: String,
    val groupId: String? = null,
    val role: String? = null,
    val snapshot: UserSnapshotDto? = null,
    val joinedAt: Instant? = null,
) {
    companion object {
        /** [id] là ID document (`uid`); dùng khi trường `userId` thiếu. */
        fun fromMap(id: String, data: Map<String, Any?>) = MemberDto(
            userId = data.string("userId") ?: id,
            groupId = data.string("groupId"),
            role = data.string("role"),
            snapshot = UserSnapshotDto.fromAny(data["userSnapshot"]),
            joinedAt = data["joinedAt"].asInstant(),
        )
    }
}

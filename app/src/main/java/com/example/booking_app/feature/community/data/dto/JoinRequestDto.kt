package com.example.booking_app.feature.community.data.dto

import java.time.Instant

/** `groups/{groupId}/joinRequests/{uid}`. */
data class JoinRequestDto(
    val userId: String,
    val snapshot: UserSnapshotDto? = null,
    val wasRemoved: Boolean = false,
    val createdAt: Instant? = null,
) {
    companion object {
        fun fromMap(id: String, data: Map<String, Any?>) = JoinRequestDto(
            userId = data.string("userId") ?: id,
            snapshot = UserSnapshotDto.fromAny(data["userSnapshot"]),
            wasRemoved = data["wasRemoved"] as? Boolean ?: false,
            createdAt = data["createdAt"].asInstant(),
        )
    }
}

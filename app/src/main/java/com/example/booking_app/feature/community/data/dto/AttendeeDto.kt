package com.example.booking_app.feature.community.data.dto

import java.time.Instant

/** `groups/{groupId}/posts/{postId}/attendees/{uid}`. */
data class AttendeeDto(
    val userId: String,
    val groupId: String? = null,
    val postId: String? = null,
    val eventAt: Instant? = null,
    val snapshot: UserSnapshotDto? = null,
    val joinedAt: Instant? = null,
) {
    companion object {
        fun fromMap(id: String, data: Map<String, Any?>) = AttendeeDto(
            userId = data.string("userId") ?: id,
            groupId = data.string("groupId"),
            postId = data.string("postId"),
            eventAt = data["eventAt"].asInstant(),
            snapshot = UserSnapshotDto.fromAny(data["userSnapshot"]),
            joinedAt = data["joinedAt"].asInstant(),
        )
    }
}

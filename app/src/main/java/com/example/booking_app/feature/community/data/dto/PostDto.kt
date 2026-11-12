package com.example.booking_app.feature.community.data.dto

import java.time.Instant

/** `groups/{groupId}/posts/{postId}`. */
data class PostDto(
    val id: String,
    val groupId: String? = null,
    val authorId: String? = null,
    val authorSnapshot: UserSnapshotDto? = null,
    val authorDeleted: Boolean = false,
    val type: String? = null,
    val content: String? = null,
    val photoPaths: List<String> = emptyList(),
    val eventTitle: String? = null,
    val eventAt: Instant? = null,
    val eventLocation: String? = null,
    val expectedCount: Long? = null,
    val attendeeCount: Long? = null,
    val status: String? = null,
    val hiddenBy: String? = null,
    val editedAt: Instant? = null,
    val createdAt: Instant? = null,
) {
    companion object {
        fun fromMap(id: String, data: Map<String, Any?>) = PostDto(
            id = id,
            groupId = data.string("groupId"),
            authorId = data.string("authorId"),
            authorSnapshot = UserSnapshotDto.fromAny(data["authorSnapshot"]),
            authorDeleted = data["authorDeleted"] as? Boolean ?: false,
            type = data.string("type"),
            content = data.string("content"),
            photoPaths = (data["photoPaths"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
            eventTitle = data.string("eventTitle"),
            eventAt = data["eventAt"].asInstant(),
            eventLocation = data.string("eventLocation"),
            expectedCount = data["expectedCount"].asLong(),
            attendeeCount = data["attendeeCount"].asLong(),
            status = data.string("status"),
            hiddenBy = data.string("hiddenBy"),
            editedAt = data["editedAt"].asInstant(),
            createdAt = data["createdAt"].asInstant(),
        )
    }
}

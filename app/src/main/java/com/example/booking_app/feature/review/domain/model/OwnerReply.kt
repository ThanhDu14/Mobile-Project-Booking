package com.example.booking_app.feature.review.domain.model

import java.time.Instant

data class OwnerReply(
    val text: String,
    val repliedAt: Instant,
    val isEdited: Boolean,
)

package com.example.booking_app.feature.notification.domain

import kotlinx.coroutines.flow.Flow

interface NotificationSession {
    val uids: Flow<String?>
}

package com.example.booking_app.feature.notification.domain

interface NotificationRouteResolver {
    /** Null means persisted notification detail; never expose a forbidden source. */
    suspend fun resolve(item: Notification): String?
}

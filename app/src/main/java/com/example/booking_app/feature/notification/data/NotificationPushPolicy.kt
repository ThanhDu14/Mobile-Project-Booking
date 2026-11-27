package com.example.booking_app.feature.notification.data

data class NotificationPush(val recipientUid: String, val notificationId: String)
object NotificationPushPolicy {
    fun parse(data: Map<String, String>, currentUid: String?, allowed: Boolean): NotificationPush? {
        if (!allowed || currentUid == null || data.keys != setOf("recipientUid", "notificationId")) return null
        val uid = data["recipientUid"] ?: return null
        val id = data["notificationId"] ?: return null
        if (uid != currentUid || uid.isBlank() || id.isBlank() || '/' in uid || '/' in id || id.length > 1500) return null
        return NotificationPush(uid, id)
    }
}

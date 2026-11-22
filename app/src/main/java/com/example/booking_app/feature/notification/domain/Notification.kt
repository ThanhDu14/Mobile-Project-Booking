package com.example.booking_app.feature.notification.domain

/** Persisted values match the shared database catalog; unknown values remain displayable. */
enum class NotificationType {
    BOOKING_CONFIRMED, BOOKING_REJECTED, BOOKING_CANCELLED, BOOKING_REMINDER, BOOKING_NEW,
    DROPIN_REGISTERED, DROPIN_SPOT_OPENED, DROPIN_CHANGED, DROPIN_WAITLIST_CLOSED,
    PROMOTION, OWNER_APPROVED, OWNER_REJECTED, OWNER_REVOKED,
    REVIEW_REPLIED, REVIEW_NEW, REVIEW_REMINDER, GROUP_ACTIVITY, CHAT_MESSAGE, SYSTEM;

    companion object {
        fun fromStoredValue(value: String): NotificationType? =
            if (value == "REVIEW_REPLY") REVIEW_REPLIED else entries.firstOrNull { it.name == value }
    }
}

data class Notification(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val data: Map<String, Any?>,
    val isRead: Boolean,
    val createdAtMillis: Long,
)

/** Cursor is opaque to UI so the Firestore query decision does not leak into the domain. */
interface NotificationCursor

data class NotificationPage(
    val items: List<Notification>,
    val nextCursor: NotificationCursor?,
)

interface NotificationRepository {
    suspend fun loadPage(after: NotificationCursor? = null): NotificationPage
    suspend fun markRead(id: String)
    /** Covers the entire inbox, including items outside the currently displayed page. */
    suspend fun markAllRead()
}

object NewAccountNotificationPreferences {
    fun defaults(): Map<NotificationType, Boolean> = NotificationType.entries.associateWith {
        it != NotificationType.PROMOTION
    }

    fun isPushEligible(type: NotificationType, storedValue: Boolean): Boolean =
        type == NotificationType.BOOKING_NEW || storedValue
}

object NotificationPreferences {
    fun resolve(stored: Map<String, Boolean>): Map<NotificationType, Boolean> =
        NotificationType.entries.associateWith { type ->
            type == NotificationType.BOOKING_NEW || (stored[type.name] ?: (type != NotificationType.PROMOTION))
        }

    fun groupState(types: Set<NotificationType>, preferences: Map<NotificationType, Boolean>): GroupState {
        require(types.isNotEmpty())
        val enabled = types.count { preferences.getValue(it) }
        return when (enabled) {
            0 -> GroupState.OFF
            types.size -> GroupState.ON
            else -> GroupState.MIXED
        }
    }
}

enum class GroupState { OFF, ON, MIXED }

interface NotificationPreferencesRepository {
    suspend fun load(): Map<NotificationType, Boolean>
    suspend fun setEnabled(types: Set<NotificationType>, enabled: Boolean)
}

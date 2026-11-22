package com.example.booking_app.feature.notification.data

import com.example.booking_app.core.network.awaitWithTimeout
import com.example.booking_app.feature.notification.domain.NotificationPreferences
import com.example.booking_app.feature.notification.domain.NotificationPreferencesRepository
import com.example.booking_app.feature.notification.domain.NotificationType
import com.example.booking_app.core.session.SessionManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import javax.inject.Inject

class FirestoreNotificationPreferencesRepository @Inject constructor(
    private val session: SessionManager,
    private val firestore: FirebaseFirestore,
) : NotificationPreferencesRepository {
    private fun uid() = checkNotNull(session.currentUid()) { "Authentication required" }

    override suspend fun load(): Map<NotificationType, Boolean> {
        val uid = uid()
        val snapshot = firestore.collection("users").document(uid).get(Source.SERVER).awaitWithTimeout()
        check(uid() == uid) { "Account changed" }
        check(snapshot.exists()) { "Profile unavailable" }
        val stored = (snapshot.get("notificationPrefs") as? Map<*, *>)?.entries
            ?.mapNotNull { (key, value) -> if (key is String && value is Boolean) key to value else null }
            ?.toMap().orEmpty()
        return NotificationPreferences.resolve(stored)
    }

    override suspend fun setEnabled(types: Set<NotificationType>, enabled: Boolean) {
        require(types.isNotEmpty())
        require(enabled || NotificationType.BOOKING_NEW !in types)
        val uid = uid()
        val fields = types.associate { "notificationPrefs.${it.name}" to enabled }.toMutableMap<String, Any>()
        fields["updatedAt"] = FieldValue.serverTimestamp()
        firestore.collection("users").document(uid).update(fields).awaitWithTimeout()
        check(uid() == uid) { "Account changed" }
    }
}

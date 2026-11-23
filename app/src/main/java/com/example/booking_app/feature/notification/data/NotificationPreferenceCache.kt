package com.example.booking_app.feature.notification.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.example.booking_app.core.session.DataStoreSessionPrefs
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.notification.domain.NotificationPreferences
import com.example.booking_app.feature.notification.domain.NotificationType
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

interface NotificationPreferenceCache {
    suspend fun epoch(): Long
    suspend fun read(uid: String, epoch: Long): Map<NotificationType, Boolean>?
    suspend fun write(uid: String, values: Map<NotificationType, Boolean>, epoch: Long)
}

@Singleton
class DataStoreNotificationPreferenceCache @Inject constructor(
    private val store: DataStore<Preferences>,
    private val session: SessionManager,
) : NotificationPreferenceCache {
    private fun prefix(uid: String) = DataStoreSessionPrefs.USER_CACHE_PREFIX + "notification." +
        MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) } + "."
    override suspend fun epoch() = store.data.first()[DataStoreSessionPrefs.USER_CACHE_EPOCH] ?: 0L

    override suspend fun read(uid: String, epoch: Long): Map<NotificationType, Boolean>? {
        val data = store.data.first()
        if (session.currentUid() != uid || (data[DataStoreSessionPrefs.USER_CACHE_EPOCH] ?: 0L) != epoch) return null
        val prefix = prefix(uid)
        if (data[booleanPreferencesKey(prefix + "present")] != true) return null
        return NotificationPreferences.resolve(NotificationType.entries.mapNotNull { type ->
            data[booleanPreferencesKey(prefix + type.name)]?.let { type.name to it }
        }.toMap())
    }

    override suspend fun write(uid: String, values: Map<NotificationType, Boolean>, epoch: Long) {
        store.edit { data ->
            // The epoch prevents a server operation started before sign-out from repopulating cleared data.
            if (session.currentUid() == uid && (data[DataStoreSessionPrefs.USER_CACHE_EPOCH] ?: 0L) == epoch) {
                val prefix = prefix(uid)
                val resolved = NotificationPreferences.resolve(values.mapKeys { it.key.name })
                resolved.forEach { (type, enabled) -> data[booleanPreferencesKey(prefix + type.name)] = enabled }
                data[booleanPreferencesKey(prefix + "present")] = true
            }
        }
    }
}

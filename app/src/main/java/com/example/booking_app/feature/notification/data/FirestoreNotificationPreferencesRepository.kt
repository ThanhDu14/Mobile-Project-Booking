package com.example.booking_app.feature.notification.data

import com.example.booking_app.core.network.awaitWithTimeout
import com.example.booking_app.feature.notification.domain.NotificationPreferences
import com.example.booking_app.feature.notification.domain.NotificationPreferencesRepository
import com.example.booking_app.feature.notification.domain.NotificationType
import com.example.booking_app.feature.notification.domain.NotificationPreferenceSnapshot
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.session.SessionManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import javax.inject.Inject
import javax.inject.Singleton
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class FirestoreNotificationPreferencesRepository @Inject constructor(
    private val session: SessionManager,
    private val firestore: FirebaseFirestore,
    private val cache: NotificationPreferenceCache? = null,
) : NotificationPreferencesRepository {
    private val mutex = Mutex()
    private fun uid() = checkNotNull(session.currentUid()) { "Authentication required" }

    override suspend fun load() = loadSnapshot().values

    private suspend fun cacheEpoch(): Long? = try { cache?.epoch() } catch (_: IOException) { null }
    private suspend fun cacheValues(uid: String, values: Map<NotificationType, Boolean>, epoch: Long?) {
        if (epoch != null) try { cache?.write(uid, values, epoch) } catch (_: IOException) { /* Remote result remains authoritative. */ }
    }
    override suspend fun loadSnapshot(): NotificationPreferenceSnapshot = mutex.withLock {
        val uid = uid()
        val epoch = cacheEpoch()
        val snapshot = try { firestore.collection("users").document(uid).get(Source.SERVER).awaitWithTimeout()
        } catch (error: AppError) {
            check(uid() == uid) { "Account changed" }
            if (error != AppError.Network && error != AppError.Timeout) throw error
            val saved = try { epoch?.let { cache?.read(uid, it) } } catch (_: IOException) { null }
            check(uid() == uid) { "Account changed" }
            return@withLock NotificationPreferenceSnapshot(saved ?: throw error, fromCache = true)
        }
        check(uid() == uid) { "Account changed" }
        check(snapshot.exists()) { "Profile unavailable" }
        val values = resolve(snapshot.get("notificationPrefs"))
        cacheValues(uid, values, epoch)
        check(uid() == uid) { "Account changed" }
        NotificationPreferenceSnapshot(values)
    }

    private fun resolve(raw: Any?): Map<NotificationType, Boolean> {
        val stored = (raw as? Map<*, *>)?.entries
            ?.mapNotNull { (key, value) -> if (key is String && value is Boolean) key to value else null }
            ?.toMap().orEmpty()
        return NotificationPreferences.resolve(stored)
    }

    override suspend fun setEnabled(types: Set<NotificationType>, enabled: Boolean) = mutex.withLock {
        require(types.isNotEmpty())
        require(enabled || NotificationType.BOOKING_NEW !in types)
        val uid = uid()
        val epoch = cacheEpoch()
        val fields = types.associate { "notificationPrefs.${it.name}" to enabled }.toMutableMap<String, Any>()
        fields["updatedAt"] = FieldValue.serverTimestamp()
        val ref = firestore.collection("users").document(uid)
        // Transactions use their own RPCs and can bypass the SDK's disabled watch/write network.
        val serverProfile = ref.get(Source.SERVER).awaitWithTimeout()
        check(uid() == uid) { "Account changed" }
        check(serverProfile.exists()) { "Profile unavailable" }
        val values = firestore.runTransaction { tx ->
            check(uid() == uid) { "Account changed" }
            val profile = tx.get(ref)
            check(profile.exists()) { "Profile unavailable" }
            val updated = resolve(profile.get("notificationPrefs")) + types.associateWith { enabled }
            tx.update(ref, fields)
            updated
        }.awaitWithTimeout()
        check(uid() == uid) { "Account changed" }
        cacheValues(uid, values, epoch)
        check(uid() == uid) { "Account changed" }
    }
}

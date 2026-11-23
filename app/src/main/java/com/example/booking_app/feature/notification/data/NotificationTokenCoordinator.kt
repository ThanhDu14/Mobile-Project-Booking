package com.example.booking_app.feature.notification.data

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import com.example.booking_app.core.di.ApplicationScope
import com.example.booking_app.core.network.awaitWithTimeout
import com.example.booking_app.core.session.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreTokenRegistrationStore @Inject constructor(private val firestore: FirebaseFirestore) : TokenRegistrationStore {
    override suspend fun register(uid: String, token: String, previousHash: String?, stillCurrent: () -> Boolean) {
        mutate(uid, stillCurrent) { old ->
            check(old.all { it is String }) { "Invalid legacy tokens need separate repair" }
            old.filterNot { it is String && previousHash != null && tokenHash(it) == previousHash && it != token }.toSet().plus(token).toList()
        }
    }
    override suspend fun detach(uid: String, hash: String, stillCurrent: () -> Boolean) = mutate(uid, stillCurrent) { old ->
        old.filterNot { it is String && tokenHash(it) == hash }
    }
    private suspend fun mutate(uid: String, current: () -> Boolean, change: (List<*>) -> List<*>) {
        val ref = firestore.collection("users").document(uid)
        firestore.runTransaction { tx ->
            val profile = tx.get(ref)
            check(current() && profile.exists() && profile.getString("accountStatus") == "ACTIVE" && profile.get("deletionRequestedAt") == null)
            val old = profile.get("fcmTokens")?.let { check(it is List<*>); it } as? List<*> ?: emptyList<Any>()
            val next = change(old)
            check(current())
            if (next != old) tx.update(ref, mapOf("fcmTokens" to next, "updatedAt" to FieldValue.serverTimestamp()))
        }.awaitWithTimeout()
    }
}

@Singleton
class NotificationTokenCoordinator @Inject constructor(
    @ApplicationContext context: Context, source: FcmTokenSource, store: FirestoreTokenRegistrationStore,
    prefs: NotificationDevicePrefs, private val session: SessionManager, @ApplicationScope private val scope: CoroutineScope,
) : FcmRegistrationGuard {
    private val app = context.applicationContext as Application
    private val engine = NotificationTokenEngine(source, store, prefs, session::currentUid) {
        NotificationManagerCompat.from(app).areNotificationsEnabled()
    }
    private var started = false
    fun start() {
        if (started) return
        started = true
        scope.launch { session.sessionState.collect { state ->
            when (state) {
                is SessionState.SignedIn -> { engine.signedIn(); requestSync() }
                is SessionState.SignedOut -> engine.signedOut(state.reason == SignOutReason.ACCOUNT_DELETED)
                else -> Unit
            }
        } }
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { requestSync() }
            override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
            override fun onActivityStarted(a: Activity) = Unit
            override fun onActivityPaused(a: Activity) = Unit
            override fun onActivityStopped(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
            override fun onActivityDestroyed(a: Activity) = Unit
        })
        app.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { requestSync() }
        })
    }
    fun requestSync() { scope.launch { engine.sync() } }
    override suspend fun pauseRegistration(uid: String) { engine.beforeSignOut() }
}

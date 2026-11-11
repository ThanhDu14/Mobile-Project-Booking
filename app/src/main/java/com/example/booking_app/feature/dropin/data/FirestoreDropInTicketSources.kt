package com.example.booking_app.feature.dropin.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.dropin.data.dto.RegistrationDto
import com.example.booking_app.feature.dropin.data.dto.SessionDto
import com.example.booking_app.feature.dropin.data.dto.toDomain
import com.example.booking_app.feature.dropin.domain.usecase.CachedDropInTicket
import com.example.booking_app.feature.dropin.domain.usecase.GetVenueLocation
import com.example.booking_app.feature.dropin.domain.usecase.ObserveConnectivity
import com.example.booking_app.feature.dropin.domain.usecase.ReadCachedDropInTicket
import com.example.booking_app.feature.dropin.domain.usecase.VenueLocation
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Source
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * T048: đọc vé từ cache offline của Firestore (`Source.CACHE`, research R7). Chưa có trong cache thì Firestore ném
 * `UNAVAILABLE`; mọi lỗi đều trả `null` để màn hình chờ dữ liệu realtime như cũ.
 */
class FirestoreReadCachedDropInTicket @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val sessionManager: SessionManager,
) : ReadCachedDropInTicket {

    override suspend fun invoke(sessionId: String): CachedDropInTicket? {
        val uid = sessionManager.currentUid() ?: return null
        val registration = readCache {
            val snapshot = firestore.collection(COLLECTION_SESSIONS).document(sessionId)
                .collection(COLLECTION_REGISTRATIONS).document(uid)
                .get(Source.CACHE).await()
            snapshot.takeIf { it.exists() }
                ?.let { RegistrationDto.fromMap(sessionId, it.id, it.data.orEmpty()).toDomain() }
        } ?: return null
        val session = readCache {
            val snapshot = firestore.collection(COLLECTION_SESSIONS).document(sessionId).get(Source.CACHE).await()
            if (!snapshot.exists()) return@readCache null
            val dto = SessionDto.fromMap(snapshot.id, snapshot.data.orEmpty())
            val venueId = dto.venueId
            val venueActive = if (venueId.isNullOrBlank()) true else venueActiveInCache(venueId)
            dto.toDomain(venueActive = venueActive)
        }
        return CachedDropInTicket(registration, session)
    }

    /** Trạng thái cơ sở trong cache; không có thì coi là hoạt động (như [VenueStatusCache]). */
    private suspend fun venueActiveInCache(venueId: String): Boolean = readCache {
        val snapshot = firestore.collection(COLLECTION_VENUES).document(venueId).get(Source.CACHE).await()
        snapshot.takeIf { it.exists() }?.getString(FIELD_STATUS)?.let { it == STATUS_ACTIVE }
    } ?: true

    private suspend fun <T> readCache(block: suspend () -> T?): T? = try {
        withTimeoutOrNull(CACHE_TIMEOUT_MS) { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        /** Đọc cache cục bộ thường vài chục ms; quá mức này thì bỏ để không chặn màn hình. */
        const val CACHE_TIMEOUT_MS = 1_000L
        const val COLLECTION_SESSIONS = "dropInSessions"
        const val COLLECTION_REGISTRATIONS = "registrations"
        const val COLLECTION_VENUES = "venues"
        const val FIELD_STATUS = "status"
        const val STATUS_ACTIVE = "ACTIVE"
    }
}

/** `venues/{venueId}.location` (GeoPoint). Mặc định của Firestore: có mạng đọc server, mất mạng đọc cache. */
class FirestoreGetVenueLocation @Inject constructor(
    private val firestore: FirebaseFirestore,
) : GetVenueLocation {

    override suspend fun invoke(venueId: String): VenueLocation? = try {
        withTimeoutOrNull(TIMEOUT_MS) {
            val snapshot = firestore.collection(COLLECTION_VENUES).document(venueId).get().await()
            (snapshot.get(FIELD_LOCATION) as? GeoPoint)
                ?.takeUnless { it.latitude == 0.0 && it.longitude == 0.0 }
                ?.let { VenueLocation(it.latitude, it.longitude) }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Rules chặn đọc cơ sở không ACTIVE, hoặc lỗi mạng: Fragment chỉ đường theo địa chỉ.
        null
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val COLLECTION_VENUES = "venues"
        const val FIELD_LOCATION = "location"
    }
}

/** Trạng thái mạng qua `ConnectivityManager` (mạng mặc định có `INTERNET`). */
class AndroidObserveConnectivity @Inject constructor(
    @ApplicationContext private val context: Context,
) : ObserveConnectivity {

    override fun invoke(): Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            trySend(true)
            awaitClose { }
            return@callbackFlow
        }
        trySend(manager.isOnline())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun ConnectivityManager.isOnline(): Boolean {
        val network = activeNetwork ?: return false
        return getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}

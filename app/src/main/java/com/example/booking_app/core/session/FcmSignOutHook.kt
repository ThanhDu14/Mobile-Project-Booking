package com.example.booking_app.core.session

import com.example.booking_app.core.network.awaitWithTimeout
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/** Token FCM của máy này. Tách khỏi `FirebaseMessaging` để test được. */
interface FcmTokenSource {
    suspend fun token(): String
    suspend fun deleteToken()
}

/** Gỡ một token khỏi `users/{uid}.fcmTokens`. */
interface FcmTokenStore {
    suspend fun removeToken(uid: String, token: String)
}

/** Feature-owned registration pauses before the existing token removal hook. */
interface FcmRegistrationGuard { suspend fun pauseRegistration(uid: String) }

/**
 * Cố gắng gỡ đăng ký push của máy này khi đăng xuất (spec 011 FR-007, research R5; 100 T1/P1):
 * gỡ token khỏi `users/{uid}` (Rules P15) rồi xóa token trên máy. Mọi bước bỏ qua lỗi: mất mạng vẫn xóa token
 * trên máy, token cũ còn trong `fcmTokens` sẽ bị delivery worker gỡ khi FCM trả `UNREGISTERED`.
 * Detach/rotation có thể thất bại; data cũ vẫn có thể tới máy. Feature 100 kiểm UID/quyền OS trước hiển thị.
 */
class FcmSignOutHook @Inject constructor(
    private val tokenSource: FcmTokenSource,
    private val tokenStore: FcmTokenStore,
    private val registrationGuard: dagger.Lazy<FcmRegistrationGuard>? = null,
) : SignOutHook {

    override suspend fun beforeSignOut(uid: String) {
        ignoringErrors { registrationGuard?.get()?.pauseRegistration(uid) }
        val token = ignoringErrors { tokenSource.token() }
        if (token != null) ignoringErrors { tokenStore.removeToken(uid, token) }
        ignoringErrors { tokenSource.deleteToken() }
    }

    private suspend fun <T> ignoringErrors(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

class FirebaseFcmTokenSource @Inject constructor() : FcmTokenSource {
    override suspend fun token(): String = FirebaseMessaging.getInstance().token.awaitWithTimeout()

    override suspend fun deleteToken() {
        FirebaseMessaging.getInstance().deleteToken().awaitWithTimeout()
    }
}

class FirestoreFcmTokenStore @Inject constructor(
    private val firestore: FirebaseFirestore,
) : FcmTokenStore {
    override suspend fun removeToken(uid: String, token: String) {
        firestore.collection("users").document(uid)
            .update(mapOf("fcmTokens" to FieldValue.arrayRemove(token), "updatedAt" to FieldValue.serverTimestamp()))
            .awaitWithTimeout()
    }
}

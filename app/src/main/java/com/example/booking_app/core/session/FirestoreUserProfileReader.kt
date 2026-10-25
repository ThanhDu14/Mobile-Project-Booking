package com.example.booking_app.core.session

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreUserProfileReader @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authSource: SessionAuthSource,
) : UserProfileReader {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMyProfile(): Flow<MyProfile?> =
        authSource.authState
            .map { it?.uid }
            .distinctUntilChanged()
            .flatMapLatest { uid -> if (uid == null) flowOf(null) else observe(uid) }

    private fun observe(uid: String): Flow<MyProfile?> = callbackFlow {
        val registration = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                // Lỗi quyền (tài khoản chưa khởi tạo) hoặc mạng: coi như chưa có hồ sơ, không đóng flow.
                trySend(if (error != null) null else snapshot?.toProfile())
            }
        awaitClose { registration.remove() }
    }

    private fun DocumentSnapshot.toProfile(): MyProfile? {
        if (!exists()) return null
        @Suppress("UNCHECKED_CAST")
        return MyProfile(
            displayName = getString("displayName").orEmpty(),
            level = getString("level"),
            favoriteDistrictIds = (get("favoriteDistrictIds") as? List<String>).orEmpty(),
            avatarPath = getString("avatarPath"),
            role = getString("role"),
            accountStatus = getString("accountStatus"),
            email = getString("email"),
            phone = getString("phone"),
            walletBalance = getLong("walletBalance") ?: 0L,
            ownerStatus = getString("ownerStatus") ?: "NONE",
            ownerRevokedReason = getString("ownerRevokedReason"),
            deletionRequestedAt = getTimestamp("deletionRequestedAt")?.toInstant(),
        )
    }
}

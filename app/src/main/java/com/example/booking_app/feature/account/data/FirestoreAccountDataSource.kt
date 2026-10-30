package com.example.booking_app.feature.account.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.awaitWithTimeout
import com.example.booking_app.feature.account.domain.model.AccountError
import com.example.booking_app.feature.account.domain.model.OwnerApplicationSummary
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class FirestoreAccountDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) : AccountDataSource {

    override suspend fun update(uid: String, fields: Map<String, Any?>) {
        val data = fields.mapValues { (_, value) -> if (value == ServerTimestamp) FieldValue.serverTimestamp() else value }
        firestore.collection("users").document(uid).update(data).awaitWithTimeout()
    }

    override fun observeLatestOwnerApplication(uid: String): Flow<OwnerApplicationSummary?> = callbackFlow {
        val registration = firestore.collection("ownerApplications")
            .whereEqualTo("uid", uid)
            .orderBy("submittedAt", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toAccountError())
                    return@addSnapshotListener
                }
                val doc = snapshot?.documents?.firstOrNull()
                trySend(
                    doc?.let {
                        OwnerApplicationSummary(
                            status = it.getString("status").orEmpty(),
                            submittedAt = it.getTimestamp("submittedAt")?.toInstant(),
                            rejectReason = it.getString("rejectReason"),
                        )
                    },
                )
            }
        awaitClose { registration.remove() }
    }

    private fun FirebaseFirestoreException.toAccountError(): Exception = when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> AccountError.PermissionDenied
        FirebaseFirestoreException.Code.UNAVAILABLE -> AccountError.Network
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> AppError.NotSignedIn
        else -> AccountError.Unknown(this)
    }
}

package com.example.booking_app.feature.account.data

import com.example.booking_app.core.network.awaitWithTimeout
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject

class FirestoreAccountDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) : AccountDataSource {

    override suspend fun update(uid: String, fields: Map<String, Any?>) {
        val data = fields.mapValues { (_, value) -> if (value == ServerTimestamp) FieldValue.serverTimestamp() else value }
        firestore.collection("users").document(uid).update(data).awaitWithTimeout()
    }
}

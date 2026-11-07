package com.example.booking_app.feature.owner.domain

import com.google.firebase.firestore.FirebaseFirestoreException

enum class OwnerErrorKind { PERMISSION, NETWORK, CONFLICT, UNKNOWN }

object OwnerErrorClassifier {
    fun kind(error: Throwable): OwnerErrorKind {
        val firestoreCode = (error as? FirebaseFirestoreException)?.code
        if (firestoreCode == FirebaseFirestoreException.Code.PERMISSION_DENIED) return OwnerErrorKind.PERMISSION
        if (firestoreCode == FirebaseFirestoreException.Code.UNAVAILABLE || firestoreCode == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED) return OwnerErrorKind.NETWORK
        val text = (error.message ?: "").lowercase()
        return when {
            "permission" in text || "denied" in text || "unauthorized" in text -> OwnerErrorKind.PERMISSION
            "timeout" in text || "offline" in text || "network" in text || "unavailable" in text -> OwnerErrorKind.NETWORK
            "conflict" in text || "stale" in text -> OwnerErrorKind.CONFLICT
            else -> OwnerErrorKind.UNKNOWN
        }
    }
}

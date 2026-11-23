package com.example.booking_app.feature.notification.data

import com.example.booking_app.core.network.awaitWithTimeout
import com.example.booking_app.feature.notification.domain.Notification
import com.example.booking_app.feature.notification.domain.NotificationCursor
import com.example.booking_app.feature.notification.domain.NotificationPage
import com.example.booking_app.feature.notification.domain.NotificationRepository
import com.example.booking_app.core.session.SessionManager
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import javax.inject.Inject

class FirestoreNotificationRepository @Inject constructor(
    private val session: SessionManager,
    private val firestore: FirebaseFirestore,
) : NotificationRepository {
    private data class Cursor(val uid: String, val snapshot: DocumentSnapshot) : NotificationCursor

    private fun uid(): String = checkNotNull(session.currentUid()) { "Authentication required" }
    private fun inbox(uid: String) = firestore.collection("users").document(uid).collection("notifications")
    private fun ensureSession(expected: String) { check(uid() == expected) { "Account changed" } }

    override suspend fun loadById(id: String): Notification? {
        require(id.isNotBlank() && '/' !in id)
        val uid = uid()
        val doc = inbox(uid).document(id).get(Source.SERVER).awaitWithTimeout()
        ensureSession(uid)
        return if (doc.exists()) Notification(doc.id, doc.getString("type").orEmpty(), doc.getString("title").orEmpty(),
            doc.getString("body").orEmpty(), (doc.get("data") as? Map<*, *>)?.entries?.mapNotNull { (k, v) -> (k as? String)?.let { it to v } }?.toMap().orEmpty(),
            doc.getBoolean("isRead") == true, doc.getTimestamp("createdAt")?.toDate()?.time ?: 0) else null
    }

    override suspend fun loadPage(after: NotificationCursor?): NotificationPage {
        val uid = uid()
        if (after != null) require(after is Cursor && after.uid == uid)
        var query = inbox(uid).orderBy("createdAt", Query.Direction.DESCENDING).limit(PAGE_SIZE)
        if (after != null) {
            query = query.startAfter(after.snapshot)
        }
        val docs = query.get(Source.SERVER).awaitWithTimeout().documents
        ensureSession(uid)
        return NotificationPage(docs.map { document ->
            Notification(
                id = document.id,
                type = document.getString("type").orEmpty(),
                title = document.getString("title").orEmpty(),
                body = document.getString("body").orEmpty(),
                data = (document.get("data") as? Map<*, *>)?.entries
                    ?.mapNotNull { (key, value) -> (key as? String)?.let { it to value } }?.toMap().orEmpty(),
                isRead = document.getBoolean("isRead") == true,
                createdAtMillis = document.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
            )
        }, if (docs.size == PAGE_SIZE.toInt()) Cursor(uid, docs.last()) else null)
    }

    override suspend fun markRead(id: String) {
        require(id.isNotBlank() && '/' !in id)
        val uid = uid()
        inbox(uid).document(id).update("isRead", true).awaitWithTimeout()
        ensureSession(uid)
    }

    override suspend fun markAllRead() {
        val uid = uid()
        var last: DocumentSnapshot? = null
        do {
            ensureSession(uid)
            var query = inbox(uid).orderBy("createdAt", Query.Direction.DESCENDING).limit(PAGE_SIZE)
            last?.let { query = query.startAfter(it) }
            val docs = query.get(Source.SERVER).awaitWithTimeout().documents
            ensureSession(uid)
            val unread = docs.filter { it.getBoolean("isRead") != true }
            if (unread.isNotEmpty()) {
                val batch = firestore.batch()
                unread.forEach { batch.update(it.reference, "isRead", true) }
                batch.commit().awaitWithTimeout()
            }
            last = docs.lastOrNull()
        } while (docs.size == PAGE_SIZE.toInt())
        ensureSession(uid)
    }

    private companion object { const val PAGE_SIZE = 30L }
}

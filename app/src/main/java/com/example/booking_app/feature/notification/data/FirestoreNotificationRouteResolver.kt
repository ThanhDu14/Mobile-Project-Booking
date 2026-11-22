package com.example.booking_app.feature.notification.data

import com.example.booking_app.core.network.awaitWithTimeout
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.notification.domain.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class FirestoreNotificationRouteResolver @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val session: SessionManager,
) : NotificationRouteResolver {
    override suspend fun resolve(item: Notification): String? {
        val uid = session.currentUid() ?: return null
        if (NotificationType.fromStoredValue(item.type) == NotificationType.REVIEW_REPLIED) {
            return resolveReviewReply(item, uid)
        }
        val candidate = when (NotificationType.fromStoredValue(item.type)) {
            NotificationType.BOOKING_CONFIRMED, NotificationType.BOOKING_REJECTED,
            NotificationType.BOOKING_CANCELLED, NotificationType.BOOKING_REMINDER ->
                Triple("bookings", "bookingId", "smashnow://notifications/booking/")
            NotificationType.REVIEW_REMINDER -> Triple("bookings", "bookingId", "smashnow://review/write/")
            NotificationType.DROPIN_REGISTERED, NotificationType.DROPIN_SPOT_OPENED,
            NotificationType.DROPIN_CHANGED, NotificationType.DROPIN_WAITLIST_CLOSED ->
                Triple("dropInSessions", "sessionId", "smashnow://dropin/session/")
            NotificationType.CHAT_MESSAGE -> Triple("chats", "chatId", "smashnow://chat/")
            else -> return null
        }
        val id = item.data[candidate.second] as? String ?: return null
        if (id.isBlank() || '/' in id) return null
        return try {
            val source = firestore.collection(candidate.first).document(id).get(Source.SERVER).awaitWithTimeout()
            if (session.currentUid() != uid || !source.exists()) return null
            if (candidate.first == "bookings" && source.getString("userId") != uid) return null
            if (candidate.first == "chats" && uid !in ((source.get("memberIds") as? List<*>) ?: emptyList<Any>())) return null
            candidate.third + java.net.URLEncoder.encode(id, "UTF-8").replace("+", "%20")
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { null }
    }

    private suspend fun resolveReviewReply(item: Notification, uid: String): String? {
        val reviewId = item.data["reviewId"] as? String ?: return null
        val venueId = item.data["venueId"] as? String ?: return null
        if (listOf(reviewId, venueId).any { it.isBlank() || '/' in it }) return null
        return try {
            val review = firestore.collection("reviews").document(reviewId).get(Source.SERVER).awaitWithTimeout()
            if (session.currentUid() != uid || !review.exists() || review.getString("userId") != uid
                || review.getString("venueId") != venueId || review.getString("status") != "VISIBLE"
                || review.getBoolean("hasOwnerReply") != true) return null
            val venue = firestore.collection("venues").document(venueId).get(Source.SERVER).awaitWithTimeout()
            if (session.currentUid() != uid || !venue.exists()) return null
            "smashnow://review/venue/" + java.net.URLEncoder.encode(venueId, "UTF-8").replace("+", "%20")
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { null }
    }
}

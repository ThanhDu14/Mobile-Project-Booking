package com.example.booking_app.feature.chat.domain

import com.example.booking_app.core.image.ImageTooLargeException
import com.example.booking_app.core.image.ImageUnreadableException
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.FunctionHttpException
import com.example.booking_app.core.network.toAppError
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kiểm tra ánh xạ đủ mã lỗi ở contracts/edge-functions-chat.md mục 5 (T016). */
class ChatErrorTest {

    @Test
    fun `anh xa du cac ma loi contract sang ChatError`() {
        val expected = mapOf(
            "INVALID_ARGUMENT" to ChatError.InvalidArgument::class,
            "PHOTO_MISSING" to ChatError.PhotoMissing::class,
            "UNAUTHENTICATED" to ChatError.Unauthenticated::class,
            "ACCOUNT_LOCKED" to ChatError.AccountLocked::class,
            "NOT_PARTICIPANT" to ChatError.NotParticipant::class,
            "BLOCKED" to ChatError.Blocked::class,
            "FORBIDDEN" to ChatError.Forbidden::class,
            "OWN_VENUE" to ChatError.OwnVenue::class,
            "OWN_MESSAGE" to ChatError.OwnMessage::class,
            "CHAT_NOT_FOUND" to ChatError.ChatNotFound::class,
            "MESSAGE_NOT_FOUND" to ChatError.MessageNotFound::class,
            "CHAT_READ_ONLY" to ChatError.ChatReadOnly::class,
            "VENUE_INACTIVE" to ChatError.VenueInactive::class,
            "OWNER_NOT_APPROVED" to ChatError.OwnerNotApproved::class,
            "RECALL_WINDOW_PASSED" to ChatError.RecallWindowPassed::class,
            "ALREADY_REPORTED" to ChatError.AlreadyReported::class,
            "GROUP_LOCKED" to ChatError.GroupLocked::class,
            "BUSY" to ChatError.Busy::class,
        )
        for ((code, kClass) in expected) {
            val http = FunctionHttpException(400, """{"error":{"code":"$code","message":"msg"}}""")
            val mapped = AppError.Unknown(http).toChatError()
            assertTrue("Mong đợi $kClass cho mã $code nhưng nhận $mapped", kClass.isInstance(mapped))
        }
    }

    @Test
    fun `phan biet loi thu lai duoc va khong thu lai duoc`() {
        assertTrue(ChatError.Network().isRetryable)
        assertTrue(ChatError.Busy().isRetryable)
        assertFalse(ChatError.Blocked().isRetryable)
        assertFalse(ChatError.NotParticipant().isRetryable)
        assertFalse(ChatError.InvalidArgument().isRetryable)
        assertFalse(ChatError.ChatReadOnly().isRetryable)
        assertFalse(ChatError.GroupLocked().isRetryable)
    }

    @Test
    fun `anh xa ngoai le nen anh, mang Firebase, Firestore va HttpRequestException`() {
        assertTrue(ImageTooLargeException().toChatError() is ChatError.ImageTooLarge)
        assertTrue(ImageUnreadableException().toChatError() is ChatError.ImageUnreadable)
        assertTrue(AppError.Unknown(ImageTooLargeException()).toChatError() is ChatError.ImageTooLarge)
        assertTrue(AppError.Unknown(ImageUnreadableException()).toChatError() is ChatError.ImageUnreadable)

        val fbNet = FirebaseNetworkException("offline")
        assertTrue(fbNet.toChatError() is ChatError.Network)
        assertTrue(AppError.Unknown(fbNet).toChatError() is ChatError.Network)

        val httpReq = HttpRequestException("dns failed", mockk(relaxed = true))
        assertTrue(httpReq.toAppError() === AppError.Network)
        assertTrue(httpReq.toChatError() is ChatError.Network)
        assertTrue(AppError.Unknown(httpReq).toChatError() is ChatError.Network)

        assertTrue(
            FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
                .toChatError() is ChatError.NotParticipant,
        )
        assertTrue(
            FirebaseFirestoreException("unavailable", FirebaseFirestoreException.Code.UNAVAILABLE)
                .toChatError() is ChatError.Network,
        )
        assertTrue(
            FirebaseFirestoreException("missing", FirebaseFirestoreException.Code.NOT_FOUND)
                .toChatError() is ChatError.ChatNotFound,
        )
    }
}

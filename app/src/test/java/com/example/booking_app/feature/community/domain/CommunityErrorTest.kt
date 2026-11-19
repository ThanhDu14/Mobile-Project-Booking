package com.example.booking_app.feature.community.domain

import com.example.booking_app.R
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.FunctionHttpException
import com.example.booking_app.feature.community.ui.common.CommunityUiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.reflect.KClass

class CommunityErrorTest {

    private fun body(code: String, extra: String = "") = "{\"error\":{\"code\":\"$code\",\"message\":\"m\"$extra}}"

    @Test
    fun `moi ma loi o contract muc 5 map ve dung lop`() {
        val expected: Map<String, KClass<out CommunityError>> = mapOf(
            "INVALID_ARGUMENT" to CommunityError.InvalidArgument::class,
            "CONFIRM_NAME_MISMATCH" to CommunityError.ConfirmNameMismatch::class,
            "PHOTO_MISSING" to CommunityError.PhotoMissing::class,
            "UNAUTHENTICATED" to CommunityError.Unauthenticated::class,
            "ACCOUNT_LOCKED" to CommunityError.AccountLocked::class,
            "NOT_MEMBER" to CommunityError.NotMember::class,
            "FORBIDDEN" to CommunityError.Forbidden::class,
            "HIDDEN_BY_SYSTEM" to CommunityError.HiddenBySystem::class,
            "OWN_POST" to CommunityError.OwnPost::class,
            "GROUP_NOT_FOUND" to CommunityError.GroupNotFound::class,
            "POST_NOT_FOUND" to CommunityError.PostNotFound::class,
            "REQUEST_NOT_FOUND" to CommunityError.RequestNotFound::class,
            "MEMBER_NOT_FOUND" to CommunityError.MemberNotFound::class,
            "OWNER_LIMIT_REACHED" to CommunityError.OwnerLimitReached::class,
            "ALREADY_MEMBER" to CommunityError.AlreadyMember::class,
            "ALREADY_REQUESTED" to CommunityError.AlreadyRequested::class,
            "OWNER_MUST_TRANSFER" to CommunityError.OwnerMustTransfer::class,
            "EVENT_PAST" to CommunityError.EventPast::class,
            "ALREADY_REPORTED" to CommunityError.AlreadyReported::class,
            "TOO_MANY_PENDING" to CommunityError.TooManyPending::class,
            "GROUP_LOCKED" to CommunityError.GroupLocked::class,
            "BUSY" to CommunityError.Busy::class,
        )
        assertEquals(22, expected.size)
        expected.forEach { (code, type) ->
            assertEquals(code, type, communityErrorOf(code)::class)
            assertEquals(code, communityErrorOf(code).message)
        }
        assertEquals(R.string.community_error_member_not_found, CommunityUiText.errorRes(CommunityError.MemberNotFound()))
    }

    @Test
    fun `ma la giu nguyen trong Unknown`() {
        assertEquals(CommunityError.Unknown("NEW_CODE"), communityErrorOf("NEW_CODE"))
        assertEquals(CommunityError.Unknown(null), communityErrorOf(null))
    }

    @Test
    fun `thieu ma thi dua vao HTTP status`() {
        assertTrue(communityErrorOf(null, httpStatus = 503) is CommunityError.Busy)
        assertTrue(communityErrorOf(null, httpStatus = 423) is CommunityError.GroupLocked)
    }

    @Test
    fun `OWNER_MUST_TRANSFER doc soleMember tu body`() {
        val alone = FunctionHttpException(409, body("OWNER_MUST_TRANSFER", ",\"soleMember\":true"))
        val others = FunctionHttpException(409, body("OWNER_MUST_TRANSFER", ",\"soleMember\":false"))
        val missing = FunctionHttpException(409, body("OWNER_MUST_TRANSFER"))
        assertEquals(CommunityError.OwnerMustTransfer(true), AppError.Unknown(alone).toCommunityError())
        assertEquals(CommunityError.OwnerMustTransfer(false), AppError.Unknown(others).toCommunityError())
        assertEquals(CommunityError.OwnerMustTransfer(false), AppError.Unknown(missing).toCommunityError())
    }

    @Test
    fun `AppError va loi mang map ve loi chung`() {
        assertTrue(AppError.Network.toCommunityError() is CommunityError.Network)
        assertTrue(AppError.Timeout.toCommunityError() is CommunityError.Network)
        assertTrue(IOException("x").toCommunityError() is CommunityError.Network)
        assertTrue(AppError.Unknown(IOException("x")).toCommunityError() is CommunityError.Network)
        assertTrue(AppError.Locked.toCommunityError() is CommunityError.AccountLocked)
        assertTrue(AppError.NotSignedIn.toCommunityError() is CommunityError.Unauthenticated)
        assertTrue(RuntimeException("x").toCommunityError() is CommunityError.Unknown)
    }

    @Test
    fun `FunctionHttpException truc tiep va body hong`() {
        assertTrue(FunctionHttpException(403, body("FORBIDDEN")).toCommunityError() is CommunityError.Forbidden)
        assertTrue(FunctionHttpException(503, "not json").toCommunityError() is CommunityError.Busy)
        assertTrue(FunctionHttpException(500, null).toCommunityError() is CommunityError.Unknown)
    }

    @Test
    fun `CommunityError di qua nguyen ven`() {
        val e = CommunityError.AvatarUploadFailed("g1")
        assertEquals(e, e.toCommunityError())
    }
}

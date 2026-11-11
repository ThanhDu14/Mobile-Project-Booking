package com.example.booking_app.feature.dropin.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.FunctionHttpException
import com.example.booking_app.feature.dropin.data.dto.FunctionErrorBody
import com.example.booking_app.feature.dropin.data.dto.toDropInError
import com.example.booking_app.feature.dropin.domain.model.DropInError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DropInErrorMappingTest {

    private fun http(status: Int, code: String?, extra: String = ""): Throwable {
        val body = if (code == null) "<html>oops</html>" else """{ "error": { "code": "$code", "message": "x"$extra } }"""
        return AppError.Unknown(FunctionHttpException(status, body))
    }

    @Test
    fun `SESSION_FULL kem remaining`() {
        assertEquals(DropInError.SessionFull(1), http(409, "SESSION_FULL", """, "remaining": 1""").toDropInError())
        assertEquals(DropInError.SessionFull(0), http(409, "SESSION_FULL").toDropInError())
    }

    @Test
    fun `moi ma nghiep vu - dung lop DropInError`() {
        val cases = mapOf(
            "SESSION_STARTED" to DropInError.SessionStarted::class,
            "SESSION_CANCELLED" to DropInError.SessionCancelled::class,
            "VENUE_INACTIVE" to DropInError.VenueInactive::class,
            "OWN_SESSION" to DropInError.OwnSession::class,
            "ALREADY_REGISTERED" to DropInError.AlreadyRegistered::class,
            "ALREADY_WAITLISTED" to DropInError.AlreadyWaitlisted::class,
            "NOT_CANCELLABLE" to DropInError.NotCancellable::class,
            "NOT_FOUND" to DropInError.NotFound::class,
            "SESSION_NOT_FOUND" to DropInError.NotFound::class,
            "ACCOUNT_LOCKED" to DropInError.AccountLocked::class,
            "INVALID_ARGUMENT" to DropInError.InvalidArgument::class,
            "UNAUTHENTICATED" to DropInError.Unauthenticated::class,
            "BUSY" to DropInError.Busy::class,
        )
        cases.forEach { (code, expected) ->
            val actual = http(409, code).toDropInError()
            assertTrue("$code -> $actual", expected.isInstance(actual))
        }
    }

    @Test
    fun `ma la - Unknown giu ma, body hong - Unknown null, 503 khong body - Busy`() {
        assertEquals(DropInError.Unknown("INTERNAL"), http(500, "INTERNAL").toDropInError())
        assertEquals(DropInError.Unknown(null), http(502, null).toDropInError())
        assertTrue(http(503, null).toDropInError() is DropInError.Busy)
    }

    @Test
    fun `mat mang hoac qua thoi gian - Network`() {
        assertTrue(AppError.Network.toDropInError() is DropInError.Network)
        assertTrue(AppError.Timeout.toDropInError() is DropInError.Network)
        assertTrue(IOException("down").toDropInError() is DropInError.Network)
        assertTrue(AppError.Unknown(IOException("down")).toDropInError() is DropInError.Network)
    }

    @Test
    fun `loi chung da qua FunctionErrors`() {
        assertTrue(AppError.Locked.toDropInError() is DropInError.AccountLocked)
        assertTrue(AppError.NotSignedIn.toDropInError() is DropInError.Unauthenticated)
        assertTrue(IllegalStateException().toDropInError() is DropInError.Unknown)
        val already = DropInError.OwnSession()
        assertTrue(already.toDropInError() === already)
    }

    @Test
    fun `doc body loi`() {
        val parsed = FunctionErrorBody.parse("""{ "error": { "code": "SESSION_FULL", "message": "m", "remaining": 2 } }""")
        assertEquals(FunctionErrorBody("SESSION_FULL", "m", 2), parsed)
        assertEquals(null, FunctionErrorBody.parse("not json"))
        assertEquals(null, FunctionErrorBody.parse(null))
    }
}

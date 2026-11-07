package com.example.booking_app.feature.account.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.FunctionHttpException
import com.example.booking_app.feature.account.domain.model.AccountError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** T085: ánh xạ response và lỗi của `delete-account` (contracts/edge-functions-account.md §1.4, §1.5). */
class AccountFunctionsApiTest {

    private fun http(status: Int, code: String, extra: String = "") =
        AppError.Unknown(FunctionHttpException(status, """{"error":{"code":"$code","message":"m"$extra}}"""))

    private val previewJson = """
        {"impactVersion":"sha256:ab","walletBalance":50000,
         "myBookings":[{"bookingId":"b1","venueName":"Sân Hòa Bình","startAt":"2026-10-20T12:00:00Z",
                        "status":"CONFIRMED","paidAmount":60000,"refundAmount":60000}],
         "myDropIns":[{"sessionId":"s1","venueName":"Sân Kỳ Hòa","startAt":"2026-10-22T11:00:00Z",
                       "paidAmount":0,"refundAmount":0}],
         "waitlistCount":1,
         "ownerImpact":{"venueCount":2,"guestBookingCount":7,"guestDropInCount":3,"guestCount":9},
         "extra":"bo qua"}
    """.trimIndent()

    @Test
    fun `parsePreview doc du truong, ownerImpact null thi null`() {
        val impact = DeleteAccountResponses.parsePreview(previewJson)
        assertEquals("sha256:ab", impact.impactVersion)
        assertEquals(50_000L, impact.walletBalance)
        assertEquals(Instant.parse("2026-10-20T12:00:00Z"), impact.myBookings.single().startAt)
        assertEquals(60_000L, impact.myBookings.single().refundAmount)
        assertEquals("s1", impact.myDropIns.single().sessionId)
        assertEquals(1, impact.waitlistCount)
        assertEquals(9, impact.ownerImpact?.guestCount)

        val noOwner = DeleteAccountResponses.parsePreview(
            """{"impactVersion":"v","walletBalance":0,"myBookings":[],"myDropIns":[],"waitlistCount":0,"ownerImpact":null}""",
        )
        assertNull(noOwner.ownerImpact)
    }

    @Test
    fun `ma loi cua confirm doi thanh AccountError`() {
        val map = DeleteAccountResponses::mapConfirmFailure
        assertEquals(AccountError.ReauthRequired, map(http(403, "REAUTH_REQUIRED")))
        assertEquals(AccountError.AdminSelfDelete, map(http(403, "ADMIN_SELF_DELETE")))
        assertEquals(AccountError.DeleteTooLarge, map(http(422, "DELETE_TOO_LARGE")))
        assertEquals(AccountError.DeleteConfirmMismatch, map(http(400, "CONFIRM_TEXT_MISMATCH")))
        assertTrue(map(http(500, "INTERNAL")) is AccountError.Unknown)
        assertEquals(AccountError.Network, map(AppError.Timeout))
        assertEquals(AccountError.Network, map(AppError.Network))
    }

    @Test
    fun `IMPACT_CHANGED mang ban xem truoc moi`() {
        val error = DeleteAccountResponses.mapConfirmFailure(http(409, "IMPACT_CHANGED", ""","preview":$previewJson"""))
        assertTrue(error is AccountError.ImpactChanged)
        assertEquals("sha256:ab", (error as AccountError.ImpactChanged).newImpact.impactVersion)
    }

    @Test
    fun `ACCOUNT_DELETING tu lan goi truoc la thanh cong`() {
        assertNull(DeleteAccountResponses.mapConfirmFailure(AppError.AccountDeleted))
    }

    @Test
    fun `200 DELETED va 202 CLEANUP_SCHEDULED deu la thanh cong`() {
        DeleteAccountResponses.checkConfirmBody("""{"status":"DELETED"}""")
        DeleteAccountResponses.checkConfirmBody("""{"status":"CLEANUP_SCHEDULED"}""")
    }

    @Test
    fun `timeout preview 15 giay, confirm 120 giay`() {
        assertEquals(15_000L, DeleteAccountResponses.PREVIEW_TIMEOUT_MS)
        assertEquals(120_000L, DeleteAccountResponses.CONFIRM_TIMEOUT_MS)
    }
}

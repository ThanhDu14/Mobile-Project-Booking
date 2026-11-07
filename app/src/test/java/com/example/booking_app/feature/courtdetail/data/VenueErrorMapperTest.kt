package com.example.booking_app.feature.courtdetail.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.feature.courtdetail.domain.model.VenueResult
import com.google.firebase.firestore.FirebaseFirestoreException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VenueErrorMapperTest {

    private fun ex(code: FirebaseFirestoreException.Code) = FirebaseFirestoreException("x", code)

    @Test
    fun `PERMISSION_DENIED la PermissionDenied, tang data khong biet khach hay da dang nhap`() {
        assertEquals(VenueResult.PermissionDenied, VenueErrorMapper.map(ex(FirebaseFirestoreException.Code.PERMISSION_DENIED)))
    }

    @Test
    fun `UNAVAILABLE la loi mang`() {
        assertEquals(VenueResult.Failed(AppError.Network), VenueErrorMapper.map(ex(FirebaseFirestoreException.Code.UNAVAILABLE)))
    }

    @Test
    fun `DEADLINE_EXCEEDED la het gio`() {
        assertEquals(
            VenueResult.Failed(AppError.Timeout),
            VenueErrorMapper.map(ex(FirebaseFirestoreException.Code.DEADLINE_EXCEEDED)),
        )
    }

    @Test
    fun `AppError giu nguyen, loi khac la Unknown`() {
        assertEquals(VenueResult.Failed(AppError.Timeout), VenueErrorMapper.map(AppError.Timeout))
        val other = VenueErrorMapper.map(IllegalStateException("boom")) as VenueResult.Failed
        assertTrue(other.error is AppError.Unknown)
    }

    @Test
    fun `AppError Unknown boc PERMISSION_DENIED van la PermissionDenied`() {
        val wrapped = AppError.Unknown(ex(FirebaseFirestoreException.Code.PERMISSION_DENIED))
        assertEquals(VenueResult.PermissionDenied, VenueErrorMapper.map(wrapped))
    }
}

package com.example.booking_app.feature.review.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.FunctionHttpException
import com.example.booking_app.feature.review.domain.ReviewError
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewErrorsTest {

    private fun httpError(status: Int, code: String) =
        AppError.Unknown(FunctionHttpException(status, """{ "error": { "code": "$code", "message": "x" } }"""))

    @Test
    fun `ma loi cua Edge Function - doi dung ReviewError`() {
        ReviewError.entries
            .filter { it != ReviewError.NETWORK }
            .forEach { expected -> assertEquals(expected, httpError(409, expected.name).toReviewError()) }
    }

    @Test
    fun `ma la hoac body khong doc duoc - INTERNAL`() {
        assertEquals(ReviewError.INTERNAL, httpError(500, "SOMETHING_NEW").toReviewError())
        assertEquals(ReviewError.INTERNAL, AppError.Unknown(FunctionHttpException(502, "<html>")).toReviewError())
        assertEquals(ReviewError.INTERNAL, IllegalStateException().toReviewError())
    }

    @Test
    fun `mat mang hoac qua thoi gian - NETWORK de giu ban nhap va hien Thu lai`() {
        assertEquals(ReviewError.NETWORK, AppError.Network.toReviewError())
        assertEquals(ReviewError.NETWORK, AppError.Timeout.toReviewError())
    }

    @Test
    fun `loi chung da duoc FunctionErrors xu ly - doi tuong ung`() {
        assertEquals(ReviewError.ACCOUNT_LOCKED, AppError.Locked.toReviewError())
        assertEquals(ReviewError.UNAUTHENTICATED, AppError.NotSignedIn.toReviewError())
    }
}

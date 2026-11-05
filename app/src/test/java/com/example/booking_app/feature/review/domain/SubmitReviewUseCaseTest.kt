package com.example.booking_app.feature.review.domain

import com.example.booking_app.core.network.AppError
import com.example.booking_app.feature.review.FakeReviewRepository
import com.example.booking_app.feature.review.domain.model.DraftPhoto
import com.example.booking_app.feature.review.domain.model.Ratings
import com.example.booking_app.feature.review.domain.model.ReviewCriterion
import com.example.booking_app.feature.review.domain.model.ReviewDraft
import com.example.booking_app.feature.review.domain.usecase.SubmitReviewResult
import com.example.booking_app.feature.review.domain.usecase.SubmitReviewUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubmitReviewUseCaseTest {

    private val repository = FakeReviewRepository()
    private val submit = SubmitReviewUseCase(repository)

    @Test
    fun `thieu tieu chi - Invalid chi ro tieu chi, khong goi repository`() = runTest {
        val result = submit(ReviewDraft("b1", court = 5, cleanliness = null, service = null))

        assertEquals(
            SubmitReviewResult.Invalid(listOf(ReviewCriterion.CLEANLINESS, ReviewCriterion.SERVICE), false),
            result,
        )
        assertTrue(repository.createCalls.isEmpty())
    }

    @Test
    fun `binh luan qua 1000 ky tu sau khi trim - Invalid`() = runTest {
        val result = submit(ReviewDraft("b1", 5, 4, 3, comment = "  " + "a".repeat(1001) + "  "))
        assertEquals(SubmitReviewResult.Invalid(emptyList(), true), result)
    }

    @Test
    fun `binh luan dung 1000 ky tu sau khi trim - gui duoc, comment da trim`() = runTest {
        val text = "a".repeat(1000)
        submit(ReviewDraft("b1", 5, 4, 3, comment = "  $text\n"))
        assertEquals(text, repository.createCalls.single().comment)
    }

    @Test
    fun `hop le - goi create voi ratings va photoPaths da tai len`() = runTest {
        val draft = ReviewDraft(
            "b1", 5, 4, 3,
            photos = listOf(DraftPhoto("content://1", "u/b1/a.jpg", DraftPhoto.State.UPLOADED)),
        )
        val result = submit(draft) as SubmitReviewResult.Submitted

        assertEquals(4.0, result.review.overall, 0.0)
        val call = repository.createCalls.single()
        assertEquals(Ratings(5, 4, 3), call.ratings)
        assertEquals(listOf("u/b1/a.jpg"), call.photoPaths)
    }

    @Test
    fun `repository nem ReviewException - Failed voi ma loi`() = runTest {
        repository.createError = ReviewException(ReviewError.NETWORK)
        assertEquals(SubmitReviewResult.Failed(ReviewError.NETWORK), submit(ReviewDraft("b1", 5, 4, 3)))
    }

    @Test
    fun `loi la - Failed INTERNAL`() = runTest {
        repository.createError = AppError.Unknown(IllegalStateException())
        assertEquals(SubmitReviewResult.Failed(ReviewError.INTERNAL), submit(ReviewDraft("b1", 5, 4, 3)))
    }

    @Test
    fun `diem tong lam tron 1 chu so giong server`() {
        assertEquals(4.0, SubmitReviewUseCase.overallOf(ReviewDraft("b1", 5, 4, 3))!!, 0.0)
        assertEquals(4.7, SubmitReviewUseCase.overallOf(ReviewDraft("b1", 5, 5, 4))!!, 0.0)
        assertEquals(1.3, SubmitReviewUseCase.overallOf(ReviewDraft("b1", 1, 1, 2))!!, 0.0)
        assertNull(SubmitReviewUseCase.overallOf(ReviewDraft("b1", 5, 4, null)))
    }
}

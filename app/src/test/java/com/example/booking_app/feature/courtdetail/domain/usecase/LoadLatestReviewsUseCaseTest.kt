package com.example.booking_app.feature.courtdetail.domain.usecase

import com.example.booking_app.core.network.AppError
import com.example.booking_app.feature.courtdetail.FakeLatestReviewsSource
import com.example.booking_app.feature.courtdetail.testReview
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** T053 (phần use case). Phần ánh xạ document → ReviewPreview ở `data/ReviewPreviewMapperTest`. */
class LoadLatestReviewsUseCaseTest {

    private fun at(day: Int) = Instant.parse("2026-10-%02dT03:00:00Z".format(day))

    @Test
    fun `toi da 3 danh gia, moi nhat truoc`() = runTest {
        val source = FakeLatestReviewsSource(
            reviews = listOf(
                testReview("r1", createdAt = at(26)),
                testReview("r2", createdAt = at(30)),
                testReview("r3", createdAt = at(28)),
                testReview("r4", createdAt = at(29)),
            ),
        )
        val result = LoadLatestReviewsUseCase(source)("demo-k3")
        assertEquals(listOf("r2", "r4", "r3"), result.map { it.id })
        assertEquals(3, source.lastLimit)
    }

    @Test
    fun `danh gia thieu ngay tao xep cuoi`() = runTest {
        val source = FakeLatestReviewsSource(
            reviews = listOf(testReview("r1", createdAt = null), testReview("r2", createdAt = at(30))),
        )
        assertEquals(listOf("r2", "r1"), LoadLatestReviewsUseCase(source)("demo-k3").map { it.id })
    }

    @Test
    fun `binh luan va phan hoi giu nguyen`() = runTest {
        val long = "Sân rộng, ".repeat(40)
        val source = FakeLatestReviewsSource(reviews = listOf(testReview("r1", comment = long, reply = "Cảm ơn bạn")))
        val review = LoadLatestReviewsUseCase(source)("demo-k3").single()
        assertEquals(long, review.comment)
        assertEquals("Cảm ơn bạn", review.reply)
    }

    @Test
    fun `loi mang thanh AppError Network`() = runTest {
        val source = FakeLatestReviewsSource(error = IOException("offline"))
        try {
            LoadLatestReviewsUseCase(source)("demo-k3")
            fail("Phải ném lỗi")
        } catch (e: AppError) {
            assertEquals(AppError.Network, e)
        }
    }

    @Test
    fun `AppError tu nguon giu nguyen`() = runTest {
        val source = FakeLatestReviewsSource(error = AppError.NotSignedIn)
        try {
            LoadLatestReviewsUseCase(source)("demo-k3")
            fail("Phải ném lỗi")
        } catch (e: AppError) {
            assertTrue(e is AppError.NotSignedIn)
        }
    }
}

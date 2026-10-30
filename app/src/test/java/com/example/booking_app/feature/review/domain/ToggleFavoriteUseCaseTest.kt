package com.example.booking_app.feature.review.domain

import app.cash.turbine.test
import com.example.booking_app.core.model.VenueSummary
import com.example.booking_app.core.network.AppError
import com.example.booking_app.feature.review.FakeFavoriteRepository
import com.example.booking_app.feature.review.domain.usecase.FavoriteToggle
import com.example.booking_app.feature.review.domain.usecase.ToggleFavoriteUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ToggleFavoriteUseCaseTest {

    private val repository = FakeFavoriteRepository()
    private val useCase = ToggleFavoriteUseCase(repository)
    private val venue = VenueSummary("venueA", "Sân A", null, "Quận 1")

    @Test
    fun `chua yeu thich - doi ngay sang da luu roi ghi repository`() = runTest {
        useCase(venue, isFavorite = false).test {
            assertEquals(FavoriteToggle.Changed(isFavorite = true), awaitItem())
            awaitComplete()
        }
        assertEquals(listOf("venueA" to true), repository.setCalls)
    }

    @Test
    fun `da yeu thich - doi ngay sang bo luu`() = runTest {
        useCase(venue, isFavorite = true).test {
            assertEquals(FavoriteToggle.Changed(isFavorite = false), awaitItem())
            awaitComplete()
        }
        assertEquals(listOf("venueA" to false), repository.setCalls)
    }

    @Test
    fun `repository loi - tra ve trang thai cu kem loi (FR-021)`() = runTest {
        val error = AppError.Unknown(null)
        repository.setResult = Result.failure(error)

        useCase(venue, isFavorite = false).test {
            assertEquals(FavoriteToggle.Changed(isFavorite = true), awaitItem())
            assertEquals(FavoriteToggle.Reverted(isFavorite = false, error = error), awaitItem())
            awaitComplete()
        }
    }
}

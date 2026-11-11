package com.example.booking_app.feature.courtdetail.domain.usecase

import com.example.booking_app.feature.courtdetail.FakeVenueDetailRepository
import com.example.booking_app.feature.courtdetail.domain.model.VenueAvailability
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CheckVenueBookableUseCaseTest {

    private val repository = FakeVenueDetailRepository()
    private val useCase = CheckVenueBookableUseCase(repository)

    @Test
    fun `server tra ACTIVE thi dat duoc`() = runTest {
        repository.serverStatus = { VenueAvailability.ACTIVE }
        assertEquals(Bookable.Yes, useCase("v", VenueAvailability.ACTIVE))
        assertEquals(1, repository.fetchStatusCalls)
    }

    @Test
    fun `server tra SUSPENDED hoac GONE thi khong dat duoc`() = runTest {
        repository.serverStatus = { VenueAvailability.SUSPENDED }
        assertEquals(Bookable.No(VenueAvailability.SUSPENDED), useCase("v", VenueAvailability.ACTIVE))
        repository.serverStatus = { VenueAvailability.GONE }
        assertEquals(Bookable.No(VenueAvailability.GONE), useCase("v", VenueAvailability.ACTIVE))
    }

    @Test
    fun `mat mang thi dung trang thai moi nhat tu listener`() = runTest {
        repository.serverStatus = { null }
        assertEquals(Bookable.Yes, useCase("v", VenueAvailability.ACTIVE))
        assertEquals(Bookable.No(VenueAvailability.SUSPENDED), useCase("v", VenueAvailability.SUSPENDED))
    }

    @Test
    fun `qua 15 giay coi nhu mat mang`() = runTest {
        repository.serverStatus = {
            delay(20_000)
            VenueAvailability.SUSPENDED
        }
        assertEquals(Bookable.Yes, useCase("v", VenueAvailability.ACTIVE))
        assertEquals(15_000, testScheduler.currentTime)
    }
}

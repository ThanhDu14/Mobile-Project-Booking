package com.example.booking_app.feature.booking.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.feature.booking.data.datasource.BookingRemoteDataSource
import com.example.booking_app.feature.booking.data.model.CourtDto
import com.example.booking_app.feature.booking.data.model.PriceRuleDto
import com.example.booking_app.feature.booking.data.model.SlotDto
import com.example.booking_app.feature.booking.data.repository.BookingRepositoryImpl
import com.example.booking_app.feature.booking.domain.model.HoldResult
import com.google.firebase.firestore.FirebaseFirestoreException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class BookingRepositoryImplTest {

    private val remoteDataSource: BookingRemoteDataSource = mockk()
    private lateinit var repository: BookingRepositoryImpl

    private val venueId = "demo-venue-01"

    @Before
    fun setUp() {
        repository = BookingRepositoryImpl(remoteDataSource)
    }

    @Test
    fun getVenueOperatingHours_success_returnsPair() = runTest {
        coEvery { remoteDataSource.getVenueOperatingHours(venueId) } returns Pair("0600", "2200")

        val result = repository.getVenueOperatingHours(venueId)

        assertTrue(result.isSuccess)
        assertEquals(Pair("0600", "2200"), result.getOrNull())
    }

    @Test
    fun getActiveCourts_mapsDtosToDomainEntities() = runTest {
        val dtos = listOf(
            CourtDto(id = "c1", name = "Sân 1", surfaceType = "PU", isActive = true, sortOrder = 1),
            CourtDto(id = "c2", name = "Sân 2", surfaceType = "PU", isActive = true, sortOrder = 2)
        )
        coEvery { remoteDataSource.getActiveCourts(venueId) } returns dtos

        val result = repository.getActiveCourts(venueId)

        assertTrue(result.isSuccess)
        val courts = result.getOrNull()!!
        assertEquals(2, courts.size)
        assertEquals("c1", courts[0].id)
        assertEquals("Sân 1", courts[0].name)
    }

    @Test
    fun getPriceRules_mapsDtosToDomainEntities() = runTest {
        val dtos = listOf(
            PriceRuleDto(id = "r1", daysOfWeek = listOf(1, 2), startTime = "0600", endTime = "1700", pricePerHour = 80_000L)
        )
        coEvery { remoteDataSource.getPriceRules(venueId) } returns dtos

        val result = repository.getPriceRules(venueId)

        assertTrue(result.isSuccess)
        val rules = result.getOrNull()!!
        assertEquals(1, rules.size)
        assertEquals(40_000L, rules[0].pricePerSlot)
    }

    @Test
    fun getBusySlotsRealtime_mapsDtosToTimeSlots() = runTest {
        val slotDtos = listOf(
            SlotDto(id = "c1_20261027_0600", courtId = "c1", date = "20261027", startTime = "0600", endTime = "0630", status = "BOOKED")
        )
        coEvery { remoteDataSource.getBusySlotsRealtime(venueId, "20261027") } returns flowOf(slotDtos)

        val slots = repository.getBusySlotsRealtime(venueId, "20261027").first()

        assertEquals(1, slots.size)
        assertEquals("c1_20261027_0600", slots[0].slotId)
    }

    @Test
    fun holdSlotsAtomic_delegatesToDataSource() = runTest {
        val slotIds = listOf("c1_20261027_0600")
        val holdSuccess = HoldResult.Success("req-1", 1729000000000L)
        coEvery {
            remoteDataSource.holdSlotsAtomic(venueId, "c1", "20261027", slotIds, "user-1", "req-1")
        } returns holdSuccess

        val result = repository.holdSlotsAtomic(venueId, "c1", "20261027", slotIds, "user-1", "req-1")

        assertTrue(result.isSuccess)
        assertEquals(holdSuccess, result.getOrNull())
    }

    @Test
    fun errorMapping_networkUnavailable_mapsToAppErrorNetwork() = runTest {
        val firestoreEx = FirebaseFirestoreException("Unavailable", FirebaseFirestoreException.Code.UNAVAILABLE)
        coEvery { remoteDataSource.getVenueOperatingHours(venueId) } throws firestoreEx

        val result = repository.getVenueOperatingHours(venueId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppError.Network)
    }

    @Test
    fun errorMapping_ioException_mapsToAppErrorNetwork() = runTest {
        coEvery { remoteDataSource.getVenueOperatingHours(venueId) } throws IOException("No internet")

        val result = repository.getVenueOperatingHours(venueId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppError.Network)
    }

    @Test
    fun errorMapping_permissionDenied_mapsToAppErrorNotSignedIn() = runTest {
        val firestoreEx = FirebaseFirestoreException("Permission denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        coEvery { remoteDataSource.getVenueOperatingHours(venueId) } throws firestoreEx

        val result = repository.getVenueOperatingHours(venueId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppError.NotSignedIn)
    }

    @Test
    fun errorMapping_deadlineExceeded_mapsToAppErrorTimeout() = runTest {
        val firestoreEx = FirebaseFirestoreException("Deadline exceeded", FirebaseFirestoreException.Code.DEADLINE_EXCEEDED)
        coEvery { remoteDataSource.getVenueOperatingHours(venueId) } throws firestoreEx

        val result = repository.getVenueOperatingHours(venueId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppError.Timeout)
    }

    @Test
    fun errorMapping_aborted_mapsToAppErrorConflict() = runTest {
        val firestoreEx = FirebaseFirestoreException("Transaction aborted", FirebaseFirestoreException.Code.ABORTED)
        coEvery { remoteDataSource.getVenueOperatingHours(venueId) } throws firestoreEx

        val result = repository.getVenueOperatingHours(venueId)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppError.Conflict)
    }

    @Test
    fun getVenueInfo_success_returnsVenueInfo() = runTest {
        val expectedInfo = com.example.booking_app.feature.booking.domain.model.VenueInfo(
            id = venueId,
            name = "CLB Cầu Lông Trường Chinh 909",
            address = "909/6 Trường Chinh, Quận Tân Phú",
            districtName = "Tân Phú",
            openTime = "0500",
            closeTime = "2400"
        )
        coEvery { remoteDataSource.getVenueInfo(venueId) } returns expectedInfo

        val result = repository.getVenueInfo(venueId)

        assertTrue(result.isSuccess)
        assertEquals(expectedInfo, result.getOrNull())
    }
}

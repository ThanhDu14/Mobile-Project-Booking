package com.example.booking_app.feature.owner.ui.vouchers

import com.example.booking_app.feature.owner.domain.OwnerVenueRepository
import com.example.booking_app.feature.owner.domain.voucher.OwnerVoucher
import com.example.booking_app.feature.owner.domain.voucher.OwnerVoucherContractUnavailableException
import com.example.booking_app.feature.owner.domain.voucher.OwnerVoucherDraft
import com.example.booking_app.feature.owner.domain.voucher.OwnerVoucherRepository
import com.example.booking_app.feature.owner.domain.voucher.OwnerVoucherType
import com.example.booking_app.feature.owner.fixture.OwnerFixtures
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OwnerVoucherViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val venueRepository = mockk<OwnerVenueRepository>()
    private val voucherRepository = FakeVoucherRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { venueRepository.getOwnedVenues() } returns Result.success(listOf(OwnerFixtures.venue))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun initLoadsOwnedVenuesAndSelectsFirst() = runTest {
        val viewModel = OwnerVoucherViewModel(venueRepository, voucherRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(OwnerFixtures.venue.id, state.selectedVenueId)
        assertEquals(1, state.venues.size)
        assertFalse(state.isLoading)
    }

    @Test
    fun createInvalidDraftSetsValidationErrorsWithoutCallingRepository() = runTest {
        val viewModel = OwnerVoucherViewModel(venueRepository, voucherRepository)
        advanceUntilIdle()

        val invalid = OwnerVoucherDraft(
            code = "lowercase",
            type = OwnerVoucherType.PERCENT,
            value = 150L,
            maxDiscountVnd = -1L,
            minOrderTotalVnd = 0L,
            validFrom = LocalDate.parse("2026-12-10"),
            validTo = LocalDate.parse("2026-12-01"),
            usageLimit = null,
            perUserLimit = null,
            isActive = true,
        )

        viewModel.create("venue-1", invalid)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.validationErrors.isNotEmpty())
        assertEquals(null, voucherRepository.lastCreatedDraft)
    }

    @Test
    fun createValidDraftCallsRepository() = runTest {
        val viewModel = OwnerVoucherViewModel(venueRepository, voucherRepository)
        advanceUntilIdle()

        val valid = OwnerVoucherDraft(
            code = "SUMMER26",
            type = OwnerVoucherType.PERCENT,
            value = 20L,
            maxDiscountVnd = 50_000L,
            minOrderTotalVnd = 200_000L,
            validFrom = LocalDate.parse("2026-06-01"),
            validTo = LocalDate.parse("2026-06-30"),
            usageLimit = 100,
            perUserLimit = 2,
            isActive = true,
        )

        viewModel.create("venue-1", valid)
        advanceUntilIdle()

        assertEquals(valid, voucherRepository.lastCreatedDraft)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun setActiveDelegatesToRepository() = runTest {
        val viewModel = OwnerVoucherViewModel(venueRepository, voucherRepository)
        advanceUntilIdle()

        viewModel.setActive("venue-1", "VOUCHER10", false)
        advanceUntilIdle()

        assertEquals("VOUCHER10", voucherRepository.lastToggledCode)
        assertEquals(false, voucherRepository.lastToggledActive)
    }

    @Test
    fun contractUnavailableHandledGracefully() = runTest {
        voucherRepository.shouldThrowContractError = true
        val viewModel = OwnerVoucherViewModel(venueRepository, voucherRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("chưa được", ignoreCase = true))
    }

    private class FakeVoucherRepository : OwnerVoucherRepository {
        val vouchersFlow = MutableSharedFlow<List<OwnerVoucher>>(replay = 1).apply { tryEmit(emptyList()) }
        var shouldThrowContractError = false
        var lastCreatedDraft: OwnerVoucherDraft? = null
        var lastToggledCode: String? = null
        var lastToggledActive: Boolean? = null

        override fun observeVouchers(venueId: String): Flow<List<OwnerVoucher>> = flow {
            if (shouldThrowContractError) {
                throw OwnerVoucherContractUnavailableException()
            }
            vouchersFlow.collect { emit(it) }
        }

        override suspend fun create(venueId: String, draft: OwnerVoucherDraft): Result<Unit> {
            lastCreatedDraft = draft
            return Result.success(Unit)
        }

        override suspend fun update(venueId: String, code: String, draft: OwnerVoucherDraft): Result<Unit> =
            Result.success(Unit)

        override suspend fun setActive(venueId: String, code: String, isActive: Boolean): Result<Unit> {
            lastToggledCode = code
            lastToggledActive = isActive
            return Result.success(Unit)
        }
    }
}

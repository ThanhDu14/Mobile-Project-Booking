package com.example.booking_app.feature.admin

import com.example.booking_app.feature.admin.domain.model.AdminUserLookup
import com.example.booking_app.feature.admin.domain.model.AdminVenueItem
import com.example.booking_app.feature.admin.domain.model.OwnerRevocationDecision
import com.example.booking_app.feature.admin.domain.model.UserAccountStatus
import com.example.booking_app.feature.admin.domain.repository.AdminUserVenueRepository
import com.example.booking_app.feature.admin.ui.AdminActionUiState
import com.example.booking_app.feature.admin.ui.AdminUserVenueViewModel
import com.example.booking_app.feature.admin.ui.UserLookupUiState
import com.example.booking_app.feature.admin.ui.VenueLookupUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminUserVenueViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeAdminUserVenueRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAdminUserVenueRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun searchUser_withBlankQuery_emitsError() = runTest {
        val vm = AdminUserVenueViewModel(fakeRepository)
        vm.searchUser("   ")
        advanceUntilIdle()

        val state = vm.userLookupState.value
        assertTrue(state is UserLookupUiState.Error)
        assertEquals("Vui lòng nhập số điện thoại hoặc mã người dùng", (state as UserLookupUiState.Error).message)
    }

    @Test
    fun searchUser_whenUserExists_emitsSuccess() = runTest {
        val sampleUser = AdminUserLookup(
            uid = "u-123",
            phone = "0901234567",
            fullName = "Le Van B",
            role = "OWNER",
            ownerStatus = "APPROVED",
            accountStatus = UserAccountStatus.ACTIVE,
        )
        fakeRepository.userToReturn = sampleUser

        val vm = AdminUserVenueViewModel(fakeRepository)
        vm.searchUser("0901234567")
        advanceUntilIdle()

        val state = vm.userLookupState.value
        assertTrue(state is UserLookupUiState.Success)
        assertEquals("u-123", (state as UserLookupUiState.Success).user.uid)
        assertEquals(UserAccountStatus.ACTIVE, state.user.accountStatus)
    }

    @Test
    fun searchUser_whenUserNotFound_emitsNotFound() = runTest {
        fakeRepository.userToReturn = null

        val vm = AdminUserVenueViewModel(fakeRepository)
        vm.searchUser("0999999999")
        advanceUntilIdle()

        assertTrue(vm.userLookupState.value is UserLookupUiState.NotFound)
    }

    @Test
    fun toggleAccountLock_fromActiveToLocked_callsRepositoryAndEmitsSuccess() = runTest {
        val sampleUser = AdminUserLookup(
            uid = "u-456",
            phone = "0911223344",
            fullName = "Tran Van C",
            role = "PLAYER",
            ownerStatus = "NONE",
            accountStatus = UserAccountStatus.ACTIVE,
        )
        fakeRepository.userToReturn = sampleUser

        val vm = AdminUserVenueViewModel(fakeRepository)
        vm.searchUser("0911223344")
        advanceUntilIdle()

        vm.toggleAccountLock(sampleUser)
        advanceUntilIdle()

        assertEquals("u-456", fakeRepository.lastStatusUid)
        assertEquals(UserAccountStatus.LOCKED, fakeRepository.lastAccountStatus)
        assertTrue(vm.actionState.value is AdminActionUiState.Success)
    }

    @Test
    fun toggleAccountLock_fromLockedToActive_callsRepositoryWithActive() = runTest {
        val lockedUser = AdminUserLookup(
            uid = "u-789",
            phone = "0988776655",
            fullName = "Pham Van D",
            role = "PLAYER",
            ownerStatus = "NONE",
            accountStatus = UserAccountStatus.LOCKED,
        )

        val vm = AdminUserVenueViewModel(fakeRepository)
        vm.toggleAccountLock(lockedUser)
        advanceUntilIdle()

        assertEquals("u-789", fakeRepository.lastStatusUid)
        assertEquals(UserAccountStatus.ACTIVE, fakeRepository.lastAccountStatus)
        assertTrue(vm.actionState.value is AdminActionUiState.Success)
    }

    @Test
    fun revokeOwner_withBlankReason_emitsError() = runTest {
        val vm = AdminUserVenueViewModel(fakeRepository)
        vm.revokeOwner("u-123", "   ")
        advanceUntilIdle()

        val state = vm.actionState.value
        assertTrue(state is AdminActionUiState.Error)
        assertEquals("Lý do thu hồi không được để trống", (state as AdminActionUiState.Error).message)
    }

    @Test
    fun revokeOwner_withValidReason_callsRepoAndEmitsSuccess() = runTest {
        val vm = AdminUserVenueViewModel(fakeRepository)
        vm.revokeOwner("u-123", "Vi phạm hợp đồng")
        advanceUntilIdle()

        assertEquals("u-123", fakeRepository.lastRevokedDecision?.uid)
        assertEquals("Vi phạm hợp đồng", fakeRepository.lastRevokedDecision?.reason)
        assertTrue(vm.actionState.value is AdminActionUiState.Success)
    }

    @Test
    fun searchVenue_whenVenueExists_emitsSuccess() = runTest {
        val venue = AdminVenueItem(
            venueId = "v-1",
            name = "Sân Cầu Lông Star",
            address = "123 Le Loi",
            status = "LOCKED",
            ownerId = "owner-1",
        )
        fakeRepository.venueToReturn = venue

        val vm = AdminUserVenueViewModel(fakeRepository)
        vm.searchVenue("v-1")
        advanceUntilIdle()

        val state = vm.venueLookupState.value
        assertTrue(state is VenueLookupUiState.Success)
        assertEquals("v-1", (state as VenueLookupUiState.Success).venue.venueId)
        assertTrue(state.venue.isLocked)
    }

    @Test
    fun setVenueStatus_unlocksVenue_callsRepoAndEmitsSuccess() = runTest {
        val vm = AdminUserVenueViewModel(fakeRepository)
        vm.setVenueStatus("v-1", "ACTIVE")
        advanceUntilIdle()

        assertEquals("v-1", fakeRepository.lastVenueId)
        assertEquals("ACTIVE", fakeRepository.lastVenueStatus)
        assertTrue(vm.actionState.value is AdminActionUiState.Success)
    }
}

private class FakeAdminUserVenueRepository : AdminUserVenueRepository {
    var userToReturn: AdminUserLookup? = null
    var venueToReturn: AdminVenueItem? = null

    var lastStatusUid: String? = null
    var lastAccountStatus: UserAccountStatus? = null

    var lastRevokedDecision: OwnerRevocationDecision? = null

    var lastVenueId: String? = null
    var lastVenueStatus: String? = null

    override suspend fun lookupUser(query: String): Result<AdminUserLookup?> {
        return Result.success(userToReturn)
    }

    override suspend fun setAccountStatus(uid: String, status: UserAccountStatus): Result<Unit> {
        lastStatusUid = uid
        lastAccountStatus = status
        return Result.success(Unit)
    }

    override suspend fun revokeOwner(decision: OwnerRevocationDecision): Result<Unit> {
        lastRevokedDecision = decision
        return Result.success(Unit)
    }

    override suspend fun getVenue(venueId: String): Result<AdminVenueItem?> {
        return Result.success(venueToReturn)
    }

    override suspend fun setVenueStatus(venueId: String, status: String): Result<Unit> {
        lastVenueId = venueId
        lastVenueStatus = status
        return Result.success(Unit)
    }
}

package com.example.booking_app.feature.account.ui.edit

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.AuthUser
import com.example.booking_app.core.session.FakeSessionAuthSource
import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.core.session.MyProfile
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.session.UserProfileReader
import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.account.FakeAccountRepository
import com.example.booking_app.feature.account.FakeAvatarImageProcessor
import com.example.booking_app.feature.account.FakeAvatarRepository
import com.example.booking_app.feature.account.FakeDistrictRepository
import com.example.booking_app.feature.account.domain.model.AccountError
import com.example.booking_app.feature.account.domain.model.PlayerLevel
import com.example.booking_app.feature.account.domain.usecase.ChangeAvatarUseCase
import com.example.booking_app.feature.account.domain.usecase.RemoveAvatarUseCase
import com.example.booking_app.feature.account.domain.usecase.SaveProfileUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val session = FakeSessionManager(SessionState.SignedIn("u1", AppRole.PLAYER, emailVerified = false))
    private val authSource = FakeSessionAuthSource().apply {
        authState.value = AuthUser("u1", "nam@example.com", null, "Nam", listOf("password"), isEmailVerified = false)
    }
    private val profile = MutableStateFlow<MyProfile?>(
        MyProfile(
            displayName = "Lê Hoàng Nam", level = null, favoriteDistrictIds = listOf("q10", "q-cu"),
            avatarPath = OLD_AVATAR, role = "PLAYER", accountStatus = "ACTIVE", email = "nam@example.com",
        ),
    )
    private val reader = object : UserProfileReader {
        override fun observeMyProfile() = profile
    }
    private val account = FakeAccountRepository().apply { avatarPath = OLD_AVATAR }
    private val avatars = FakeAvatarRepository()
    private val districts = FakeDistrictRepository()
    private val processor = FakeAvatarImageProcessor()

    private fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()): EditProfileViewModel {
        val vm = EditProfileViewModel(
            savedStateHandle = handle,
            profileReader = reader,
            authSource = authSource,
            districtRepository = districts,
            saveProfile = SaveProfileUseCase(account),
            changeAvatar = ChangeAvatarUseCase(avatars, account, session, FakeClock { NOW }),
            removeAvatar = RemoveAvatarUseCase(avatars, account),
            imageProcessor = processor,
        )
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `nap ho so, khu vuc da ngung van hien ten`() = runTest(mainRule.dispatcher) {
        val state = viewModel().uiState.value
        assertEquals("Lê Hoàng Nam", state.draft.displayName)
        assertEquals(listOf("q10", "q-cu"), state.draft.districtIds)
        assertEquals(listOf("Quận 10", "Quận cũ"), state.selectedDistricts.map { it.name })
        assertFalse(state.selectedDistricts[1].isActive)
        assertFalse(state.emailVerified)
        assertFalse(state.isDirty)
    }

    @Test
    fun `isDirty khi khac ban da luu, sua roi tra lai thi het`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        vm.onNameChange("Lê Hoàng Nam A")
        assertTrue(vm.uiState.value.isDirty)
        vm.onNameChange("Lê Hoàng Nam ")
        assertFalse(vm.uiState.value.isDirty)
        vm.onLevelSelected(PlayerLevel.INTERMEDIATE)
        assertTrue(vm.uiState.value.isDirty)
        vm.onLevelSelected(null)
        assertFalse(vm.uiState.value.isDirty)
    }

    @Test
    fun `ten sai do dai theo NameRule thi bao loi va khong luu`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        vm.onNameChange(" A ")
        vm.onSave()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.nameError)
        assertTrue(account.updates.isEmpty())
        vm.onNameChange("An")
        assertFalse(vm.uiState.value.nameError)
    }

    @Test
    fun `bam Luu 3 lan lien tiep chi goi luu 1 lan, isSaving khoa nut`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        val gate = CompletableDeferred<Unit>()
        account.updateGate = gate
        vm.onLevelSelected(PlayerLevel.ADVANCED)
        vm.onSave()
        runCurrent()
        assertTrue(vm.uiState.value.isSaving)
        vm.onSave()
        vm.onSave()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, account.updates.size)
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun `luu thanh cong thi bao Da luu va saved = draft`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        vm.events.test {
            vm.onNameChange(" Trần Thu Hà ")
            vm.onDistrictsPicked(listOf("q1", "q3"))
            vm.onSave()
            advanceUntilIdle()
            assertEquals(EditProfileEvent.Saved, awaitItem())
        }
        val state = vm.uiState.value
        assertEquals("Trần Thu Hà", state.saved.displayName)
        assertEquals(listOf("q1", "q3"), state.saved.districtIds)
        assertFalse(state.isDirty)
    }

    @Test
    fun `mat mang khi luu thi giu draft va bao loi kem thu lai`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        account.updateError = AccountError.Network
        vm.events.test {
            vm.onLevelSelected(PlayerLevel.BEGINNER)
            vm.onSave()
            advanceUntilIdle()
            assertEquals(EditProfileEvent.SaveFailed(AccountError.Network), awaitItem())
        }
        assertEquals(PlayerLevel.BEGINNER, vm.uiState.value.draft.level)
        assertTrue(vm.uiState.value.isDirty)
    }

    @Test
    fun `danh muc khu vuc loi thi cac o khac van sua va luu duoc, Thu lai tai lai`() = runTest(mainRule.dispatcher) {
        districts.error = AccountError.Network
        val vm = viewModel()
        assertEquals(DistrictsState.Error, vm.uiState.value.districtsState)
        vm.onNameChange("Trần Thu Hà")
        vm.onSave()
        advanceUntilIdle()
        assertEquals(1, account.updates.size)

        districts.error = null
        vm.retryDistricts()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.districtsState is DistrictsState.Content)
    }

    @Test
    fun `chon qua 5 khu vuc thi chi giu 5, bo mot khu vuc`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        vm.onDistrictsPicked(listOf("q1", "q3", "q10", "a", "b", "c"))
        assertEquals(5, vm.uiState.value.draft.districtIds.size)
        assertFalse(vm.uiState.value.canAddDistrict)
        vm.onRemoveDistrict("q1")
        assertEquals(listOf("q3", "q10", "a", "b"), vm.uiState.value.draft.districtIds)
        assertTrue(vm.uiState.value.canAddDistrict)
    }

    @Test
    fun `Back khi co thay doi thi hoi bo thay doi, khong doi thi thoat`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        vm.events.test {
            vm.onBack()
            runCurrent()
            assertEquals(EditProfileEvent.Exit, awaitItem())
            vm.onLevelSelected(PlayerLevel.BEGINNER)
            vm.onBack()
            runCurrent()
            assertEquals(EditProfileEvent.ConfirmDiscard, awaitItem())
        }
    }

    @Test
    fun `xoay man hinh giu draft qua SavedStateHandle`() = runTest(mainRule.dispatcher) {
        val handle = SavedStateHandle()
        viewModel(handle).apply {
            onNameChange("Tên đang sửa")
            onLevelSelected(PlayerLevel.ADVANCED)
            onDistrictsPicked(listOf("q1"))
        }
        val restored = viewModel(handle).uiState.value
        assertEquals("Tên đang sửa", restored.draft.displayName)
        assertEquals(PlayerLevel.ADVANCED, restored.draft.level)
        assertEquals(listOf("q1"), restored.draft.districtIds)
        assertEquals("Lê Hoàng Nam", restored.saved.displayName)
        assertTrue(restored.isDirty)
    }

    @Test
    fun `doi anh - Processing, Uploading roi Idle voi duong dan moi`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        val gate = CompletableDeferred<Unit>()
        avatars.uploadGate = gate
        vm.uiState.test {
            assertEquals(AvatarState.Idle, awaitItem().avatar)
            vm.onAvatarCropped("content://crop/1")
            assertEquals(AvatarState.Processing, awaitItem().avatar)
            assertEquals(AvatarState.Uploading, awaitItem().avatar)
            gate.complete(Unit)
            val done = awaitItem()
            assertEquals(AvatarState.Idle, done.avatar)
            assertEquals(NEW_AVATAR, done.avatarPath)
        }
        assertEquals(listOf("content://crop/1"), processor.processed)
    }

    @Test
    fun `doi anh loi thi avatar = Error, anh cu giu nguyen`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        processor.error = AccountError.UnsupportedImage
        vm.onAvatarCropped("content://crop/1")
        advanceUntilIdle()
        assertEquals(AvatarState.Error(AccountError.UnsupportedImage), vm.uiState.value.avatar)
        assertEquals(OLD_AVATAR, vm.uiState.value.avatarPath)

        processor.error = null
        avatars.uploadError = AccountError.Network
        vm.onAvatarCropped("content://crop/2")
        advanceUntilIdle()
        assertEquals(AvatarState.Error(AccountError.Network), vm.uiState.value.avatar)
        assertEquals(OLD_AVATAR, account.avatarPath)
    }

    @Test
    fun `xoa anh thi avatarPath ve null`() = runTest(mainRule.dispatcher) {
        val vm = viewModel()
        vm.onRemoveAvatar()
        advanceUntilIdle()
        assertNull(vm.uiState.value.avatarPath)
        assertNull(account.avatarPath)
    }

    private companion object {
        const val NOW = 1_760_500_000_000L
        const val OLD_AVATAR = "u1/avatar_1760000000000.jpg"
        const val NEW_AVATAR = "u1/avatar_1760500000000.jpg"
    }
}

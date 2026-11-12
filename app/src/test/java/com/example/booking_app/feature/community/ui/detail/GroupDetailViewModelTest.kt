package com.example.booking_app.feature.community.ui.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.community.ME
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.GroupRepository
import com.example.booking_app.feature.community.domain.model.GroupMembership
import com.example.booking_app.feature.community.domain.model.GroupRole
import com.example.booking_app.feature.community.group
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** T029: trang nhóm (khung US1). */
class GroupDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val groups = mockk<GroupRepository>()
    private val session = mockk<SessionManager> { every { currentUid() } returns ME }

    private fun viewModel() = GroupDetailViewModel(SavedStateHandle(mapOf("groupId" to "g1")), groups, session)

    @Test
    fun `nhom khong con thi NotFound`() = runTest {
        every { groups.observeGroup("g1") } returns flowOf(null)
        every { groups.observeMembership("g1") } returns flowOf(GroupMembership.NONE)

        viewModel().state.test {
            assertEquals(GroupDetailUiState.Loading, awaitItem())
            assertEquals(GroupDetailUiState.NotFound, awaitItem())
        }
    }

    @Test
    fun `chu nhom thay nut Sua nhom`() = runTest {
        every { groups.observeGroup("g1") } returns flowOf(group(ownerId = ME))
        every { groups.observeMembership("g1") } returns flowOf(GroupMembership(GroupRole.OWNER, false))

        viewModel().state.test {
            awaitItem()
            assertTrue((awaitItem() as GroupDetailUiState.Content).canEdit)
        }
    }

    @Test
    fun `thanh vien thuong khong thay nut Sua nhom`() = runTest {
        every { groups.observeGroup("g1") } returns flowOf(group(ownerId = "other"))
        every { groups.observeMembership("g1") } returns flowOf(GroupMembership(GroupRole.MEMBER, false))

        viewModel().state.test {
            awaitItem()
            assertFalse((awaitItem() as GroupDetailUiState.Content).canEdit)
        }
    }

    @Test
    fun `doc membership loi van hien nhom va suy ra chu nhom tu ownerId`() = runTest {
        every { groups.observeGroup("g1") } returns flowOf(group(ownerId = ME))
        every { groups.observeMembership("g1") } returns flow { throw CommunityError.Network() }

        viewModel().state.test {
            awaitItem()
            assertTrue((awaitItem() as GroupDetailUiState.Content).canEdit)
        }
    }

    @Test
    fun `doc nhom loi thi Error roi thu lai duoc`() = runTest {
        var calls = 0
        every { groups.observeGroup("g1") } answers {
            if (calls++ == 0) flow { throw CommunityError.Network() } else flowOf(group())
        }
        every { groups.observeMembership("g1") } returns flowOf(GroupMembership.NONE)

        val vm = viewModel()
        vm.state.test {
            assertEquals(GroupDetailUiState.Loading, awaitItem())
            assertTrue(awaitItem() is GroupDetailUiState.Error)
            vm.retry()
            assertEquals(GroupDetailUiState.Loading, awaitItem())
            assertTrue(awaitItem() is GroupDetailUiState.Content)
        }
    }
}

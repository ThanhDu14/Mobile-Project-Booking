package com.example.booking_app.feature.community.ui.home

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.catalog.CatalogRepository
import com.example.booking_app.core.catalog.District
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.community.ui.common.DistrictNames
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.community.ME
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.model.GroupStatus
import com.example.booking_app.feature.community.domain.usecase.ObserveMyGroups
import com.example.booking_app.feature.community.group
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** T023: màn "Cộng đồng", tab "Nhóm của tôi". */
class CommunityHomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val observeMyGroups = mockk<ObserveMyGroups>()
    private val session = mockk<SessionManager> { every { currentUid() } returns ME }
    private val catalog = mockk<CatalogRepository> { coEvery { districts() } returns listOf(District("q10", "Quận 10", 1, true)) }

    private fun viewModel() = CommunityHomeViewModel(SavedStateHandle(), observeMyGroups, session, DistrictNames(catalog))

    @Test
    fun `Loading roi Content co vai tro va so thanh vien`() = runTest {
        every { observeMyGroups() } returns flowOf(
            listOf(
                group(id = "a", ownerId = ME, memberCount = 48, pendingRequestCount = 2),
                group(id = "b", ownerId = "other", memberCount = 12),
            ),
        )

        viewModel().myGroups.test {
            assertEquals(MyGroupsUiState.Loading, awaitItem())
            val content = awaitItem() as MyGroupsUiState.Content
            assertEquals(listOf("a", "b"), content.cards.map { it.group.id })
            assertTrue(content.cards[0].isOwner)
            assertEquals(2, content.cards[0].group.pendingRequestCount)
            assertFalse(content.cards[1].isOwner)
            assertEquals(12, content.cards[1].group.memberCount)
        }
    }

    @Test
    fun `bo nhom DELETED va nhom LOCKED co nhan khoa`() = runTest {
        every { observeMyGroups() } returns flowOf(
            listOf(
                group(id = "a"),
                group(id = "del", status = GroupStatus.DELETED),
                group(id = "lock", status = GroupStatus.LOCKED),
            ),
        )

        val vm = viewModel()
        vm.myGroups.test {
            awaitItem()
            val content = awaitItem() as MyGroupsUiState.Content
            assertEquals(listOf("a", "lock"), content.cards.map { it.group.id })
            assertFalse(content.cards[0].isLocked)
            assertTrue(content.cards[1].isLocked)
        }
    }

    @Test
    fun `khong co nhom nao thi Empty va nut Tao nhom mo form`() = runTest {
        every { observeMyGroups() } returns flowOf(emptyList())
        val vm = viewModel()

        vm.myGroups.test {
            awaitItem()
            assertEquals(MyGroupsUiState.Empty, awaitItem())
        }
        vm.events.test {
            vm.onCreateClicked()
            assertEquals(CommunityHomeEvent.OpenCreateForm, awaitItem())
        }
    }

    @Test
    fun `chi con nhom DELETED cung la Empty`() = runTest {
        every { observeMyGroups() } returns flowOf(listOf(group(status = GroupStatus.DELETED)))
        viewModel().myGroups.test {
            awaitItem()
            assertEquals(MyGroupsUiState.Empty, awaitItem())
        }
    }

    @Test
    fun `loi thi Error roi Thu lai ve Content`() = runTest {
        var calls = 0
        every { observeMyGroups() } answers {
            if (calls++ == 0) flow { throw CommunityError.Network() } else flowOf(listOf(group()))
        }
        val vm = viewModel()

        vm.myGroups.test {
            assertEquals(MyGroupsUiState.Loading, awaitItem())
            val error = awaitItem() as MyGroupsUiState.Error
            assertTrue(error.cause is CommunityError.Network)
            vm.retry()
            assertEquals(MyGroupsUiState.Loading, awaitItem())
            assertTrue(awaitItem() is MyGroupsUiState.Content)
        }
    }

    @Test
    fun `nhom thay doi realtime thi danh sach cap nhat`() = runTest {
        val source = MutableStateFlow(listOf(group(id = "a")))
        every { observeMyGroups() } returns source
        val vm = viewModel()
        vm.myGroups.test {
            awaitItem()
            assertEquals(1, (awaitItem() as MyGroupsUiState.Content).cards.size)
            source.value = listOf(group(id = "a"), group(id = "b"))
            assertEquals(2, (awaitItem() as MyGroupsUiState.Content).cards.size)
        }
    }

    @Test
    fun `bam the nhom mo GroupDetail va khach bam Tao nhom thi can dang nhap`() = runTest {
        every { observeMyGroups() } returns flowOf(emptyList())
        val guest = mockk<SessionManager> { every { currentUid() } returns null }
        val vm = CommunityHomeViewModel(SavedStateHandle(), observeMyGroups, guest, DistrictNames(catalog))
        advanceUntilIdle()

        vm.events.test {
            vm.onGroupClicked("g5")
            assertEquals(CommunityHomeEvent.OpenGroup("g5"), awaitItem())
            vm.onCreateClicked()
            assertEquals(CommunityHomeEvent.RequireLogin, awaitItem())
        }
    }

    @Test
    fun `tab duoc giu qua SavedStateHandle`() = runTest {
        every { observeMyGroups() } returns flowOf(emptyList())
        val handle = SavedStateHandle()
        val vm = CommunityHomeViewModel(handle, observeMyGroups, session, DistrictNames(catalog))
        vm.selectTab(CommunityHomeTab.DISCOVER)
        assertEquals(CommunityHomeTab.DISCOVER, CommunityHomeViewModel(handle, observeMyGroups, session, DistrictNames(catalog)).selectedTab.value)
    }
}

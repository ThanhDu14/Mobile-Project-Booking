package com.example.booking_app.feature.dropin.ui.mine

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.dropin.domain.model.DropInCursor
import com.example.booking_app.feature.dropin.domain.model.DropInError
import com.example.booking_app.feature.dropin.domain.model.MyDropInItem
import com.example.booking_app.feature.dropin.domain.model.Page
import com.example.booking_app.feature.dropin.domain.model.RegistrationOutcome
import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import com.example.booking_app.feature.dropin.domain.usecase.LoadMyDropIns
import com.example.booking_app.feature.dropin.ui.NOW
import com.example.booking_app.feature.dropin.ui.registration
import com.example.booking_app.feature.dropin.ui.session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** T043: "Lượt vãng lai của tôi" (US3). */
@OptIn(ExperimentalCoroutinesApi::class)
class MyDropInsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Fake usecase: "Sắp tới" là một StateFlow đổi được, "Đã qua" trả lần lượt theo hàng đợi. */
    private class FakeLoadMyDropIns : LoadMyDropIns {
        var upcomingFlow: Flow<List<MyDropInItem>> = MutableStateFlow(emptyList())
        val pastResults = ArrayDeque<Result<Page<MyDropInItem>>>()
        val pastCursors = mutableListOf<DropInCursor?>()

        override fun upcoming(): Flow<List<MyDropInItem>> = upcomingFlow

        override suspend fun past(cursor: DropInCursor?): Result<Page<MyDropInItem>> {
            pastCursors += cursor
            return pastResults.removeFirstOrNull() ?: Result.success(Page(emptyList(), null))
        }
    }

    private val load = FakeLoadMyDropIns()

    private fun viewModel() = MyDropInsViewModel(SavedStateHandle(), load)

    /** Lượt bắt đầu sau [hoursFromNow] giờ (âm là đã qua). */
    private fun item(
        sessionId: String,
        hoursFromNow: Long,
        outcome: RegistrationOutcome = if (hoursFromNow > 0) RegistrationOutcome.UPCOMING else RegistrationOutcome.ATTENDED,
        status: RegistrationStatus = if (hoursFromNow > 0) RegistrationStatus.REGISTERED else RegistrationStatus.CHECKED_IN,
        sessionChanged: Boolean = false,
    ): MyDropInItem {
        val s = session(id = sessionId, startAt = NOW.plusSeconds(hoursFromNow * 3600))
        val r = registration(sessionId = sessionId, status = status, sessionChanged = sessionChanged)
            .copy(sessionStartAt = s.startAt, sessionEndAt = s.endAt)
        return MyDropInItem(registration = r, session = s, outcome = outcome)
    }

    private fun cursor(id: String) = DropInCursor(NOW.minusSeconds(100 * 3600L), "dropInSessions/$id/registrations/u1")

    @Test
    fun `Sap toi sap theo gio gan nhat truoc`() = runTest {
        load.upcomingFlow = MutableStateFlow(listOf(item("c", 50), item("a", 2), item("b", 10)))

        val vm = viewModel()
        assertEquals(MyDropInsUiState.Loading, vm.uiState.value)
        advanceUntilIdle()

        val content = vm.uiState.value as MyDropInsUiState.Content
        assertEquals(listOf("a", "b", "c"), content.upcoming.map { it.item.sessionId })
    }

    @Test
    fun `Sap toi cap nhat realtime`() = runTest {
        val upcoming = MutableStateFlow(listOf(item("a", 2)))
        load.upcomingFlow = upcoming
        val vm = viewModel()
        advanceUntilIdle()

        upcoming.value = listOf(item("a", 2), item("z", 1))
        advanceUntilIdle()

        val content = vm.uiState.value as MyDropInsUiState.Content
        assertEquals(listOf("z", "a"), content.upcoming.map { it.item.sessionId })
    }

    @Test
    fun `Da qua moi nhat truoc va phan trang theo con tro`() = runTest {
        load.pastResults += Result.success(Page(listOf(item("p2", -48), item("p1", -2)), cursor("p2")))
        load.pastResults += Result.success(Page(listOf(item("p4", -200), item("p3", -100)), null))

        val vm = viewModel()
        advanceUntilIdle()

        var content = vm.uiState.value as MyDropInsUiState.Content
        assertEquals(listOf("p1", "p2"), content.past.map { it.item.sessionId })
        assertTrue(content.canLoadMorePast)

        vm.loadMorePast()
        advanceUntilIdle()

        content = vm.uiState.value as MyDropInsUiState.Content
        assertEquals(listOf("p1", "p2", "p3", "p4"), content.past.map { it.item.sessionId })
        assertFalse(content.canLoadMorePast)
        assertEquals(listOf(null, cursor("p2")), load.pastCursors)
    }

    @Test
    fun `Da qua khong lap luot dang nam o Sap toi`() = runTest {
        load.upcomingFlow = MutableStateFlow(listOf(item("a", 2)))
        // Trang "Đã qua" lỡ chứa lượt "a" (truy vấn theo giờ, chưa lọc) và lặp "p1" giữa hai trang.
        load.pastResults += Result.success(Page(listOf(item("a", 2), item("p1", -2)), cursor("p1")))
        load.pastResults += Result.success(Page(listOf(item("p1", -2), item("p2", -30)), null))

        val vm = viewModel()
        advanceUntilIdle()
        vm.loadMorePast()
        advanceUntilIdle()

        val content = vm.uiState.value as MyDropInsUiState.Content
        assertEquals(listOf("a"), content.upcoming.map { it.item.sessionId })
        assertEquals(listOf("p1", "p2"), content.past.map { it.item.sessionId })
    }

    @Test
    fun `nhan ket qua o tab Da qua`() = runTest {
        load.pastResults += Result.success(
            Page(
                listOf(
                    item("p1", -2, RegistrationOutcome.ATTENDED),
                    item("p2", -30, RegistrationOutcome.NO_SHOW, RegistrationStatus.REGISTERED),
                    item("p3", -50, RegistrationOutcome.CANCELLED_BY_ME, RegistrationStatus.CANCELLED),
                    item("p4", -70, RegistrationOutcome.SESSION_CANCELLED, RegistrationStatus.CANCELLED),
                ),
                null,
            ),
        )
        val vm = viewModel()
        advanceUntilIdle()

        val content = vm.uiState.value as MyDropInsUiState.Content
        assertEquals(
            listOf(
                RegistrationOutcome.ATTENDED,
                RegistrationOutcome.NO_SHOW,
                RegistrationOutcome.CANCELLED_BY_ME,
                RegistrationOutcome.SESSION_CANCELLED,
            ),
            content.past.map { it.outcome },
        )
    }

    @Test
    fun `Empty khi chua co luot nao va nut Tim buoi vang lai mo danh sach (AC 3_6)`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(MyDropInsUiState.Empty, vm.uiState.value)
        vm.events.test {
            vm.onFindSessionsClicked()
            assertEquals(MyDropInsEvent.FindSessions, awaitItem())
        }
    }

    @Test
    fun `nhan Buoi da thay doi khi sessionChanged (AC 3_5)`() = runTest {
        load.upcomingFlow = MutableStateFlow(listOf(item("a", 2, sessionChanged = true), item("b", 5)))

        val vm = viewModel()
        advanceUntilIdle()

        val rows = (vm.uiState.value as MyDropInsUiState.Content).upcoming
        assertTrue(rows.first { it.item.sessionId == "a" }.showSessionChanged)
        assertFalse(rows.first { it.item.sessionId == "b" }.showSessionChanged)
        // Tab "Sắp tới" không hiện nhãn kết quả cho lượt còn hiệu lực.
        assertNull(rows.first().outcome)
    }

    @Test
    fun `bam mot luot mo ve cua buoi do`() = runTest {
        load.upcomingFlow = MutableStateFlow(listOf(item("a", 2)))
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onItemClicked("a")
            assertEquals(MyDropInsEvent.OpenTicket("a"), awaitItem())
        }
    }

    @Test
    fun `loi doc Sap toi thi Error, Thu lai tai lai`() = runTest {
        load.upcomingFlow = flow { throw DropInError.Network() }
        val vm = viewModel()
        advanceUntilIdle()

        val error = vm.uiState.value as MyDropInsUiState.Error
        assertTrue(error.cause is DropInError.Network)

        load.upcomingFlow = MutableStateFlow(listOf(item("a", 2)))
        vm.retry()
        advanceUntilIdle()

        val content = vm.uiState.value as MyDropInsUiState.Content
        assertEquals(listOf("a"), content.upcoming.map { it.item.sessionId })
    }

    @Test
    fun `tai them Da qua loi thi giu danh sach va bao su kien`() = runTest {
        load.pastResults += Result.success(Page(listOf(item("p1", -2)), cursor("p1")))
        load.pastResults += Result.failure(DropInError.Network())
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.loadMorePast()
            advanceUntilIdle()
            val event = awaitItem() as MyDropInsEvent.LoadMoreFailed
            assertTrue(event.cause is DropInError.Network)
        }
        val content = vm.uiState.value as MyDropInsUiState.Content
        assertEquals(listOf("p1"), content.past.map { it.item.sessionId })
        assertTrue(content.canLoadMorePast)
        assertFalse(content.loadingMorePast)
    }

    @Test
    fun `chon tab duoc giu lai`() = runTest {
        val handle = SavedStateHandle()
        val vm = MyDropInsViewModel(handle, load)
        assertEquals(MyDropInsTab.UPCOMING, vm.selectedTab.value)

        vm.selectTab(MyDropInsTab.PAST)

        assertEquals(MyDropInsTab.PAST, MyDropInsViewModel(handle, load).selectedTab.value)
    }
}

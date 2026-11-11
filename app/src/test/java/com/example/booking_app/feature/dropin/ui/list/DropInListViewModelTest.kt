package com.example.booking_app.feature.dropin.ui.list

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.dropin.domain.model.DropInCursor
import com.example.booking_app.feature.dropin.domain.model.DropInError
import com.example.booking_app.feature.dropin.domain.model.DropInFilter
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.example.booking_app.feature.dropin.domain.model.Level
import com.example.booking_app.feature.dropin.domain.model.Page
import com.example.booking_app.feature.dropin.domain.model.TimeSlot
import com.example.booking_app.feature.dropin.domain.usecase.GetDefaultDropInFilter
import com.example.booking_app.feature.dropin.domain.usecase.LoadDropInPage
import com.example.booking_app.feature.dropin.ui.NOW
import com.example.booking_app.feature.dropin.ui.session
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** T018: danh sách buổi vãng lai (US1). */
@OptIn(ExperimentalCoroutinesApi::class)
class DropInListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Lời gọi `LoadDropInPage` đã nhận (bộ lọc, con trỏ). */
    private val calls = mutableListOf<Pair<DropInFilter, DropInCursor?>>()

    /** Kết quả trả lần lượt cho từng lời gọi; hết thì trả trang rỗng. */
    private val results = ArrayDeque<Result<Page<DropInSession>>>()

    private val loadPage = LoadDropInPage { filter, cursor ->
        calls += filter to cursor
        results.removeFirstOrNull() ?: Result.success(Page(emptyList(), null))
    }

    private var defaultFilter = DropInFilter()
    private var defaultFilterCalls = 0
    private val getDefaultFilter = GetDefaultDropInFilter {
        defaultFilterCalls++
        defaultFilter
    }

    /** Bộ nhớ bộ lọc dùng chung trong phiên (singleton), mỗi test một bản mới. */
    private val memory = DropInFilterMemory()

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        DropInListViewModel(handle, loadPage, getDefaultFilter, memory)

    private fun s(id: String, hoursFromNow: Long) = session(id = id, startAt = NOW.plusSeconds(hoursFromNow * 3600))

    @Test
    fun `Loading roi Content sap theo startAt`() = runTest {
        val gate = CompletableDeferred<Result<Page<DropInSession>>>()
        val vm = DropInListViewModel(
            SavedStateHandle(),
            { _, _ -> gate.await() },
            getDefaultFilter,
            memory,
        )
        runCurrent()
        assertEquals(DropInListUiState.Loading, vm.uiState.value)

        gate.complete(Result.success(Page(listOf(s("b", 5), s("a", 2), s("c", 9)), null)))
        advanceUntilIdle()

        val content = vm.uiState.value as DropInListUiState.Content
        assertEquals(listOf("a", "b", "c"), content.items.map { it.id })
        assertFalse(content.canLoadMore)
    }

    @Test
    fun `dien san khu vuc tu ho so`() = runTest {
        defaultFilter = DropInFilter(districtIds = setOf("q10", "q3"))
        results += Result.success(Page(listOf(s("a", 2)), null))

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(setOf("q10", "q3"), vm.filter.value.districtIds)
        assertEquals(setOf("q10", "q3"), calls.single().first.districtIds)
        assertNull(calls.single().second)
    }

    @Test
    fun `nhan DropInFilterArgs tu AI thi khong dung bo loc mac dinh`() = runTest {
        defaultFilter = DropInFilter(districtIds = setOf("q1"))
        val args = DropInFilterArgs(
            districtIds = listOf("q10"),
            date = "2026-11-14",
            timeSlot = "EVENING",
            level = "INTERMEDIATE",
            maxPrice = 80_000,
        )

        val vm = viewModel(SavedStateHandle(mapOf(DropInListViewModel.ARG_FILTER to args)))
        advanceUntilIdle()

        val expected = DropInFilter(
            districtIds = setOf("q10"),
            date = LocalDate.of(2026, 11, 14),
            timeSlot = TimeSlot.EVENING,
            level = Level.INTERMEDIATE,
            maxPrice = 80_000,
        )
        assertEquals(expected, vm.filter.value)
        assertEquals(expected, calls.single().first)
        assertEquals(0, defaultFilterCalls)
    }

    @Test
    fun `bo loc da luu trong SavedStateHandle duoc dung lai khi tao lai ViewModel`() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        advanceUntilIdle()
        first.applyFilter(DropInFilterArgs(level = "ADVANCED"))
        advanceUntilIdle()

        val second = viewModel(handle)
        advanceUntilIdle()

        assertEquals(Level.ADVANCED, second.filter.value.level)
        assertEquals(1, defaultFilterCalls)
    }

    @Test
    fun `roi tab roi quay lai thi ViewModel moi dung lai bo loc da ap dung`() = runTest {
        defaultFilter = DropInFilter(districtIds = setOf("q10"))
        val first = viewModel()
        advanceUntilIdle()
        first.applyFilter(DropInFilterArgs(timeSlot = "NOON", level = "ADVANCED"))
        advanceUntilIdle()

        // Bấm tab khác rồi bấm lại "Vãng lai": back stack entry mới, SavedStateHandle rỗng.
        val second = viewModel()
        advanceUntilIdle()

        assertEquals(DropInFilter(timeSlot = TimeSlot.NOON, level = Level.ADVANCED), second.filter.value)
        assertEquals(second.filter.value, calls.last().first)
        assertEquals(1, defaultFilterCalls)
    }

    @Test
    fun `ap dung lan hai thay the bo loc lan dau`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.applyFilter(DropInFilterArgs(level = "ADVANCED"))
        advanceUntilIdle()
        vm.applyFilter(DropInFilterArgs(timeSlot = "NOON", level = "ADVANCED"))
        advanceUntilIdle()

        assertEquals(DropInFilter(timeSlot = TimeSlot.NOON, level = Level.ADVANCED), calls.last().first)
    }

    @Test
    fun `xoa het bo loc thi lan sau vao lai cung khong loc`() = runTest {
        defaultFilter = DropInFilter(districtIds = setOf("q10"))
        val first = viewModel()
        advanceUntilIdle()
        first.clearFilter()
        advanceUntilIdle()

        val second = viewModel()
        advanceUntilIdle()

        assertTrue(second.filter.value.isEmpty)
    }

    @Test
    fun `bo loc tu AI uu tien hon bo loc da nho`() = runTest {
        val first = viewModel()
        advanceUntilIdle()
        first.applyFilter(DropInFilterArgs(level = "BEGINNER"))
        advanceUntilIdle()

        val fromAi = viewModel(SavedStateHandle(mapOf(DropInListViewModel.ARG_FILTER to DropInFilterArgs(level = "ADVANCED"))))
        advanceUntilIdle()

        assertEquals(Level.ADVANCED, fromAi.filter.value.level)
    }

    @Test
    fun `loc khong co ket qua thi Empty hasFilter true`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(DropInListUiState.Empty(hasFilter = false), vm.uiState.value)

        vm.applyFilter(DropInFilterArgs(level = "BEGINNER"))
        advanceUntilIdle()

        assertEquals(DropInListUiState.Empty(hasFilter = true), vm.uiState.value)
    }

    @Test
    fun `xoa het bo loc thi tai lai voi bo loc rong`() = runTest {
        defaultFilter = DropInFilter(level = Level.BEGINNER)
        val vm = viewModel()
        advanceUntilIdle()

        vm.clearFilter()
        advanceUntilIdle()

        assertTrue(vm.filter.value.isEmpty)
        assertTrue(calls.last().first.isEmpty)
    }

    @Test
    fun `loi thi Error, thu lai thanh cong thi Content`() = runTest {
        results += Result.failure(DropInError.Network())
        results += Result.success(Page(listOf(s("a", 2)), null))
        val vm = viewModel()
        advanceUntilIdle()

        val error = vm.uiState.value as DropInListUiState.Error
        assertTrue(error.cause is DropInError.Network)

        vm.retry()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is DropInListUiState.Content)
        assertEquals(2, calls.size)
    }

    @Test
    fun `tai trang tiep theo con tro va noi vao cuoi`() = runTest {
        val firstPage = (1..Page.PAGE_SIZE).map { s("a$it", it.toLong()) }
        val cursor = DropInCursor(firstPage.last().startAt, firstPage.last().id)
        results += Result.success(Page(firstPage, cursor))
        results += Result.success(Page(listOf(s("b1", 30), s("b2", 31)), null))
        val vm = viewModel()
        advanceUntilIdle()
        val content = vm.uiState.value as DropInListUiState.Content
        assertEquals(Page.PAGE_SIZE, content.items.size)
        assertTrue(content.canLoadMore)

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(cursor, calls[1].second)
        val after = vm.uiState.value as DropInListUiState.Content
        assertEquals(Page.PAGE_SIZE + 2, after.items.size)
        assertEquals("b2", after.items.last().id)
        assertFalse(after.canLoadMore)

        // Hết trang thì không gọi thêm.
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(2, calls.size)
    }

    @Test
    fun `tai trang tiep loi thi giu danh sach va bao su kien`() = runTest {
        results += Result.success(Page(listOf(s("a", 2)), DropInCursor(NOW, "a")))
        results += Result.failure(DropInError.Network())
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.loadMore()
            advanceUntilIdle()
            assertTrue(awaitItem() is DropInListEvent.LoadMoreFailed)
        }
        val content = vm.uiState.value as DropInListUiState.Content
        assertEquals(listOf("a"), content.items.map { it.id })
        assertTrue(content.canLoadMore)
        assertFalse(content.loadingMore)
    }

    @Test
    fun `bam the thi mo chi tiet`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onSessionClicked("s9")
            assertEquals(DropInListEvent.OpenDetail("s9"), awaitItem())
        }
    }
}

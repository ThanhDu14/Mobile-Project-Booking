package com.example.booking_app.feature.community.ui.search

import androidx.lifecycle.SavedStateHandle
import com.example.booking_app.core.catalog.CatalogRepository
import com.example.booking_app.core.catalog.District
import com.example.booking_app.core.session.MyProfile
import com.example.booking_app.core.session.UserProfileReader
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.model.Page
import com.example.booking_app.feature.community.domain.model.PageCursor
import com.example.booking_app.feature.community.domain.usecase.SearchGroups
import com.example.booking_app.feature.community.group
import com.example.booking_app.feature.community.ui.common.DistrictNames
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** T031: tìm nhóm (tab "Khám phá"): chuẩn hóa từ khóa, debounce, phân trang, điền sẵn khu vực, 4 trạng thái. */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupSearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val searchGroups = mockk<SearchGroups>()
    private val profileReader = mockk<UserProfileReader>()
    private val catalog = mockk<CatalogRepository> {
        coEvery { districts() } returns listOf(District("q10", "Quận 10", 1, true), District("q1", "Quận 1", 2, true))
    }

    private fun profile(favorites: List<String>) = MyProfile(
        displayName = "A", level = null, favoriteDistrictIds = favorites, avatarPath = null, role = null, accountStatus = null,
    )

    private fun givenFavorites(vararg ids: String) {
        every { profileReader.observeMyProfile() } returns flowOf(profile(ids.toList()))
    }

    private fun givenPage(items: Int = 1, next: PageCursor? = null) {
        coEvery { searchGroups(any(), any(), any()) } returns Result.success(Page(List(items) { group(id = "g$it") }, next))
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        GroupSearchViewModel(handle, searchGroups, profileReader, DistrictNames(catalog))

    @Test
    fun `dien san khu vuc tu favoriteDistrictIds va tim ngay khi mo`() = runTest {
        givenFavorites("q10", "q1")
        givenPage()

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("q10"), vm.districtIds.value)
        coVerify(exactly = 1) { searchGroups("", listOf("q10"), null) }
        assertTrue(vm.state.value is GroupSearchUiState.Content)
    }

    @Test
    fun `khach hoac chua chon khu vuc thi tim tren moi quan`() = runTest {
        every { profileReader.observeMyProfile() } returns flowOf(null)
        givenPage()

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), vm.districtIds.value)
        coVerify { searchGroups("", emptyList(), null) }
    }

    @Test
    fun `chuan hoa tu khoa bo dau doi d thanh d chu thuong`() = runTest {
        givenFavorites()
        givenPage()
        val vm = viewModel()
        advanceUntilIdle()

        vm.onQueryChanged("  Cầu   Lông ĐÀ  ")
        advanceUntilIdle()

        coVerify(exactly = 1) { searchGroups("cau long da", emptyList(), null) }
    }

    @Test
    fun `debounce 300ms chi tim mot lan voi tu khoa cuoi`() = runTest {
        givenFavorites()
        givenPage()
        val vm = viewModel()
        advanceUntilIdle()

        vm.onQueryChanged("c")
        advanceTimeBy(100)
        vm.onQueryChanged("ca")
        advanceTimeBy(100)
        vm.onQueryChanged("cau")
        advanceTimeBy(299)
        runCurrent()
        coVerify(exactly = 0) { searchGroups("cau", any(), any()) }

        advanceTimeBy(2)
        runCurrent()
        coVerify(exactly = 1) { searchGroups("cau", any(), any()) }
        coVerify(exactly = 0) { searchGroups("c", any(), any()) }
        coVerify(exactly = 0) { searchGroups("ca", any(), any()) }
    }

    @Test
    fun `doi khu vuc thi tim ngay khong cho debounce`() = runTest {
        givenFavorites()
        givenPage()
        val vm = viewModel()
        advanceUntilIdle()

        vm.onDistrictSelected("q1")
        runCurrent()

        assertEquals(listOf("q1"), vm.districtIds.value)
        coVerify(exactly = 1) { searchGroups("", listOf("q1"), null) }
    }

    @Test
    fun `tai trang tiep theo cursor va noi vao danh sach den khi het trang`() = runTest {
        givenFavorites()
        val cursor1 = PageCursor(40, "g19")
        val cursor2 = PageCursor(20, "g39")
        coEvery { searchGroups("", emptyList(), null) } returns Result.success(Page(List(20) { group(id = "g$it") }, cursor1))
        coEvery { searchGroups("", emptyList(), cursor1) } returns Result.success(Page(List(20) { group(id = "g${20 + it}") }, cursor2))
        coEvery { searchGroups("", emptyList(), cursor2) } returns Result.success(Page(List(5) { group(id = "g${40 + it}") }, null))

        val vm = viewModel()
        advanceUntilIdle()
        assertTrue((vm.state.value as GroupSearchUiState.Content).canLoadMore)

        vm.loadMore()
        advanceUntilIdle()
        assertEquals(40, (vm.state.value as GroupSearchUiState.Content).cards.size)

        vm.loadMore()
        advanceUntilIdle()
        val content = vm.state.value as GroupSearchUiState.Content
        assertEquals(45, content.cards.size)
        assertFalse(content.canLoadMore)

        // Hết trang: gọi tiếp không gửi thêm truy vấn.
        vm.loadMore()
        advanceUntilIdle()
        coVerify(exactly = 3) { searchGroups(any(), any(), any()) }
    }

    @Test
    fun `trang sau loc con it hon 20 van cho tai tiep theo cursor`() = runTest {
        givenFavorites()
        // Còn 3 nhóm sau khi lọc các từ còn lại, nhưng next vẫn có (trang gốc đủ 20).
        givenPage(items = 3, next = PageCursor(7, "g2"))

        val vm = viewModel()
        advanceUntilIdle()

        val content = vm.state.value as GroupSearchUiState.Content
        assertEquals(3, content.cards.size)
        assertTrue(content.canLoadMore)
    }

    @Test
    fun `trang dau rong sau khi loc nhung con cursor thi tu tai tiep truoc khi bao rong`() = runTest {
        givenFavorites()
        val cursor = PageCursor(9, "g0")
        coEvery { searchGroups("", emptyList(), null) } returns Result.success(Page(emptyList(), cursor))
        coEvery { searchGroups("", emptyList(), cursor) } returns Result.success(Page(listOf(group(id = "x")), null))

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("x"), (vm.state.value as GroupSearchUiState.Content).cards.map { it.group.id })
    }

    @Test
    fun `khong co ket qua va khong co bo loc thi Empty hasFilter false`() = runTest {
        givenFavorites()
        givenPage(items = 0)

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(GroupSearchUiState.Empty(hasFilter = false), vm.state.value)
    }

    @Test
    fun `khong co ket qua khi co tu khoa hoac khu vuc thi Empty hasFilter true`() = runTest {
        givenFavorites("q10")
        givenPage(items = 0)

        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(GroupSearchUiState.Empty(hasFilter = true), vm.state.value)

        vm.onDistrictSelected(null)
        advanceUntilIdle()
        assertEquals(GroupSearchUiState.Empty(hasFilter = false), vm.state.value)

        vm.onQueryChanged("zzz")
        advanceUntilIdle()
        assertEquals(GroupSearchUiState.Empty(hasFilter = true), vm.state.value)
    }

    @Test
    fun `Bo bot bo loc xoa tu khoa va khu vuc roi tim lai`() = runTest {
        givenFavorites("q10")
        givenPage(items = 0)
        val vm = viewModel()
        advanceUntilIdle()
        vm.onQueryChanged("zzz")
        advanceUntilIdle()

        vm.clearFilters()
        advanceUntilIdle()

        assertEquals("", vm.query.value)
        assertEquals(emptyList<String>(), vm.districtIds.value)
        coVerify { searchGroups("", emptyList(), null) }
        assertEquals(GroupSearchUiState.Empty(hasFilter = false), vm.state.value)
    }

    @Test
    fun `loi tai trang dau thi Error roi retry tim lai duoc`() = runTest {
        givenFavorites()
        coEvery { searchGroups(any(), any(), any()) } returns Result.failure(CommunityError.Network())
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.state.value is GroupSearchUiState.Error)

        givenPage(items = 2)
        vm.retry()
        advanceUntilIdle()

        assertEquals(2, (vm.state.value as GroupSearchUiState.Content).cards.size)
    }

    @Test
    fun `loi khi tai them giu nguyen danh sach va cho thu lai`() = runTest {
        givenFavorites()
        val cursor = PageCursor(5, "g0")
        coEvery { searchGroups("", emptyList(), null) } returns Result.success(Page(listOf(group(id = "g0")), cursor))
        coEvery { searchGroups("", emptyList(), cursor) } returns Result.failure(CommunityError.Network())
        val vm = viewModel()
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        val content = vm.state.value as GroupSearchUiState.Content
        assertEquals(1, content.cards.size)
        assertTrue(content.canLoadMore)
        assertTrue(content.loadMoreFailed)
        assertFalse(content.loadingMore)
    }

    @Test
    fun `the nhom mang ten quan`() = runTest {
        givenFavorites()
        coEvery { searchGroups(any(), any(), any()) } returns Result.success(Page(listOf(group(districtId = "q10")), null))

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("Quận 10", (vm.state.value as GroupSearchUiState.Content).cards.single().districtName)
    }

    @Test
    fun `bo loc giu qua SavedStateHandle`() = runTest {
        givenFavorites("q10")
        givenPage()
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        advanceUntilIdle()
        first.onDistrictSelected("q1")
        first.onQueryChanged("Cầu")
        advanceUntilIdle()

        val restored = viewModel(handle)
        advanceUntilIdle()

        assertEquals("Cầu", restored.query.value)
        assertEquals(listOf("q1"), restored.districtIds.value)
    }

    @Test
    fun `nguoi dung bo khu vuc thi khong dien san lai khi tao lai ViewModel`() = runTest {
        givenFavorites("q10")
        givenPage()
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        advanceUntilIdle()
        first.onDistrictSelected(null)
        advanceUntilIdle()

        val restored = viewModel(handle)
        advanceUntilIdle()

        assertEquals(emptyList<String>(), restored.districtIds.value)
    }
}

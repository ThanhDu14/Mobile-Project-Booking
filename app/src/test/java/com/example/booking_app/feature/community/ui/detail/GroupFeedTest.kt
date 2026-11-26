package com.example.booking_app.feature.community.ui.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.catalog.CatalogRepository
import com.example.booking_app.core.media.SignedUrlBatch
import com.example.booking_app.core.media.SignedUrlCache
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.core.testing.stubSessionFromUid
import com.example.booking_app.feature.community.ME
import com.example.booking_app.feature.community.data.FirestorePostRepository
import com.example.booking_app.feature.community.data.dto.PostResultDto
import com.example.booking_app.feature.community.data.dto.UserSnapshotDto
import com.example.booking_app.feature.community.data.dto.toDomain
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.GroupRepository
import com.example.booking_app.feature.community.domain.PostRepository
import com.example.booking_app.feature.community.domain.model.AttendResult
import com.example.booking_app.feature.community.domain.model.EventInfo
import com.example.booking_app.feature.community.domain.model.GroupMembership
import com.example.booking_app.feature.community.domain.model.GroupRole
import com.example.booking_app.feature.community.domain.model.GroupStatus
import com.example.booking_app.feature.community.domain.model.HiddenBy
import com.example.booking_app.feature.community.domain.model.Page
import com.example.booking_app.feature.community.domain.model.PageCursor
import com.example.booking_app.feature.community.domain.model.Post
import com.example.booking_app.feature.community.domain.model.PostStatus
import com.example.booking_app.feature.community.domain.model.PostType
import com.example.booking_app.feature.community.domain.usecase.DeletePost
import com.example.booking_app.feature.community.domain.usecase.JoinGroup
import com.example.booking_app.feature.community.domain.usecase.LeaveGroup
import com.example.booking_app.feature.community.domain.usecase.ModeratePost
import com.example.booking_app.feature.community.domain.usecase.ToggleAttendance
import com.example.booking_app.feature.community.group
import com.example.booking_app.feature.community.noopSignedUrls
import com.example.booking_app.feature.community.post
import com.example.booking_app.feature.community.ui.common.DistrictNames
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** T043: phần bảng tin của `GroupDetailViewModel`. */
class GroupFeedTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val groups = mockk<GroupRepository>()
    private val posts = mockk<PostRepository>()
    private val session = mockk<SessionManager> {
        every { currentUid() } returns ME
        stubSessionFromUid()
    }
    private val catalog = mockk<CatalogRepository> { coEvery { districts() } returns emptyList() }

    private var nowMs: Long = Instant.parse("2026-11-10T00:00:00Z").toEpochMilli()

    private fun viewModel(signedUrls: SignedUrlCache = noopSignedUrls()) = GroupDetailViewModel(
        SavedStateHandle(mapOf("groupId" to "g1")),
        groups,
        posts,
        session,
        JoinGroup(groups),
        LeaveGroup(groups),
        DeletePost(posts),
        ModeratePost(posts),
        ToggleAttendance(posts),
        signedUrls,
        DistrictNames(catalog),
        FakeClock { nowMs },
    )

    private fun givenMember(role: GroupRole = GroupRole.MEMBER) {
        every { groups.observeGroup("g1") } returns flowOf(group(ownerId = if (role == GroupRole.OWNER) ME else "owner"))
        every { groups.observeMembership("g1") } returns flowOf(GroupMembership(role, false))
    }

    @Test
    fun `thanh vien thuong gop bai VISIBLE voi bai HIDDEN cua chinh minh, bo bai an cua nguoi khac, sap createdAt desc`() = runTest {
        givenMember(GroupRole.MEMBER)
        val t1 = Instant.parse("2026-11-10T01:00:00Z")
        val t2 = Instant.parse("2026-11-10T02:00:00Z")
        val t3 = Instant.parse("2026-11-10T03:00:00Z")
        val t4 = Instant.parse("2026-11-10T04:00:00Z")
        coEvery { posts.feed("g1", false, null) } returns Result.success(
            Page(
                listOf(
                    post(id = "p1", createdAt = t1, authorId = "other", status = PostStatus.VISIBLE),
                    post(id = "p2", createdAt = t3, authorId = ME, status = PostStatus.HIDDEN, hiddenBy = HiddenBy.GROUP),
                    post(id = "p3", createdAt = t4, authorId = "other", status = PostStatus.HIDDEN, hiddenBy = HiddenBy.GROUP),
                    post(id = "p4", createdAt = t2, authorId = "other", status = PostStatus.VISIBLE),
                ),
                next = null,
            ),
        )
        every { posts.observeNewPosts("g1", false, any()) } returns emptyFlow()

        val vm = viewModel()
        advanceUntilIdle()

        val feed = (vm.state.value as GroupDetailUiState.Content).feed as FeedUiState.Content
        assertEquals(listOf("p2", "p4", "p1"), feed.items.map { it.post.id })
        assertTrue(feed.items[0].showHiddenBadge)
        assertFalse(feed.items[1].showHiddenBadge)
    }

    @Test
    fun `quan tri vien thay moi bai ke ca bai an cua nguoi khac`() = runTest {
        givenMember(GroupRole.ADMIN)
        val t1 = Instant.parse("2026-11-10T01:00:00Z")
        val t2 = Instant.parse("2026-11-10T02:00:00Z")
        coEvery { posts.feed("g1", true, null) } returns Result.success(
            Page(
                listOf(
                    post(id = "p1", createdAt = t2, authorId = "other", status = PostStatus.HIDDEN, hiddenBy = HiddenBy.GROUP),
                    post(id = "p2", createdAt = t1, authorId = "other", status = PostStatus.VISIBLE),
                ),
                next = null,
            ),
        )
        every { posts.observeNewPosts("g1", true, any()) } returns emptyFlow()

        val vm = viewModel()
        advanceUntilIdle()

        val feed = (vm.state.value as GroupDetailUiState.Content).feed as FeedUiState.Content
        assertEquals(listOf("p1", "p2"), feed.items.map { it.post.id })
        assertTrue(feed.items[0].showHiddenBadge)
        assertTrue(feed.items[0].canUnhide)
    }

    @Test
    fun `tai trang 20 va tai them trang tiep theo`() = runTest {
        givenMember(GroupRole.MEMBER)
        val base = Instant.parse("2026-11-10T10:00:00Z")
        val page1 = (1..20).map { i -> post(id = "p$i", createdAt = base.minusSeconds(i.toLong() * 60)) }
        val cursor1 = PageCursor(page1.last().createdAt!!.toEpochMilli(), page1.last().id)
        val page2 = listOf(post(id = "p21", createdAt = base.minusSeconds(21 * 60)))

        coEvery { posts.feed("g1", false, null) } returns Result.success(Page(page1, cursor1))
        coEvery { posts.feed("g1", false, cursor1) } returns Result.success(Page(page2, null))
        every { posts.observeNewPosts("g1", false, any()) } returns emptyFlow()

        val vm = viewModel()
        advanceUntilIdle()

        val first = (vm.state.value as GroupDetailUiState.Content).feed as FeedUiState.Content
        assertEquals(20, first.items.size)
        assertTrue(first.canLoadMore)

        vm.loadMoreFeed()
        advanceUntilIdle()

        val second = (vm.state.value as GroupDetailUiState.Content).feed as FeedUiState.Content
        assertEquals(21, second.items.size)
        assertFalse(second.canLoadMore)
    }

    @Test
    fun `nhom chua co bai thi Empty`() = runTest {
        givenMember(GroupRole.MEMBER)
        coEvery { posts.feed("g1", false, null) } returns Result.success(Page(emptyList(), null))
        every { posts.observeNewPosts("g1", false, any()) } returns emptyFlow()

        val vm = viewModel()
        advanceUntilIdle()

        val content = vm.state.value as GroupDetailUiState.Content
        assertEquals(FeedUiState.Empty, content.feed)
    }

    @Test
    fun `bai moi tu listener chen len dau kem signed URL`() = runTest {
        givenMember(GroupRole.MEMBER)
        val t1 = Instant.parse("2026-11-10T01:00:00Z")
        val t2 = Instant.parse("2026-11-10T02:00:00Z")
        val newPostsFlow = MutableSharedFlow<List<Post>>()
        coEvery { posts.feed("g1", false, null) } returns Result.success(
            Page(listOf(post(id = "old", createdAt = t1)), null),
        )
        every { posts.observeNewPosts("g1", false, t1) } returns newPostsFlow

        val cache = SignedUrlCache(
            fetch = { paths ->
                Result.success(SignedUrlBatch(paths.associateWith { "https://cdn/$it" }, Instant.ofEpochMilli(9_999_999L)))
            },
            clock = FakeClock { 1_000L },
        )
        val vm = viewModel(signedUrls = cache)
        advanceUntilIdle()

        newPostsFlow.emit(listOf(post(id = "newest", createdAt = t2, photoPaths = listOf("g1/newest/0.jpg"))))
        advanceUntilIdle()

        val feed = (vm.state.value as GroupDetailUiState.Content).feed as FeedUiState.Content
        assertEquals(listOf("newest", "old"), feed.items.map { it.post.id })
        assertEquals("https://cdn/g1/newest/0.jpg", feed.items.first().photoUrls["g1/newest/0.jpg"])
    }

    @Test
    fun `tac gia da xoa thi isAuthorDeleted true`() = runTest {
        givenMember(GroupRole.MEMBER)
        coEvery { posts.feed("g1", false, null) } returns Result.success(
            Page(listOf(post(id = "p1", authorDeleted = true)), null),
        )
        every { posts.observeNewPosts("g1", false, any()) } returns emptyFlow()

        val vm = viewModel()
        advanceUntilIdle()

        val feed = (vm.state.value as GroupDetailUiState.Content).feed as FeedUiState.Content
        assertTrue(feed.items.single().isAuthorDeleted)
    }

    @Test
    fun `nguoi ngoai khong tai bang tin va showFeed false`() = runTest {
        every { groups.observeGroup("g1") } returns flowOf(group(ownerId = "other"))
        every { groups.observeMembership("g1") } returns flowOf(GroupMembership.NONE)

        val vm = viewModel()
        advanceUntilIdle()

        val content = vm.state.value as GroupDetailUiState.Content
        assertFalse(content.showFeed)
        coVerify(exactly = 0) { posts.feed(any(), any(), any()) }
    }

    @Test
    fun `tai bang tin loi thi FeedUiState Error va retryFeed tai lai`() = runTest {
        givenMember(GroupRole.MEMBER)
        var calls = 0
        coEvery { posts.feed("g1", false, null) } answers {
            if (calls++ == 0) Result.failure(CommunityError.Network()) else Result.success(Page(listOf(post("p1")), null))
        }
        every { posts.observeNewPosts("g1", false, any()) } returns emptyFlow()

        val vm = viewModel()
        advanceUntilIdle()
        assertTrue((vm.state.value as GroupDetailUiState.Content).feed is FeedUiState.Error)

        vm.retryFeed()
        advanceUntilIdle()
        val feed = (vm.state.value as GroupDetailUiState.Content).feed as FeedUiState.Content
        assertEquals(listOf("p1"), feed.items.map { it.post.id })
    }

    @Test
    fun `tham gia su kien cap nhat lac quan va hoan lai khi loi`() = runTest {
        givenMember(GroupRole.MEMBER)
        val futureAt = Instant.parse("2026-11-20T12:00:00Z")
        val eventPost = post(id = "e1").copy(
            type = PostType.EVENT,
            event = EventInfo("Giao lưu", futureAt, "Sân A", expectedCount = 8, attendeeCount = 2, iAmAttending = false),
        )
        val unattendGate = CompletableDeferred<Result<AttendResult>>()
        coEvery { posts.feed("g1", false, null) } returns Result.success(Page(listOf(eventPost), null))
        every { posts.observeNewPosts("g1", false, any()) } returns emptyFlow()
        coEvery { posts.attend("g1", "e1") } returns Result.success(AttendResult(attendeeCount = 3, attending = true))
        coEvery { posts.unattend("g1", "e1") } coAnswers { unattendGate.await() }

        val vm = viewModel()
        advanceUntilIdle()

        vm.onToggleAttendance("e1")
        advanceUntilIdle()
        val afterJoin = ((vm.state.value as GroupDetailUiState.Content).feed as FeedUiState.Content).items.single()
        assertTrue(afterJoin.post.event!!.iAmAttending)
        assertEquals(3, afterJoin.post.event!!.attendeeCount)
        assertFalse(afterJoin.attendanceBusy)

        // Bỏ tham gia: kiểm tra trạng thái lạc quan trung gian trước khi API trả về.
        vm.onToggleAttendance("e1")
        runCurrent()
        val optimistic = ((vm.state.value as GroupDetailUiState.Content).feed as FeedUiState.Content).items.single()
        assertFalse(optimistic.post.event!!.iAmAttending)
        assertEquals(2, optimistic.post.event!!.attendeeCount)
        assertTrue(optimistic.attendanceBusy)

        // API trả lỗi mạng → hoàn lại trạng thái đang tham gia (3 người, hết busy).
        unattendGate.complete(Result.failure(CommunityError.Network()))
        advanceUntilIdle()
        val afterFail = ((vm.state.value as GroupDetailUiState.Content).feed as FeedUiState.Content).items.single()
        assertTrue(afterFail.post.event!!.iAmAttending)
        assertEquals(3, afterFail.post.event!!.attendeeCount)
        assertFalse(afterFail.attendanceBusy)
    }

    @Test
    fun `observeNewPosts khong ghi de trang thai tham gia lac quan va loi van giu truong moi`() = runTest {
        givenMember(GroupRole.MEMBER)
        val futureAt = Instant.parse("2026-11-20T12:00:00Z")
        val t1 = Instant.parse("2026-11-10T01:00:00Z")
        val t2 = Instant.parse("2026-11-10T02:00:00Z")
        val initialEvent = post(id = "e1", createdAt = t2).copy(
            type = PostType.EVENT,
            event = EventInfo("Tiêu đề cũ", futureAt, "Sân A", expectedCount = 8, attendeeCount = 2, iAmAttending = false),
        )
        val newPostsFlow = MutableSharedFlow<List<Post>>()
        val attendGate = CompletableDeferred<Result<AttendResult>>()
        coEvery { posts.feed("g1", false, null) } returns Result.success(Page(listOf(post("old", createdAt = t1)), null))
        every { posts.observeNewPosts("g1", false, t1) } returns newPostsFlow
        coEvery { posts.attend("g1", "e1") } coAnswers { attendGate.await() }

        val vm = viewModel()
        advanceUntilIdle()

        newPostsFlow.emit(listOf(initialEvent))
        advanceUntilIdle()

        // Bấm tham gia → trạng thái lạc quan (iAmAttending = true, count = 3).
        vm.onToggleAttendance("e1")
        runCurrent()

        // Listener phát lại sự kiện với tiêu đề mới trong lúc attend đang bay → giữ nguyên lạc quan.
        val updatedFromServer = initialEvent.copy(
            event = initialEvent.event!!.copy(title = "Tiêu đề mới", attendeeCount = 2, iAmAttending = false),
        )
        newPostsFlow.emit(listOf(updatedFromServer))
        runCurrent()

        val duringInFlight = ((vm.state.value as GroupDetailUiState.Content).feed as FeedUiState.Content)
            .items.first { it.post.id == "e1" }
        assertEquals("Tiêu đề mới", duringInFlight.post.event!!.title)
        assertTrue(duringInFlight.post.event!!.iAmAttending)
        assertEquals(3, duringInFlight.post.event!!.attendeeCount)

        // Khi attend lỗi → hoàn lại tham gia dựa trên trạng thái hiện tại, vẫn giữ "Tiêu đề mới".
        attendGate.complete(Result.failure(CommunityError.Network()))
        advanceUntilIdle()

        val afterFail = ((vm.state.value as GroupDetailUiState.Content).feed as FeedUiState.Content)
            .items.first { it.post.id == "e1" }
        assertEquals("Tiêu đề mới", afterFail.post.event!!.title)
        assertFalse(afterFail.post.event!!.iAmAttending)
        assertEquals(2, afterFail.post.event!!.attendeeCount)
    }

    @Test
    fun `su kien da qua hoac dung bang now thi isEventPast true va canAttend false`() = runTest {
        givenMember(GroupRole.MEMBER)
        val nowInstant = Instant.ofEpochMilli(nowMs)
        val pastEvent = post(id = "e_past").copy(
            type = PostType.EVENT,
            event = EventInfo("Đã diễn ra", nowInstant, "Sân B", expectedCount = 6, attendeeCount = 4, iAmAttending = false),
        )
        coEvery { posts.feed("g1", false, null) } returns Result.success(Page(listOf(pastEvent), null))
        every { posts.observeNewPosts("g1", false, any()) } returns emptyFlow()

        val vm = viewModel()
        advanceUntilIdle()

        val item = ((vm.state.value as GroupDetailUiState.Content).feed as FeedUiState.Content).items.single()
        assertTrue(item.isEventPast)
        assertFalse(item.canAttend)
    }

    @Test
    fun `an va hien lai bai cap nhat bang tin, bai SYSTEM khong hien nut hien lai, nhom LOCKED an nut dang bai`() = runTest {
        every { groups.observeGroup("g1") } returns flowOf(group(ownerId = ME, status = GroupStatus.ACTIVE))
        every { groups.observeMembership("g1") } returns flowOf(GroupMembership(GroupRole.OWNER, false))
        coEvery { posts.feed("g1", true, null) } returns Result.success(
            Page(
                listOf(
                    post(id = "p1", authorId = "other", status = PostStatus.VISIBLE),
                    post(id = "p2", authorId = "other", status = PostStatus.HIDDEN, hiddenBy = HiddenBy.SYSTEM),
                    post(id = "p3", authorId = ME, status = PostStatus.VISIBLE),
                ),
                null,
            ),
        )
        every { posts.observeNewPosts("g1", true, any()) } returns emptyFlow()
        coEvery { posts.hide("g1", "p1") } returns Result.success(Unit)
        coEvery { posts.unhide("g1", "p1") } returns Result.success(Unit)

        val vm = viewModel()
        advanceUntilIdle()

        val initial = ((vm.state.value as GroupDetailUiState.Content).feed as FeedUiState.Content).items.associateBy { it.post.id }
        assertTrue(initial.getValue("p1").canHide)
        assertTrue(initial.getValue("p1").canReport)
        assertFalse(initial.getValue("p2").canUnhide) // SYSTEM ẩn không hiện lại được (AC 6.4)
        assertFalse(initial.getValue("p2").canReport) // Bài đã ẩn không báo cáo được (US6#1)
        assertFalse(initial.getValue("p3").canReport) // Không báo cáo bài của mình (AC 6.5)

        vm.onHidePostConfirmed("p1")
        advanceUntilIdle()
        val afterHide = ((vm.state.value as GroupDetailUiState.Content).feed as FeedUiState.Content).items.first { it.post.id == "p1" }
        assertEquals(PostStatus.HIDDEN, afterHide.post.status)
        assertEquals(HiddenBy.GROUP, afterHide.post.hiddenBy)
        assertTrue(afterHide.canUnhide)
        assertFalse(afterHide.canReport)

        vm.onUnhidePost("p1")
        advanceUntilIdle()
        val afterUnhide = ((vm.state.value as GroupDetailUiState.Content).feed as FeedUiState.Content).items.first { it.post.id == "p1" }
        assertEquals(PostStatus.VISIBLE, afterUnhide.post.status)

        // Nhóm LOCKED: ẩn nút đăng bài và tắt mọi menu ghi.
        every { groups.observeGroup("g1") } returns flowOf(group(ownerId = ME, status = GroupStatus.LOCKED))
        val lockedVm = viewModel()
        advanceUntilIdle()
        val lockedContent = lockedVm.state.value as GroupDetailUiState.Content
        assertFalse(lockedContent.canPost)
        assertTrue(lockedContent.group.isLocked)
        val lockedItems = (lockedContent.feed as FeedUiState.Content).items
        assertTrue(lockedItems.none { it.hasMenuActions })
    }

    // ---------- US3 fix: observeNewPosts xóa/ẩn, effectiveMember, delete, isEdited, loadMoreError, mergeMemberFeedPage ----------

    @Test
    fun `bai moi tu observeNewPosts bi xoa hoac bi an thi bien khoi bang tin`() = runTest {
        givenMember(GroupRole.MEMBER)
        val t1 = Instant.parse("2026-11-10T01:00:00Z")
        val t2 = Instant.parse("2026-11-10T02:00:00Z")
        val newPostsFlow = MutableSharedFlow<List<Post>>()
        coEvery { posts.feed("g1", false, null) } returns Result.success(
            Page(listOf(post(id = "old", createdAt = t1)), null),
        )
        every { posts.observeNewPosts("g1", false, t1) } returns newPostsFlow

        val vm = viewModel()
        advanceUntilIdle()

        newPostsFlow.emit(listOf(post(id = "p_new", createdAt = t2, authorId = "other")))
        advanceUntilIdle()
        val withNew = (vm.state.value as GroupDetailUiState.Content).feed as FeedUiState.Content
        assertEquals(listOf("p_new", "old"), withNew.items.map { it.post.id })

        // Bài mới bị xóa (snapshot trả rỗng) → biến khỏi bảng tin.
        newPostsFlow.emit(emptyList())
        advanceUntilIdle()
        val afterRemove = (vm.state.value as GroupDetailUiState.Content).feed as FeedUiState.Content
        assertEquals(listOf("old"), afterRemove.items.map { it.post.id })
    }

    @Test
    fun `suy ra OWNER tu ownerId khi observeMembership loi van mo bang tin va tai voi isMod true`() = runTest {
        every { groups.observeGroup("g1") } returns flowOf(group(ownerId = ME))
        every { groups.observeMembership("g1") } returns flow { throw CommunityError.Network() }
        coEvery { posts.feed("g1", true, null) } returns Result.success(Page(listOf(post("p1")), null))
        every { posts.observeNewPosts("g1", true, any()) } returns emptyFlow()

        val vm = viewModel()
        advanceUntilIdle()

        val content = vm.state.value as GroupDetailUiState.Content
        assertTrue(content.showFeed)
        assertEquals(JoinAction.NONE, content.joinAction)
        val feed = content.feed as FeedUiState.Content
        assertEquals(listOf("p1"), feed.items.map { it.post.id })
        coVerify(exactly = 1) { posts.feed("g1", true, null) }
    }

    @Test
    fun `onDeletePostConfirmed thanh cong go bai va phat PostDeleted, that bai giu bai va phat ShowError`() = runTest {
        givenMember(GroupRole.MEMBER)
        coEvery { posts.feed("g1", false, null) } returns Result.success(
            Page(listOf(post("p1", authorId = ME, edited = true), post("p2", authorId = ME)), null),
        )
        every { posts.observeNewPosts("g1", false, any()) } returns emptyFlow()
        coEvery { posts.delete("g1", "p1", any()) } returns Result.success(Unit)
        coEvery { posts.delete("g1", "p2", any()) } returns Result.failure(CommunityError.Network())

        val vm = viewModel()
        advanceUntilIdle()

        val before = (vm.state.value as GroupDetailUiState.Content).feed as FeedUiState.Content
        assertTrue(before.items.first { it.post.id == "p1" }.post.isEdited)
        assertFalse(before.items.first { it.post.id == "p2" }.post.isEdited)

        vm.events.test {
            vm.onDeletePostConfirmed("p1")
            advanceUntilIdle()
            assertEquals(GroupDetailEvent.PostDeleted, awaitItem())

            val afterDelete = (vm.state.value as GroupDetailUiState.Content).feed as FeedUiState.Content
            assertEquals(listOf("p2"), afterDelete.items.map { it.post.id })

            vm.onDeletePostConfirmed("p2")
            advanceUntilIdle()
            val errEvent = awaitItem() as GroupDetailEvent.ShowError
            assertTrue(errEvent.error is CommunityError.Network)

            val afterFail = (vm.state.value as GroupDetailUiState.Content).feed as FeedUiState.Content
            assertEquals(listOf("p2"), afterFail.items.map { it.post.id })
        }
    }

    @Test
    fun `loadMoreError chan loadMoreFeed tu goi lai va retryLoadMoreFeed tai lai duoc`() = runTest {
        givenMember(GroupRole.MEMBER)
        val t1 = Instant.parse("2026-11-10T02:00:00Z")
        val t2 = Instant.parse("2026-11-10T01:00:00Z")
        val cursor = PageCursor(t1.toEpochMilli(), "p1")
        coEvery { posts.feed("g1", false, null) } returns Result.success(Page(listOf(post("p1", createdAt = t1)), cursor))
        var nextCalls = 0
        coEvery { posts.feed("g1", false, cursor) } answers {
            if (nextCalls++ == 0) Result.failure(CommunityError.Network())
            else Result.success(Page(listOf(post("p2", createdAt = t2)), null))
        }
        every { posts.observeNewPosts("g1", false, any()) } returns emptyFlow()

        val vm = viewModel()
        advanceUntilIdle()

        vm.loadMoreFeed()
        advanceUntilIdle()
        val errFeed = (vm.state.value as GroupDetailUiState.Content).feed as FeedUiState.Content
        assertNotNull(errFeed.loadMoreError)

        // Gọi loadMoreFeed khi đang lỗi → bị chặn, không gọi thêm request.
        vm.loadMoreFeed()
        advanceUntilIdle()
        coVerify(exactly = 1) { posts.feed("g1", false, cursor) }

        // Bấm nút Thử lại → gọi retryLoadMoreFeed thành công.
        vm.retryLoadMoreFeed()
        advanceUntilIdle()
        val okFeed = (vm.state.value as GroupDetailUiState.Content).feed as FeedUiState.Content
        assertNull(okFeed.loadMoreError)
        assertEquals(listOf("p1", "p2"), okFeed.items.map { it.post.id })
        coVerify(exactly = 2) { posts.feed("g1", false, cursor) }
    }

    @Test
    fun `mergeMemberFeedPage cat bai HIDDEN cu hon boundary khi con trang tiep va giu het o trang cuoi`() {
        val t1 = Instant.parse("2026-11-10T01:00:00Z")
        val t2 = Instant.parse("2026-11-10T02:00:00Z")
        val t3 = Instant.parse("2026-11-10T03:00:00Z")
        val t4 = Instant.parse("2026-11-10T04:00:00Z")

        val visible = listOf(
            post("v1", createdAt = t4),
            post("v2", createdAt = t2),
        )
        val ownHidden = listOf(
            post("h_in", createdAt = t3, authorId = ME, status = PostStatus.HIDDEN),
            post("h_on_boundary", createdAt = t2, authorId = ME, status = PostStatus.HIDDEN),
            post("h_older", createdAt = t1, authorId = ME, status = PostStatus.HIDDEN),
        )

        val cursor = PageCursor(t2.toEpochMilli(), "v2")
        val pageWithNext = FirestorePostRepository.mergeMemberFeedPage(visible, ownHidden, cursor)
        assertEquals(listOf("v1", "h_in", "v2", "h_on_boundary"), pageWithNext.map { it.id })

        val lastPage = FirestorePostRepository.mergeMemberFeedPage(visible, ownHidden, next = null)
        assertEquals(listOf("v1", "h_in", "v2", "h_on_boundary", "h_older"), lastPage.map { it.id })
    }

    @Test
    fun `PostResultDto toDomain giu authorSnapshot va authorDeleted`() {
        val dto = PostResultDto(
            id = "p1",
            groupId = "g1",
            authorId = "u1",
            authorSnapshot = UserSnapshotDto(displayName = "Nguyễn A", avatarPath = "u1/avatar.jpg"),
            authorDeleted = true,
            type = "POST",
            content = "Xin chào",
        )
        val domain = dto.toDomain()!!
        assertEquals("Nguyễn A", domain.author.displayName)
        assertEquals("u1/avatar.jpg", domain.author.avatarPath)
        assertTrue(domain.author.isDeleted)
    }

    // ---------- Fix vòng 2: NEW#2, NEW#4 ----------

    @Test
    fun `refresh bang tin khi request tham gia dang bay khong nhay Empty va so nguoi tham gia khong lech`() = runTest {
        givenMember(GroupRole.MEMBER)
        val futureAt = Instant.parse("2026-11-20T12:00:00Z")
        val eventPost = post(id = "e1").copy(
            type = PostType.EVENT,
            event = EventInfo("Giao lưu", futureAt, "Sân A", expectedCount = 8, attendeeCount = 2, iAmAttending = false),
        )
        val attendGate = CompletableDeferred<Result<AttendResult>>()
        val refreshGate = CompletableDeferred<Result<Page<Post>>>()
        var feedCalls = 0
        coEvery { posts.feed("g1", false, null) } coAnswers {
            if (feedCalls++ == 0) Result.success(Page(listOf(eventPost), null)) else refreshGate.await()
        }
        every { posts.observeNewPosts("g1", false, any()) } returns emptyFlow()
        coEvery { posts.attend("g1", "e1") } coAnswers { attendGate.await() }

        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            // Bắt đầu tham gia lạc quan: count = 3, attendanceBusy = true.
            vm.onToggleAttendance("e1")
            runCurrent()
            val optimistic = ((vm.state.value as GroupDetailUiState.Content).feed as FeedUiState.Content).items.single()
            assertTrue(optimistic.post.event!!.iAmAttending)
            assertEquals(3, optimistic.post.event!!.attendeeCount)
            assertTrue(optimistic.attendanceBusy)

            // Làm mới bảng tin trong lúc attend đang bay → hủy attend, giữ Loading (không nháy Empty).
            vm.refreshFeed()
            runCurrent()
            assertEquals(FeedUiState.Loading, (vm.state.value as GroupDetailUiState.Content).feed)

            // Hoàn tất bảng tin từ server (count = 2, chưa tham gia) và kích lỗi ở gate cũ.
            refreshGate.complete(Result.success(Page(listOf(eventPost), null)))
            attendGate.complete(Result.failure(CommunityError.Network()))
            advanceUntilIdle()

            val refreshed = ((vm.state.value as GroupDetailUiState.Content).feed as FeedUiState.Content).items.single()
            assertFalse(refreshed.post.event!!.iAmAttending)
            assertEquals(2, refreshed.post.event!!.attendeeCount)
            assertFalse(refreshed.attendanceBusy)
            expectNoEvents()
        }
    }

    @Test
    fun `listenNewPosts cho resolveUrls khong de len thao tac xoa va an xay ra trong luc cho`() = runTest {
        givenMember(GroupRole.OWNER)
        val t1 = Instant.parse("2026-11-10T01:00:00Z")
        val t2 = Instant.parse("2026-11-10T02:00:00Z")
        val t3 = Instant.parse("2026-11-10T03:00:00Z")
        val newPostsFlow = MutableSharedFlow<List<Post>>()
        val urlGate = CompletableDeferred<Result<SignedUrlBatch>>()
        val postToDelete = post(id = "p_del", createdAt = t2, authorId = ME, photoPaths = listOf("g1/p_del/0.jpg"))
        val postToHide = post(id = "p_hide", createdAt = t3, authorId = "other", photoPaths = listOf("g1/p_hide/0.jpg"))

        coEvery { posts.feed("g1", true, null) } returns Result.success(Page(listOf(post("old", createdAt = t1)), null))
        every { posts.observeNewPosts("g1", true, t1) } returns newPostsFlow
        coEvery { posts.delete("g1", "p_del", any()) } returns Result.success(Unit)
        coEvery { posts.hide("g1", "p_hide") } returns Result.success(Unit)

        var fetchCalls = 0
        val cache = SignedUrlCache(
            fetch = { paths ->
                if (fetchCalls++ == 0) {
                    Result.success(SignedUrlBatch(paths.associateWith { "https://cdn/$it" }, Instant.ofEpochMilli(9_999_999L)))
                } else {
                    urlGate.await()
                }
            },
            clock = FakeClock { 1_000L },
        )

        val vm = viewModel(signedUrls = cache)
        advanceUntilIdle()

        // Lần phát đầu đưa p_del và p_hide lên bảng tin.
        newPostsFlow.emit(listOf(postToHide, postToDelete))
        advanceUntilIdle()

        // Snapshot mới có ảnh chưa cache làm resolveUrls bị tạm dừng.
        val snapshotWithNewPhoto = listOf(
            postToHide.copy(photoPaths = listOf("g1/p_hide/1.jpg")),
            postToDelete.copy(photoPaths = listOf("g1/p_del/1.jpg")),
        )
        newPostsFlow.emit(snapshotWithNewPhoto)
        runCurrent()

        // Trong lúc đang chờ resolveUrls, người dùng xóa p_del và ẩn p_hide.
        vm.onDeletePostConfirmed("p_del")
        vm.onHidePostConfirmed("p_hide")
        runCurrent()

        // resolveUrls hoàn tất → p_del không bị chèn lại, p_hide giữ nguyên trạng thái đã ẩn.
        urlGate.complete(
            Result.success(
                SignedUrlBatch(
                    mapOf("g1/p_hide/1.jpg" to "https://cdn/g1/p_hide/1.jpg", "g1/p_del/1.jpg" to "https://cdn/g1/p_del/1.jpg"),
                    Instant.ofEpochMilli(9_999_999L),
                ),
            ),
        )
        advanceUntilIdle()

        val feed = (vm.state.value as GroupDetailUiState.Content).feed as FeedUiState.Content
        assertEquals(listOf("p_hide", "old"), feed.items.map { it.post.id })
        val hiddenItem = feed.items.first { it.post.id == "p_hide" }
        assertEquals(PostStatus.HIDDEN, hiddenItem.post.status)
        assertEquals(HiddenBy.GROUP, hiddenItem.post.hiddenBy)
    }
}

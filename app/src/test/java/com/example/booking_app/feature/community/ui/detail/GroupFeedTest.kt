package com.example.booking_app.feature.community.ui.detail

import androidx.lifecycle.SavedStateHandle
import com.example.booking_app.core.catalog.CatalogRepository
import com.example.booking_app.core.media.SignedUrlBatch
import com.example.booking_app.core.media.SignedUrlCache
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.community.ME
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.GroupRepository
import com.example.booking_app.feature.community.domain.PostRepository
import com.example.booking_app.feature.community.domain.model.GroupMembership
import com.example.booking_app.feature.community.domain.model.GroupRole
import com.example.booking_app.feature.community.domain.model.HiddenBy
import com.example.booking_app.feature.community.domain.model.Page
import com.example.booking_app.feature.community.domain.model.PageCursor
import com.example.booking_app.feature.community.domain.model.Post
import com.example.booking_app.feature.community.domain.model.PostStatus
import com.example.booking_app.feature.community.domain.usecase.DeletePost
import com.example.booking_app.feature.community.domain.usecase.JoinGroup
import com.example.booking_app.feature.community.domain.usecase.LeaveGroup
import com.example.booking_app.feature.community.group
import com.example.booking_app.feature.community.noopSignedUrls
import com.example.booking_app.feature.community.post
import com.example.booking_app.feature.community.ui.common.DistrictNames
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val session = mockk<SessionManager> { every { currentUid() } returns ME }
    private val catalog = mockk<CatalogRepository> { coEvery { districts() } returns emptyList() }

    private fun viewModel(signedUrls: SignedUrlCache = noopSignedUrls()) = GroupDetailViewModel(
        SavedStateHandle(mapOf("groupId" to "g1")),
        groups,
        posts,
        session,
        JoinGroup(groups),
        LeaveGroup(groups),
        DeletePost(posts),
        signedUrls,
        DistrictNames(catalog),
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
}

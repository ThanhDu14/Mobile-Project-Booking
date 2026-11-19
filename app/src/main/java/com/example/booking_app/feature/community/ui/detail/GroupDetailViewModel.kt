package com.example.booking_app.feature.community.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.media.SignedUrlCache
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.GroupPermissions
import com.example.booking_app.feature.community.domain.GroupRepository
import com.example.booking_app.feature.community.domain.PostRepository
import com.example.booking_app.feature.community.domain.model.Group
import com.example.booking_app.feature.community.domain.model.GroupMembership
import com.example.booking_app.feature.community.domain.model.GroupRole
import com.example.booking_app.feature.community.domain.model.GroupVisibility
import com.example.booking_app.feature.community.domain.model.JoinResult
import com.example.booking_app.feature.community.domain.model.PageCursor
import com.example.booking_app.feature.community.domain.model.Post
import com.example.booking_app.feature.community.domain.model.PostStatus
import com.example.booking_app.feature.community.domain.toCommunityError
import com.example.booking_app.feature.community.domain.usecase.DeletePost
import com.example.booking_app.feature.community.domain.usecase.JoinGroup
import com.example.booking_app.feature.community.domain.usecase.LeaveGroup
import com.example.booking_app.feature.community.ui.common.DistrictNames
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** Nút tham gia trên trang nhóm (T038). */
enum class JoinAction {
    /** Ẩn: đã là thành viên hoặc nhóm bị khóa. */
    NONE,

    /** "Tham gia" (nhóm công khai). */
    JOIN,

    /** "Xin tham gia" (nhóm riêng tư). */
    REQUEST,

    /** "Đang chờ duyệt" kèm "Hủy yêu cầu". */
    PENDING,
}

/** Một bài hiển thị trên bảng tin (T050) kèm URL ảnh đã ký và quyền theo người xem. */
data class PostItem(
    val post: Post,
    val photoUrls: Map<String, String> = emptyMap(),
    val canEdit: Boolean = false,
    val canDelete: Boolean = false,
    val canHide: Boolean = false,
    val canUnhide: Boolean = false,
    val canReport: Boolean = false,
) {
    /** Nhãn "Đã bị ẩn" hiện với người đăng và quản trị viên. */
    val showHiddenBadge: Boolean get() = post.isHidden

    /** Tác giả đã xóa tài khoản → UI hiện "Người dùng đã xóa". */
    val isAuthorDeleted: Boolean get() = post.author.isDeleted

    /** Có ít nhất một hành động trong menu bài đăng. */
    val hasMenuActions: Boolean get() = canEdit || canDelete || canHide || canUnhide || canReport
}

/** 4 trạng thái bảng tin (FR-020, T043/T050). */
sealed interface FeedUiState {
    data object Loading : FeedUiState
    data class Content(
        val items: List<PostItem>,
        val canLoadMore: Boolean = false,
        val loadingMore: Boolean = false,
        val loadMoreError: CommunityError? = null,
    ) : FeedUiState
    data object Empty : FeedUiState
    data class Error(val cause: CommunityError) : FeedUiState
}

sealed interface GroupDetailUiState {
    data object Loading : GroupDetailUiState

    /** Nhóm không có, đã `DELETED` hoặc bị Rules từ chối: "Nhóm không còn tồn tại", không phải lỗi chung. */
    data object NotFound : GroupDetailUiState

    data class Content(
        val group: Group,
        val membership: GroupMembership,
        val permissions: GroupPermissions,
        val districtName: String? = null,
        /** Đang gửi tham gia/hủy/rời nhóm: khóa các nút để không gửi hai lần. */
        val busy: Boolean = false,
        val feed: FeedUiState = FeedUiState.Loading,
    ) : GroupDetailUiState {
        val canEdit: Boolean get() = permissions.canEdit

        /**
         * Chế độ nhóm quyết định nhãn nút; việc người từng bị mời ra phải xin duyệt do server quyết định (kết quả
         * `REQUESTED`), app không biết trước.
         */
        val joinAction: JoinAction
            get() = when {
                membership.isMember || group.isLocked -> JoinAction.NONE
                membership.pending -> JoinAction.PENDING
                group.visibility == GroupVisibility.PUBLIC -> JoinAction.JOIN
                else -> JoinAction.REQUEST
            }

        val canLeave: Boolean get() = permissions.canLeave
        val canPost: Boolean get() = permissions.canPost
        val showFeed: Boolean get() = membership.isMember

        /** Nút "Yêu cầu tham gia (N)": chủ nhóm và quản trị viên. */
        val canReviewRequests: Boolean get() = permissions.canModerate
        val pendingRequestCount: Int get() = group.pendingRequestCount
    }

    data class Error(val cause: CommunityError) : GroupDetailUiState
}

sealed interface GroupDetailEvent {
    /** Khách bấm tham gia: mở đăng nhập rồi quay lại [returnTo]. */
    data class RequireLogin(val returnTo: String) : GroupDetailEvent

    data class JoinOutcome(val result: JoinResult) : GroupDetailEvent
    data object RequestCancelled : GroupDetailEvent
    data object Left : GroupDetailEvent
    data object PostDeleted : GroupDetailEvent

    /** Chủ nhóm rời khi chưa chuyển quyền: mở chọn người nhận quyền (US5). */
    data class OpenTransferOwner(val groupId: String) : GroupDetailEvent

    /** Chủ nhóm là thành viên duy nhất: gợi ý xóa nhóm. */
    data object SuggestDeleteGroup : GroupDetailEvent

    data class ShowError(val error: CommunityError) : GroupDetailEvent
}

/**
 * T029 + T038 + T050: trang nhóm và bảng tin. Nhóm và quan hệ thành viên nghe realtime; bảng tin chỉ nạp khi người xem
 * là thành viên (AC 3.7), phân trang 20 bài, nghe bài mới chèn lên đầu, ký URL ảnh qua [SignedUrlCache].
 */
@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val groups: GroupRepository,
    private val posts: PostRepository,
    private val session: SessionManager,
    private val joinGroup: JoinGroup,
    private val leaveGroup: LeaveGroup,
    private val deletePost: DeletePost,
    private val signedUrls: SignedUrlCache,
    private val districtNames: DistrictNames,
) : ViewModel() {

    val groupId: String = checkNotNull(savedStateHandle[ARG_GROUP_ID]) { "Thiếu groupId" }

    private val _state = MutableStateFlow<GroupDetailUiState>(GroupDetailUiState.Loading)
    val state: StateFlow<GroupDetailUiState> = _state.asStateFlow()

    private val _events = Channel<GroupDetailEvent>(Channel.BUFFERED)
    val events: Flow<GroupDetailEvent> = _events.receiveAsFlow()

    private val busy = MutableStateFlow(false)
    private val rawFeedState = MutableStateFlow<RawFeedState>(RawFeedState.Loading)

    private var job: Job? = null
    private var feedJob: Job? = null
    private var newPostsJob: Job? = null
    private var loadMoreJob: Job? = null

    private var activeFeedKey: Pair<Boolean, Boolean>? = null
    private var currentIsMod: Boolean = false
    private var rawPosts: List<Post> = emptyList()
    private var photoUrls: MutableMap<String, String> = LinkedHashMap()
    private var nextCursor: PageCursor? = null
    private var loadingMore: Boolean = false
    private var loadMoreError: CommunityError? = null

    private sealed interface RawFeedState {
        data object Loading : RawFeedState
        data class Content(
            val posts: List<Post>,
            val urls: Map<String, String>,
            val canLoadMore: Boolean,
            val loadingMore: Boolean,
            val loadMoreError: CommunityError?,
        ) : RawFeedState
        data object Empty : RawFeedState
        data class Error(val cause: CommunityError) : RawFeedState
    }

    init {
        start()
    }

    fun retry() {
        _state.value = GroupDetailUiState.Loading
        activeFeedKey = null
        start()
    }

    fun retryFeed() {
        val current = _state.value as? GroupDetailUiState.Content ?: return
        if (!current.membership.isMember) return
        loadFeed(isMod(current.membership.role))
    }

    fun refreshFeed() = retryFeed()

    fun loadMoreFeed() {
        val cursor = nextCursor ?: return
        if (loadingMore) return
        loadingMore = true
        loadMoreError = null
        publishFeed()

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            val uid = session.currentUid().orEmpty()
            posts.feed(groupId, currentIsMod, cursor).fold(
                onSuccess = { page ->
                    val filtered = filterVisible(page.items, currentIsMod, uid)
                    resolveUrls(filtered)
                    rawPosts = mergePosts(rawPosts, filtered)
                    nextCursor = page.next
                    loadingMore = false
                    loadMoreError = null
                    publishFeed()
                },
                onFailure = { err ->
                    loadingMore = false
                    loadMoreError = err.toCommunityError()
                    publishFeed()
                },
            )
        }
    }

    fun onDeletePostConfirmed(postId: String) {
        viewModelScope.launch {
            deletePost(groupId, postId).fold(
                onSuccess = {
                    rawPosts = rawPosts.filterNot { it.id == postId }
                    publishFeed()
                    _events.trySend(GroupDetailEvent.PostDeleted)
                },
                onFailure = { err ->
                    _events.trySend(GroupDetailEvent.ShowError(err.toCommunityError()))
                },
            )
        }
    }

    private fun start() {
        job?.cancel()
        cancelFeedJobs()
        job = viewModelScope.launch {
            val uid = session.currentUid().orEmpty()
            val names = districtNames.byId()
            val membership = groups.observeMembership(groupId).catch { emit(GroupMembership.NONE) }
            combine(groups.observeGroup(groupId), membership, busy, rawFeedState) { group, member, isBusy, rawFeed ->
                if (group == null) {
                    GroupDetailUiState.NotFound
                } else {
                    val role = member.role ?: GroupRole.OWNER.takeIf { group.ownerId == uid }
                    val permissions = GroupPermissions(uid, role, group.status)
                    GroupDetailUiState.Content(
                        group = group,
                        membership = member,
                        permissions = permissions,
                        districtName = names[group.districtId],
                        busy = isBusy,
                        feed = rawFeed.toUi(permissions),
                    )
                }
            }
                .catch {
                    val error = it.toCommunityError()
                    _state.value = if (error is CommunityError.Forbidden || error is CommunityError.GroupNotFound) {
                        GroupDetailUiState.NotFound
                    } else {
                        GroupDetailUiState.Error(error)
                    }
                }
                .collect { uiState ->
                    _state.value = uiState
                    if (uiState is GroupDetailUiState.Content) {
                        syncFeed(uiState.membership)
                    } else {
                        clearFeed()
                    }
                }
        }
    }

    private fun syncFeed(membership: GroupMembership) {
        val key = membership.isMember to isMod(membership.role)
        if (key == activeFeedKey) return
        activeFeedKey = key
        if (!membership.isMember) {
            clearFeed()
        } else {
            loadFeed(isMod = key.second)
        }
    }

    private fun loadFeed(isMod: Boolean) {
        cancelFeedJobs()
        currentIsMod = isMod
        rawPosts = emptyList()
        nextCursor = null
        loadingMore = false
        loadMoreError = null
        rawFeedState.value = RawFeedState.Loading

        feedJob = viewModelScope.launch {
            val uid = session.currentUid().orEmpty()
            posts.feed(groupId, isMod, cursor = null).fold(
                onSuccess = { page ->
                    val filtered = filterVisible(page.items, isMod, uid)
                    resolveUrls(filtered)
                    rawPosts = filtered.distinctBy { it.id }.sortedWith(POST_ORDER)
                    nextCursor = page.next
                    publishFeed()
                    val after = rawPosts.firstOrNull()?.createdAt ?: Instant.EPOCH
                    listenNewPosts(isMod, after, uid)
                },
                onFailure = { err ->
                    rawFeedState.value = RawFeedState.Error(err.toCommunityError())
                },
            )
        }
    }

    private fun listenNewPosts(isMod: Boolean, after: Instant, uid: String) {
        newPostsJob?.cancel()
        newPostsJob = viewModelScope.launch {
            posts.observeNewPosts(groupId, isMod, after)
                .catch { /* Lỗi listener bài mới không ghi đè trang đã tải. */ }
                .collect { incoming ->
                    val filtered = filterVisible(incoming, isMod, uid)
                    if (filtered.isEmpty()) return@collect
                    resolveUrls(filtered)
                    rawPosts = mergePosts(filtered, rawPosts)
                    publishFeed()
                }
        }
    }

    private suspend fun resolveUrls(items: List<Post>) {
        val paths = items.flatMap { it.photoPaths }
        if (paths.isEmpty()) return
        val resolved = signedUrls.resolve(paths)
        if (resolved.isNotEmpty()) photoUrls.putAll(resolved)
    }

    private fun filterVisible(items: List<Post>, isMod: Boolean, uid: String): List<Post> =
        if (isMod) items else items.filter { it.status == PostStatus.VISIBLE || it.author.userId == uid }

    private fun mergePosts(preferred: List<Post>, existing: List<Post>): List<Post> {
        val byId = LinkedHashMap<String, Post>()
        for (p in existing) byId[p.id] = p
        for (p in preferred) byId[p.id] = p
        return byId.values.sortedWith(POST_ORDER)
    }

    private fun publishFeed() {
        rawFeedState.value = if (rawPosts.isEmpty()) {
            RawFeedState.Empty
        } else {
            RawFeedState.Content(
                posts = rawPosts,
                urls = photoUrls.toMap(),
                canLoadMore = nextCursor != null,
                loadingMore = loadingMore,
                loadMoreError = loadMoreError,
            )
        }
    }

    private fun clearFeed() {
        cancelFeedJobs()
        rawPosts = emptyList()
        nextCursor = null
        loadingMore = false
        loadMoreError = null
        rawFeedState.value = RawFeedState.Loading
    }

    private fun cancelFeedJobs() {
        feedJob?.cancel()
        newPostsJob?.cancel()
        loadMoreJob?.cancel()
    }

    private fun RawFeedState.toUi(permissions: GroupPermissions): FeedUiState = when (this) {
        RawFeedState.Loading -> FeedUiState.Loading
        RawFeedState.Empty -> FeedUiState.Empty
        is RawFeedState.Error -> FeedUiState.Error(cause)
        is RawFeedState.Content -> FeedUiState.Content(
            items = posts.map { post ->
                PostItem(
                    post = post,
                    photoUrls = post.photoPaths.mapNotNull { path -> urls[path]?.let { path to it } }.toMap(),
                    canEdit = permissions.canEditPost(post),
                    canDelete = permissions.canDeletePost(post),
                    canHide = permissions.canHide(post),
                    canUnhide = permissions.canUnhide(post),
                    canReport = permissions.canReport(post),
                )
            },
            canLoadMore = canLoadMore,
            loadingMore = loadingMore,
            loadMoreError = loadMoreError,
        )
    }

    private fun isMod(role: GroupRole?): Boolean = role == GroupRole.OWNER || role == GroupRole.ADMIN

    fun onJoinClicked() {
        if (session.currentUid() == null) {
            _events.trySend(GroupDetailEvent.RequireLogin(deepLink()))
            return
        }
        act(KEY_JOIN_ID, { id -> joinGroup(groupId, id) }) { _events.trySend(GroupDetailEvent.JoinOutcome(it)) }
    }

    fun onCancelRequestClicked() =
        act(KEY_CANCEL_ID, { id -> joinGroup.cancel(groupId, id) }) { _events.trySend(GroupDetailEvent.RequestCancelled) }

    /** Gọi sau khi người dùng xác nhận trong hộp thoại "Rời nhóm?". */
    fun onLeaveConfirmed() = act(
        KEY_LEAVE_ID,
        { id -> leaveGroup(groupId, id) },
        onError = { error ->
            if (error is CommunityError.OwnerMustTransfer) {
                _events.trySend(
                    if (error.soleMember) GroupDetailEvent.SuggestDeleteGroup else GroupDetailEvent.OpenTransferOwner(groupId),
                )
                true
            } else {
                false
            }
        },
    ) { _events.trySend(GroupDetailEvent.Left) }

    private fun <T> act(
        key: String,
        call: suspend (String) -> Result<T>,
        onError: (CommunityError) -> Boolean = { false },
        onSuccess: (T) -> Unit,
    ) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                val requestId = savedStateHandle.get<String>(key) ?: UUID.randomUUID().toString().also { savedStateHandle[key] = it }
                call(requestId).fold(
                    onSuccess = {
                        savedStateHandle[key] = null
                        onSuccess(it)
                    },
                    onFailure = {
                        val error = it.toCommunityError()
                        if (error !is CommunityError.Network && error !is CommunityError.Busy) savedStateHandle[key] = null
                        if (!onError(error)) _events.trySend(GroupDetailEvent.ShowError(error))
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } finally {
                busy.value = false
            }
        }
    }

    private fun deepLink() = "smashnow://community/group/$groupId"

    companion object {
        const val ARG_GROUP_ID = "groupId"
        private const val KEY_JOIN_ID = "join_request_id"
        private const val KEY_CANCEL_ID = "cancel_request_id"
        private const val KEY_LEAVE_ID = "leave_request_id"

        private val POST_ORDER: Comparator<Post> = compareByDescending<Post> { it.createdAt ?: Instant.EPOCH }
            .thenByDescending { it.id }
    }
}

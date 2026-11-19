package com.example.booking_app.feature.community.ui.composer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.media.SignedUrlCache
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.PostRepository
import com.example.booking_app.feature.community.domain.model.DraftPhoto
import com.example.booking_app.feature.community.domain.model.Post
import com.example.booking_app.feature.community.domain.model.PostDraft
import com.example.booking_app.feature.community.domain.model.PostType
import com.example.booking_app.feature.community.domain.toCommunityError
import com.example.booking_app.feature.community.domain.usecase.EditPost
import com.example.booking_app.feature.community.domain.usecase.PublishPost
import com.example.booking_app.feature.community.domain.usecase.UploadPostPhotos
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

enum class PostContentError { REQUIRED, TOO_LONG }
enum class EventTimeError { REQUIRED, PAST }

data class PostComposerState(
    val isEdit: Boolean = false,
    val type: PostType = PostType.POST,
    val content: String = "",
    val contentError: PostContentError? = null,
    val photos: List<DraftPhoto> = emptyList(),
    /** URL xem trước cho ảnh đã có từ server khi ở chế độ sửa (`remotePath -> signedUrl`). */
    val remotePhotoUrls: Map<String, String> = emptyMap(),
    // US4: trường sự kiện giao lưu
    val eventTitle: String = "",
    val eventAt: Instant? = null,
    val eventLocation: String = "",
    val expectedCountText: String = "",
    val eventTitleError: PostContentError? = null,
    val eventTimeError: EventTimeError? = null,
    val eventLocationError: PostContentError? = null,
    val expectedCountError: Boolean = false,
    val saving: Boolean = false,
    val loading: Boolean = false,
    val loadError: CommunityError? = null,
) {
    val isEvent: Boolean get() = type == PostType.EVENT
    val canAddMorePhotos: Boolean get() = !saving && photos.size < PostComposerViewModel.MAX_PHOTOS

    /** Nút "Đăng"/"Lưu" chỉ bật khi không đang lưu/tải và mọi ảnh đã `UPLOADED` kèm `remotePath` (AC 3.3). */
    val canPublish: Boolean
        get() = !saving && !loading && loadError == null &&
            photos.all { it.state == DraftPhoto.State.UPLOADED && !it.remotePath.isNullOrBlank() }
}

sealed interface PostComposerEvent {
    data object MaxPhotosReached : PostComposerEvent
    data object ConfirmDiscard : PostComposerEvent
    data object Close : PostComposerEvent
    data class Posted(val post: Post) : PostComposerEvent
    data class ShowError(val error: CommunityError) : PostComposerEvent
}

/**
 * T049 + T056: soạn và sửa bài đăng / sự kiện giao lưu. `requestId` (= `postId`) sinh một lần và giữ qua
 * [SavedStateHandle] (AC 3.3/R9).
 */
@HiltViewModel
class PostComposerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val posts: PostRepository,
    private val uploadPhotos: UploadPostPhotos,
    private val publishPost: PublishPost,
    private val editPost: EditPost,
    private val signedUrls: SignedUrlCache,
    private val clock: AppClock,
) : ViewModel() {

    val groupId: String = checkNotNull(savedStateHandle[ARG_GROUP_ID]) { "Thiếu groupId" }
    val editPostId: String? = savedStateHandle.get<String>(ARG_POST_ID)?.takeIf { it.isNotBlank() }
    val initialType: PostType = savedStateHandle.get<String>(ARG_TYPE)
        ?.let { raw -> PostType.entries.firstOrNull { it.name == raw } }
        ?: PostType.POST

    /** `postId` dùng cho `group-media-url` và `group-post create`; giữ cố định qua `SavedStateHandle`. */
    val requestId: String = editPostId
        ?: savedStateHandle.get<String>(KEY_REQUEST_ID)
        ?: UUID.randomUUID().toString().also { savedStateHandle[KEY_REQUEST_ID] = it }

    private val _state = MutableStateFlow(
        PostComposerState(isEdit = editPostId != null, type = initialType, loading = editPostId != null),
    )
    val state: StateFlow<PostComposerState> = _state.asStateFlow()

    private val _events = Channel<PostComposerEvent>(Channel.BUFFERED)
    val events: Flow<PostComposerEvent> = _events.receiveAsFlow()

    private val uploadJobs = LinkedHashMap<String, Job>()
    private val slotByKey = LinkedHashMap<String, Int>()
    private val originalSlots = LinkedHashSet<Int>()
    private val allocatedInSession = LinkedHashSet<Int>()
    private var hasUploadedInSession = false
    private var originalContent: String = ""
    private var originalPhotoPaths: List<String> = emptyList()
    private var originalEventTitle: String = ""
    private var originalEventAt: Instant? = null
    private var originalEventLocation: String = ""
    private var originalExpectedCountText: String = ""

    init {
        if (editPostId != null) loadPost()
    }

    fun loadPost() {
        val postId = editPostId ?: return
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            posts.getPost(groupId, postId).fold(
                onSuccess = { post ->
                    if (post == null) {
                        _state.update { it.copy(loading = false, loadError = CommunityError.PostNotFound()) }
                    } else {
                        val urls = if (post.photoPaths.isEmpty()) emptyMap() else signedUrls.resolve(post.photoPaths)
                        originalContent = post.content
                        originalPhotoPaths = post.photoPaths
                        originalEventTitle = post.event?.title.orEmpty()
                        originalEventAt = post.event?.at
                        originalEventLocation = post.event?.location.orEmpty()
                        originalExpectedCountText = post.event?.expectedCount?.toString().orEmpty()
                        slotByKey.clear()
                        originalSlots.clear()
                        allocatedInSession.clear()
                        val existingPhotos = post.photoPaths.mapIndexed { idx, path ->
                            val parsedIndex = parseSlotIndex(path) ?: idx
                            slotByKey[path] = parsedIndex
                            originalSlots += parsedIndex
                            DraftPhoto(localUri = path, remotePath = path, state = DraftPhoto.State.UPLOADED)
                        }
                        _state.update {
                            it.copy(
                                loading = false,
                                loadError = null,
                                type = post.type,
                                content = post.content,
                                photos = existingPhotos,
                                remotePhotoUrls = urls,
                                eventTitle = originalEventTitle,
                                eventAt = originalEventAt,
                                eventLocation = originalEventLocation,
                                expectedCountText = originalExpectedCountText,
                            )
                        }
                    }
                },
                onFailure = { err ->
                    _state.update { it.copy(loading = false, loadError = err.toCommunityError()) }
                },
            )
        }
    }

    fun onContentChanged(value: String) = _state.update { it.copy(content = value, contentError = null) }

    fun onEventTitleChanged(value: String) = _state.update { it.copy(eventTitle = value, eventTitleError = null) }

    fun onEventAtChanged(value: Instant) = _state.update { it.copy(eventAt = value, eventTimeError = null) }

    fun onEventLocationChanged(value: String) = _state.update { it.copy(eventLocation = value, eventLocationError = null) }

    fun onExpectedCountChanged(value: String) = _state.update { it.copy(expectedCountText = value, expectedCountError = false) }

    fun onPhotosPicked(uris: List<String>) {
        if (uris.isEmpty()) return
        val current = _state.value
        if (current.saving) return
        val remaining = (MAX_PHOTOS - current.photos.size).coerceAtLeast(0)
        if (uris.size > remaining) {
            _events.trySend(PostComposerEvent.MaxPhotosReached)
        }
        val existingKeys = current.photos.mapTo(HashSet()) { it.localUri }
        val toAdd = uris.asSequence()
            .filter { it.isNotBlank() && it !in existingKeys }
            .distinct()
            .take(remaining)
            .toList()
        if (toAdd.isEmpty()) return

        val pending = toAdd.map { DraftPhoto(localUri = it, state = DraftPhoto.State.PENDING) }
        _state.update { it.copy(photos = it.photos + pending) }
        for (uri in toAdd) {
            val slot = allocateSlot(uri)
            startUpload(uri, slot)
        }
    }

    fun onRetryPhoto(localUri: String) {
        val photo = _state.value.photos.firstOrNull { it.localUri == localUri } ?: return
        if (photo.state != DraftPhoto.State.FAILED) return
        val slot = slotByKey[localUri] ?: allocateSlot(localUri)
        startUpload(localUri, slot)
    }

    fun onRemovePhoto(localUri: String) {
        uploadJobs.remove(localUri)?.cancel()
        slotByKey.remove(localUri)
        _state.update { it.copy(photos = it.photos.filterNot { p -> p.localUri == localUri }) }
    }

    fun submit() {
        val current = _state.value
        if (!current.canPublish) return

        val validated = validate(current)
        val hasError = validated.contentError != null ||
            validated.eventTitleError != null ||
            validated.eventTimeError != null ||
            validated.eventLocationError != null ||
            validated.expectedCountError
        if (hasError) {
            _state.value = validated
            return
        }

        val draft = PostDraft(
            requestId = requestId,
            type = current.type,
            content = current.content.trim(),
            photos = current.photos,
            eventTitle = current.eventTitle.trim(),
            eventAt = current.eventAt,
            eventLocation = current.eventLocation.trim(),
            expectedCount = current.expectedCountText.trim().toIntOrNull(),
        )
        _state.value = validated.copy(saving = true)
        viewModelScope.launch {
            val result = try {
                if (current.isEdit) editPost(groupId, draft) else publishPost(groupId, draft)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            result.fold(
                onSuccess = { post ->
                    _events.trySend(PostComposerEvent.Posted(post))
                },
                onFailure = { err ->
                    val communityError = err.toCommunityError()
                    _state.update { state ->
                        val updatedPhotos = if (communityError is CommunityError.PhotoMissing) {
                            state.photos.map { p ->
                                if (p.remotePath != null && p.localUri != p.remotePath) {
                                    p.copy(remotePath = null, state = DraftPhoto.State.FAILED)
                                } else {
                                    p
                                }
                            }
                        } else {
                            state.photos
                        }
                        state.copy(saving = false, photos = updatedPhotos)
                    }
                    _events.trySend(PostComposerEvent.ShowError(communityError))
                },
            )
        }
    }

    private fun validate(state: PostComposerState): PostComposerState {
        val trimmedContent = state.content.trim()
        val contentError = when {
            state.type == PostType.POST && trimmedContent.isEmpty() -> PostContentError.REQUIRED
            trimmedContent.length > MAX_CONTENT_LENGTH -> PostContentError.TOO_LONG
            else -> null
        }
        if (state.type != PostType.EVENT) {
            return state.copy(
                contentError = contentError,
                eventTitleError = null,
                eventTimeError = null,
                eventLocationError = null,
                expectedCountError = false,
            )
        }
        val title = state.eventTitle.trim()
        val titleError = when {
            title.isEmpty() -> PostContentError.REQUIRED
            title.length > MAX_EVENT_TITLE -> PostContentError.TOO_LONG
            else -> null
        }
        val now = Instant.ofEpochMilli(clock.now())
        val timeError = when {
            state.eventAt == null -> EventTimeError.REQUIRED
            !state.eventAt.isAfter(now) -> EventTimeError.PAST
            else -> null
        }
        val location = state.eventLocation.trim()
        val locationError = when {
            location.isEmpty() -> PostContentError.REQUIRED
            location.length > MAX_EVENT_LOCATION -> PostContentError.TOO_LONG
            else -> null
        }
        val expected = state.expectedCountText.trim().toIntOrNull()
        val expectedError = expected == null || expected !in MIN_EXPECTED_COUNT..MAX_EXPECTED_COUNT

        return state.copy(
            contentError = contentError,
            eventTitleError = titleError,
            eventTimeError = timeError,
            eventLocationError = locationError,
            expectedCountError = expectedError,
        )
    }

    fun onBackPressed() {
        if (hasUnsavedChanges()) {
            _events.trySend(PostComposerEvent.ConfirmDiscard)
        } else {
            _events.trySend(PostComposerEvent.Close)
        }
    }

    fun onDiscardConfirmed() {
        uploadJobs.values.forEach { it.cancel() }
        uploadJobs.clear()
        val shouldDiscard = !_state.value.isEdit && (hasUploadedInSession || _state.value.photos.isNotEmpty())
        viewModelScope.launch {
            if (shouldDiscard) {
                runCatching { uploadPhotos.discard(groupId, requestId) }
                    .onFailure { if (it is CancellationException) throw it }
            }
            _events.trySend(PostComposerEvent.Close)
        }
    }

    private fun hasUnsavedChanges(): Boolean {
        val current = _state.value
        if (current.isEdit) {
            val photosChanged = current.photos.any { it.localUri != it.remotePath } ||
                current.photos.map { it.remotePath ?: it.localUri } != originalPhotoPaths
            return current.content != originalContent ||
                photosChanged ||
                current.eventTitle != originalEventTitle ||
                current.eventAt != originalEventAt ||
                current.eventLocation != originalEventLocation ||
                current.expectedCountText != originalExpectedCountText
        }
        return current.content.isNotBlank() ||
            current.photos.isNotEmpty() ||
            current.eventTitle.isNotBlank() ||
            current.eventAt != null ||
            current.eventLocation.isNotBlank() ||
            current.expectedCountText.isNotBlank()
    }

    private fun startUpload(localUri: String, slotIndex: Int) {
        hasUploadedInSession = true
        uploadJobs.remove(localUri)?.cancel()
        uploadJobs[localUri] = viewModelScope.launch {
            uploadPhotos.uploadOne(groupId, requestId, slotIndex, localUri).collect { updated ->
                val remotePath = updated.remotePath
                if (updated.state == DraftPhoto.State.UPLOADED && !remotePath.isNullOrBlank()) {
                    signedUrls.invalidate(listOf(remotePath))
                }
                _state.update { state ->
                    state.copy(
                        photos = state.photos.map { photo ->
                            if (photo.localUri == localUri) updated else photo
                        },
                    )
                }
            }
        }
    }

    private fun allocateSlot(key: String): Int {
        slotByKey[key]?.let { return it }
        val active = slotByKey.values.toSet()
        val free = (0 until MAX_PHOTOS).firstOrNull { it !in active && it !in originalSlots && it !in allocatedInSession }
            ?: (0 until MAX_PHOTOS).firstOrNull { it !in active && it !in originalSlots }
            ?: (0 until MAX_PHOTOS).firstOrNull { it !in active }
            ?: 0
        slotByKey[key] = free
        allocatedInSession += free
        return free
    }

    private fun parseSlotIndex(path: String): Int? {
        val fileName = path.substringAfterLast('/')
        val stem = fileName.substringBeforeLast('.')
        return stem.toIntOrNull()?.takeIf { it in 0 until MAX_PHOTOS }
    }

    companion object {
        const val ARG_GROUP_ID = "groupId"
        const val ARG_POST_ID = "postId"
        const val ARG_TYPE = "type"
        const val KEY_REQUEST_ID = "post_request_id"
        const val MAX_PHOTOS = 4
        const val MAX_CONTENT_LENGTH = 2000
        const val MAX_EVENT_TITLE = 100
        const val MAX_EVENT_LOCATION = 200
        const val MIN_EXPECTED_COUNT = 2
        const val MAX_EXPECTED_COUNT = 100
    }
}

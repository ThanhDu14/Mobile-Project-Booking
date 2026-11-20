package com.example.booking_app.feature.chat.ui.list

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.feature.chat.data.dto.ChatDto
import com.example.booking_app.feature.chat.data.dto.ChatUserSnapshotDto
import com.example.booking_app.feature.chat.data.dto.LastMessageDto
import com.example.booking_app.feature.chat.data.dto.toSummary
import com.example.booking_app.feature.chat.domain.ChatError
import com.example.booking_app.feature.chat.domain.ChatRepository
import com.example.booking_app.feature.chat.domain.model.ChatMode
import com.example.booking_app.feature.chat.domain.model.ChatStatus
import com.example.booking_app.feature.chat.domain.model.ChatSummary
import com.example.booking_app.feature.chat.domain.model.ChatType
import com.example.booking_app.feature.chat.domain.model.MessageType
import com.example.booking_app.feature.chat.domain.model.Participant
import com.example.booking_app.feature.chat.ui.common.ChatUiText
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** T036: unit test cho [ChatListViewModel] (US3). */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<ChatRepository>(relaxed = true)
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val chatsFlow = MutableStateFlow<List<ChatSummary>>(emptyList())

    private fun summary(
        id: String,
        updatedAtSec: Long,
        unreadCount: Int = 0,
        lastMessageType: MessageType? = MessageType.TEXT,
        lastMessagePreview: String? = "Xin chào",
        status: ChatStatus = ChatStatus.ACTIVE,
        muted: Boolean = false,
    ) = ChatSummary(
        id = id,
        type = ChatType.DIRECT,
        title = "Chat $id",
        lastMessagePreview = lastMessagePreview,
        lastMessageType = lastMessageType,
        updatedAt = Instant.ofEpochSecond(1_700_000_000L + updatedAtSec),
        unreadCount = unreadCount,
        isReadOnly = status == ChatStatus.READ_ONLY,
        muted = muted,
        status = status,
    )

    private fun createViewModel(
        mode: ChatMode = ChatMode.PLAYER,
        uid: String? = "player1",
    ): ChatListViewModel {
        every { sessionManager.currentUid() } returns uid
        every { repository.observeChats(mode) } returns chatsFlow
        return ChatListViewModel(
            savedStateHandle = SavedStateHandle(mapOf(ChatListViewModel.ARG_MODE to mode.name)),
            repository = repository,
            sessionManager = sessionManager,
        )
    }

    @Test
    fun `sap xep updatedAt desc bo cuoc DELETED va tin moi nhay len dau`() = runTest {
        chatsFlow.value = listOf(
            summary("c-old", updatedAtSec = 10, unreadCount = 0),
            summary("c-new", updatedAtSec = 50, unreadCount = 2),
            summary("c-mid", updatedAtSec = 30, unreadCount = 1),
            summary("c-del", updatedAtSec = 99, unreadCount = 5, status = ChatStatus.DELETED),
        )

        val vm = createViewModel()
        runCurrent()

        val initial = (vm.state.value as UiState.Content).data
        assertEquals(listOf("c-new", "c-mid", "c-old"), initial.map { it.id })

        // Tin mới tới ở c-old -> nhảy lên đầu danh sách ngay (AC 3.2).
        chatsFlow.value = listOf(
            summary("c-old", updatedAtSec = 200, unreadCount = 1),
            summary("c-new", updatedAtSec = 50, unreadCount = 2),
            summary("c-mid", updatedAtSec = 30, unreadCount = 1),
        )
        runCurrent()

        val updated = (vm.state.value as UiState.Content).data
        assertEquals(listOf("c-old", "c-new", "c-mid"), updated.map { it.id })
        assertEquals(1, updated.first().unreadCount)
    }

    @Test
    fun `huy hieu chua doc vuot 99 hien 99+ va mapper doc unreadCounts dung`() = runTest {
        assertNull(ChatUiText.unreadBadge(0))
        assertEquals("1", ChatUiText.unreadBadge(1))
        assertEquals("99", ChatUiText.unreadBadge(99))
        assertEquals("99+", ChatUiText.unreadBadge(100))
        assertEquals("99+", ChatUiText.unreadBadge(250))

        val dto = ChatDto(
            id = "owner1_player1",
            type = "DIRECT",
            memberIds = listOf("owner1", "player1"),
            ownerId = "owner1",
            playerId = "player1",
            memberSnapshots = mapOf("owner1" to ChatUserSnapshotDto("Anh Tuấn", null)),
            unreadCounts = mapOf("player1" to 120, "owner1" to 0),
            status = "ACTIVE",
            updatedAt = Instant.ofEpochSecond(1_700_000_100L),
        )
        val mapped = dto.toSummary(currentUid = "player1", myParticipant = Participant(muted = true))
        assertEquals(120, mapped.unreadCount)
        assertEquals("99+", ChatUiText.unreadBadge(mapped.unreadCount))
        assertTrue(mapped.muted)
    }

    @Test
    fun `tin cuoi la anh va tin thu hoi giu dung loai va ban xem truoc`() = runTest {
        val imageDto = ChatDto(
            id = "c-img",
            type = "DIRECT",
            memberIds = listOf("owner1", "player1"),
            lastMessage = LastMessageDto(
                messageId = "m1",
                senderId = "owner1",
                type = "IMAGE",
                preview = "[Hình ảnh]",
                createdAt = Instant.ofEpochSecond(1_700_000_020L),
            ),
            updatedAt = Instant.ofEpochSecond(1_700_000_020L),
            status = "ACTIVE",
        ).toSummary(currentUid = "player1")

        val recalledDto = ChatDto(
            id = "c-rec",
            type = "DIRECT",
            memberIds = listOf("owner1", "player1"),
            lastMessage = LastMessageDto(
                messageId = "m2",
                senderId = "owner1",
                type = "TEXT",
                preview = "Tin nhắn đã được thu hồi",
                createdAt = Instant.ofEpochSecond(1_700_000_010L),
            ),
            updatedAt = Instant.ofEpochSecond(1_700_000_010L),
            status = "ACTIVE",
        ).toSummary(currentUid = "player1")

        chatsFlow.value = listOf(imageDto, recalledDto)
        val vm = createViewModel()
        runCurrent()

        val items = (vm.state.value as UiState.Content).data
        assertEquals(MessageType.IMAGE, items[0].lastMessageType)
        assertEquals("[Hình ảnh]", items[0].lastMessagePreview)
        assertEquals(MessageType.TEXT, items[1].lastMessageType)
        assertEquals("Tin nhắn đã được thu hồi", items[1].lastMessagePreview)
    }

    @Test
    fun `trang thai Empty o che do PLAYER mo Tham gia nhom va Tim san`() = runTest {
        chatsFlow.value = emptyList()
        val vm = createViewModel(mode = ChatMode.PLAYER)
        runCurrent()

        assertEquals(UiState.Empty, vm.state.value)
        assertEquals(ChatMode.PLAYER, vm.mode)

        vm.events.test {
            vm.onJoinGroupClicked()
            assertEquals(ChatListEvent.OpenJoinGroup, awaitItem())
            vm.onFindVenueClicked()
            assertEquals(ChatListEvent.OpenFindVenue, awaitItem())
        }
    }

    @Test
    fun `trang thai Empty o che do OWNER phan biet mode rieng`() = runTest {
        chatsFlow.value = emptyList()
        val vm = createViewModel(mode = ChatMode.OWNER)
        runCurrent()

        assertEquals(ChatMode.OWNER, vm.mode)
        assertEquals(UiState.Empty, vm.state.value)
    }

    @Test
    fun `loi listener hien Error va thu lai tai lai thanh cong`() = runTest {
        var attempt = 0
        every { sessionManager.currentUid() } returns "player1"
        every { repository.observeChats(ChatMode.PLAYER) } answers {
            attempt++
            if (attempt == 1) {
                flow { throw ChatError.Network() }
            } else {
                chatsFlow
            }
        }
        chatsFlow.value = listOf(summary("c1", updatedAtSec = 10))

        val vm = ChatListViewModel(
            savedStateHandle = SavedStateHandle(mapOf(ChatListViewModel.ARG_MODE to ChatMode.PLAYER.name)),
            repository = repository,
            sessionManager = sessionManager,
        )
        runCurrent()

        val error = vm.state.value as UiState.Error
        assertEquals(ChatError.Network().code, error.message)

        error.retry?.invoke()
        runCurrent()

        val content = vm.state.value as UiState.Content
        assertEquals(listOf("c1"), content.data.map { it.id })
    }

    @Test
    fun `khach chua dang nhap phat su kien RequireLogin`() = runTest {
        val vm = createViewModel(mode = ChatMode.OWNER, uid = null)
        vm.events.test {
            val event = awaitItem() as ChatListEvent.RequireLogin
            assertEquals("smashnow://chat/owner", event.returnTo)
        }
        val error = vm.state.value as UiState.Error
        assertTrue(error.retry != null)
    }

    @Test
    fun `tach danh sach PLAYER bo cuoc co ownerId trung currentUid va OWNER chi giu DIRECT cua minh`() = runTest {
        val items = listOf(
            summary("c-group", updatedAtSec = 30).copy(type = ChatType.GROUP, ownerId = null),
            summary("c-ask-other", updatedAtSec = 20).copy(type = ChatType.DIRECT, ownerId = "owner2"),
            summary("c-customer", updatedAtSec = 10).copy(type = ChatType.DIRECT, ownerId = "owner1"),
        )
        chatsFlow.value = items

        val playerVm = createViewModel(mode = ChatMode.PLAYER, uid = "owner1")
        runCurrent()
        assertEquals(
            listOf("c-group", "c-ask-other"),
            (playerVm.state.value as UiState.Content).data.map { it.id },
        )

        val ownerVm = createViewModel(mode = ChatMode.OWNER, uid = "owner1")
        runCurrent()
        assertEquals(
            listOf("c-customer"),
            (ownerVm.state.value as UiState.Content).data.map { it.id },
        )
    }

    @Test
    fun `cold start deep link cho phien khoi phuc xong truoc khi yeu cau dang nhap`() = runTest {
        val sessionFlow = MutableStateFlow<SessionState>(SessionState.Loading)
        every { sessionManager.sessionState } returns sessionFlow
        every { sessionManager.currentUid() } answers { (sessionFlow.value as? SessionState.SignedIn)?.uid }
        chatsFlow.value = listOf(summary("c1", updatedAtSec = 10))
        every { repository.observeChats(ChatMode.PLAYER) } returns chatsFlow

        val vm = ChatListViewModel(
            savedStateHandle = SavedStateHandle(mapOf(ChatListViewModel.ARG_MODE to ChatMode.PLAYER.name)),
            repository = repository,
            sessionManager = sessionManager,
        )
        runCurrent()
        assertEquals(UiState.Loading, vm.state.value)

        vm.events.test {
            expectNoEvents()
            sessionFlow.value = SessionState.SignedIn("player1", AppRole.PLAYER, true)
            runCurrent()
            expectNoEvents()
        }
        assertEquals(listOf("c1"), (vm.state.value as UiState.Content).data.map { it.id })
    }
}

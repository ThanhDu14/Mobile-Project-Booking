package com.example.booking_app.feature.chat.ui.conversation

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.R
import com.example.booking_app.core.image.ImageCompressor
import com.example.booking_app.core.image.ImageTooLargeException
import com.example.booking_app.core.image.ImageUnreadableException
import com.example.booking_app.core.media.SignedUrlBatch
import com.example.booking_app.core.media.SignedUrlCache
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.core.testing.stubSessionFromUid
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.chat.data.NetworkMonitor
import com.example.booking_app.feature.chat.data.dto.ChatUserSnapshotDto
import com.example.booking_app.feature.chat.data.dto.MessageDto
import com.example.booking_app.feature.chat.data.dto.toDomain
import com.example.booking_app.feature.chat.domain.ChatError
import com.example.booking_app.feature.chat.domain.ChatLocalMediaStore
import com.example.booking_app.feature.chat.domain.ChatRepository
import com.example.booking_app.feature.chat.domain.model.ChatDetail
import com.example.booking_app.feature.chat.domain.model.ChatMessage
import com.example.booking_app.feature.chat.domain.model.ChatStatus
import com.example.booking_app.feature.chat.domain.model.ChatType
import com.example.booking_app.feature.chat.domain.model.Delivery
import com.example.booking_app.feature.chat.domain.model.DirectChatResult
import com.example.booking_app.feature.chat.domain.model.GroupHeaderInfo
import com.example.booking_app.feature.chat.domain.model.MessageStatus
import com.example.booking_app.feature.chat.domain.model.OutboxEntry
import com.example.booking_app.feature.chat.domain.model.Participant
import com.example.booking_app.feature.chat.domain.model.UserSummary
import com.example.booking_app.feature.chat.domain.model.VenueSummary
import com.example.booking_app.feature.chat.domain.usecase.DiscardMessage
import com.example.booking_app.feature.chat.domain.usecase.LoadOlderMessages
import com.example.booking_app.feature.chat.domain.usecase.MarkChatRead
import com.example.booking_app.feature.chat.domain.usecase.ObserveMessages
import com.example.booking_app.feature.chat.domain.usecase.OpenDirectChat
import com.example.booking_app.feature.chat.domain.usecase.RecallMessage
import com.example.booking_app.feature.chat.domain.usecase.RetryMessage
import com.example.booking_app.feature.chat.domain.usecase.SendMessage
import com.example.booking_app.feature.chat.domain.usecase.SetBlocked
import com.example.booking_app.feature.chat.domain.usecase.SetMuted
import com.example.booking_app.feature.chat.ui.common.ChatUiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** T023: unit test cho [ChatViewModel] (US1). */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @Test
    fun `sign out clears private header draft selected media and signed URLs`() = runTest {
        val session = MutableStateFlow<SessionState>(SessionState.SignedIn("player1", AppRole.PLAYER, true))
        every { sessionManager.sessionState } returns session
        chatFlow.value = directChat()
        messagesFlow.value = listOf(serverMessage("private-image", "TEST private").copy(imagePath = "owner1_player1/private.jpg"))
        val vm = createViewModel()
        every { sessionManager.currentUid() } answers { (session.value as? SessionState.SignedIn)?.uid }
        runCurrent()
        assertTrue(vm.uiState.value.header != null)
        assertTrue(vm.uiState.value.imageUrls.isNotEmpty())
        vm.onComposerChanged("TEST private draft")
        vm.onImageSelected("content://test/private-image")
        session.value = SessionState.SignedOut(null)
        runCurrent()
        assertNull(vm.uiState.value.header)
        assertEquals("", vm.uiState.value.composerText)
        assertNull(vm.uiState.value.pendingImageUri)
        assertTrue(vm.uiState.value.imageUrls.isEmpty())
        assertTrue(vm.uiState.value.messages is UiState.Error)
    }

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeClock(var currentMs: Long = 1_700_000_000_000L) : AppClock {
        override fun now(): Long = currentMs
        override fun elapsedRealtime(): Long = currentMs
    }

    private val repository = mockk<ChatRepository>(relaxed = true)
    private val imageCompressor = mockk<ImageCompressor>()
    private val deletedLocalUris = mutableListOf<String>()
    private val localMediaStore = object : ChatLocalMediaStore {
        val saved = mutableMapOf<String, ByteArray>()
        override suspend fun saveCompressed(messageId: String, jpegBytes: ByteArray): String {
            val uri = "file:///cache/chat/outbox_$messageId.jpg"
            saved[uri] = jpegBytes
            return uri
        }
        override suspend fun readPrecompressed(uri: String): ByteArray? = saved[uri]
        override fun deleteIfOwned(uri: String?) {
            if (!uri.isNullOrBlank()) deletedLocalUris.add(uri)
        }
    }
    private val sessionManager = mockk<SessionManager>(relaxed = true).also { it.stubSessionFromUid() }
    private val clock = FakeClock()
    private val onlineFlow = MutableStateFlow(true)
    private val networkMonitor = object : NetworkMonitor {
        override val isOnline = onlineFlow
        override fun isCurrentlyOnline() = onlineFlow.value
    }

    private val chatFlow = MutableStateFlow<ChatDetail?>(null)
    private val groupHeaderFlow = MutableStateFlow<GroupHeaderInfo?>(null)
    private val messagesFlow = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val outboxFlow = MutableStateFlow<List<OutboxEntry>>(emptyList())
    private val requestedSignedPaths = mutableListOf<String>()
    private var signedUrlVersion = 0
    private var failSignedUrls = false
    private val signedUrlCache = SignedUrlCache(
        fetch = { paths ->
            requestedSignedPaths.addAll(paths)
            if (failSignedUrls) {
                Result.failure(ChatError.Network())
            } else {
                signedUrlVersion++
                val suffix = if (signedUrlVersion == 1) "" else "?v=$signedUrlVersion"
                Result.success(SignedUrlBatch(paths.associateWith { "https://signed/$it$suffix" }, null))
            }
        },
        clock = clock,
    )

    private fun directChat(
        id: String = "owner1_player1",
        status: ChatStatus = ChatStatus.ACTIVE,
        venue: VenueSummary? = VenueSummary("v1", "Sân Hòa Bình", "v1/cover.jpg"),
    ) = ChatDetail(
        id = id,
        type = ChatType.DIRECT,
        memberIds = listOf("owner1", "player1"),
        ownerId = "owner1",
        playerId = "player1",
        venue = venue,
        otherParty = UserSummary("owner1", "Anh Tuấn", "owner1/avatar.jpg"),
        status = status,
        myParticipant = Participant(),
    )

    private fun serverMessage(
        id: String,
        text: String,
        secondsOffset: Long = 0,
        isMine: Boolean = false,
    ) = ChatMessage(
        id = id,
        senderId = if (isMine) "player1" else "owner1",
        text = text,
        createdAt = Instant.ofEpochMilli(1_700_000_000_000L + secondsOffset * 1000),
        delivery = Delivery.SENT,
        isMine = isMine,
    )

    private fun groupChat(
        groupId: String = "g1",
        status: ChatStatus = ChatStatus.ACTIVE,
        memberIds: List<String> = listOf("player1", "member2", "member3"),
    ) = ChatDetail(
        id = "group_$groupId",
        type = ChatType.GROUP,
        memberIds = memberIds,
        groupId = groupId,
        status = status,
        myParticipant = Participant(),
    )

    private fun createViewModel(
        args: Map<String, Any?> = mapOf(ChatViewModel.ARG_CHAT_ID to "owner1_player1"),
        uid: String? = "player1",
        serverOffsetMs: Long = 0L,
    ): ChatViewModel {
        every { sessionManager.currentUid() } returns uid
        every { repository.observeChat(any()) } returns chatFlow
        every { repository.observeGroupHeader(any()) } returns groupHeaderFlow
        every { repository.observeMessages(any()) } returns messagesFlow
        every { repository.observeOutbox(any()) } returns outboxFlow
        every { repository.estimatedServerOffsetMs() } returns serverOffsetMs
        coEvery { repository.markRead(any()) } returns Result.success(Unit)
        coEvery { repository.setViewing(any(), any()) } returns Result.success(Unit)
        coEvery { imageCompressor.compress(any()) } returns ByteArray(1024)
        coEvery { repository.send(any()) } coAnswers {
            val entry = firstArg<OutboxEntry>()
            outboxFlow.value = outboxFlow.value + entry
            Result.success(Unit)
        }
        return ChatViewModel(
            savedStateHandle = SavedStateHandle(args),
            repository = repository,
            openDirectChat = OpenDirectChat(repository),
            observeMessages = ObserveMessages(repository),
            loadOlderMessages = LoadOlderMessages(repository),
            sendMessage = SendMessage(repository, imageCompressor, localMediaStore, clock),
            retryMessage = RetryMessage(repository),
            discardMessage = DiscardMessage(repository),
            markChatRead = MarkChatRead(repository),
            setMuted = SetMuted(repository),
            setBlocked = SetBlocked(repository),
            recallMessage = RecallMessage(repository),
            signedUrlCache = signedUrlCache,
            networkMonitor = networkMonitor,
            sessionManager = sessionManager,
            clock = clock,
        )
    }

    @Test
    fun `Loading den Content gop tin server va outbox o cuoi dung trang thai`() = runTest {
        val vm = createViewModel()
        assertEquals(UiState.Loading, vm.uiState.value.messages)

        chatFlow.value = directChat()
        messagesFlow.value = listOf(serverMessage("m1", "Chào bạn", 1))
        outboxFlow.value = listOf(
            OutboxEntry("m-pending", "owner1_player1", "Đang gửi...", createdAtLocal = 2000L, status = Delivery.PENDING),
            OutboxEntry("m-failed", "owner1_player1", "Bị lỗi", createdAtLocal = 3000L, status = Delivery.FAILED),
        )
        runCurrent()

        val content = vm.uiState.value.messages as UiState.Content
        assertEquals(listOf("m1", "m-pending", "m-failed"), content.data.map { it.id })
        assertEquals(Delivery.SENT, content.data[0].delivery)
        assertEquals(Delivery.PENDING, content.data[1].delivery)
        assertEquals(Delivery.FAILED, content.data[2].delivery)
    }

    @Test
    fun `gui tin hien ngay PENDING va chan o nhap rong hoac vuot 1000 ky tu`() = runTest {
        chatFlow.value = directChat()
        val vm = createViewModel()
        runCurrent()

        // Rỗng hoặc chỉ khoảng trắng: không gửi.
        vm.onComposerChanged("   \n\t  ")
        assertFalse(vm.uiState.value.canSubmit)
        vm.onSendClicked()
        runCurrent()
        coVerify(exactly = 0) { repository.send(any()) }

        // Quá 1000 ký tự: chặn gửi và hiện bộ đếm.
        vm.onComposerChanged("a".repeat(1001))
        assertTrue(vm.uiState.value.isOverLimit)
        assertTrue(vm.uiState.value.showCounter)
        assertFalse(vm.uiState.value.canSubmit)
        vm.onSendClicked()
        runCurrent()
        coVerify(exactly = 0) { repository.send(any()) }

        // Hợp lệ: gửi và hiện PENDING ngay lập tức.
        vm.onComposerChanged("Tối thứ 7 còn sân không?")
        assertTrue(vm.uiState.value.canSubmit)
        vm.onSendClicked()
        runCurrent()

        val items = (vm.uiState.value.messages as UiState.Content).data
        assertEquals(1, items.size)
        assertEquals("Tối thứ 7 còn sân không?", items.first().text)
        assertEquals(Delivery.PENDING, items.first().delivery)
        assertEquals("", vm.uiState.value.composerText)
    }

    @Test
    fun `tin moi nhat hien thi thi goi markRead`() = runTest {
        chatFlow.value = directChat()
        messagesFlow.value = listOf(serverMessage("m1", "Tin 1"))
        val vm = createViewModel()
        runCurrent()

        vm.onLatestMessageVisible()
        runCurrent()
        coVerify(exactly = 1) { repository.markRead("owner1_player1") }

        // Gọi lại cùng tin cuối không gọi trùng.
        vm.onLatestMessageVisible()
        runCurrent()
        coVerify(exactly = 1) { repository.markRead("owner1_player1") }

        // Có tin mới tới -> gọi lại markRead.
        messagesFlow.value = listOf(serverMessage("m1", "Tin 1"), serverMessage("m2", "Tin 2", 5))
        runCurrent()
        vm.onLatestMessageVisible()
        runCurrent()
        coVerify(exactly = 2) { repository.markRead("owner1_player1") }
    }

    @Test
    fun `gia han viewingUntil moi 45 giay theo gio server uoc luong va dat null khi roi`() = runTest {
        // Giả lập đồng hồ máy chậm hơn server 30 giây (serverOffsetMs = +30_000).
        val serverOffset = 30_000L
        chatFlow.value = directChat()
        val captured = mutableListOf<Instant?>()
        val vm = createViewModel(serverOffsetMs = serverOffset)
        // Cài sau createViewModel vì hàm đó gán lại setViewing mặc định.
        coEvery { repository.setViewing("owner1_player1", any()) } answers {
            captured.add(secondArg())
            Result.success(Unit)
        }
        runCurrent()

        vm.onScreenVisible()
        try {
            runCurrent()
            assertEquals(1, captured.size)
            assertEquals(
                Instant.ofEpochMilli(clock.currentMs + serverOffset + ChatViewModel.VIEWING_TTL_MS),
                captured[0],
            )

            // Sau 45 giây tự gia hạn lần 2.
            clock.currentMs += ChatViewModel.VIEWING_RENEW_INTERVAL_MS
            advanceTimeBy(ChatViewModel.VIEWING_RENEW_INTERVAL_MS)
            runCurrent()
            assertEquals(2, captured.size)
            assertEquals(
                Instant.ofEpochMilli(clock.currentMs + serverOffset + ChatViewModel.VIEWING_TTL_MS),
                captured[1],
            )
        } finally {
            // Rời màn hình: đặt viewingUntil = null (luôn hủy heartbeat kể cả khi assert trên fail).
            vm.onScreenHidden()
        }
        runCurrent()
        assertEquals(3, captured.size)
        assertNull(captured[2])
    }

    @Test
    fun `dau man hinh chat rieng co ten va anh co so va READ_ONLY an o nhap`() = runTest {
        chatFlow.value = directChat(
            status = ChatStatus.READ_ONLY,
            venue = VenueSummary("v1", "Sân cầu lông Hòa Bình", "venues/v1.jpg"),
        )
        val vm = createViewModel()
        runCurrent()

        val header = vm.uiState.value.header!!
        assertEquals("Sân cầu lông Hòa Bình", header.venue?.name)
        assertEquals("venues/v1.jpg", header.venue?.photoPath)
        assertFalse(vm.uiState.value.canSend)
        assertEquals(ComposerDisabledReason.READ_ONLY, vm.uiState.value.disabledReason)
    }

    @Test
    fun `cuon len tai them 30 tin cu`() = runTest {
        val initial30 = (1..30).map { i -> serverMessage("m$i", "Tin $i", secondsOffset = i.toLong() + 100) }
        val older30 = (1..30).map { i -> serverMessage("old$i", "Cũ $i", secondsOffset = i.toLong()) }
        chatFlow.value = directChat()
        messagesFlow.value = initial30
        coEvery { repository.loadOlder("owner1_player1", initial30.first().createdAt!!) } returns Result.success(older30)

        val vm = createViewModel()
        runCurrent()
        assertEquals(30, (vm.uiState.value.messages as UiState.Content).data.size)

        vm.loadOlder()
        advanceUntilIdle()

        val merged = (vm.uiState.value.messages as UiState.Content).data
        assertEquals(60, merged.size)
        assertEquals("old1", merged.first().id)
        assertEquals("m30", merged.last().id)
        assertTrue(vm.uiState.value.canLoadOlder)
    }

    @Test
    fun `deep link venueId goi openDirect va moi dang nhap khi khach chua dang nhap`() = runTest {
        // Trường hợp khách chưa đăng nhập
        val guestVm = createViewModel(args = mapOf(ChatViewModel.ARG_VENUE_ID to "v1"), uid = null)
        guestVm.events.test {
            val event = awaitItem()
            assertTrue(event is ChatEvent.RequireLogin)
            assertEquals("smashnow://chat/direct?venueId=v1", (event as ChatEvent.RequireLogin).returnTo)
        }

        // Trường hợp người chơi đã đăng nhập
        coEvery { repository.openDirect("v1") } returns Result.success(DirectChatResult("owner1_player1", created = true))
        chatFlow.value = directChat()
        val playerVm = createViewModel(args = mapOf(ChatViewModel.ARG_VENUE_ID to "v1"), uid = "player1")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.openDirect("v1") }
        assertEquals("owner1_player1", playerVm.uiState.value.header?.chatId)
    }

    // ---------- T032: US2 Chat nhóm ----------

    @Test
    fun `chat nhom mo tu deep link groupId hien ten anh nhom va nguoi gui theo senderSnapshot`() = runTest {
        chatFlow.value = groupChat(groupId = "g1")
        groupHeaderFlow.value = GroupHeaderInfo(
            groupId = "g1",
            name = "CLB Cầu lông Quận 1",
            avatarPath = "g1/avatar.jpg",
            memberCount = 12,
            isLocked = false,
        )
        messagesFlow.value = listOf(
            ChatMessage(
                id = "m-other",
                senderId = "member2",
                senderName = "Trần Quốc",
                senderAvatarPath = "member2/avatar.jpg",
                text = "Tối nay 19h nhé mọi người",
                createdAt = Instant.ofEpochMilli(1_700_000_001_000L),
                delivery = Delivery.SENT,
                isMine = false,
            ),
            ChatMessage(
                id = "m-mine",
                senderId = "player1",
                senderName = "Mình",
                text = "Ok mình tới đúng giờ",
                createdAt = Instant.ofEpochMilli(1_700_000_002_000L),
                delivery = Delivery.SENT,
                isMine = true,
            ),
        )

        val vm = createViewModel(args = mapOf(ChatViewModel.ARG_GROUP_ID to "g1"))
        runCurrent()

        verify(exactly = 1) { repository.observeChat("group_g1") }
        verify(exactly = 1) { repository.observeGroupHeader("g1") }

        val header = vm.uiState.value.header!!
        assertEquals(ChatType.GROUP, header.type)
        assertEquals("CLB Cầu lông Quận 1", header.title)
        assertEquals("g1/avatar.jpg", header.avatarPath)
        assertEquals("group-avatars", header.avatarBucket)
        assertEquals(12, header.groupMemberCount)
        assertTrue(vm.uiState.value.canSend)

        val items = (vm.uiState.value.messages as UiState.Content).data
        assertEquals(2, items.size)
        assertFalse(items[0].isMine)
        assertEquals("Trần Quốc", items[0].senderName)
        assertEquals("member2/avatar.jpg", items[0].senderAvatarPath)
        assertTrue(items[1].isMine)
    }

    @Test
    fun `tin co senderDeleted bo ten anh cu de hien Nguoi dung da xoa`() = runTest {
        val deletedDomain = MessageDto(
            id = "m-del",
            senderId = "gone-user",
            senderSnapshot = ChatUserSnapshotDto(displayName = "Tên cũ", avatarPath = "gone/old.jpg"),
            senderDeleted = true,
            text = "Tin nhắn cũ",
            createdAt = Instant.ofEpochMilli(1_700_000_001_000L),
            status = "NORMAL",
        ).toDomain(currentUid = "player1")

        assertTrue(deletedDomain.senderDeleted)
        assertNull(deletedDomain.senderName)
        assertNull(deletedDomain.senderAvatarPath)

        chatFlow.value = groupChat(groupId = "g1")
        groupHeaderFlow.value = GroupHeaderInfo("g1", "CLB Quận 1")
        messagesFlow.value = listOf(deletedDomain)

        val vm = createViewModel(args = mapOf(ChatViewModel.ARG_GROUP_ID to "g1"))
        runCurrent()

        val msg = (vm.uiState.value.messages as UiState.Content).data.single()
        assertTrue(msg.senderDeleted)
        assertNull(msg.senderName)
        assertNull(msg.senderAvatarPath)
    }

    @Test
    fun `nhom LOCKED an o nhap va hien Nhom da bi tam khoa`() = runTest {
        chatFlow.value = groupChat(groupId = "g1")
        groupHeaderFlow.value = GroupHeaderInfo(
            groupId = "g1",
            name = "CLB Bị Khóa",
            memberCount = 5,
            isLocked = true,
        )

        val vm = createViewModel(args = mapOf(ChatViewModel.ARG_GROUP_ID to "g1"))
        runCurrent()

        assertFalse(vm.uiState.value.canSend)
        assertEquals(ComposerDisabledReason.GROUP_LOCKED, vm.uiState.value.disabledReason)
        assertEquals(R.string.chat_error_group_locked, ChatUiText.errorRes(ChatError.GroupLocked()))
    }

    @Test
    fun `mat quyen PERMISSION_DENIED sau khi bi moi ra dong man hinh va bao loi`() = runTest {
        val kickedChatFlow = flow<ChatDetail?> {
            throw ChatError.NotParticipant()
        }
        every { sessionManager.currentUid() } returns "player1"
        every { repository.observeChat("group_g1") } returns kickedChatFlow
        every { repository.observeGroupHeader(any()) } returns groupHeaderFlow
        every { repository.observeMessages("group_g1") } returns messagesFlow
        every { repository.observeOutbox("group_g1") } returns outboxFlow
        every { repository.estimatedServerOffsetMs() } returns 0L
        coEvery { repository.setViewing(any(), any()) } returns Result.success(Unit)

        val vm = ChatViewModel(
            savedStateHandle = SavedStateHandle(mapOf(ChatViewModel.ARG_GROUP_ID to "g1")),
            repository = repository,
            openDirectChat = OpenDirectChat(repository),
            observeMessages = ObserveMessages(repository),
            loadOlderMessages = LoadOlderMessages(repository),
            sendMessage = SendMessage(repository, imageCompressor, localMediaStore, clock),
            retryMessage = RetryMessage(repository),
            discardMessage = DiscardMessage(repository),
            markChatRead = MarkChatRead(repository),
            setMuted = SetMuted(repository),
            setBlocked = SetBlocked(repository),
            recallMessage = RecallMessage(repository),
            signedUrlCache = signedUrlCache,
            networkMonitor = networkMonitor,
            sessionManager = sessionManager,
            clock = clock,
        )

        vm.events.test {
            runCurrent()
            assertEquals(ChatEvent.KickedOut, awaitItem())
        }
        assertEquals(R.string.chat_error_not_participant, ChatUiText.errorRes(ChatError.NotParticipant()))
    }

    // ---------- T040: US3 Tắt/bật thông báo ----------

    @Test
    fun `bat tat thong bao ghi muted va phat thong bao`() = runTest {
        chatFlow.value = directChat()
        val vm = createViewModel()
        coEvery { repository.setMuted("owner1_player1", any()) } returns Result.success(Unit)
        runCurrent()

        vm.events.test {
            vm.onToggleMuted()
            runCurrent()
            assertEquals(ChatEvent.ShowMessage(R.string.chat_muted_success), awaitItem())
            coVerify(exactly = 1) { repository.setMuted("owner1_player1", true) }

            // Sau khi participant cập nhật muted = true -> bấm lần nữa để bật lại.
            chatFlow.value = directChat().copy(myParticipant = Participant(muted = true))
            runCurrent()
            assertTrue(vm.uiState.value.header!!.muted)

            vm.onToggleMuted()
            runCurrent()
            assertEquals(ChatEvent.ShowMessage(R.string.chat_unmuted_success), awaitItem())
            coVerify(exactly = 1) { repository.setMuted("owner1_player1", false) }
        }
    }

    // ---------- T042: US4 Gửi ảnh ----------

    @Test
    fun `tin anh hien ngay PENDING voi anh cuc bo va bam anh mo PhotoViewer`() = runTest {
        chatFlow.value = directChat()
        val vm = createViewModel()
        runCurrent()

        vm.onImageSelected("content://media/photo1.jpg")
        assertTrue(vm.uiState.value.canSubmit)
        vm.onSendClicked()
        runCurrent()

        val items = (vm.uiState.value.messages as UiState.Content).data
        assertEquals(1, items.size)
        assertEquals(Delivery.PENDING, items.single().delivery)
        assertTrue(items.single().localImageUri!!.startsWith("file:///cache/chat/outbox_"))

        // Khi tin ảnh đã lên server -> resolve signed URL và bấm vào mở PhotoViewer.
        outboxFlow.value = emptyList()
        messagesFlow.value = listOf(
            ChatMessage(
                id = "m-img-1",
                senderId = "player1",
                imagePath = "owner1_player1/m-img-1.jpg",
                createdAt = Instant.ofEpochMilli(1_700_000_010_000L),
                delivery = Delivery.SENT,
                isMine = true,
            ),
        )
        advanceUntilIdle()

        vm.events.test {
            vm.onPhotoClicked((vm.uiState.value.messages as UiState.Content).data.single())
            runCurrent()
            assertEquals(
                ChatEvent.OpenPhoto("https://signed/owner1_player1/m-img-1.jpg"),
                awaitItem(),
            )
        }
    }

    @Test
    fun `anh qua 2MB hoac khong phai anh bao loi va khong vao outbox`() = runTest {
        chatFlow.value = directChat()
        val vm = createViewModel()
        runCurrent()

        // Quá 2 MB sau nén (AC 4.3)
        coEvery { imageCompressor.compress("content://img/huge.jpg") } throws ImageTooLargeException()
        vm.onComposerChanged("Ảnh hóa đơn")
        vm.onImageSelected("content://img/huge.jpg")
        vm.events.test {
            vm.onSendClicked()
            runCurrent()
            val event = awaitItem() as ChatEvent.ShowError
            assertTrue(event.error is ChatError.ImageTooLarge)
            assertEquals(R.string.chat_error_image_too_large, ChatUiText.errorRes(event.error))
        }
        assertTrue(outboxFlow.value.isEmpty())
        assertEquals("Ảnh hóa đơn", vm.uiState.value.composerText)
        assertNull(vm.uiState.value.pendingImageUri)

        // Không phải ảnh hợp lệ (AC 4.3)
        coEvery { imageCompressor.compress("content://file/doc.pdf") } throws ImageUnreadableException()
        vm.onImageSelected("content://file/doc.pdf")
        vm.events.test {
            vm.onSendClicked()
            runCurrent()
            val event = awaitItem() as ChatEvent.ShowError
            assertTrue(event.error is ChatError.ImageUnreadable)
            assertEquals(R.string.chat_error_image_unreadable, ChatUiText.errorRes(event.error))
        }
        assertTrue(outboxFlow.value.isEmpty())
    }

    @Test
    fun `tu choi quyen camera phat su kien giai thich va van chon anh thu vien duoc`() = runTest {
        chatFlow.value = directChat()
        val vm = createViewModel()
        runCurrent()

        vm.events.test {
            vm.onCameraPermissionDenied()
            assertEquals(ChatEvent.CameraPermissionDenied, awaitItem())
        }

        // Vẫn chọn ảnh từ Photo Picker và gửi bình thường (AC 4.5).
        vm.onImageSelected("content://picker/gallery1.jpg")
        vm.onSendClicked()
        runCurrent()
        assertEquals(1, outboxFlow.value.size)
        assertTrue(outboxFlow.value.single().localImageUri!!.startsWith("file:///cache/chat/outbox_"))
    }

    // ---------- T048: US5 Thu hồi, báo cáo, chặn, tin bị ẩn ----------

    @Test
    fun `menu giu tin dung quyen thu hoi bao cao va an voi tin thu hoi hoac bi an`() = runTest {
        chatFlow.value = directChat()
        val vm = createViewModel()
        runCurrent()

        val now = Instant.ofEpochMilli(clock.currentMs)
        val myRecent = ChatMessage(
            id = "m-my-recent",
            senderId = "player1",
            text = "Tin vừa gửi",
            createdAt = now.minusSeconds(300), // 5 phút trước
            status = MessageStatus.NORMAL,
            delivery = Delivery.SENT,
            isMine = true,
        )
        val myExpired = myRecent.copy(id = "m-my-old", createdAt = now.minusSeconds(601))
        val otherNormal = ChatMessage(
            id = "m-other",
            senderId = "owner1",
            text = "Tin chủ sân",
            createdAt = now.minusSeconds(60),
            status = MessageStatus.NORMAL,
            delivery = Delivery.SENT,
            isMine = false,
        )
        val recalled = myRecent.copy(id = "m-recalled", text = "", status = MessageStatus.RECALLED)
        val hidden = otherNormal.copy(id = "m-hidden", text = "", status = MessageStatus.HIDDEN)

        assertEquals(listOf(MessageMenuAction.COPY, MessageMenuAction.RECALL), vm.availableActionsFor(myRecent))
        assertEquals(listOf(MessageMenuAction.COPY), vm.availableActionsFor(myExpired))
        assertEquals(listOf(MessageMenuAction.COPY, MessageMenuAction.REPORT), vm.availableActionsFor(otherNormal))
        assertTrue(vm.availableActionsFor(recalled).isEmpty())
        assertTrue(vm.availableActionsFor(hidden).isEmpty())
    }

    @Test
    fun `thu hoi tin va chan chi ap dung cho chat rieng`() = runTest {
        chatFlow.value = directChat()
        val vm = createViewModel()
        coEvery { repository.recall("owner1_player1", "m1") } returns Result.success(Unit)
        coEvery { repository.setBlocked("owner1_player1", true) } returns Result.success(Unit)
        runCurrent()

        vm.events.test {
            vm.onRecallConfirmed("m1")
            runCurrent()
            assertEquals(ChatEvent.ShowMessage(R.string.chat_recalled_success), awaitItem())

            vm.onToggleBlockedConfirmed()
            runCurrent()
            assertEquals(ChatEvent.ShowMessage(R.string.chat_blocked_success), awaitItem())
        }
        coVerify(exactly = 1) { repository.recall("owner1_player1", "m1") }
        coVerify(exactly = 1) { repository.setBlocked("owner1_player1", true) }

        // Chat nhóm không cho chặn (AC 5.5).
        chatFlow.value = groupChat("g1")
        groupHeaderFlow.value = GroupHeaderInfo("g1", "CLB")
        val groupVm = createViewModel(args = mapOf(ChatViewModel.ARG_GROUP_ID to "g1"))
        runCurrent()
        groupVm.onToggleBlockedConfirmed()
        runCurrent()
        coVerify(exactly = 0) { repository.setBlocked("group_g1", any()) }
    }

    @Test
    fun `bi chan khi gui an o nhap va bao khong the gui tin`() = runTest {
        chatFlow.value = directChat()
        val vm = createViewModel()
        runCurrent()
        assertTrue(vm.uiState.value.canSend)

        outboxFlow.value = listOf(
            OutboxEntry(
                messageId = "m-blk",
                chatId = "owner1_player1",
                text = "Alo",
                createdAtLocal = clock.currentMs,
                status = Delivery.FAILED,
                retryable = false,
                errorCode = ChatError.Blocked().code,
            ),
        )
        runCurrent()

        assertFalse(vm.uiState.value.canSend)
        assertEquals(ComposerDisabledReason.BLOCKED, vm.uiState.value.disabledReason)
        assertEquals(R.string.chat_error_blocked, ChatUiText.errorRes(ChatError.Blocked()))
    }

    @Test
    fun `tin HIDDEN va RECALLED khong xin signed URL anh va khong mo PhotoViewer`() = runTest {
        requestedSignedPaths.clear()
        val hiddenFromDto = MessageDto(
            id = "m-hidden-img",
            senderId = "owner1",
            text = "Nội dung cũ chưa xóa trên server",
            imagePath = "owner1_player1/m-hidden-img.jpg",
            createdAt = Instant.ofEpochMilli(1_700_000_005_000L),
            status = "HIDDEN",
        ).toDomain(currentUid = "player1")
        assertEquals(MessageStatus.HIDDEN, hiddenFromDto.status)
        assertEquals("", hiddenFromDto.text)
        assertNull(hiddenFromDto.imagePath)

        // Kể cả nếu model vẫn còn imagePath trên tin HIDDEN/RECALLED thì ViewModel cũng KHÔNG xin signed URL (FR-015).
        chatFlow.value = directChat()
        messagesFlow.value = listOf(
            ChatMessage(
                id = "m-hidden-raw",
                senderId = "owner1",
                imagePath = "owner1_player1/m-hidden-raw.jpg",
                createdAt = Instant.ofEpochMilli(1_700_000_006_000L),
                status = MessageStatus.HIDDEN,
                delivery = Delivery.SENT,
                isMine = false,
            ),
            ChatMessage(
                id = "m-recalled-raw",
                senderId = "player1",
                imagePath = "owner1_player1/m-recalled-raw.jpg",
                createdAt = Instant.ofEpochMilli(1_700_000_007_000L),
                status = MessageStatus.RECALLED,
                delivery = Delivery.SENT,
                isMine = true,
            ),
        )

        val vm = createViewModel()
        advanceUntilIdle()

        assertTrue(requestedSignedPaths.isEmpty())
        assertTrue(vm.uiState.value.imageUrls.isEmpty())
    }

    // ---------- Review Argon US1 #4, #5, #7 & US2 #1, #3 ----------

    @Test
    fun `canLoadOlder khong bi chot cung false khi snapshot cache dau it hon 30 tin roi server tra du 30 tin`() = runTest {
        chatFlow.value = directChat()
        // Lần phát đầu từ cache cục bộ chỉ có 5 tin (< 30).
        messagesFlow.value = (1..5).map { i -> serverMessage("c$i", "Cache $i", secondsOffset = i.toLong()) }
        val vm = createViewModel()
        runCurrent()
        assertFalse(vm.uiState.value.canLoadOlder)

        // Snapshot từ server trả đủ 30 tin -> canLoadOlder phải bật lại true.
        val server30 = (1..30).map { i -> serverMessage("s$i", "Server $i", secondsOffset = i.toLong() + 100) }
        val older10 = (1..10).map { i -> serverMessage("o$i", "Older $i", secondsOffset = i.toLong()) }
        coEvery { repository.loadOlder("owner1_player1", server30.first().createdAt!!) } returns Result.success(older10)
        messagesFlow.value = server30
        runCurrent()
        assertTrue(vm.uiState.value.canLoadOlder)

        // Tải trang cũ trả về 10 tin (< 30) -> chốt reachedOldestPage = true -> canLoadOlder = false.
        vm.loadOlder()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.canLoadOlder)
    }

    @Test
    fun `onLatestMessageVisible cho phep thu lai markRead khi lan truoc that bai`() = runTest {
        chatFlow.value = directChat()
        messagesFlow.value = listOf(serverMessage("m1", "Tin 1"))
        val vm = createViewModel()
        var attempt = 0
        coEvery { repository.markRead("owner1_player1") } answers {
            attempt++
            if (attempt == 1) Result.failure(ChatError.Network()) else Result.success(Unit)
        }
        runCurrent()

        vm.onLatestMessageVisible()
        runCurrent()
        coVerify(exactly = 1) { repository.markRead("owner1_player1") }

        // Lần đầu lỗi nên marker phải được xóa -> gọi lại cho cùng tin m1 vẫn gọi markRead.
        vm.onLatestMessageVisible()
        runCurrent()
        coVerify(exactly = 2) { repository.markRead("owner1_player1") }

        // Sau khi thành công thì gọi lại cùng tin m1 không gọi trùng nữa.
        vm.onLatestMessageVisible()
        runCurrent()
        coVerify(exactly = 2) { repository.markRead("owner1_player1") }
    }

    @Test
    fun `mo chat that bai header null khong cho gui va Unauthenticated co callback retry`() = runTest {
        val guestVm = createViewModel(args = mapOf(ChatViewModel.ARG_CHAT_ID to "owner1_player1"), uid = null)
        runCurrent()
        val unauthErr = guestVm.uiState.value.messages as UiState.Error
        assertTrue(unauthErr.retry != null)
        assertFalse(guestVm.uiState.value.canSend)

        coEvery { repository.openDirect("v-inactive") } returns Result.failure(ChatError.VenueInactive())
        val failVm = createViewModel(args = mapOf(ChatViewModel.ARG_VENUE_ID to "v-inactive"), uid = "player1")
        advanceUntilIdle()
        assertNull(failVm.uiState.value.header)
        assertTrue(failVm.uiState.value.messages is UiState.Error)
        assertFalse(failVm.uiState.value.canSend)
    }

    @Test
    fun `observeGroupHeader khong dang ky lai listener khi ChatDetail phat lai cung groupId`() = runTest {
        chatFlow.value = groupChat(groupId = "g1")
        groupHeaderFlow.value = GroupHeaderInfo("g1", "CLB Cầu lông", memberCount = 10)
        val vm = createViewModel(args = mapOf(ChatViewModel.ARG_GROUP_ID to "g1"))
        runCurrent()

        // Phát lại ChatDetail nhiều lần (bật/tắt muted, đổi lastReadAt) nhưng cùng groupId "g1".
        chatFlow.value = groupChat(groupId = "g1").copy(myParticipant = Participant(muted = true))
        runCurrent()
        chatFlow.value = groupChat(groupId = "g1").copy(myParticipant = Participant(muted = false, lastReadAt = Instant.now()))
        runCurrent()

        verify(exactly = 1) { repository.observeGroupHeader("g1") }
        assertEquals("CLB Cầu lông", vm.uiState.value.header?.title)
    }

    @Test
    fun `outbox bao GROUP_LOCKED hoac CHAT_READ_ONLY khoa o nhap va onScreenHidden khong goi setViewing sau KickedOut`() = runTest {
        chatFlow.value = groupChat(groupId = "g1")
        groupHeaderFlow.value = GroupHeaderInfo("g1", "CLB", isLocked = false)
        val vm = createViewModel(args = mapOf(ChatViewModel.ARG_GROUP_ID to "g1"))
        runCurrent()
        assertTrue(vm.uiState.value.canSend)

        // Lỗi GROUP_LOCKED từ outbox khóa ô nhập ngay cả khi groupHeader chưa đồng bộ.
        outboxFlow.value = listOf(
            OutboxEntry("m-lock", "group_g1", "Alo", createdAtLocal = 100L, status = Delivery.FAILED, retryable = false, errorCode = "GROUP_LOCKED"),
        )
        runCurrent()
        assertEquals(ComposerDisabledReason.GROUP_LOCKED, vm.uiState.value.disabledReason)

        // Lỗi CHAT_READ_ONLY từ outbox chuyển sang READ_ONLY.
        outboxFlow.value = listOf(
            OutboxEntry("m-ro", "group_g1", "Alo", createdAtLocal = 101L, status = Delivery.FAILED, retryable = false, errorCode = "CHAT_READ_ONLY"),
        )
        runCurrent()
        assertEquals(ComposerDisabledReason.READ_ONLY, vm.uiState.value.disabledReason)

        // Lỗi NOT_PARTICIPANT phát KickedOut -> onScreenHidden() KHÔNG được gọi setViewing(chatId, null).
        vm.events.test {
            outboxFlow.value = listOf(
                OutboxEntry("m-kick", "group_g1", "Alo", createdAtLocal = 102L, status = Delivery.FAILED, retryable = false, errorCode = "NOT_PARTICIPANT"),
            )
            runCurrent()
            assertEquals(ChatEvent.KickedOut, awaitItem())
        }
        vm.onScreenHidden()
        runCurrent()
        coVerify(exactly = 0) { repository.setViewing("group_g1", null) }
    }

    // ---------- Review Argon US4 #3: Làm mới signed URL khi hết hạn & khi có mạng lại ----------

    @Test
    fun `signed URL het han duoc lam moi khi bam anh va tu xin lai khi co mang lai`() = runTest {
        val imgMsg = ChatMessage(
            id = "m-img-exp",
            senderId = "owner1",
            imagePath = "owner1_player1/m-img-exp.jpg",
            createdAt = Instant.ofEpochMilli(1_700_000_010_000L),
            delivery = Delivery.SENT,
            isMine = false,
        )
        // 1. Mở chat lúc đang mất mạng → xin signed URL lần đầu thất bại.
        onlineFlow.value = false
        failSignedUrls = true
        chatFlow.value = directChat()
        messagesFlow.value = listOf(imgMsg)

        val vm = createViewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.imageUrls.isEmpty())

        // 2. Có mạng lại → tự động xin lại signed URL không cần snapshot Firestore mới.
        failSignedUrls = false
        onlineFlow.value = true
        advanceUntilIdle()
        val firstUrl = vm.uiState.value.imageUrls["owner1_player1/m-img-exp.jpg"]
        assertEquals("https://signed/owner1_player1/m-img-exp.jpg", firstUrl)

        // 3. Sau 1 giờ URL hết hạn → bấm vào ảnh xin URL mới còn hạn trước khi mở PhotoViewer.
        clock.currentMs += SignedUrlCache.DEFAULT_TTL_MS
        vm.events.test {
            vm.onPhotoClicked(imgMsg)
            runCurrent()
            assertEquals(
                ChatEvent.OpenPhoto("https://signed/owner1_player1/m-img-exp.jpg?v=2"),
                awaitItem(),
            )
        }
        assertEquals(
            "https://signed/owner1_player1/m-img-exp.jpg?v=2",
            vm.uiState.value.imageUrls["owner1_player1/m-img-exp.jpg"],
        )
    }

    // ---------- Review Argon US5 #2, #3, #5 ----------

    @Test
    fun `xoa hoac gui lai tin loi BLOCKED va tin moi tu doi phuong mo khoa lai o nhap`() = runTest {
        chatFlow.value = directChat()
        messagesFlow.value = listOf(serverMessage("m0", "Chào", secondsOffset = 1, isMine = false))
        val vm = createViewModel()
        coEvery { repository.discard("owner1_player1", any()) } coAnswers {
            val id = secondArg<String>()
            outboxFlow.value = outboxFlow.value.filterNot { it.messageId == id }
            Result.success(com.example.booking_app.feature.chat.domain.model.DiscardResult.DISCARDED)
        }
        coEvery { repository.retry("owner1_player1", any()) } coAnswers {
            val id = secondArg<String>()
            outboxFlow.value = outboxFlow.value.map {
                if (it.messageId == id) it.copy(status = Delivery.PENDING, errorCode = null) else it
            }
            Result.success(Unit)
        }
        runCurrent()

        // 1. Một tin lỗi BLOCKED -> khóa ô nhập; bấm Xóa tin lỗi vẫn giữ banner bị chặn (M9: xóa tin không phải bỏ chặn).
        outboxFlow.value = listOf(
            OutboxEntry("m-blk-1", "owner1_player1", "Alo", createdAtLocal = 100L, status = Delivery.FAILED, retryable = false, errorCode = "BLOCKED"),
        )
        runCurrent()
        assertEquals(ComposerDisabledReason.BLOCKED, vm.uiState.value.disabledReason)

        vm.onDiscardMessage("m-blk-1")
        runCurrent()
        assertTrue(outboxFlow.value.isEmpty())
        assertEquals(ComposerDisabledReason.BLOCKED, vm.uiState.value.disabledReason)

        // 2. Có 2 tin lỗi BLOCKED -> bấm Gửi lại tin 1 không bị tin 2 khóa ngược lại ngay.
        outboxFlow.value = listOf(
            OutboxEntry("m-blk-a", "owner1_player1", "Tin A", createdAtLocal = 200L, status = Delivery.FAILED, retryable = false, errorCode = "BLOCKED"),
            OutboxEntry("m-blk-b", "owner1_player1", "Tin B", createdAtLocal = 201L, status = Delivery.FAILED, retryable = false, errorCode = "BLOCKED"),
        )
        runCurrent()
        assertEquals(ComposerDisabledReason.BLOCKED, vm.uiState.value.disabledReason)

        vm.onRetryMessage("m-blk-a")
        runCurrent()
        assertNull(vm.uiState.value.disabledReason)

        // Nếu m-blk-a lại lỗi BLOCKED lần mới -> khóa lại.
        outboxFlow.value = outboxFlow.value.map {
            if (it.messageId == "m-blk-a") it.copy(status = Delivery.FAILED, errorCode = "BLOCKED") else it
        }
        runCurrent()
        assertEquals(ComposerDisabledReason.BLOCKED, vm.uiState.value.disabledReason)

        // 3. Đối phương nhắn tin mới sang (đã bỏ chặn) -> tự mở khóa lại ô nhập.
        messagesFlow.value = messagesFlow.value + serverMessage("m1", "Anh mở chặn rồi nhé", secondsOffset = 10, isMine = false)
        runCurrent()
        assertNull(vm.uiState.value.disabledReason)
    }

    @Test
    fun `thu hoi tin o olderMessages cap nhat ngay UI va don cache signed URL anh bi thu hoi hoac an`() = runTest {
        val initial30 = (1..30).map { i -> serverMessage("m$i", "Tin $i", secondsOffset = i.toLong() + 100, isMine = true) }
        val olderImg = ChatMessage(
            id = "old-img",
            senderId = "player1",
            text = "Ảnh trang cũ",
            imagePath = "owner1_player1/old-img.jpg",
            createdAt = Instant.ofEpochMilli(1_700_000_000_000L + 50_000L),
            status = MessageStatus.NORMAL,
            delivery = Delivery.SENT,
            isMine = true,
        )
        chatFlow.value = directChat()
        messagesFlow.value = initial30
        coEvery { repository.loadOlder("owner1_player1", initial30.first().createdAt!!) } returns Result.success(listOf(olderImg))
        coEvery { repository.recall("owner1_player1", "old-img") } returns Result.success(Unit)

        val vm = createViewModel()
        runCurrent()

        vm.loadOlder()
        advanceUntilIdle()
        assertEquals("https://signed/owner1_player1/old-img.jpg", vm.uiState.value.imageUrls["owner1_player1/old-img.jpg"])
        assertEquals("https://signed/owner1_player1/old-img.jpg", signedUrlCache.peek("owner1_player1/old-img.jpg"))

        // Thu hồi tin nằm trong olderMessages -> UI cập nhật RECALLED và xóa signed URL khỏi state + SignedUrlCache.
        vm.onRecallConfirmed("old-img")
        advanceUntilIdle()

        val updatedOld = (vm.uiState.value.messages as UiState.Content).data.first { it.id == "old-img" }
        assertEquals(MessageStatus.RECALLED, updatedOld.status)
        assertEquals("", updatedOld.text)
        assertNull(updatedOld.imagePath)
        assertNull(vm.uiState.value.imageUrls["owner1_player1/old-img.jpg"])
        assertNull(signedUrlCache.peek("owner1_player1/old-img.jpg"))
    }

    @Test
    fun `khong cho chan khi doi phuong da xoa tai khoan`() = runTest {
        chatFlow.value = directChat(status = ChatStatus.READ_ONLY).copy(
            otherParty = UserSummary("owner1", null, null, isDeleted = true),
        )
        val vm = createViewModel()
        runCurrent()

        vm.onToggleBlockedConfirmed()
        runCurrent()
        coVerify(exactly = 0) { repository.setBlocked(any(), any()) }
    }

    // ---------- L4: Cold-start deep link khi SessionState đang Loading ----------

    @Test
    fun `cold start deep link cho phien khoi phuc xong roi moi mo chat khong day sang dang nhap`() = runTest {
        val sessionFlow = MutableStateFlow<SessionState>(SessionState.Loading)
        every { sessionManager.sessionState } returns sessionFlow
        every { sessionManager.currentUid() } answers { (sessionFlow.value as? SessionState.SignedIn)?.uid }
        every { repository.observeChat("group_g1") } returns chatFlow
        every { repository.observeGroupHeader("g1") } returns groupHeaderFlow
        every { repository.observeMessages("group_g1") } returns messagesFlow
        every { repository.observeOutbox("group_g1") } returns outboxFlow
        chatFlow.value = groupChat(groupId = "g1")
        groupHeaderFlow.value = GroupHeaderInfo("g1", "CLB Q7", memberCount = 5)
        messagesFlow.value = listOf(serverMessage("m1", "Xin chào", secondsOffset = 1))

        val vm = ChatViewModel(
            savedStateHandle = SavedStateHandle(mapOf(ChatViewModel.ARG_GROUP_ID to "g1")),
            repository = repository,
            openDirectChat = OpenDirectChat(repository),
            observeMessages = ObserveMessages(repository),
            loadOlderMessages = LoadOlderMessages(repository),
            sendMessage = SendMessage(repository, imageCompressor, localMediaStore, clock),
            retryMessage = RetryMessage(repository),
            discardMessage = DiscardMessage(repository),
            markChatRead = MarkChatRead(repository),
            setMuted = SetMuted(repository),
            setBlocked = SetBlocked(repository),
            recallMessage = RecallMessage(repository),
            signedUrlCache = signedUrlCache,
            networkMonitor = networkMonitor,
            sessionManager = sessionManager,
            clock = clock,
        )
        runCurrent()

        // Khi phiên đang Loading: hiện Loading, chưa phát RequireLogin.
        assertEquals(UiState.Loading, vm.uiState.value.messages)
        vm.events.test {
            expectNoEvents()
            sessionFlow.value = SessionState.SignedIn("player2", AppRole.PLAYER, true)
            runCurrent()
            expectNoEvents()
        }
        val content = vm.uiState.value.messages as UiState.Content
        assertEquals(listOf("m1"), content.data.map { it.id })
        assertEquals("CLB Q7", vm.uiState.value.header?.title)
    }

    @Test
    fun `cold start deep link khi phien ket thuc SignedOut moi phat RequireLogin`() = runTest {
        val sessionFlow = MutableStateFlow<SessionState>(SessionState.Loading)
        every { sessionManager.sessionState } returns sessionFlow
        every { sessionManager.currentUid() } answers { (sessionFlow.value as? SessionState.SignedIn)?.uid }

        val vm = ChatViewModel(
            savedStateHandle = SavedStateHandle(mapOf(ChatViewModel.ARG_GROUP_ID to "g1")),
            repository = repository,
            openDirectChat = OpenDirectChat(repository),
            observeMessages = ObserveMessages(repository),
            loadOlderMessages = LoadOlderMessages(repository),
            sendMessage = SendMessage(repository, imageCompressor, localMediaStore, clock),
            retryMessage = RetryMessage(repository),
            discardMessage = DiscardMessage(repository),
            markChatRead = MarkChatRead(repository),
            setMuted = SetMuted(repository),
            setBlocked = SetBlocked(repository),
            recallMessage = RecallMessage(repository),
            signedUrlCache = signedUrlCache,
            networkMonitor = networkMonitor,
            sessionManager = sessionManager,
            clock = clock,
        )
        runCurrent()
        assertEquals(UiState.Loading, vm.uiState.value.messages)

        vm.events.test {
            sessionFlow.value = SessionState.SignedOut(null)
            runCurrent()
            assertEquals(ChatEvent.RequireLogin("smashnow://chat/group/g1"), awaitItem())
        }
        assertTrue(vm.uiState.value.messages is UiState.Error)
    }

    @Test
    fun `M2 Initializing chua chot phien khong phat RequireLogin, SignedOut moi phat`() = runTest {
        val sessionFlow = MutableStateFlow<SessionState>(SessionState.Initializing)
        every { sessionManager.sessionState } returns sessionFlow
        every { sessionManager.currentUid() } answers { (sessionFlow.value as? SessionState.SignedIn)?.uid }

        val vm = ChatViewModel(
            savedStateHandle = SavedStateHandle(mapOf(ChatViewModel.ARG_GROUP_ID to "g1")),
            repository = repository,
            openDirectChat = OpenDirectChat(repository),
            observeMessages = ObserveMessages(repository),
            loadOlderMessages = LoadOlderMessages(repository),
            sendMessage = SendMessage(repository, imageCompressor, localMediaStore, clock),
            retryMessage = RetryMessage(repository),
            discardMessage = DiscardMessage(repository),
            markChatRead = MarkChatRead(repository),
            setMuted = SetMuted(repository),
            setBlocked = SetBlocked(repository),
            recallMessage = RecallMessage(repository),
            signedUrlCache = signedUrlCache,
            networkMonitor = networkMonitor,
            sessionManager = sessionManager,
            clock = clock,
        )
        runCurrent()
        assertEquals(UiState.Loading, vm.uiState.value.messages)

        vm.events.test {
            sessionFlow.value = SessionState.SignedOut(null)
            runCurrent()
            assertEquals(ChatEvent.RequireLogin("smashnow://chat/group/g1"), awaitItem())
        }
        assertTrue(vm.uiState.value.messages is UiState.Error)
    }
}

package com.example.booking_app.feature.chat.ui.conversation

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.R
import com.example.booking_app.core.image.ImageCompressor
import com.example.booking_app.core.image.ImageTooLargeException
import com.example.booking_app.core.image.ImageUnreadableException
import com.example.booking_app.core.media.SignedUrlBatch
import com.example.booking_app.core.media.SignedUrlCache
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.chat.data.NetworkMonitor
import com.example.booking_app.feature.chat.data.dto.ChatUserSnapshotDto
import com.example.booking_app.feature.chat.data.dto.MessageDto
import com.example.booking_app.feature.chat.data.dto.toDomain
import com.example.booking_app.feature.chat.domain.ChatError
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

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeClock(var currentMs: Long = 1_700_000_000_000L) : AppClock {
        override fun now(): Long = currentMs
        override fun elapsedRealtime(): Long = currentMs
    }

    private val repository = mockk<ChatRepository>(relaxed = true)
    private val imageCompressor = mockk<ImageCompressor>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
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
    private val signedUrlCache = SignedUrlCache(
        fetch = { paths ->
            requestedSignedPaths.addAll(paths)
            Result.success(SignedUrlBatch(paths.associateWith { "https://signed/$it" }, null))
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
            sendMessage = SendMessage(repository, imageCompressor, clock),
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

        // Rời màn hình: đặt viewingUntil = null.
        vm.onScreenHidden()
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
            sendMessage = SendMessage(repository, imageCompressor, clock),
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
        assertEquals("content://media/photo1.jpg", items.single().localImageUri)

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
        assertEquals("content://picker/gallery1.jpg", outboxFlow.value.single().localImageUri)
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
}

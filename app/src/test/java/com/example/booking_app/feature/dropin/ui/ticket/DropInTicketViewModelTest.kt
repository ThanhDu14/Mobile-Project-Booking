package com.example.booking_app.feature.dropin.ui.ticket

import androidx.lifecycle.SavedStateHandle
import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.dropin.domain.model.DropInError
import com.example.booking_app.feature.dropin.domain.model.DropInRegistration
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.example.booking_app.feature.dropin.domain.model.PaymentMethod
import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import com.example.booking_app.feature.dropin.domain.model.SessionStatus
import com.example.booking_app.feature.dropin.domain.usecase.AddDropInParty
import com.example.booking_app.feature.dropin.domain.usecase.CachedDropInTicket
import com.example.booking_app.feature.dropin.domain.usecase.GetVenueLocation
import com.example.booking_app.feature.dropin.domain.usecase.ObserveConnectivity
import com.example.booking_app.feature.dropin.domain.usecase.ObserveDropInSession
import com.example.booking_app.feature.dropin.domain.usecase.ObserveMyDropInRegistration
import com.example.booking_app.feature.dropin.domain.usecase.ReadCachedDropInTicket
import com.example.booking_app.feature.dropin.domain.usecase.VenueLocation
import com.example.booking_app.feature.dropin.ui.NOW
import com.example.booking_app.feature.dropin.ui.registration
import com.example.booking_app.feature.dropin.ui.session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** T040 (AC 2.3, 2.4, 3.5), T044 (FR-015, AC 3.2, 4.4) và thêm người vào lượt (FR-008a). */
@OptIn(ExperimentalCoroutinesApi::class)
class DropInTicketViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val mineFlow = MutableStateFlow<DropInRegistration?>(registration())
    private val sessionFlow = MutableStateFlow<DropInSession?>(session())
    private val online = MutableStateFlow(true)
    private var cached: CachedDropInTicket? = null
    private var venueLocation: VenueLocation? = VenueLocation(10.7731, 106.6672)

    /** Mỗi lần gọi AddDropInParty: (sessionId, additional, requestId). */
    private val addCalls = mutableListOf<Triple<String, Int, String>>()
    private val addResults = ArrayDeque<Result<DropInRegistration>>()
    private val addParty = AddDropInParty { sessionId, additional, requestId ->
        addCalls += Triple(sessionId, additional, requestId)
        addResults.removeFirstOrNull() ?: Result.success(registration(partySize = 2 + additional))
    }

    private val events = mutableListOf<DropInTicketEvent>()

    private fun TestScope.viewModel(
        observeMine: ObserveMyDropInRegistration = ObserveMyDropInRegistration { mineFlow },
        observeSession: ObserveDropInSession = ObserveDropInSession { sessionFlow },
        justRegistered: Boolean? = null,
    ): DropInTicketViewModel {
        val args = buildMap<String, Any?> {
            put(DropInTicketViewModel.ARG_SESSION_ID, "s1")
            if (justRegistered != null) put(DropInTicketViewModel.ARG_JUST_REGISTERED, justRegistered)
        }
        val vm = DropInTicketViewModel(
            SavedStateHandle(args),
            observeMine,
            observeSession,
            FakeClock { NOW.toEpochMilli() },
            ReadCachedDropInTicket { cached },
            ObserveConnectivity { online },
            addParty,
            GetVenueLocation { venueLocation },
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        return vm
    }

    private fun DropInTicketViewModel.content() = uiState.value as DropInTicketUiState.Content

    private fun DropInTicketViewModel.form() = checkNotNull(addPartyForm.value) { "Form thêm người đang đóng" }

    // ---------- T040 ----------

    @Test
    fun `dang ky con hieu luc thi co QR va huy duoc`() = runTest {
        val vm = viewModel(justRegistered = true)
        advanceUntilIdle()

        assertEquals("qr-token-123", vm.content().qrToken)
        assertTrue(vm.content().labels.isEmpty())
        assertTrue(vm.content().canCancel)
        assertTrue(vm.justRegistered)
    }

    @Test
    fun `buoi bi huy thi khong co QR va co nhan`() = runTest {
        sessionFlow.value = session(status = SessionStatus.CANCELLED)
        val vm = viewModel()
        advanceUntilIdle()

        assertNull(vm.content().qrToken)
        assertEquals(NoQrReason.SESSION_CANCELLED, vm.content().noQrReason)
        assertEquals(listOf(TicketLabel.SESSION_CANCELLED), vm.content().labels)
        assertFalse(vm.content().canCancel)
        assertFalse(vm.justRegistered)
    }

    @Test
    fun `buoi doi gio thi co nhan Buoi da thay doi`() = runTest {
        mineFlow.value = registration(sessionChanged = true)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(TicketLabel.SESSION_CHANGED), vm.content().labels)
    }

    @Test
    fun `da check-in thi van co QR nhung khong huy duoc`() = runTest {
        mineFlow.value = registration(status = RegistrationStatus.CHECKED_IN)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("qr-token-123", vm.content().qrToken)
        assertFalse(vm.content().canCancel)
    }

    @Test
    fun `chua co dang ky thi NotFound`() = runTest {
        mineFlow.value = null
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DropInTicketUiState.NotFound, vm.uiState.value)
    }

    @Test
    fun `doc buoi loi van hien ve`() = runTest {
        val vm = viewModel(observeSession = ObserveDropInSession { flow<DropInSession?> { error("offline") } })
        advanceUntilIdle()

        assertNull(vm.content().session)
        assertEquals("qr-token-123", vm.content().qrToken)
    }

    // ---------- T044: cache, offline, nhãn, hủy ----------

    @Test
    fun `doc cache truoc - hien ve ngay khi realtime chua phat`() = runTest {
        cached = CachedDropInTicket(registration(), session())
        val vm = viewModel(
            observeMine = ObserveMyDropInRegistration { MutableSharedFlow() },
            observeSession = ObserveDropInSession { MutableSharedFlow() },
        )
        advanceUntilIdle()

        assertEquals("qr-token-123", vm.content().qrToken)
        assertEquals("DI-7K2P", vm.content().registration.ticketCode)
    }

    @Test
    fun `mat mang co cache - Offline(cached) van con QR`() = runTest {
        online.value = false
        cached = CachedDropInTicket(registration(), session())
        val vm = viewModel(
            observeMine = ObserveMyDropInRegistration { MutableSharedFlow() },
            observeSession = ObserveDropInSession { MutableSharedFlow() },
        )
        advanceUntilIdle()

        val state = vm.uiState.value as DropInTicketUiState.Offline
        assertEquals("qr-token-123", state.cached.qrToken)
    }

    @Test
    fun `dang xem thi mat mang - chuyen Offline, co mang lai - Content`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is DropInTicketUiState.Content)

        online.value = false
        advanceUntilIdle()
        assertEquals("qr-token-123", (vm.uiState.value as DropInTicketUiState.Offline).cached.qrToken)

        online.value = true
        advanceUntilIdle()
        assertTrue(vm.uiState.value is DropInTicketUiState.Content)
    }

    @Test
    fun `realtime loi nhung co cache - Offline(cached)`() = runTest {
        cached = CachedDropInTicket(registration(), session())
        val vm = viewModel(
            observeMine = ObserveMyDropInRegistration { flow<DropInRegistration?> { throw DropInError.Network() } },
        )
        advanceUntilIdle()

        assertTrue(vm.uiState.value is DropInTicketUiState.Offline)
    }

    @Test
    fun `mat mang, khong co cache va khong doc duoc dang ky - Error Network, khong phai NotFound`() = runTest {
        online.value = false
        mineFlow.value = null
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value as DropInTicketUiState.Error
        assertTrue(state.cause is DropInError.Network)
    }

    @Test
    fun `co so ngung hoat dong - nhan Co so tam ngung hoat dong`() = runTest {
        sessionFlow.value = session(venueActive = false)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(TicketLabel.VENUE_INACTIVE), vm.content().labels)
        assertNull(vm.content().addParty)
    }

    @Test
    fun `buoi da bat dau - khong huy duoc (AC 4_4)`() = runTest {
        sessionFlow.value = session(startAt = NOW.minusSeconds(600))
        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.content().canCancel)
        assertEquals("qr-token-123", vm.content().qrToken)
    }

    @Test
    fun `chi duong - co toa do thi theo toa do, khong co thi theo dia chi`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.openDirections()
        advanceUntilIdle()
        val first = events.last() as DropInTicketEvent.OpenDirections
        assertEquals(VenueLocation(10.7731, 106.6672), first.location)
        assertEquals("Sân cầu lông Hòa Bình", first.label)

        venueLocation = null
        vm.openDirections()
        advanceUntilIdle()
        val second = events.last() as DropInTicketEvent.OpenDirections
        assertNull(second.location)
        assertEquals("245 Ba Tháng Hai", second.address)
    }

    // ---------- FR-008a: thêm người ----------

    @Test
    fun `hien nut Them nguoi khi REGISTERED, chua bat dau, duoi 4 nguoi va con cho`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        // partySize 2, còn 5 chỗ → thêm tối đa min(4 − 2, 5) = 2.
        assertEquals(AddPartyOffer(maxAdditional = 2, pricePerPerson = 60_000), vm.content().addParty)
    }

    @Test
    fun `gioi han them theo so cho con lai khi it hon`() = runTest {
        sessionFlow.value = session(capacity = 12, registeredCount = 11)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(1, vm.content().addParty?.maxAdditional)
    }

    @Test
    fun `an nut Them nguoi khi du 4 nguoi, het cho, da check-in, buoi da bat dau hoac bi huy`() = runTest {
        val vm = viewModel()

        mineFlow.value = registration(partySize = 4)
        advanceUntilIdle()
        assertNull(vm.content().addParty)

        mineFlow.value = registration()
        sessionFlow.value = session(registeredCount = 12, status = SessionStatus.FULL)
        advanceUntilIdle()
        assertNull(vm.content().addParty)

        sessionFlow.value = session()
        mineFlow.value = registration(status = RegistrationStatus.CHECKED_IN)
        advanceUntilIdle()
        assertNull(vm.content().addParty)

        mineFlow.value = registration()
        sessionFlow.value = session(startAt = NOW.minusSeconds(60))
        advanceUntilIdle()
        assertNull(vm.content().addParty)

        sessionFlow.value = session(status = SessionStatus.CANCELLED)
        advanceUntilIdle()
        assertNull(vm.content().addParty)
    }

    @Test
    fun `chon so nguoi trong 1 den max va tinh tien phai tra them`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.startAddParty()
        assertEquals(1, vm.form().additional)
        assertEquals(2, vm.form().maxAdditional)
        assertEquals(60_000L, vm.form().extraAmount)

        vm.setAdditional(5)
        assertEquals(2, vm.form().additional)
        assertEquals(120_000L, vm.form().extraAmount)

        vm.setAdditional(0)
        assertEquals(1, vm.form().additional)
        assertEquals(PaymentMethod.MOCK_PAY, vm.form().paymentMethod)
    }

    @Test
    fun `thanh cong - goi use case dung tham so, dong form va bao`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.startAddParty()
        vm.setAdditional(2)
        vm.submitAddParty()
        advanceUntilIdle()

        val (sessionId, additional, requestId) = addCalls.single()
        assertEquals("s1", sessionId)
        assertEquals(2, additional)
        assertTrue(requestId.isNotBlank())
        assertNull(vm.addPartyForm.value)
        assertEquals(DropInTicketEvent.PartyAdded(partySize = 4), events.last())
    }

    @Test
    fun `loi mang - giu form, thu lai dung cung requestId`() = runTest {
        addResults += Result.failure(DropInError.Network())
        val vm = viewModel()
        advanceUntilIdle()

        vm.startAddParty()
        vm.submitAddParty()
        advanceUntilIdle()

        assertTrue(vm.form().error is DropInError.Network)
        assertTrue(vm.form().canRetry)
        assertFalse(vm.form().submitting)

        vm.submitAddParty()
        advanceUntilIdle()

        assertEquals(2, addCalls.size)
        assertEquals(addCalls[0].third, addCalls[1].third)
        assertNull(vm.addPartyForm.value)
    }

    @Test
    fun `Busy - cho thu lai cung requestId, dong form roi mo lai van giu requestId`() = runTest {
        addResults += Result.failure(DropInError.Busy())
        val vm = viewModel()
        advanceUntilIdle()

        vm.startAddParty()
        vm.submitAddParty()
        advanceUntilIdle()
        assertTrue(vm.form().canRetry)

        vm.dismissAddParty()
        assertNull(vm.addPartyForm.value)
        vm.startAddParty()
        vm.submitAddParty()
        advanceUntilIdle()

        assertEquals(addCalls[0].third, addCalls[1].third)
    }

    @Test
    fun `doi so luong sau loi hoac them lan nua sau thanh cong - requestId moi`() = runTest {
        addResults += Result.failure(DropInError.Network())
        val vm = viewModel()
        advanceUntilIdle()

        vm.startAddParty()
        vm.submitAddParty()
        advanceUntilIdle()
        vm.setAdditional(2)
        vm.submitAddParty() // thành công
        advanceUntilIdle()
        assertNotEquals(addCalls[0].third, addCalls[1].third)

        vm.startAddParty()
        vm.submitAddParty()
        advanceUntilIdle()
        assertEquals(3, addCalls.size)
        assertNotEquals(addCalls[1].third, addCalls[2].third)
    }

    @Test
    fun `PartyLimit - cap nhat gioi han chon theo maxAdditional`() = runTest {
        addResults += Result.failure(DropInError.PartyLimit(maxAdditional = 1))
        val vm = viewModel()
        advanceUntilIdle()

        vm.startAddParty()
        vm.setAdditional(2)
        vm.submitAddParty()
        advanceUntilIdle()

        assertEquals(1, vm.form().maxAdditional)
        assertEquals(1, vm.form().additional)
        assertEquals(DropInError.PartyLimit(1), vm.form().error)
        assertFalse(vm.form().canRetry)
    }

    @Test
    fun `PartyLimit 0 - dong form va bao loi`() = runTest {
        addResults += Result.failure(DropInError.PartyLimit(maxAdditional = 0))
        val vm = viewModel()
        advanceUntilIdle()

        vm.startAddParty()
        vm.submitAddParty()
        advanceUntilIdle()

        assertNull(vm.addPartyForm.value)
        assertEquals(DropInTicketEvent.AddPartyFailed(DropInError.PartyLimit(0)), events.last())
    }

    @Test
    fun `SessionFull - gioi han chon theo so cho con`() = runTest {
        addResults += Result.failure(DropInError.SessionFull(remaining = 1))
        val vm = viewModel()
        advanceUntilIdle()

        vm.startAddParty()
        vm.setAdditional(2)
        vm.submitAddParty()
        advanceUntilIdle()

        assertEquals(1, vm.form().maxAdditional)
        assertEquals(1, vm.form().additional)
        assertEquals(DropInError.SessionFull(1), vm.form().error)
    }

    @Test
    fun `NotRegistered - dong form va bao loi`() = runTest {
        addResults += Result.failure(DropInError.NotRegistered())
        val vm = viewModel()
        advanceUntilIdle()

        vm.startAddParty()
        vm.submitAddParty()
        advanceUntilIdle()

        assertNull(vm.addPartyForm.value)
        val event = events.last() as DropInTicketEvent.AddPartyFailed
        assertTrue(event.cause is DropInError.NotRegistered)
    }

    @Test
    fun `ngoai tuyen - khong mo form Them nguoi`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        online.value = false
        advanceUntilIdle()
        assertTrue(vm.uiState.value is DropInTicketUiState.Offline)

        vm.startAddParty()
        assertNull(vm.addPartyForm.value)
    }

    @Test
    fun `AT_VENUE - form giu cach thanh toan tra tai san`() = runTest {
        mineFlow.value = registration(paymentMethod = PaymentMethod.AT_VENUE)
        val vm = viewModel()
        advanceUntilIdle()

        vm.startAddParty()
        assertEquals(PaymentMethod.AT_VENUE, vm.form().paymentMethod)
    }

    @Test
    fun `khong co nut Them nguoi thi startAddParty khong mo form`() = runTest {
        mineFlow.value = registration(partySize = 4)
        val vm = viewModel()
        advanceUntilIdle()

        vm.startAddParty()
        assertNull(vm.addPartyForm.value)
    }
}

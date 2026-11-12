package com.example.booking_app.feature.dropin.ui.confirm

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.dropin.domain.model.DropInError
import com.example.booking_app.feature.dropin.domain.model.DropInRegistration
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.example.booking_app.feature.dropin.domain.model.DropInWarning
import com.example.booking_app.feature.dropin.domain.model.PaymentMethod
import com.example.booking_app.feature.dropin.domain.model.RegisterRequest
import com.example.booking_app.feature.dropin.domain.usecase.CheckDropInWarnings
import com.example.booking_app.feature.dropin.domain.usecase.ObserveDropInSession
import com.example.booking_app.feature.dropin.domain.usecase.RegisterDropIn
import com.example.booking_app.feature.dropin.ui.registration
import com.example.booking_app.feature.dropin.ui.session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** T032: màn hình xác nhận đăng ký (US2, AC 2.1, 2.2, 2.5, 2.6, 2.8). */
@OptIn(ExperimentalCoroutinesApi::class)
class DropInConfirmViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sessionFlow = MutableStateFlow<DropInSession?>(session(capacity = 12, registeredCount = 7))
    private val requests = mutableListOf<RegisterRequest>()
    private val results = ArrayDeque<Result<DropInRegistration>>()
    private val register = RegisterDropIn { req ->
        requests += req
        results.removeFirstOrNull() ?: Result.success(registration(partySize = req.partySize))
    }
    private var warnings = emptyList<DropInWarning>()

    private fun viewModel(handle: SavedStateHandle = handle()) = DropInConfirmViewModel(
        handle,
        ObserveDropInSession { sessionFlow },
        register,
        CheckDropInWarnings { warnings },
    )

    private fun handle() = SavedStateHandle(mapOf(DropInConfirmViewModel.ARG_SESSION_ID to "s1"))

    private fun DropInConfirmViewModel.form(): ConfirmForm = when (val s = uiState.value) {
        is DropInConfirmUiState.Editing -> s.form
        is DropInConfirmUiState.Submitting -> s.form
        is DropInConfirmUiState.Failed -> s.form
        else -> throw AssertionError("Không có form: $s")
    }

    @Test
    fun `mac dinh 1 nguoi, thanh toan gia lap, maxParty toi da 4`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        val form = (vm.uiState.value as DropInConfirmUiState.Editing).form
        assertEquals(1, form.partySize)
        assertEquals(4, form.maxParty)
        assertEquals(PaymentMethod.MOCK_PAY, form.paymentMethod)
    }

    @Test
    fun `maxParty bang so cho con lai khi it hon 4`() = runTest {
        sessionFlow.value = session(capacity = 12, registeredCount = 10)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(2, vm.form().maxParty)
        vm.increment()
        vm.increment()
        vm.increment()
        assertEquals(2, vm.form().partySize)
        vm.setPartySize(9)
        assertEquals(2, vm.form().partySize)
    }

    @Test
    fun `tong tien bang gia nhan so nguoi`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setPartySize(3)

        assertEquals(180_000L, vm.form().total)
    }

    @Test
    fun `canh bao chi hien, khong chan xac nhan`() = runTest {
        warnings = listOf(DropInWarning.OVERLAP_BOOKING)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(DropInWarning.OVERLAP_BOOKING), vm.form().warnings)
        assertTrue(vm.form().canSubmit)
    }

    @Test
    fun `requestId giu nguyen qua SavedStateHandle`() = runTest {
        val handle = handle()
        val first = viewModel(handle)
        val second = viewModel(handle)
        advanceUntilIdle()

        assertEquals(first.requestId, second.requestId)
        assertNotNull(java.util.UUID.fromString(first.requestId))
    }

    @Test
    fun `thanh cong thi dieu huong toi ve voi dung yeu cau`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.setPartySize(2)
        vm.setPaymentMethod(PaymentMethod.AT_VENUE)

        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            assertEquals(DropInConfirmEvent.NavigateToTicket("s1"), awaitItem())
        }
        assertEquals(RegisterRequest("s1", 2, PaymentMethod.AT_VENUE, vm.requestId), requests.single())
    }

    @Test
    fun `dang gui thi khoa nut, bam nhieu lan chi gui mot lan`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.submit()
        assertTrue(vm.uiState.value is DropInConfirmUiState.Submitting)
        vm.submit()
        vm.submit()
        advanceUntilIdle()

        assertEquals(1, requests.size)
    }

    @Test
    fun `SessionFull thi Failed kem so cho con lai`() = runTest {
        results += Result.failure(DropInError.SessionFull(remaining = 1))
        val vm = viewModel()
        advanceUntilIdle()
        vm.setPartySize(2)

        vm.submit()
        advanceUntilIdle()

        val failed = vm.uiState.value as DropInConfirmUiState.Failed
        assertEquals(1, failed.remaining)
        assertTrue(failed.reason is DropInError.SessionFull)
        assertEquals(1, failed.form.maxParty)
        assertEquals(1, failed.form.partySize)
    }

    @Test
    fun `mat mang thi giu lua chon va thu lai cung requestId`() = runTest {
        results += Result.failure(DropInError.Network())
        val vm = viewModel()
        advanceUntilIdle()
        vm.setPartySize(3)
        vm.setPaymentMethod(PaymentMethod.AT_VENUE)

        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            assertEquals(DropInConfirmEvent.NetworkError, awaitItem())

            val editing = vm.uiState.value as DropInConfirmUiState.Editing
            assertEquals(3, editing.form.partySize)
            assertEquals(PaymentMethod.AT_VENUE, editing.form.paymentMethod)

            vm.submit()
            advanceUntilIdle()
            assertEquals(DropInConfirmEvent.NavigateToTicket("s1"), awaitItem())
        }
        assertEquals(2, requests.size)
        assertEquals(requests[0].requestId, requests[1].requestId)
        assertEquals(requests[0], requests[1])
    }

    @Test
    fun `Busy thi tu gui lai cung requestId toi da 2 lan roi thanh cong`() = runTest {
        results += Result.failure(DropInError.Busy())
        results += Result.failure(DropInError.Busy())
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            assertEquals(DropInConfirmEvent.NavigateToTicket("s1"), awaitItem())
        }
        assertEquals(3, requests.size)
        assertEquals(1, requests.map { it.requestId }.distinct().size)
    }

    @Test
    fun `Busy qua 2 lan gui lai thi Failed`() = runTest {
        repeat(3) { results += Result.failure(DropInError.Busy()) }
        val vm = viewModel()
        advanceUntilIdle()

        vm.submit()
        advanceUntilIdle()

        assertEquals(3, requests.size)
        val failed = vm.uiState.value as DropInConfirmUiState.Failed
        assertTrue(failed.reason is DropInError.Busy)
    }

    @Test
    fun `buoi da bat dau thi Failed bao ly do`() = runTest {
        results += Result.failure(DropInError.SessionStarted())
        val vm = viewModel()
        advanceUntilIdle()

        vm.submit()
        advanceUntilIdle()

        assertTrue((vm.uiState.value as DropInConfirmUiState.Failed).reason is DropInError.SessionStarted)
    }

    @Test
    fun `da dang ky tu truoc thi mo ve hien co`() = runTest {
        results += Result.failure(DropInError.AlreadyRegistered())
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.submit()
            advanceUntilIdle()
            assertEquals(DropInConfirmEvent.NavigateToTicket("s1"), awaitItem())
        }
    }

    @Test
    fun `het cho thi khong cho xac nhan`() = runTest {
        sessionFlow.value = session(capacity = 12, registeredCount = 12)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(0, vm.form().maxParty)
        assertFalse(vm.form().canSubmit)
        vm.submit()
        advanceUntilIdle()
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `lua chon giu qua SavedStateHandle`() = runTest {
        val handle = handle()
        val first = viewModel(handle)
        advanceUntilIdle()
        first.setPartySize(3)
        first.setPaymentMethod(PaymentMethod.AT_VENUE)

        val second = viewModel(handle)
        advanceUntilIdle()

        assertEquals(3, second.form().partySize)
        assertEquals(PaymentMethod.AT_VENUE, second.form().paymentMethod)
    }

    @Test
    fun `buoi khong ton tai thi NotFound`() = runTest {
        sessionFlow.value = null
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DropInConfirmUiState.NotFound, vm.uiState.value)
    }
}

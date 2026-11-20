package com.example.booking_app.feature.dropin.ui.ticket

import androidx.lifecycle.SavedStateHandle
import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.dropin.domain.model.DropInRegistration
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.example.booking_app.feature.dropin.domain.model.PaymentMethod
import com.example.booking_app.feature.dropin.domain.model.PaymentStatus
import com.example.booking_app.feature.dropin.domain.usecase.AddDropInParty
import com.example.booking_app.feature.dropin.domain.usecase.GetVenueLocation
import com.example.booking_app.feature.dropin.domain.usecase.ObserveConnectivity
import com.example.booking_app.feature.dropin.domain.usecase.ObserveDropInSession
import com.example.booking_app.feature.dropin.domain.usecase.ObserveMyDropInRegistration
import com.example.booking_app.feature.dropin.domain.usecase.ReadCachedDropInTicket
import com.example.booking_app.feature.dropin.ui.NOW
import com.example.booking_app.feature.dropin.ui.registration
import com.example.booking_app.feature.dropin.ui.session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** T029: vé của lượt được nhận từ hàng chờ (spec 081 US2, AC 2.3, 2.4, FR-011, research R8). */
@OptIn(ExperimentalCoroutinesApi::class)
class DropInTicketViewModelWaitlistTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val mineFlow = MutableStateFlow<DropInRegistration?>(registration(promotedAt = NOW))
    private val sessionFlow = MutableStateFlow<DropInSession?>(session())

    private fun TestScope.viewModel(): DropInTicketViewModel {
        val vm = DropInTicketViewModel(
            SavedStateHandle(mapOf(DropInTicketViewModel.ARG_SESSION_ID to "s1")),
            ObserveMyDropInRegistration { mineFlow },
            ObserveDropInSession { sessionFlow },
            FakeClock { NOW.toEpochMilli() },
            ReadCachedDropInTicket { null },
            ObserveConnectivity { MutableStateFlow(true) },
            AddDropInParty { _, _, _ -> Result.success(registration()) },
            GetVenueLocation { null },
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private fun DropInTicketViewModel.content() = uiState.value as DropInTicketUiState.Content

    private fun promoted(
        at: Instant,
        method: PaymentMethod = PaymentMethod.MOCK_PAY,
        status: PaymentStatus = PaymentStatus.PAID,
    ) = registration(promotedAt = at, paymentMethod = method, paymentStatus = status)

    @Test
    fun `luot duoc nhan tu hang cho thi co nhan Duoc nhan tu hang cho`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.content().fromWaitlist)
        assertTrue(TicketLabel.FROM_WAITLIST in vm.content().labels)
    }

    @Test
    fun `dang ky thuong khong co nhan hang cho va khong co han huy mien phi`() = runTest {
        mineFlow.value = registration()
        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.content().fromWaitlist)
        assertTrue(vm.content().labels.isEmpty())
        assertNull(vm.content().freeCancelUntil)
    }

    @Test
    fun `duoc nhan sat gio va con trong 30 phut thi hien han huy mien phi`() = runTest {
        val promotedAt = NOW.minus(Duration.ofMinutes(10))
        mineFlow.value = promoted(promotedAt)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(promotedAt.plus(Duration.ofMinutes(30)), vm.content().freeCancelUntil)
    }

    @Test
    fun `dung 30 phut van con han, qua 30 phut thi an`() = runTest {
        mineFlow.value = promoted(NOW.minus(Duration.ofMinutes(30)))
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(NOW, vm.content().freeCancelUntil)

        mineFlow.value = promoted(NOW.minus(Duration.ofMinutes(31)))
        advanceUntilIdle()
        assertNull(vm.content().freeCancelUntil)
    }

    @Test
    fun `duoc nhan khong sat gio (tu 240 phut tro len) thi khong co han 30 phut`() = runTest {
        val promotedAt = NOW.minus(Duration.ofMinutes(10))
        val vm = viewModel()

        // Buổi bắt đầu sau 4 giờ 10 phút kể từ lúc được nhận: đúng 250 phút, không sát giờ.
        sessionFlow.value = session(startAt = promotedAt.plus(Duration.ofMinutes(250)))
        mineFlow.value = promoted(promotedAt)
        advanceUntilIdle()
        assertNull(vm.content().freeCancelUntil)

        // Đúng 240 phút vẫn không tính (điều kiện là nhỏ hơn 240).
        sessionFlow.value = session(startAt = promotedAt.plus(Duration.ofMinutes(240)))
        advanceUntilIdle()
        assertNull(vm.content().freeCancelUntil)

        // 239 phút thì được tính.
        sessionFlow.value = session(startAt = promotedAt.plus(Duration.ofMinutes(239)))
        advanceUntilIdle()
        assertEquals(promotedAt.plus(Duration.ofMinutes(30)), vm.content().freeCancelUntil)
    }

    @Test
    fun `tra tai san thi khong hien han huy mien phi vi khong co tien de hoan`() = runTest {
        mineFlow.value = promoted(
            NOW.minus(Duration.ofMinutes(5)),
            method = PaymentMethod.AT_VENUE,
            status = PaymentStatus.UNPAID,
        )
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.content().fromWaitlist)
        assertNull(vm.content().freeCancelUntil)
    }
}

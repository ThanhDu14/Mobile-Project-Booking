package com.example.booking_app.feature.dropin.ui.mine

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.dropin.domain.model.DropInCursor
import com.example.booking_app.feature.dropin.domain.model.MyDropInItem
import com.example.booking_app.feature.dropin.domain.model.Page
import com.example.booking_app.feature.dropin.domain.model.RegistrationOutcome
import com.example.booking_app.feature.dropin.domain.model.RegistrationStatus
import com.example.booking_app.feature.dropin.domain.model.SessionStatus
import com.example.booking_app.feature.dropin.domain.model.WaitlistEndReason
import com.example.booking_app.feature.dropin.domain.model.WaitlistPosition
import com.example.booking_app.feature.dropin.domain.usecase.LoadMyDropIns
import com.example.booking_app.feature.dropin.ui.NOW
import com.example.booking_app.feature.dropin.ui.registration
import com.example.booking_app.feature.dropin.ui.session
import com.example.booking_app.feature.review.FakeReviewRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** T025, T035, T047: lượt chờ ở "Lượt vãng lai của tôi" (spec 081 FR-015, AC 2.4, 4.4). */
@OptIn(ExperimentalCoroutinesApi::class)
class MyDropInsViewModelWaitlistTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeLoad : LoadMyDropIns {
        var upcomingFlow: Flow<List<MyDropInItem>> = MutableStateFlow(emptyList())
        val pastResults = ArrayDeque<Result<Page<MyDropInItem>>>()
        override fun upcoming(): Flow<List<MyDropInItem>> = upcomingFlow
        override suspend fun past(cursor: DropInCursor?): Result<Page<MyDropInItem>> =
            pastResults.removeFirstOrNull() ?: Result.success(Page(emptyList(), null))
    }

    private val load = FakeLoad()
    private val clock = object : AppClock {
        override fun now(): Long = NOW.toEpochMilli()
        override fun elapsedRealtime(): Long = 0
    }

    private fun viewModel() = MyDropInsViewModel(SavedStateHandle(), load, FakeReviewRepository(), clock)

    private fun waitingItem(sessionId: String, ticket: Int?, tickets: List<Int>): MyDropInItem {
        val s = session(
            id = sessionId,
            startAt = NOW.plusSeconds(5 * 3600L),
            capacity = 12,
            registeredCount = 12,
            status = SessionStatus.FULL,
            waitlistTickets = tickets,
        )
        val r = registration(
            sessionId = sessionId,
            status = RegistrationStatus.WAITLISTED,
            qrToken = null,
            waitlistTicket = ticket,
        ).copy(sessionStartAt = s.startAt, sessionEndAt = s.endAt)
        return MyDropInItem(r, s, RegistrationOutcome.WAITING)
    }

    private fun endedItem(sessionId: String, outcome: RegistrationOutcome, reason: WaitlistEndReason) = run {
        val s = session(id = sessionId, startAt = NOW.minusSeconds(4 * 3600L))
        val r = registration(
            sessionId = sessionId,
            status = RegistrationStatus.CANCELLED,
            qrToken = null,
            waitlistTicket = 2,
            waitlistEndReason = reason,
        ).copy(sessionStartAt = s.startAt, sessionEndAt = s.endAt)
        MyDropInItem(r, s, outcome)
    }

    @Test
    fun `luot WAITLISTED nam o Sap toi kem vi tri tinh tu mang ve cua buoi`() = runTest {
        load.upcomingFlow = MutableStateFlow(listOf(waitingItem("a", ticket = 5, tickets = listOf(3, 5, 9))))
        val vm = viewModel()
        advanceUntilIdle()

        val row = (vm.uiState.value as MyDropInsUiState.Content).upcoming.single()
        assertNull(row.outcome)
        assertEquals(WaitlistPosition(2, 3), row.waitlistPosition)
        assertFalse(row.fromWaitlist)
    }

    @Test
    fun `vi tri doi khi nguoi phia truoc roi hang cho`() = runTest {
        val flow = MutableStateFlow(listOf(waitingItem("a", ticket = 5, tickets = listOf(3, 5))))
        load.upcomingFlow = flow
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(2, (vm.uiState.value as MyDropInsUiState.Content).upcoming.single().waitlistPosition?.position)

        flow.value = listOf(waitingItem("a", ticket = 5, tickets = listOf(5)))
        advanceUntilIdle()

        assertEquals(1, (vm.uiState.value as MyDropInsUiState.Content).upcoming.single().waitlistPosition?.position)
    }

    @Test
    fun `luot duoc nhan tu hang cho o Sap toi co nhan va khong con vi tri`() = runTest {
        val s = session(id = "a", startAt = NOW.plusSeconds(5 * 3600L))
        val r = registration(sessionId = "a", promotedAt = NOW.minusSeconds(60), waitlistTicket = 5)
            .copy(sessionStartAt = s.startAt, sessionEndAt = s.endAt)
        load.upcomingFlow = MutableStateFlow(listOf(MyDropInItem(r, s, RegistrationOutcome.UPCOMING)))
        val vm = viewModel()
        advanceUntilIdle()

        val row = (vm.uiState.value as MyDropInsUiState.Content).upcoming.single()
        assertTrue(row.fromWaitlist)
        assertNull(row.waitlistPosition)
        assertNull(row.outcome)
    }

    @Test
    fun `bam luot cho mo man hinh hang cho, bam luot thuong mo ve`() = runTest {
        val s = session(id = "b", startAt = NOW.plusSeconds(2 * 3600L))
        val regular = MyDropInItem(
            registration(sessionId = "b").copy(sessionStartAt = s.startAt, sessionEndAt = s.endAt),
            s,
            RegistrationOutcome.UPCOMING,
        )
        load.upcomingFlow = MutableStateFlow(listOf(regular, waitingItem("a", 5, listOf(5))))
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onItemClicked("a")
            assertEquals(MyDropInsEvent.OpenWaitlist("a"), awaitItem())
            vm.onItemClicked("b")
            assertEquals(MyDropInsEvent.OpenTicket("b"), awaitItem())
        }
    }

    @Test
    fun `Da qua giu nhan ket qua cua hang cho`() = runTest {
        load.pastResults += Result.success(
            Page(
                listOf(
                    endedItem("p1", RegistrationOutcome.LEFT_WAITLIST, WaitlistEndReason.LEFT),
                    endedItem("p2", RegistrationOutcome.LEFT_WAITLIST, WaitlistEndReason.AUTO_LEFT_OVERLAP),
                    endedItem("p3", RegistrationOutcome.NOT_ADMITTED, WaitlistEndReason.CLOSED),
                    endedItem("p4", RegistrationOutcome.SESSION_CANCELLED, WaitlistEndReason.SESSION_CANCELLED),
                ),
                null,
            ),
        )
        val vm = viewModel()
        advanceUntilIdle()

        val past = (vm.uiState.value as MyDropInsUiState.Content).past
        assertEquals(
            listOf(
                RegistrationOutcome.LEFT_WAITLIST,
                RegistrationOutcome.LEFT_WAITLIST,
                RegistrationOutcome.NOT_ADMITTED,
                RegistrationOutcome.SESSION_CANCELLED,
            ),
            past.map { it.outcome },
        )
        assertEquals(WaitlistEndReason.AUTO_LEFT_OVERLAP, past[1].item.registration.waitlistEndReason)
    }
}

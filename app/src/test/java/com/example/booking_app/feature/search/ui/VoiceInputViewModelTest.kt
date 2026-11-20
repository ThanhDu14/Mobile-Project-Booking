package com.example.booking_app.feature.search.ui

import androidx.lifecycle.SavedStateHandle
import com.example.booking_app.core.catalog.CatalogRepository
import com.example.booking_app.core.location.LocationProvider
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.search.domain.AiParseResponse
import com.example.booking_app.feature.search.domain.AiCourtParse
import com.example.booking_app.feature.search.domain.AiCourtFilter
import com.example.booking_app.feature.search.domain.AiSearchUsage
import com.example.booking_app.feature.search.domain.CourtAiApi
import com.example.booking_app.feature.search.domain.CourtSearchCriteria
import com.example.booking_app.feature.search.domain.CourtSearchPage
import com.example.booking_app.feature.search.domain.CourtSearchRepository
import com.example.booking_app.feature.search.domain.MemoryAiQueryHistory
import com.example.booking_app.feature.search.domain.VoiceError
import com.example.booking_app.feature.search.domain.VoiceEvent
import com.example.booking_app.feature.search.domain.VoiceInput
import com.google.firebase.firestore.GeoPoint
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Nhận dạng giả: test đẩy sự kiện qua [events]; mỗi lần nghe tạo kênh mới. */
internal class FakeVoiceInput(private val available: Boolean = true) : VoiceInput {
    var events = Channel<VoiceEvent>(Channel.UNLIMITED)
        private set
    var listens = 0
        private set

    override fun isAvailable() = available

    override fun listen(): Flow<VoiceEvent> {
        listens++
        events = Channel(Channel.UNLIMITED)
        return events.consumeAsFlow()
    }

    fun send(event: VoiceEvent, finished: Boolean = event !is VoiceEvent.Partial) {
        events.trySend(event)
        if (finished) events.close()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceInputViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val api = mockk<CourtAiApi>().also { coEvery { it.usage() } returns AiSearchUsage(0, 0, 10) }

    private fun viewModel(voice: FakeVoiceInput): CourtSearchViewModel {
        val catalog = mockk<CatalogRepository>()
        coEvery { catalog.districts() } returns emptyList()
        coEvery { catalog.amenities() } returns emptyList()
        val repo = object : CourtSearchRepository {
            override fun search(criteria: CourtSearchCriteria, origin: GeoPoint?) =
                flowOf(CourtSearchPage(emptyList(), complete = true, capped = false))
        }
        return CourtSearchViewModel(SavedStateHandle(), repo, api, catalog, mockk<LocationProvider>(),
            FakeSessionManager(SessionState.SignedIn("player", AppRole.PLAYER, true)),
            FakeClock { Instant.parse("2026-11-15T03:00:00Z").toEpochMilli() },
            MemoryAiQueryHistory(), FakeSearchHistory(), voice)
    }

    @Test
    fun micIsHiddenWithoutARecognitionService() = runTest {
        val vm = viewModel(FakeVoiceInput(available = false))
        runCurrent()
        assertFalse(vm.state.value.voiceAvailable)
        vm.toggleVoice()
        assertFalse(vm.state.value.listening)
    }

    @Test
    fun partialThenFinalFillsTheFieldWithoutSendingOrUsingQuota() = runTest {
        val voice = FakeVoiceInput()
        val vm = viewModel(voice)
        runCurrent()
        vm.toggleVoice()
        runCurrent()
        assertTrue(vm.state.value.listening)

        voice.send(VoiceEvent.Level(0.7f), finished = false)
        voice.send(VoiceEvent.Partial("tối mai"))
        runCurrent()
        assertEquals("tối mai", vm.state.value.aiInput)
        assertEquals(0.7f, vm.voiceLevel.value)

        voice.send(VoiceEvent.Final("tối mai quận 10 dưới 100k"))
        runCurrent()
        assertEquals("tối mai quận 10 dưới 100k", vm.state.value.aiInput)
        assertFalse(vm.state.value.listening)
        assertEquals(0f, vm.voiceLevel.value) // micro về trạng thái nghỉ
        coVerify(exactly = 0) { api.parse(any()) }
    }

    @Test
    fun recognitionErrorKeepsTheOldTextAndSuggestsTyping() = runTest {
        val voice = FakeVoiceInput()
        val vm = viewModel(voice)
        runCurrent()
        vm.input("sân quận 10")
        vm.toggleVoice()
        runCurrent()
        voice.send(VoiceEvent.Partial("ừm"))
        voice.send(VoiceEvent.Error(VoiceError.NO_MATCH))
        runCurrent()
        assertEquals("sân quận 10", vm.state.value.aiInput)
        assertEquals(SearchNotice.VOICE_NO_MATCH, vm.state.value.notice)
        assertFalse(vm.state.value.listening)

        vm.toggleVoice()
        runCurrent()
        voice.send(VoiceEvent.Error(VoiceError.NETWORK))
        runCurrent()
        assertEquals(SearchNotice.VOICE_NETWORK, vm.state.value.notice)
        coVerify(exactly = 0) { api.parse(any()) }
    }

    @Test
    fun tappingAgainStopsListening() = runTest {
        val voice = FakeVoiceInput()
        val vm = viewModel(voice)
        runCurrent()
        vm.toggleVoice()
        runCurrent()
        vm.toggleVoice()
        runCurrent()
        assertFalse(vm.state.value.listening)
        assertEquals(1, voice.listens)
    }

    @Test
    fun deniedPermissionDimsTheMicUntilGrantedInSettings() = runTest {
        val voice = FakeVoiceInput()
        val vm = viewModel(voice)
        runCurrent()
        vm.voicePermission(granted = false)
        assertTrue(vm.state.value.voiceDenied)
        assertEquals(0, voice.listens)

        vm.voicePermissionChecked(granted = false)
        assertTrue(vm.state.value.voiceDenied)
        vm.voicePermissionChecked(granted = true) // quay lại từ Cài đặt
        assertFalse(vm.state.value.voiceDenied)
        assertEquals(0, voice.listens)

        vm.voicePermission(granted = true)
        runCurrent()
        assertTrue(vm.state.value.listening)
    }

    @Test
    fun sendingWhileListeningStopsTheMicSoALateResultCannotOverwriteTheQuestion() = runTest {
        val voice = FakeVoiceInput()
        coEvery { api.parse(any()) } returns AiParseResponse(
            AiCourtParse("unclear", filter = AiCourtFilter(districtId = "q10")), AiSearchUsage(1, 1, 10),
        )
        val vm = viewModel(voice)
        runCurrent()
        vm.toggleVoice()
        runCurrent()
        voice.send(VoiceEvent.Partial("sân quận 10"))
        runCurrent()

        vm.submitAi()
        assertFalse(vm.state.value.listening)
        runCurrent()
        assertTrue(vm.state.value.clarification != null)
        // Kết quả cuối đến muộn không còn được nhận.
        voice.send(VoiceEvent.Final("sân quận 10 tối nay"))
        runCurrent()
        assertEquals("sân quận 10", vm.state.value.aiInput)
        assertTrue(vm.state.value.clarification != null)
    }
}

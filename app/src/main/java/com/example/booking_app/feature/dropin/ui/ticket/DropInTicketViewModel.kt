package com.example.booking_app.feature.dropin.ui.ticket

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.util.AppClock
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** Nhãn trạng thái trên vé (Chip Material 3, AC 3.5). */
enum class TicketLabel { SESSION_CHANGED, SESSION_CANCELLED, VENUE_INACTIVE }

/** Lý do vé không có mã QR. */
enum class NoQrReason { SESSION_CANCELLED, INACTIVE, PENDING }

/** Được thêm người vào lượt (FR-008a): tối đa [maxAdditional] người, mỗi người [pricePerPerson] (giá đã chốt). */
data class AddPartyOffer(val maxAdditional: Int, val pricePerPerson: Long)

sealed interface DropInTicketUiState {
    data object Loading : DropInTicketUiState

    /**
     * [qrToken] `null` khi không được hiện QR, lý do ở [noQrReason]. [session] có thể `null` khi đọc buổi lỗi: vé vẫn
     * hiện bằng thông tin chốt trong đăng ký. Bitmap QR do Fragment vẽ bằng [QrRenderer] để ViewModel test được trên JVM.
     * [addParty] `null` thì không hiện nút "Thêm người".
     */
    data class Content(
        val registration: DropInRegistration,
        val session: DropInSession?,
        val qrToken: String?,
        val noQrReason: NoQrReason?,
        val labels: List<TicketLabel>,
        val canCancel: Boolean,
        val addParty: AddPartyOffer? = null,
    ) : DropInTicketUiState

    /** Không có mạng (hoặc đọc realtime lỗi): hiện bản đã lưu trên máy, QR vẫn dùng được (FR-015). */
    data class Offline(val cached: Content) : DropInTicketUiState

    /** Chưa có đăng ký cho buổi này (trạng thái rỗng). */
    data object NotFound : DropInTicketUiState

    data class Error(val cause: Throwable) : DropInTicketUiState
}

/** Bottom sheet "Thêm người" (FR-008a). `null` ở [DropInTicketViewModel.addPartyForm] nghĩa là đang đóng. */
data class AddPartyForm(
    val additional: Int,
    val maxAdditional: Int,
    val pricePerPerson: Long,
    val paymentMethod: PaymentMethod,
    /** Số người hiện có của lượt, để tính lại giới hạn khi buổi hết chỗ. */
    val currentPartySize: Int,
    val submitting: Boolean = false,
    val error: Throwable? = null,
) {
    /** Số tiền phải trả thêm = giá/người × số người thêm. */
    val extraAmount: Long get() = pricePerPerson * additional

    /** Mất mạng hoặc hệ thống bận: gửi lại cùng `requestId`. */
    val canRetry: Boolean get() = error is DropInError.Network || error is DropInError.Busy
}

sealed interface DropInTicketEvent {
    /** Mở bản đồ: theo tọa độ khi có [location], không thì tìm theo [address]. */
    data class OpenDirections(val label: String, val location: VenueLocation?, val address: String?) : DropInTicketEvent

    data class PartyAdded(val partySize: Int) : DropInTicketEvent

    /** Thêm người không thể tiếp tục (không còn lượt, đã đủ 4 người, hết chỗ): form đã đóng. */
    data class AddPartyFailed(val cause: Throwable) : DropInTicketEvent
}

/**
 * Vé vãng lai (US2 T040, US3 T048). Đọc vé trong cache của Firestore trước để hiện ngay, sau đó nghe realtime đăng
 * ký của mình và buổi. Mất mạng thì [DropInTicketUiState.Offline] với bản đã có (FR-015). Thêm người vào lượt
 * (FR-008a): `requestId` sinh một lần cho mỗi số lượng, giữ trong [SavedStateHandle] để thử lại không cộng hai lần.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DropInTicketViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val observeMine: ObserveMyDropInRegistration,
    private val observeSession: ObserveDropInSession,
    private val clock: AppClock,
    private val readCached: ReadCachedDropInTicket,
    private val observeConnectivity: ObserveConnectivity,
    private val addDropInParty: AddDropInParty,
    private val getVenueLocation: GetVenueLocation,
) : ViewModel() {

    val sessionId: String = checkNotNull(savedStateHandle[ARG_SESSION_ID]) { "Thiếu sessionId" }

    /** Vừa đăng ký xong (từ màn hình xác nhận): hiện dải "Đăng ký thành công". */
    val justRegistered: Boolean = savedStateHandle[ARG_JUST_REGISTERED] ?: false

    private val reload = MutableStateFlow(0)

    val uiState: StateFlow<DropInTicketUiState> = reload
        .flatMapLatest { ticketFlow() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DropInTicketUiState.Loading)

    private val _addPartyForm = MutableStateFlow<AddPartyForm?>(null)
    val addPartyForm: StateFlow<AddPartyForm?> = _addPartyForm.asStateFlow()

    private val _events = Channel<DropInTicketEvent>(Channel.BUFFERED)
    val events: Flow<DropInTicketEvent> = _events.receiveAsFlow()

    fun retry() {
        reload.update { it + 1 }
    }

    // ---------- Vé: cache trước, realtime sau (T048) ----------

    /** Kết quả của nguồn realtime: chưa phát, có giá trị, hoặc lỗi. */
    private sealed interface Live {
        data object Pending : Live
        data class Value(val state: DropInTicketUiState) : Live
        data class Failed(val cause: Throwable) : Live
    }

    private fun ticketFlow(): Flow<DropInTicketUiState> = flow {
        emit(DropInTicketUiState.Loading)
        // Bản trong cache hiện ngay (< 1 giây), kể cả khi mất mạng.
        var last: DropInTicketUiState.Content? = readCachedContent()
        last?.let { emit(it) }

        val sessionFlow = observeSession(sessionId)
            .map<DropInSession?, DropInSession?> { it }
            // Đọc buổi lỗi: vẫn hiện vé bằng thông tin chốt trong đăng ký.
            .catch { emit(null) }
        val live: Flow<Live> = combine<DropInRegistration?, DropInSession?, Live>(
            observeMine(sessionId),
            sessionFlow,
        ) { registration, session -> Live.Value(build(registration, session)) }
            .onStart { emit(Live.Pending) }
            .catch { emit(Live.Failed(it)) }
        val online = observeConnectivity().distinctUntilChanged()

        combine(live, online) { result, isOnline -> result to isOnline }.collect { (result, isOnline) ->
            val previous = last
            val next: DropInTicketUiState? = when (result) {
                Live.Pending -> previous?.let { if (isOnline) it else DropInTicketUiState.Offline(it) }
                is Live.Failed -> previous?.let { DropInTicketUiState.Offline(it) } ?: DropInTicketUiState.Error(result.cause)
                is Live.Value -> when (val state = result.state) {
                    is DropInTicketUiState.Content -> {
                        last = state
                        if (isOnline) state else DropInTicketUiState.Offline(state)
                    }
                    // Mất mạng mà Firestore chưa có đăng ký trong cache: không kết luận "chưa có vé".
                    else -> when {
                        isOnline -> state
                        previous != null -> DropInTicketUiState.Offline(previous)
                        else -> DropInTicketUiState.Error(DropInError.Network())
                    }
                }
            }
            if (next != null) emit(next)
        }
    }

    private suspend fun readCachedContent(): DropInTicketUiState.Content? {
        val cached: CachedDropInTicket? = try {
            readCached(sessionId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (cached == null) return null
        return build(cached.registration, cached.session) as? DropInTicketUiState.Content
    }

    private fun build(registration: DropInRegistration?, session: DropInSession?): DropInTicketUiState {
        if (registration == null) return DropInTicketUiState.NotFound
        val cancelled = session?.status == SessionStatus.CANCELLED
        val labels = buildList {
            if (cancelled) add(TicketLabel.SESSION_CANCELLED)
            else if (registration.sessionChanged) add(TicketLabel.SESSION_CHANGED)
            if (session != null && !session.venueActive) add(TicketLabel.VENUE_INACTIVE)
        }
        val noQrReason = when {
            cancelled -> NoQrReason.SESSION_CANCELLED
            !registration.isActive -> NoQrReason.INACTIVE
            registration.qrToken.isNullOrEmpty() -> NoQrReason.PENDING
            else -> null
        }
        val now = Instant.ofEpochMilli(clock.now())
        val startAt = session?.startAt ?: registration.sessionStartAt
        val notStarted = startAt != null && now.isBefore(startAt)
        val canCancel = registration.status == RegistrationStatus.REGISTERED && !cancelled && notStarted
        return DropInTicketUiState.Content(
            registration = registration,
            session = session,
            qrToken = registration.qrToken.takeIf { noQrReason == null },
            noQrReason = noQrReason,
            labels = labels,
            canCancel = canCancel,
            addParty = addPartyOffer(registration, session, now),
        )
    }

    /** FR-008a: lượt `REGISTERED`, buổi chưa hủy, chưa bắt đầu, cơ sở hoạt động, dưới 4 người và còn chỗ. */
    private fun addPartyOffer(registration: DropInRegistration, session: DropInSession?, now: Instant): AddPartyOffer? {
        if (session == null) return null
        if (registration.status != RegistrationStatus.REGISTERED) return null
        if (session.status == SessionStatus.CANCELLED || !session.venueActive) return null
        if (!now.isBefore(session.startAt)) return null
        val max = minOf(MAX_PARTY - registration.partySize, session.remaining)
        if (max <= 0) return null
        val price = registration.pricePerPerson.takeIf { it > 0 } ?: session.pricePerPerson
        return AddPartyOffer(maxAdditional = max, pricePerPerson = price)
    }

    // ---------- Chỉ đường (AC 3.2) ----------

    fun openDirections() {
        val content = currentContent() ?: return
        val session = content.session ?: return
        viewModelScope.launch {
            val location = try {
                getVenueLocation(session.venueId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            _events.send(
                DropInTicketEvent.OpenDirections(
                    label = session.venueName,
                    location = location,
                    address = session.venueAddress.takeIf { it.isNotBlank() },
                ),
            )
        }
    }

    // ---------- Thêm người (FR-008a) ----------

    /** Mở bottom sheet; chỉ khi vé đang hiện realtime (không offline) và được thêm người. */
    fun startAddParty() {
        if (_addPartyForm.value != null) return
        val content = uiState.value as? DropInTicketUiState.Content ?: return
        val offer = content.addParty ?: return
        // Lần trước gửi lỗi rồi đóng: mở lại với số lượng cũ để thử lại cùng requestId.
        val pendingCount = savedStateHandle.get<Int>(KEY_ADD_COUNT)?.takeIf { it in 1..offer.maxAdditional }
        _addPartyForm.value = AddPartyForm(
            additional = pendingCount ?: 1,
            maxAdditional = offer.maxAdditional,
            pricePerPerson = offer.pricePerPerson,
            paymentMethod = content.registration.paymentMethod,
            currentPartySize = content.registration.partySize,
        )
    }

    fun increaseAdditional() {
        _addPartyForm.value?.let { setAdditional(it.additional + 1) }
    }

    fun decreaseAdditional() {
        _addPartyForm.value?.let { setAdditional(it.additional - 1) }
    }

    /** Đổi số người thêm (giới hạn 1..max); đổi số lượng thì lần gửi sau dùng `requestId` mới. */
    fun setAdditional(value: Int) {
        val form = _addPartyForm.value ?: return
        if (form.submitting) return
        val size = value.coerceIn(1, form.maxAdditional.coerceAtLeast(1))
        if (size == form.additional) return
        clearAddRequestId()
        _addPartyForm.value = form.copy(additional = size, error = null)
    }

    fun dismissAddParty() {
        if (_addPartyForm.value?.submitting == true) return
        // Giữ requestId: mở lại và gửi cùng số lượng vẫn an toàn nếu lần trước thực ra đã thành công.
        _addPartyForm.value = null
    }

    fun submitAddParty() {
        val form = _addPartyForm.value ?: return
        if (form.submitting || form.additional !in 1..form.maxAdditional) return
        val requestId = addRequestIdFor(form.additional)
        _addPartyForm.value = form.copy(submitting = true, error = null)
        viewModelScope.launch {
            val result = try {
                addDropInParty(sessionId, form.additional, requestId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            onAddPartyResult(form, result)
        }
    }

    private suspend fun onAddPartyResult(form: AddPartyForm, result: Result<DropInRegistration>) {
        when (val error = result.exceptionOrNull()) {
            null -> {
                clearAddRequestId()
                _addPartyForm.value = null
                val partySize = result.getOrNull()?.partySize ?: (form.currentPartySize + form.additional)
                _events.send(DropInTicketEvent.PartyAdded(partySize))
            }
            is DropInError.PartyLimit -> limitTo(form, error.maxAdditional, error)
            is DropInError.SessionFull ->
                limitTo(form, minOf(error.remaining, MAX_PARTY - form.currentPartySize), error)
            is DropInError.NotRegistered -> close(error)
            // Network, Busy: giữ requestId để thử lại; lỗi khác: hiện thông báo trong form.
            else -> _addPartyForm.value = form.copy(submitting = false, error = error)
        }
    }

    /** Server báo giới hạn mới: thu hẹp lựa chọn; không còn thêm được ai thì đóng form và báo. */
    private suspend fun limitTo(form: AddPartyForm, max: Int, error: DropInError) {
        if (max <= 0) {
            close(error)
            return
        }
        val size = form.additional.coerceIn(1, max)
        if (size != form.additional) clearAddRequestId()
        _addPartyForm.value = form.copy(additional = size, maxAdditional = max, submitting = false, error = error)
    }

    private suspend fun close(error: Throwable) {
        clearAddRequestId()
        _addPartyForm.value = null
        _events.send(DropInTicketEvent.AddPartyFailed(error))
    }

    /** Cùng số lượng thì dùng lại mã đã sinh (thử lại sau lỗi mạng/bận); khác thì sinh mã mới. */
    private fun addRequestIdFor(additional: Int): String {
        val saved = savedStateHandle.get<String>(KEY_ADD_REQUEST_ID)
        if (saved != null && savedStateHandle.get<Int>(KEY_ADD_COUNT) == additional) return saved
        return UUID.randomUUID().toString().also {
            savedStateHandle[KEY_ADD_REQUEST_ID] = it
            savedStateHandle[KEY_ADD_COUNT] = additional
        }
    }

    private fun clearAddRequestId() {
        savedStateHandle.remove<String>(KEY_ADD_REQUEST_ID)
        savedStateHandle.remove<Int>(KEY_ADD_COUNT)
    }

    private fun currentContent(): DropInTicketUiState.Content? = when (val state = uiState.value) {
        is DropInTicketUiState.Content -> state
        is DropInTicketUiState.Offline -> state.cached
        else -> null
    }

    companion object {
        const val ARG_SESSION_ID = "sessionId"
        const val ARG_JUST_REGISTERED = "justRegistered"
        internal const val KEY_ADD_REQUEST_ID = "dropin_add_party_request_id"
        internal const val KEY_ADD_COUNT = "dropin_add_party_count"

        /** Tối đa người của một lượt (FR-006). */
        const val MAX_PARTY = 4
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}

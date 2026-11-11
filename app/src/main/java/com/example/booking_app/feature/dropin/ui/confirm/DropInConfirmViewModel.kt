package com.example.booking_app.feature.dropin.ui.confirm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.dropin.domain.model.DropInError
import com.example.booking_app.feature.dropin.domain.model.DropInRegistration
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.example.booking_app.feature.dropin.domain.model.DropInWarning
import com.example.booking_app.feature.dropin.domain.model.PaymentMethod
import com.example.booking_app.feature.dropin.domain.model.RegisterRequest
import com.example.booking_app.feature.dropin.domain.usecase.CheckDropInWarnings
import com.example.booking_app.feature.dropin.domain.usecase.ObserveDropInSession
import com.example.booking_app.feature.dropin.domain.usecase.RegisterDropIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Lựa chọn trên màn hình xác nhận; giữ nguyên qua mọi trạng thái để thử lại không mất lựa chọn (AC 2.8). */
data class ConfirmForm(
    val session: DropInSession,
    val partySize: Int,
    /** `min(4, remaining)` (AC 2.2); 0 khi buổi đã hết chỗ. */
    val maxParty: Int,
    val paymentMethod: PaymentMethod,
    val warnings: List<DropInWarning> = emptyList(),
) {
    val total: Long get() = session.pricePerPerson * partySize
    val canSubmit: Boolean get() = maxParty > 0 && partySize in 1..maxParty
}

sealed interface DropInConfirmUiState {
    data object Loading : DropInConfirmUiState

    data class Editing(val form: ConfirmForm) : DropInConfirmUiState

    /** Đang gửi: nút "Xác nhận" khóa. */
    data class Submitting(val form: ConfirmForm) : DropInConfirmUiState

    /** Server từ chối; [remaining] chỉ có với `SESSION_FULL` (AC 2.5). */
    data class Failed(val form: ConfirmForm, val reason: Throwable, val remaining: Int? = null) : DropInConfirmUiState

    data object NotFound : DropInConfirmUiState

    data class LoadError(val cause: Throwable) : DropInConfirmUiState
}

sealed interface DropInConfirmEvent {
    /** Thành công hoặc đã có vé từ trước: mở vé, bỏ màn hình xác nhận khỏi back stack (FR-025). */
    data class NavigateToTicket(val sessionId: String) : DropInConfirmEvent

    /** Mất mạng: Snackbar, lựa chọn vẫn giữ. */
    data object NetworkError : DropInConfirmEvent

    /** Token hết hạn mà làm mới không được: chuyển sang đăng nhập. */
    data class RequireLogin(val sessionId: String) : DropInConfirmEvent
}

/**
 * Xác nhận đăng ký (US2, T037). `requestId` sinh một lần và giữ trong [SavedStateHandle] cùng số người và cách
 * thanh toán, nên xoay màn hình, mất mạng rồi thử lại hay gửi lại khi `BUSY` đều dùng cùng mã (idempotent, AC 2.6).
 */
@HiltViewModel
class DropInConfirmViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val observeSession: ObserveDropInSession,
    private val registerDropIn: RegisterDropIn,
    private val checkWarnings: CheckDropInWarnings,
) : ViewModel() {

    val sessionId: String = checkNotNull(savedStateHandle[ARG_SESSION_ID]) { "Thiếu sessionId" }

    val requestId: String = savedStateHandle.get<String>(KEY_REQUEST_ID)
        ?: UUID.randomUUID().toString().also { savedStateHandle[KEY_REQUEST_ID] = it }

    private val _uiState = MutableStateFlow<DropInConfirmUiState>(DropInConfirmUiState.Loading)
    val uiState: StateFlow<DropInConfirmUiState> = _uiState.asStateFlow()

    private val _events = Channel<DropInConfirmEvent>(Channel.BUFFERED)
    val events: Flow<DropInConfirmEvent> = _events.receiveAsFlow()

    private var warningsRequested = false

    private var sessionJob: Job? = null

    init {
        startObserving()
    }

    /** Nút "Thử lại" của trạng thái [DropInConfirmUiState.LoadError]: đọc lại buổi. */
    fun retryLoad() {
        if (_uiState.value !is DropInConfirmUiState.LoadError) return
        _uiState.value = DropInConfirmUiState.Loading
        startObserving()
    }

    private fun startObserving() {
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            observeSession(sessionId)
                .catch { error -> if (currentForm() == null) _uiState.value = DropInConfirmUiState.LoadError(error) }
                .collect(::onSession)
        }
    }

    fun increment() = setPartySize((currentForm()?.partySize ?: 0) + 1)

    fun decrement() = setPartySize((currentForm()?.partySize ?: 2) - 1)

    fun setPartySize(value: Int) = edit { form ->
        val size = value.coerceIn(MIN_PARTY, form.maxParty.coerceAtLeast(MIN_PARTY))
        savedStateHandle[KEY_PARTY_SIZE] = size
        form.copy(partySize = size)
    }

    fun setPaymentMethod(method: PaymentMethod) = edit { form ->
        savedStateHandle[KEY_PAYMENT] = method.name
        form.copy(paymentMethod = method)
    }

    fun submit() {
        val form = when (val state = _uiState.value) {
            is DropInConfirmUiState.Editing -> state.form
            is DropInConfirmUiState.Failed -> state.form
            else -> return
        }
        if (!form.canSubmit) return
        _uiState.value = DropInConfirmUiState.Submitting(form)
        val request = RegisterRequest(sessionId, form.partySize, form.paymentMethod, requestId)
        viewModelScope.launch {
            var busyRetries = 0
            var result = send(request)
            while (result.exceptionOrNull() is DropInError.Busy && busyRetries < MAX_BUSY_RETRIES) {
                busyRetries++
                delay(BUSY_RETRY_DELAY_MS * busyRetries)
                result = send(request)
            }
            onResult(result)
        }
    }

    private suspend fun onResult(result: Result<DropInRegistration>) {
        val form = currentForm() ?: return
        val error = result.exceptionOrNull()
        when {
            error == null || error is DropInError.AlreadyRegistered ->
                // Giữ Submitting để nút còn khóa cho tới khi rời màn hình.
                _events.send(DropInConfirmEvent.NavigateToTicket(sessionId))
            error is DropInError.SessionFull -> {
                val maxParty = maxPartyFor(error.remaining)
                val size = form.partySize.coerceAtMost(maxParty).coerceAtLeast(MIN_PARTY)
                savedStateHandle[KEY_PARTY_SIZE] = size
                _uiState.value = DropInConfirmUiState.Failed(
                    form.copy(maxParty = maxParty, partySize = size),
                    error,
                    error.remaining,
                )
            }
            error is DropInError.Network -> {
                _uiState.value = DropInConfirmUiState.Editing(form)
                _events.send(DropInConfirmEvent.NetworkError)
            }
            error is DropInError.Unauthenticated -> {
                _uiState.value = DropInConfirmUiState.Editing(form)
                _events.send(DropInConfirmEvent.RequireLogin(sessionId))
            }
            else -> _uiState.value = DropInConfirmUiState.Failed(form, error)
        }
    }

    private suspend fun send(request: RegisterRequest): Result<DropInRegistration> =
        try {
            registerDropIn(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private fun onSession(session: DropInSession?) {
        val current = currentForm()
        if (session == null) {
            if (current == null) _uiState.value = DropInConfirmUiState.NotFound
            return
        }
        val maxParty = maxPartyFor(session.remaining)
        val wanted = current?.partySize ?: savedStateHandle.get<Int>(KEY_PARTY_SIZE) ?: MIN_PARTY
        val form = ConfirmForm(
            session = session,
            partySize = wanted.coerceAtMost(maxParty).coerceAtLeast(MIN_PARTY),
            maxParty = maxParty,
            paymentMethod = current?.paymentMethod ?: savedPayment(),
            warnings = current?.warnings.orEmpty(),
        )
        replaceForm(form)
        if (!warningsRequested) {
            warningsRequested = true
            viewModelScope.launch { loadWarnings(session) }
        }
    }

    private suspend fun loadWarnings(session: DropInSession) {
        val warnings = try {
            checkWarnings(session)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        if (warnings.isNotEmpty()) currentForm()?.let { replaceForm(it.copy(warnings = warnings)) }
    }

    /** Sửa lựa chọn; đang gửi thì bỏ qua. Sau khi bị từ chối, sửa lựa chọn đưa màn hình về [Editing]. */
    private fun edit(change: (ConfirmForm) -> ConfirmForm) {
        val form = when (val state = _uiState.value) {
            is DropInConfirmUiState.Editing -> state.form
            is DropInConfirmUiState.Failed -> state.form
            else -> return
        }
        _uiState.value = DropInConfirmUiState.Editing(change(form))
    }

    /** Thay form nhưng giữ loại trạng thái hiện tại (đang gửi, đã bị từ chối). */
    private fun replaceForm(form: ConfirmForm) {
        _uiState.value = when (val state = _uiState.value) {
            is DropInConfirmUiState.Submitting -> state.copy(form = form)
            is DropInConfirmUiState.Failed -> state.copy(form = form)
            else -> DropInConfirmUiState.Editing(form)
        }
    }

    private fun currentForm(): ConfirmForm? = when (val state = _uiState.value) {
        is DropInConfirmUiState.Editing -> state.form
        is DropInConfirmUiState.Submitting -> state.form
        is DropInConfirmUiState.Failed -> state.form
        else -> null
    }

    private fun savedPayment(): PaymentMethod =
        savedStateHandle.get<String>(KEY_PAYMENT)
            ?.let { name -> PaymentMethod.entries.firstOrNull { it.name == name } }
            ?: PaymentMethod.MOCK_PAY

    private fun maxPartyFor(remaining: Int): Int = remaining.coerceIn(0, MAX_PARTY)

    companion object {
        const val ARG_SESSION_ID = "sessionId"
        internal const val KEY_REQUEST_ID = "dropin_request_id"
        internal const val KEY_PARTY_SIZE = "dropin_party_size"
        internal const val KEY_PAYMENT = "dropin_payment_method"
        const val MIN_PARTY = 1
        const val MAX_PARTY = 4

        /** `BUSY`: tự gửi lại tối đa 2 lần (contracts/edge-functions-dropin.md mục 4). */
        const val MAX_BUSY_RETRIES = 2
        private const val BUSY_RETRY_DELAY_MS = 500L
    }
}

package com.example.booking_app.feature.account.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.session.AppMode
import com.example.booking_app.core.session.AppModeController
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.MyProfile
import com.example.booking_app.core.session.SessionAuthSource
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.session.UserProfileReader
import com.example.booking_app.feature.account.domain.DistrictRepository
import com.example.booking_app.feature.account.domain.OwnerApplicationRepository
import com.example.booking_app.feature.account.domain.OwnerSectionResolver
import com.example.booking_app.feature.account.domain.model.AccountError
import com.example.booking_app.feature.account.domain.model.OwnerApplicationSummary
import com.example.booking_app.feature.account.domain.model.OwnerSection
import com.example.booking_app.feature.account.domain.model.PlayerLevel
import com.example.booking_app.feature.account.domain.usecase.SwitchModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AccountUiState {
    data object Loading : AccountUiState

    data class Error(val error: AppError) : AccountUiState

    data class Content(
        val profile: MyProfile,
        val appRole: AppRole,
        val entries: Set<AccountEntry>,
        /** Tên khu vực theo thứ tự `favoriteDistrictIds`; ID không còn trong danh mục thì bỏ qua. */
        val districtNames: List<String> = emptyList(),
        val level: PlayerLevel? = null,
        /** Số dư ví đã định dạng, ví dụ `50.000` (chuỗi `account_wallet_balance` thêm " đ"). */
        val walletBalance: String = "0",
        val showVerifyBanner: Boolean = false,
        val ownerSection: OwnerSection = OwnerSection.Hidden,
        /** Lối sang màn hình chủ sân đã có destination (R14). */
        val ownerLinks: Set<OwnerLink> = emptySet(),
        /** Chế độ đang dùng; `null` là tài khoản không phải `OWNER`, không có khối chuyển chế độ (US4-3). */
        val mode: AppMode? = null,
    ) : AccountUiState
}

sealed interface AccountEvent {
    data class Open(val entry: AccountEntry) : AccountEvent

    /** Hỏi lại trước khi đăng xuất (FR-007). */
    data object ConfirmSignOut : AccountEvent

    /** Mở màn hình của tính năng khác bằng deep link (R14). */
    data class OpenLink(val deepLink: String) : AccountEvent

    /** Bấm "Quản lý sân" khi quyền đã bị thu hồi (US4-4, FR-012). */
    data class OwnerRevoked(val reason: String?) : AccountEvent

    data object ModeSwitchFailed : AccountEvent
}

/**
 * Trang Tài khoản (contracts/ui-screens.md §3). Hồ sơ đọc từ listener `users/{uid}` của 010; listener không báo lỗi
 * (mất mạng thì chỉ chờ) nên quá [LOAD_TIMEOUT_MS] chưa có hồ sơ thì coi là lỗi để hiện "Thử lại" (FR-025).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val authSource: SessionAuthSource,
    private val profileReader: UserProfileReader,
    private val districtRepository: DistrictRepository,
    private val ownerApplications: OwnerApplicationRepository,
    private val appModeController: AppModeController,
    private val switchMode: SwitchModeUseCase,
) : ViewModel() {

    private val retryCount = MutableStateFlow(0)
    private val availableDeepLinks = MutableStateFlow<Set<String>>(emptySet())

    private val _isSigningOut = MutableStateFlow(false)

    /** Khóa nút "Đăng xuất" trong lúc chạy `SignOutHook` (tối đa vài giây khi mạng yếu). */
    val isSigningOut: StateFlow<Boolean> = _isSigningOut

    private val _isSwitchingMode = MutableStateFlow(false)

    /** Khóa khối chuyển chế độ trong lúc làm mới token (tối đa 3 giây, R7). */
    val isSwitchingMode: StateFlow<Boolean> = _isSwitchingMode

    val uiState: StateFlow<AccountUiState> = retryCount
        .flatMapLatest { loadState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AccountUiState.Loading)

    private val _events = Channel<AccountEvent>(Channel.BUFFERED)
    val events: Flow<AccountEvent> = _events.receiveAsFlow()

    /** Fragment truyền các deep link có destination trong graph gốc (`navController.graph.hasDeepLink`, R14). */
    fun setAvailableDeepLinks(links: Set<String>) {
        availableDeepLinks.value = links
    }

    fun retry() {
        retryCount.value++
    }

    fun onEntryClick(entry: AccountEntry) {
        viewModelScope.launch { _events.send(AccountEvent.Open(entry)) }
    }

    fun onSignOutClick() {
        viewModelScope.launch { _events.send(AccountEvent.ConfirmSignOut) }
    }

    /** Nút trong mục chủ sân: "Sửa và nộp lại" khi bị từ chối, "Đăng ký làm chủ sân" khi chưa nộp hoặc bị thu hồi. */
    fun onOwnerActionClick() {
        val link = when ((uiState.value as? AccountUiState.Content)?.ownerSection) {
            is OwnerSection.Rejected -> OwnerLink.RESUBMIT
            OwnerSection.CanApply, is OwnerSection.Revoked -> OwnerLink.APPLY_NEW
            else -> return
        }
        onOwnerLinkClick(link)
    }

    fun onOwnerLinkClick(link: OwnerLink) {
        viewModelScope.launch { _events.send(AccountEvent.OpenLink(link.deepLink)) }
    }

    /**
     * Chọn chế độ trên toggle. Thành công thì `MainActivity` đổi graph theo `AppModeController.mode`; bị thu hồi quyền
     * thì `mode` giữ `PLAYER` nên toggle tự quay về "Người chơi" và hiện lý do (FR-012).
     */
    fun onModeSelected(target: AppMode) {
        val content = uiState.value as? AccountUiState.Content ?: return
        if (content.mode == null || content.mode == target || _isSwitchingMode.value) return
        _isSwitchingMode.value = true
        viewModelScope.launch {
            try {
                switchMode(target, content.profile.ownerRevokedReason).onFailure { error ->
                    val latestReason = (uiState.value as? AccountUiState.Content)?.profile?.ownerRevokedReason
                    _events.send(
                        if (error is AccountError.OwnerRevoked) AccountEvent.OwnerRevoked(latestReason ?: error.reason)
                        else AccountEvent.ModeSwitchFailed,
                    )
                }
            } finally {
                _isSwitchingMode.value = false
            }
        }
    }

    /**
     * Gọi `SessionManager.signOut()` đúng một lần dù bấm lặp. `MainActivity` (T018) tự đưa về `nav_player` chế độ
     * khách và xóa back stack khi `sessionState` thành `SignedOut` (FR-007, 010 R15).
     */
    fun signOut() {
        if (_isSigningOut.value) return
        _isSigningOut.value = true
        viewModelScope.launch {
            try {
                sessionManager.signOut()
            } finally {
                _isSigningOut.value = false
            }
        }
    }

    private fun loadState(): Flow<AccountUiState> = combine(
        profileOrTimeout(),
        sessionManager.sessionState,
        authSource.authState,
        availableDeepLinks,
        combine(latestOwnerApplication(), appModeController.mode, ::Pair),
    ) { profile, session, user, links, (application, mode) ->
        when {
            profile is ProfileLoad.Failed -> AccountUiState.Error(profile.error)
            profile !is ProfileLoad.Loaded || session !is SessionState.SignedIn -> AccountUiState.Loading
            else -> {
                val providers = user?.providers.orEmpty()
                AccountUiState.Content(
                    profile = profile.profile,
                    appRole = session.appRole,
                    entries = accountEntries(providers, session.appRole) { it in links },
                    districtNames = profile.districtNames,
                    level = PlayerLevel.fromValue(profile.profile.level),
                    walletBalance = formatVnd(profile.profile.walletBalance),
                    showVerifyBanner = PASSWORD_PROVIDER in providers && !session.emailVerified,
                    ownerSection = OwnerSectionResolver.resolve(
                        session.appRole, profile.profile.ownerStatus, profile.profile.ownerRevokedReason, application,
                    ),
                    ownerLinks = OwnerLink.entries.filterTo(LinkedHashSet()) { it.deepLink in links },
                    mode = mode.takeIf { session.appRole == AppRole.OWNER },
                )
            }
        }
    }

    private fun profileOrTimeout(): Flow<ProfileLoad> = profileReader.observeMyProfile().transformLatest { profile ->
        if (profile != null) {
            emit(ProfileLoad.Loaded(profile, districtNames(profile.favoriteDistrictIds)))
        } else {
            emit(ProfileLoad.Loading)
            delay(LOAD_TIMEOUT_MS)
            emit(ProfileLoad.Failed(AppError.Timeout))
        }
    }

    /**
     * Hồ sơ chủ sân gần nhất, chỉ mở listener khi `ownerStatus` là `PENDING`/`REJECTED` (R6) và đóng khi đổi trạng thái.
     * Đọc lại `observeMyProfile()` thay vì chia sẻ với [profileOrTimeout]: Firestore gộp hai listener cùng document.
     * Rules chặn hoặc mất mạng thì vẫn hiện trạng thái, chỉ thiếu ngày nộp và lý do từ chối.
     */
    private fun latestOwnerApplication(): Flow<OwnerApplicationSummary?> = profileReader.observeMyProfile()
        .map { it != null && OwnerSectionResolver.needsLatestApplication(it.ownerStatus) }
        .distinctUntilChanged()
        .flatMapLatest { needed ->
            if (!needed) flowOf(null)
            else ownerApplications.observeLatest().onStart { emit(null) }.catch { emit(null) }
        }

    /** Danh mục lỗi thì chỉ thiếu chip khu vực, trang vẫn hiện (không chặn cả trang). */
    private suspend fun districtNames(ids: List<String>): List<String> {
        if (ids.isEmpty()) return emptyList()
        val byId = try {
            districtRepository.districtsByIds(ids)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyMap()
        }
        return ids.mapNotNull { byId[it]?.name }
    }

    private sealed interface ProfileLoad {
        data object Loading : ProfileLoad
        data class Loaded(val profile: MyProfile, val districtNames: List<String>) : ProfileLoad
        data class Failed(val error: AppError) : ProfileLoad
    }

    private companion object {
        const val PASSWORD_PROVIDER = "password"
        const val LOAD_TIMEOUT_MS = 15_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

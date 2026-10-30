package com.example.booking_app.core.session

import com.example.booking_app.core.di.ApplicationScope
import com.example.booking_app.core.network.AccountDeletedBus
import com.example.booking_app.core.network.AccountLockedBus
import com.example.booking_app.core.network.AppError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

private const val CLAIM_APP_ROLE = "appRole"
private const val CLAIM_EMAIL_VERIFIED = "email_verified"
private const val STATUS_LOCKED = "LOCKED"
private const val SIGN_OUT_HOOK_TIMEOUT_MS = 5_000L

/**
 * Máy trạng thái phiên theo research R4 (claim `appRole`), R5 (khóa tài khoản), R6 (đổi vai trò)
 * và R15 (chế độ khách). Spec 011 thêm `SignOutHook`, lý do `SESSION_REVOKED`, `ACCOUNT_DELETED` (R1).
 */
@Singleton
class FirebaseSessionManager @Inject constructor(
    private val authSource: SessionAuthSource,
    private val prefs: SessionPrefs,
    private val profileReader: UserProfileReader,
    private val lockedBus: AccountLockedBus,
    @ApplicationScope private val scope: CoroutineScope,
    // [011] Có giá trị mặc định để test cũ của 010 không phải sửa.
    private val signOutHooks: Set<@JvmSuppressWildcards SignOutHook> = emptySet(),
    private val deletedBus: AccountDeletedBus = AccountDeletedBus(),
) : SessionManager {

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Loading)
    override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    override val isGuest: StateFlow<Boolean> = _sessionState
        .map { it is SessionState.SignedOut }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val mutex = Mutex()

    @Volatile
    private var currentUser: AuthUser? = null

    /** Lý do của lần đăng xuất gần nhất, gắn vào `SignedOut` khi Firebase báo không còn người dùng. */
    @Volatile
    private var signOutReason: SignOutReason? = null

    init {
        scope.launch {
            authSource.authState.collectLatest { user ->
                currentUser = user
                if (user == null) {
                    _sessionState.value = SessionState.SignedOut(signOutReason)
                    return@collectLatest
                }
                signOutReason = null
                evaluate(user, forceTokenRefresh = true, reload = true)
                profileReader.observeMyProfile().collect { onProfile(it) }
            }
        }
        scope.launch {
            lockedBus.events.collect { if (currentUser != null) signOutWithoutHooks(SignOutReason.LOCKED) }
        }
        scope.launch {
            deletedBus.events.collect { if (currentUser != null) signOut(SignOutReason.ACCOUNT_DELETED) }
        }
    }

    override suspend fun refresh(forceTokenRefresh: Boolean) {
        val user = currentUser ?: return
        evaluate(user, forceTokenRefresh, reload = false)
    }

    override suspend fun signOut() = signOut(reason = null)

    override suspend fun signOut(reason: SignOutReason?) {
        // Token đã chết khi tài khoản bị xóa: hook không ghi được gì (spec 011 R11).
        if (reason != SignOutReason.ACCOUNT_DELETED) runSignOutHooks()
        signOutReason = reason
        prefs.clearOnSignOut()
        authSource.signOut()
        _sessionState.value = SessionState.SignedOut(reason)
    }

    /** Mỗi hook tối đa 5 giây, lỗi bị bỏ qua, chạy trước `FirebaseAuth.signOut()` (spec 011 R1). */
    private suspend fun runSignOutHooks() {
        val uid = currentUser?.uid ?: return
        for (hook in signOutHooks) {
            try {
                withTimeout(SIGN_OUT_HOOK_TIMEOUT_MS) { hook.beforeSignOut(uid) }
            } catch (e: Exception) {
                // Hủy từ bên ngoài thì dừng; hết giờ hoặc lỗi của hook thì chạy tiếp hook khác.
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
            }
        }
    }

    override fun currentUid(): String? = (sessionState.value as? SessionState.SignedIn)?.uid

    override fun currentAppRole(): AppRole? = (sessionState.value as? SessionState.SignedIn)?.appRole

    private suspend fun evaluate(user: AuthUser, forceTokenRefresh: Boolean, reload: Boolean) = mutex.withLock {
        val claims = try {
            if (reload) authSource.reload()
            authSource.getClaims(forceTokenRefresh)
        } catch (e: AppError) {
            when (e) {
                AppError.Locked -> {
                    signOutWithoutHooks(SignOutReason.LOCKED)
                    return@withLock
                }
                // Phiên bị thu hồi: token không còn dùng được nên không chạy hook (spec 011 R9).
                AppError.SessionRevoked -> {
                    signOutWithoutHooks(SignOutReason.SESSION_REVOKED)
                    return@withLock
                }
                // Mất mạng: dùng claim của token đã lưu để vẫn vào được app (FR-013).
                AppError.Network, AppError.Timeout -> try {
                    authSource.getClaims(forceRefresh = false)
                } catch (cached: AppError) {
                    _sessionState.value = SessionState.InitFailed(cached)
                    return@withLock
                }
                else -> {
                    _sessionState.value = SessionState.InitFailed(e)
                    return@withLock
                }
            }
        }

        val appRole = AppRole.fromClaim(claims[CLAIM_APP_ROLE] as? String)
        _sessionState.value = if (appRole != null) {
            val emailVerified = claims[CLAIM_EMAIL_VERIFIED] as? Boolean ?: user.isEmailVerified
            SessionState.SignedIn(user.uid, appRole, emailVerified)
        } else {
            val pending = prefs.pendingSignup()
            if (pending?.uid == user.uid) {
                SessionState.Initializing
            } else {
                if (pending != null) prefs.clearPendingSignup()
                SessionState.NeedsConsent(user.toPrefill())
            }
        }
    }

    private suspend fun onProfile(profile: MyProfile?) {
        val state = sessionState.value as? SessionState.SignedIn ?: return
        if (profile == null) return
        if (profile.accountStatus == STATUS_LOCKED) {
            signOutWithoutHooks(SignOutReason.LOCKED)
            return
        }
        val documentRole = AppRole.fromClaim(profile.role)
        if (documentRole != null && documentRole != state.appRole) {
            // D ghi claim trước rồi mới ghi document (R6), nên làm mới token là thấy vai trò mới.
            val user = currentUser ?: return
            evaluate(user, forceTokenRefresh = true, reload = false)
        }
    }

    private suspend fun signOutWithoutHooks(reason: SignOutReason) = withContext(NonCancellable) {
        signOutReason = reason
        _sessionState.value = SessionState.SignedOut(reason)
        prefs.clearOnSignOut()
        authSource.signOut()
    }

    private fun AuthUser.toPrefill() = ConsentPrefill(
        provider = when {
            "google.com" in providers -> ConsentPrefill.Provider.GOOGLE
            "phone" in providers -> ConsentPrefill.Provider.PHONE
            else -> ConsentPrefill.Provider.PASSWORD
        },
        email = email,
        phone = phone,
        displayName = displayName,
    )
}

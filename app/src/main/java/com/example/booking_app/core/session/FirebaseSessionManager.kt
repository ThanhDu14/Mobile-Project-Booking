package com.example.booking_app.core.session

import com.example.booking_app.core.di.ApplicationScope
import com.example.booking_app.core.network.AccountLockedBus
import com.example.booking_app.core.network.AppError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
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
import javax.inject.Inject
import javax.inject.Singleton

private const val CLAIM_APP_ROLE = "appRole"
private const val CLAIM_EMAIL_VERIFIED = "email_verified"
private const val STATUS_LOCKED = "LOCKED"

/**
 * Máy trạng thái phiên theo research R4 (claim `appRole`), R5 (khóa tài khoản), R6 (đổi vai trò)
 * và R15 (chế độ khách).
 */
@Singleton
class FirebaseSessionManager @Inject constructor(
    private val authSource: SessionAuthSource,
    private val prefs: SessionPrefs,
    private val profileReader: UserProfileReader,
    private val lockedBus: AccountLockedBus,
    @ApplicationScope private val scope: CoroutineScope,
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
            lockedBus.events.collect { if (currentUser != null) signOutLocked() }
        }
    }

    override suspend fun refresh(forceTokenRefresh: Boolean) {
        val user = currentUser ?: return
        evaluate(user, forceTokenRefresh, reload = false)
    }

    override suspend fun signOut() {
        signOutReason = null
        prefs.clearOnSignOut()
        authSource.signOut()
        _sessionState.value = SessionState.SignedOut(reason = null)
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
                    signOutLocked()
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
            signOutLocked()
            return
        }
        val documentRole = AppRole.fromClaim(profile.role)
        if (documentRole != null && documentRole != state.appRole) {
            // D ghi claim trước rồi mới ghi document (R6), nên làm mới token là thấy vai trò mới.
            val user = currentUser ?: return
            evaluate(user, forceTokenRefresh = true, reload = false)
        }
    }

    private suspend fun signOutLocked() = withContext(NonCancellable) {
        signOutReason = SignOutReason.LOCKED
        _sessionState.value = SessionState.SignedOut(SignOutReason.LOCKED)
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

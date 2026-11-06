package com.example.booking_app.feature.account.data

import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.session.UserProfileReader
import com.example.booking_app.feature.account.domain.usecase.SyncAccountEmailUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sau khi người dùng bấm link đổi email và đăng nhập lại, email trong Firebase Auth khác `users.email`: gọi
 * `sync-account-email` để server ghi lại (US7, research R10). Mỗi giá trị email chỉ gọi một lần; lỗi thì bỏ qua,
 * lần đăng nhập sau thử lại. Khởi động từ `SmashNowApp`.
 */
@Singleton
class EmailSyncObserver @Inject constructor(
    private val sessionManager: SessionManager,
    private val profileReader: UserProfileReader,
    private val reauthDataSource: ReauthDataSource,
    private val syncAccountEmail: SyncAccountEmailUseCase,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope): Job = sessionManager.sessionState
        .flatMapLatest { state ->
            if (state !is SessionState.SignedIn) {
                emptyFlow()
            } else {
                profileReader.observeMyProfile().filterNotNull().map { profile ->
                    val authEmail = reauthDataSource.currentAccount()?.email?.trim()?.lowercase()
                    authEmail?.takeIf { it != profile.email?.lowercase() }
                }
            }
        }
        .distinctUntilChanged()
        .filterNotNull()
        .onEach { sync() }
        .launchIn(scope)

    private suspend fun sync() {
        try {
            syncAccountEmail()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Không chặn người dùng; email trên hồ sơ chỉ để hiển thị.
        }
    }
}

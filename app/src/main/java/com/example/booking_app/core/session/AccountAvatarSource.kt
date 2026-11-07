package com.example.booking_app.core.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

/** Nguồn trạng thái avatar cho header dùng chung (spec 011 FR-028). Tính năng khác inject interface này. */
interface AccountAvatarSource {
    val state: Flow<AccountAvatarState>
}

/** Gộp phiên đăng nhập và hồ sơ: đổi ảnh, đổi tên, đăng xuất đều phát trạng thái mới. */
class DefaultAccountAvatarSource @Inject constructor(
    private val sessionManager: SessionManager,
    private val profileReader: UserProfileReader,
) : AccountAvatarSource {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val state: Flow<AccountAvatarState> = sessionManager.sessionState
        .map { it is SessionState.SignedIn }
        .distinctUntilChanged()
        .flatMapLatest { signedIn ->
            if (!signedIn) {
                flowOf(AccountAvatarState.Guest)
            } else {
                profileReader.observeMyProfile()
                    .map { profile ->
                        if (profile == null) {
                            AccountAvatarState.Pending
                        } else {
                            AccountAvatarState.SignedIn(initialsOf(profile.displayName), profile.avatarPath)
                        }
                    }
                    .onStart { emit(AccountAvatarState.Pending) }
            }
        }
        .distinctUntilChanged()
}

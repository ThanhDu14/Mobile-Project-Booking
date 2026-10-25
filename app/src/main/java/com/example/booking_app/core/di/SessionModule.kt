package com.example.booking_app.core.di

import com.example.booking_app.core.network.FirebaseIdTokenProvider
import com.example.booking_app.core.network.IdTokenProvider
import com.example.booking_app.core.session.AppModeController
import com.example.booking_app.core.session.AuthNavigator
import com.example.booking_app.core.session.DataStoreSessionPrefs
import com.example.booking_app.core.session.DefaultAppModeController
import com.example.booking_app.core.session.DeepLinkAuthNavigator
import com.example.booking_app.core.session.FcmSignOutHook
import com.example.booking_app.core.session.FcmTokenSource
import com.example.booking_app.core.session.FcmTokenStore
import com.example.booking_app.core.session.FirebaseFcmTokenSource
import com.example.booking_app.core.session.FirebaseSessionAuthSource
import com.example.booking_app.core.session.FirebaseSessionManager
import com.example.booking_app.core.session.FirestoreFcmTokenStore
import com.example.booking_app.core.session.FirestoreUserProfileReader
import com.example.booking_app.core.session.GoogleSignOutHook
import com.example.booking_app.core.session.SessionAuthSource
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.SessionPrefs
import com.example.booking_app.core.session.SignOutHook
import com.example.booking_app.core.session.UserProfileReader
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {

    @Binds
    abstract fun bindSessionManager(impl: FirebaseSessionManager): SessionManager

    @Binds
    abstract fun bindIdTokenProvider(impl: FirebaseIdTokenProvider): IdTokenProvider

    @Binds
    abstract fun bindUserProfileReader(impl: FirestoreUserProfileReader): UserProfileReader

    @Binds
    abstract fun bindSessionAuthSource(impl: FirebaseSessionAuthSource): SessionAuthSource

    @Binds
    abstract fun bindSessionPrefs(impl: DataStoreSessionPrefs): SessionPrefs

    @Binds
    abstract fun bindAuthNavigator(impl: DeepLinkAuthNavigator): AuthNavigator

    // [011] research R7
    @Binds
    abstract fun bindAppModeController(impl: DefaultAppModeController): AppModeController

    /**
     * [011] Tập `SignOutHook` (có thể rỗng). Tính năng cần dọn dữ liệu khi đăng xuất thì thêm
     * `@Binds @IntoSet abstract fun bind...(impl: ...): SignOutHook` trong module của mình.
     */
    @Multibinds
    abstract fun signOutHooks(): Set<SignOutHook>

    // [011] research R5, T054
    @Binds
    @IntoSet
    abstract fun bindFcmSignOutHook(impl: FcmSignOutHook): SignOutHook

    @Binds
    @IntoSet
    abstract fun bindGoogleSignOutHook(impl: GoogleSignOutHook): SignOutHook

    @Binds
    abstract fun bindFcmTokenSource(impl: FirebaseFcmTokenSource): FcmTokenSource

    @Binds
    abstract fun bindFcmTokenStore(impl: FirestoreFcmTokenStore): FcmTokenStore
}

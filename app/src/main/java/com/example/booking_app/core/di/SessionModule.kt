package com.example.booking_app.core.di

import com.example.booking_app.core.network.FirebaseIdTokenProvider
import com.example.booking_app.core.network.IdTokenProvider
import com.example.booking_app.core.session.AuthNavigator
import com.example.booking_app.core.session.DataStoreSessionPrefs
import com.example.booking_app.core.session.DeepLinkAuthNavigator
import com.example.booking_app.core.session.FirebaseSessionAuthSource
import com.example.booking_app.core.session.FirebaseSessionManager
import com.example.booking_app.core.session.FirestoreUserProfileReader
import com.example.booking_app.core.session.SessionAuthSource
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.SessionPrefs
import com.example.booking_app.core.session.UserProfileReader
import dagger.Binds
import dagger.Module
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
}

package com.example.booking_app.feature.auth.data.di

import com.example.booking_app.core.session.VerificationEmailSender
import com.example.booking_app.feature.auth.data.AuthRepositoryImpl
import com.example.booking_app.feature.auth.data.CredentialManagerGoogleDataSource
import com.example.booking_app.feature.auth.data.GoogleCredentialDataSource
import com.example.booking_app.feature.auth.data.OnSignupApi
import com.example.booking_app.feature.auth.data.SupabaseOnSignupApi
import com.example.booking_app.feature.auth.domain.AuthRepository
import com.example.booking_app.feature.auth.domain.usecase.ResendVerificationEmailUseCase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    abstract fun bindOnSignupApi(impl: SupabaseOnSignupApi): OnSignupApi

    @Binds
    abstract fun bindGoogleCredentialDataSource(impl: CredentialManagerGoogleDataSource): GoogleCredentialDataSource

    /** Banner xác minh email ở `core/ui` gửi lại email qua interface này. */
    @Binds
    abstract fun bindVerificationEmailSender(impl: ResendVerificationEmailUseCase): VerificationEmailSender
}

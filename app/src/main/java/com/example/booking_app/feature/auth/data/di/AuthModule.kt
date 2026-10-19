package com.example.booking_app.feature.auth.data.di

import com.example.booking_app.feature.auth.data.AuthRepositoryImpl
import com.example.booking_app.feature.auth.data.OnSignupApi
import com.example.booking_app.feature.auth.data.SupabaseOnSignupApi
import com.example.booking_app.feature.auth.domain.AuthRepository
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
}

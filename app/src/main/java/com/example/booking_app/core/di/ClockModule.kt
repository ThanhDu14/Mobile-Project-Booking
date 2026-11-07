package com.example.booking_app.core.di

import com.example.booking_app.core.util.AppClock
import com.example.booking_app.core.util.SystemAppClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ClockModule {

    @Binds
    abstract fun bindClock(impl: SystemAppClock): AppClock
}

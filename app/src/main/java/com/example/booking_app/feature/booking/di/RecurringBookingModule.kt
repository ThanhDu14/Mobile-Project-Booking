package com.example.booking_app.feature.booking.di

import com.example.booking_app.feature.booking.data.datasource.FirestoreRecurringBookingRemoteDataSource
import com.example.booking_app.feature.booking.data.datasource.RecurringBookingRemoteDataSource
import com.example.booking_app.feature.booking.data.repository.RecurringBookingRepositoryImpl
import com.example.booking_app.feature.booking.domain.repository.RecurringBookingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RecurringBookingModule {

    @Binds
    @Singleton
    abstract fun bindRecurringBookingRemoteDataSource(
        impl: FirestoreRecurringBookingRemoteDataSource
    ): RecurringBookingRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindRecurringBookingRepository(
        impl: RecurringBookingRepositoryImpl
    ): RecurringBookingRepository
}

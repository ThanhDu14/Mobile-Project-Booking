package com.example.booking_app.feature.booking.di

import com.example.booking_app.feature.booking.data.datasource.BookingRemoteDataSource
import com.example.booking_app.feature.booking.data.datasource.FirestoreBookingRemoteDataSource
import com.example.booking_app.feature.booking.data.repository.BookingRepositoryImpl
import com.example.booking_app.feature.booking.domain.repository.BookingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BookingModule {

    @Binds
    @Singleton
    abstract fun bindBookingRemoteDataSource(
        impl: FirestoreBookingRemoteDataSource
    ): BookingRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindBookingRepository(
        impl: BookingRepositoryImpl
    ): BookingRepository
}

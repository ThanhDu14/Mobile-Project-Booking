package com.example.booking_app.feature.booking_management.data

import com.example.booking_app.feature.booking_management.domain.BookingManagementRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BookingManagementModule {
    @Binds
    abstract fun bindBookingManagementRepository(impl: FirestoreBookingManagementRepository): BookingManagementRepository
}

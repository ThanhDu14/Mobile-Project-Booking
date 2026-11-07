package com.example.booking_app.feature.owner.di

import com.example.booking_app.feature.owner.data.ManageSlotBlocksApi
import com.example.booking_app.feature.owner.data.OwnerBookingRepositoryImpl
import com.example.booking_app.feature.owner.data.BookingCheckInRepositoryImpl
import com.example.booking_app.feature.owner.data.OwnerDropInRepositoryImpl
import com.example.booking_app.feature.owner.data.ContractPendingOwnerVoucherRepository
import com.example.booking_app.feature.owner.data.FirestoreOwnerStatisticsRepository
import com.example.booking_app.feature.owner.data.OwnerOnboardingDataSource
import com.example.booking_app.feature.owner.data.OwnerVenueRepositoryImpl
import com.example.booking_app.feature.owner.data.FirestoreOwnerCalendarRepository
import com.example.booking_app.feature.owner.data.SupabaseManageSlotBlocksApi
import com.example.booking_app.feature.owner.data.SupabaseSyncVenuePriceApi
import com.example.booking_app.feature.owner.data.SyncVenuePriceApi
import com.example.booking_app.feature.owner.domain.OwnerVenueRepository
import com.example.booking_app.feature.owner.domain.calendar.OwnerCalendarRepository
import com.example.booking_app.feature.owner.domain.OwnerOnboardingRepository
import com.example.booking_app.feature.owner.domain.booking.OwnerBookingRepository
import com.example.booking_app.feature.owner.domain.booking.BookingCheckInRepository
import com.example.booking_app.feature.owner.domain.dropin.OwnerDropInRepository
import com.example.booking_app.feature.owner.domain.voucher.OwnerVoucherRepository
import com.example.booking_app.feature.owner.domain.statistics.OwnerStatisticsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OwnerModule {
    @Binds
    @Singleton
    abstract fun bindOwnerOnboardingRepository(impl: OwnerOnboardingDataSource): OwnerOnboardingRepository

    @Binds
    @Singleton
    abstract fun bindOwnerVenueRepository(impl: OwnerVenueRepositoryImpl): OwnerVenueRepository

    @Binds
    @Singleton
    abstract fun bindOwnerCalendarRepository(impl: FirestoreOwnerCalendarRepository): OwnerCalendarRepository

    @Binds
    @Singleton
    abstract fun bindOwnerBookingRepository(impl: OwnerBookingRepositoryImpl): OwnerBookingRepository

    @Binds
    @Singleton
    abstract fun bindBookingCheckInRepository(impl: BookingCheckInRepositoryImpl): BookingCheckInRepository

    @Binds
    @Singleton
    abstract fun bindOwnerDropInRepository(impl: OwnerDropInRepositoryImpl): OwnerDropInRepository

    @Binds
    @Singleton
    abstract fun bindOwnerVoucherRepository(impl: ContractPendingOwnerVoucherRepository): OwnerVoucherRepository

    @Binds
    @Singleton
    abstract fun bindOwnerStatisticsRepository(impl: FirestoreOwnerStatisticsRepository): OwnerStatisticsRepository

    @Binds
    @Singleton
    abstract fun bindSyncVenuePriceApi(impl: SupabaseSyncVenuePriceApi): SyncVenuePriceApi

    @Binds
    @Singleton
    abstract fun bindManageSlotBlocksApi(impl: SupabaseManageSlotBlocksApi): ManageSlotBlocksApi
}

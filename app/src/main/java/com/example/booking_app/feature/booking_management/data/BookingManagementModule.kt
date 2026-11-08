package com.example.booking_app.feature.booking_management.data

import com.example.booking_app.feature.booking_management.domain.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BookingManagementModule {
    @Binds
    abstract fun bindBookingManagementRepository(impl: FirestoreBookingManagementRepository): BookingManagementRepository

    companion object {
        @Provides
        @Singleton
        fun provideCalculateRefundQuoteUseCase(): CalculateRefundQuoteUseCase =
            CalculateRefundQuoteUseCase()

        @Provides
        @Singleton
        fun provideGenerateQrCodeUseCase(): GenerateQrCodeUseCase =
            GenerateQrCodeUseCase()

        @Provides
        @Singleton
        fun provideCancelBookingUseCase(repository: BookingManagementRepository): CancelBookingUseCase =
            CancelBookingUseCase(repository)

        @Provides
        @Singleton
        fun provideCancelRemainingGroupUseCase(repository: BookingManagementRepository): CancelRemainingGroupUseCase =
            CancelRemainingGroupUseCase(repository)

        @Provides
        @Singleton
        fun provideGetBookingPageUseCase(repository: BookingManagementRepository): GetBookingPageUseCase =
            GetBookingPageUseCase(repository)

        @Provides
        @Singleton
        fun provideObserveBookingUseCase(repository: BookingManagementRepository): ObserveBookingUseCase =
            ObserveBookingUseCase(repository)

        @Provides
        @Singleton
        fun provideObserveBookingTabInvalidationsUseCase(repository: BookingManagementRepository): ObserveBookingTabInvalidationsUseCase =
            ObserveBookingTabInvalidationsUseCase(repository)

        @Provides
        @Singleton
        fun provideObserveBookingGroupUseCase(repository: BookingManagementRepository): ObserveBookingGroupUseCase =
            ObserveBookingGroupUseCase(repository)
    }
}

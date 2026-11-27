package com.example.booking_app.feature.payment.data.di

import com.example.booking_app.feature.booking.data.time.ServerTimeOffsetProvider
import com.example.booking_app.feature.payment.data.FirestoreVenuePayoutRepository
import com.example.booking_app.feature.payment.data.FirestoreVoucherRepository
import com.example.booking_app.feature.payment.data.PaymentRepositoryImpl
import com.example.booking_app.feature.payment.data.remote.PaymentFunctionsApi
import com.example.booking_app.feature.payment.data.remote.SupabasePaymentFunctionsApi
import com.example.booking_app.feature.payment.domain.HoldClock
import com.example.booking_app.feature.payment.domain.repository.PaymentRepository
import com.example.booking_app.feature.payment.domain.repository.VenuePayoutRepository
import com.example.booking_app.feature.payment.domain.repository.VoucherRepository
import com.example.booking_app.feature.payment.ui.AndroidPaymentMessages
import com.example.booking_app.feature.payment.ui.PaymentMessages
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PaymentModule {

    @Binds
    abstract fun bindVoucherRepository(impl: FirestoreVoucherRepository): VoucherRepository

    @Binds
    abstract fun bindVenuePayoutRepository(impl: FirestoreVenuePayoutRepository): VenuePayoutRepository

    @Binds
    abstract fun bindPaymentRepository(impl: PaymentRepositoryImpl): PaymentRepository

    @Binds
    abstract fun bindPaymentFunctionsApi(impl: SupabasePaymentFunctionsApi): PaymentFunctionsApi

    @Binds
    abstract fun bindPaymentMessages(impl: AndroidPaymentMessages): PaymentMessages

    companion object {
        /** Đồng hồ giữ chỗ theo giờ server (đã bù lệch giờ máy), dùng chung với màn xác nhận 040. */
        @Provides
        fun provideHoldClock(server: ServerTimeOffsetProvider): HoldClock = HoldClock { server.now() }
    }
}

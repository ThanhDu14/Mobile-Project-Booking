package com.example.booking_app.feature.payment.data.di

import com.example.booking_app.feature.payment.data.FirestoreVoucherRepository
import com.example.booking_app.feature.payment.domain.repository.VoucherRepository
import com.example.booking_app.feature.payment.ui.AndroidPaymentMessages
import com.example.booking_app.feature.payment.ui.PaymentMessages
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PaymentModule {

    @Binds
    abstract fun bindVoucherRepository(impl: FirestoreVoucherRepository): VoucherRepository

    @Binds
    abstract fun bindPaymentMessages(impl: AndroidPaymentMessages): PaymentMessages
}

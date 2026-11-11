package com.example.booking_app.feature.dropin.data.di

import com.example.booking_app.feature.dropin.data.AndroidObserveConnectivity
import com.example.booking_app.feature.dropin.data.FirestoreGetVenueLocation
import com.example.booking_app.feature.dropin.data.FirestoreReadCachedDropInTicket
import com.example.booking_app.feature.dropin.domain.usecase.GetVenueLocation
import com.example.booking_app.feature.dropin.domain.usecase.ObserveConnectivity
import com.example.booking_app.feature.dropin.domain.usecase.ReadCachedDropInTicket
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Nguồn dữ liệu riêng của màn hình vé (US3, T048): cache offline, trạng thái mạng, tọa độ cơ sở. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DropInTicketModule {

    @Binds
    abstract fun bindReadCachedDropInTicket(impl: FirestoreReadCachedDropInTicket): ReadCachedDropInTicket

    @Binds
    abstract fun bindObserveConnectivity(impl: AndroidObserveConnectivity): ObserveConnectivity

    @Binds
    abstract fun bindGetVenueLocation(impl: FirestoreGetVenueLocation): GetVenueLocation
}

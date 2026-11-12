package com.example.booking_app.feature.dropin.data.di

import com.example.booking_app.feature.booking.domain.EmptyUpcomingBookingsReader
import com.example.booking_app.feature.booking.domain.UpcomingBookingsReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * TẠM: bind fake trả rỗng cho [UpcomingBookingsReader] tới khi B (spec 040/060) cài bản thật.
 * B bind bản thật trong module của mình thì XÓA file này (nếu không Hilt báo trùng binding).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class UpcomingBookingsStubModule {

    @Binds
    abstract fun bindUpcomingBookingsReader(impl: EmptyUpcomingBookingsReader): UpcomingBookingsReader
}

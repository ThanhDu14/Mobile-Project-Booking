package com.example.booking_app.feature.courtdetail.di

import com.example.booking_app.feature.courtdetail.data.FirestoreVenueDetailRepository
import com.example.booking_app.feature.courtdetail.domain.VenueDetailRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CourtDetailModule {

    @Binds
    abstract fun bindVenueDetailRepository(impl: FirestoreVenueDetailRepository): VenueDetailRepository
}

package com.example.booking_app.feature.search.di

import com.example.booking_app.feature.search.data.FirestoreHomeVenueRepository
import com.example.booking_app.feature.search.domain.HomeVenueRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SearchModule {

    @Binds
    abstract fun bindHomeVenueRepository(impl: FirestoreHomeVenueRepository): HomeVenueRepository
}

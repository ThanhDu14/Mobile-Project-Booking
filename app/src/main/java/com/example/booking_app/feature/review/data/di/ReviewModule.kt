package com.example.booking_app.feature.review.data.di

import com.example.booking_app.feature.review.data.FirestoreFavoriteRepository
import com.example.booking_app.feature.review.domain.FavoriteRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReviewModule {

    @Binds
    @Singleton
    abstract fun bindFavoriteRepository(impl: FirestoreFavoriteRepository): FavoriteRepository
}

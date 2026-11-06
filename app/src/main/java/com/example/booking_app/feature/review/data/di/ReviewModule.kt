package com.example.booking_app.feature.review.data.di

import com.example.booking_app.feature.review.data.FirestoreFavoriteRepository
import com.example.booking_app.feature.review.data.FirestoreReviewRepository
import com.example.booking_app.feature.review.data.OwnerVenuesAdapter
import com.example.booking_app.feature.review.domain.OwnerVenuesProvider
import com.example.booking_app.feature.review.data.ReviewApi
import com.example.booking_app.feature.review.data.SupabaseReviewApi
import com.example.booking_app.feature.review.data.SupabaseReviewPhotoUploader
import com.example.booking_app.feature.review.domain.FavoriteRepository
import com.example.booking_app.feature.review.domain.ReviewPhotoUploader
import com.example.booking_app.feature.review.domain.ReviewRepository
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

    @Binds
    @Singleton
    abstract fun bindReviewRepository(impl: FirestoreReviewRepository): ReviewRepository

    @Binds
    abstract fun bindReviewApi(impl: SupabaseReviewApi): ReviewApi

    @Binds
    abstract fun bindReviewPhotoUploader(impl: SupabaseReviewPhotoUploader): ReviewPhotoUploader

    @Binds
    abstract fun bindOwnerVenuesProvider(impl: OwnerVenuesAdapter): OwnerVenuesProvider
}

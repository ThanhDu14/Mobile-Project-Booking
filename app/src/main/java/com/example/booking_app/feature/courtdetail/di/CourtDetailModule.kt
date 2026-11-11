package com.example.booking_app.feature.courtdetail.di

import com.example.booking_app.feature.courtdetail.data.FirestoreLatestReviewsSource
import com.example.booking_app.feature.courtdetail.data.FirestoreVenueDetailRepository
import com.example.booking_app.feature.courtdetail.data.ReviewFavoriteGateway
import com.example.booking_app.feature.courtdetail.domain.FavoriteGateway
import com.example.booking_app.feature.courtdetail.domain.LatestReviewsSource
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

    @Binds
    abstract fun bindFavoriteGateway(impl: ReviewFavoriteGateway): FavoriteGateway

    // TODO(C): đổi sang bản gọi ReviewRepository.latestVisible khi 070 có (research R3).
    @Binds
    abstract fun bindLatestReviewsSource(impl: FirestoreLatestReviewsSource): LatestReviewsSource
}

package com.example.booking_app.feature.account.data.di

import com.example.booking_app.feature.account.data.AccountDataSource
import com.example.booking_app.feature.account.data.AccountRepositoryImpl
import com.example.booking_app.feature.account.data.AvatarRepositoryImpl
import com.example.booking_app.feature.account.data.CredentialManagerGoogleReauthTokenSource
import com.example.booking_app.feature.account.data.DistrictDataSource
import com.example.booking_app.feature.account.data.DistrictRepositoryImpl
import com.example.booking_app.feature.account.data.FirestoreAccountDataSource
import com.example.booking_app.feature.account.data.FirestoreDistrictDataSource
import com.example.booking_app.feature.account.data.GoogleReauthTokenSource
import com.example.booking_app.feature.account.data.ImageProcessor
import com.example.booking_app.feature.account.data.FirebaseReauthDataSource
import com.example.booking_app.feature.account.data.OwnerApplicationRepositoryImpl
import com.example.booking_app.feature.account.data.ReauthDataSource
import com.example.booking_app.feature.account.domain.AccountRepository
import com.example.booking_app.feature.account.domain.AvatarImageProcessor
import com.example.booking_app.feature.account.domain.AvatarRepository
import com.example.booking_app.feature.account.domain.DistrictRepository
import com.example.booking_app.feature.account.domain.OwnerApplicationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AccountModule {

    @Binds
    abstract fun bindAccountDataSource(impl: FirestoreAccountDataSource): AccountDataSource

    @Binds
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    abstract fun bindOwnerApplicationRepository(impl: OwnerApplicationRepositoryImpl): OwnerApplicationRepository

    @Binds
    abstract fun bindDistrictDataSource(impl: FirestoreDistrictDataSource): DistrictDataSource

    @Binds
    abstract fun bindDistrictRepository(impl: DistrictRepositoryImpl): DistrictRepository

    @Binds
    abstract fun bindAvatarRepository(impl: AvatarRepositoryImpl): AvatarRepository

    @Binds
    abstract fun bindAvatarImageProcessor(impl: ImageProcessor): AvatarImageProcessor

    @Binds
    abstract fun bindReauthDataSource(impl: FirebaseReauthDataSource): ReauthDataSource

    @Binds
    abstract fun bindGoogleReauthTokenSource(impl: CredentialManagerGoogleReauthTokenSource): GoogleReauthTokenSource
}

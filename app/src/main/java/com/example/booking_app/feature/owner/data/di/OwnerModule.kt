package com.example.booking_app.feature.owner.data.di

import com.example.booking_app.feature.owner.data.OwnerApplicationStatusApi
import com.example.booking_app.feature.owner.data.OwnerDocumentDownloadApi
import com.example.booking_app.feature.owner.data.SupabaseOwnerDocumentDownloadApi
import com.example.booking_app.feature.owner.data.OwnerDistrictCatalogApi
import com.example.booking_app.feature.owner.data.OwnerApplicationStatusRepositoryImpl
import com.example.booking_app.feature.owner.data.OwnerApplicationSubmitApi
import com.example.booking_app.feature.owner.data.OwnerApplicationSubmissionRepositoryImpl
import com.example.booking_app.feature.owner.data.SupabaseOwnerApplicationStatusApi
import com.example.booking_app.feature.owner.data.SupabaseOwnerDistrictCatalogApi
import com.example.booking_app.feature.owner.data.SupabaseOwnerApplicationSubmitApi
import com.example.booking_app.feature.owner.domain.OwnerApplicationStatusRepository
import com.example.booking_app.feature.owner.domain.OwnerApplicationSubmissionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class OwnerModule {
    @Binds
    abstract fun bindOwnerDistrictCatalogApi(impl: SupabaseOwnerDistrictCatalogApi): OwnerDistrictCatalogApi

    @Binds
    abstract fun bindOwnerStatusApi(impl: SupabaseOwnerApplicationStatusApi): OwnerApplicationStatusApi

    @Binds
    abstract fun bindOwnerStatusRepository(impl: OwnerApplicationStatusRepositoryImpl): OwnerApplicationStatusRepository

    @Binds
    abstract fun bindOwnerSubmitApi(impl: SupabaseOwnerApplicationSubmitApi): OwnerApplicationSubmitApi

    @Binds
    abstract fun bindOwnerDocumentDownloadApi(impl: SupabaseOwnerDocumentDownloadApi): OwnerDocumentDownloadApi

    @Binds
    abstract fun bindOwnerSubmissionRepository(impl: OwnerApplicationSubmissionRepositoryImpl): OwnerApplicationSubmissionRepository
}

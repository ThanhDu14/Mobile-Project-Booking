package com.example.booking_app.feature.admin.di

import com.example.booking_app.feature.admin.data.AdminApplicationRepositoryImpl
import com.example.booking_app.feature.admin.data.AdminReportRepositoryImpl
import com.example.booking_app.feature.admin.data.AdminToolsRepositoryImpl
import com.example.booking_app.feature.admin.data.AdminUserVenueRepositoryImpl
import com.example.booking_app.feature.admin.domain.repository.AdminApplicationRepository
import com.example.booking_app.feature.admin.domain.repository.AdminReportRepository
import com.example.booking_app.feature.admin.domain.repository.AdminToolsRepository
import com.example.booking_app.feature.admin.domain.repository.AdminUserVenueRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AdminModule {

    @Binds
    @Singleton
    abstract fun bindAdminApplicationRepository(
        impl: AdminApplicationRepositoryImpl,
    ): AdminApplicationRepository

    @Binds
    @Singleton
    abstract fun bindAdminReportRepository(
        impl: AdminReportRepositoryImpl,
    ): AdminReportRepository

    @Binds
    @Singleton
    abstract fun bindAdminUserVenueRepository(
        impl: AdminUserVenueRepositoryImpl,
    ): AdminUserVenueRepository

    @Binds
    @Singleton
    abstract fun bindAdminToolsRepository(
        impl: AdminToolsRepositoryImpl,
    ): AdminToolsRepository
}

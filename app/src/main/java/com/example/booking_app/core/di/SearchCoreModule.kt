package com.example.booking_app.core.di

import com.example.booking_app.core.catalog.CatalogDataSource
import com.example.booking_app.core.catalog.CatalogRepository
import com.example.booking_app.core.catalog.FirestoreCatalogDataSource
import com.example.booking_app.core.catalog.FirestoreCatalogRepository
import com.example.booking_app.core.location.AndroidLocationProvider
import com.example.booking_app.core.location.LocationProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Thành phần dùng chung của tìm kiếm (spec 020 T016), chi tiết sân (030) dùng lại. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SearchCoreModule {

    @Binds
    abstract fun bindLocationProvider(impl: AndroidLocationProvider): LocationProvider

    @Binds
    abstract fun bindCatalogDataSource(impl: FirestoreCatalogDataSource): CatalogDataSource

    @Binds
    @Singleton
    abstract fun bindCatalogRepository(impl: FirestoreCatalogRepository): CatalogRepository
}

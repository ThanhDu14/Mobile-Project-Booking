package com.example.booking_app.feature.owner.di

import com.example.booking_app.feature.owner.data.ManageSlotBlocksApi
import com.example.booking_app.feature.owner.data.OwnerVenueRepositoryImpl
import com.example.booking_app.feature.owner.data.SupabaseManageSlotBlocksApi
import com.example.booking_app.feature.owner.data.SupabaseSyncVenuePriceApi
import com.example.booking_app.feature.owner.data.SyncVenuePriceApi
import com.example.booking_app.feature.owner.domain.OwnerVenueRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OwnerModule {
    @Binds
    @Singleton
    abstract fun bindOwnerVenueRepository(impl: OwnerVenueRepositoryImpl): OwnerVenueRepository

    @Binds
    @Singleton
    abstract fun bindSyncVenuePriceApi(impl: SupabaseSyncVenuePriceApi): SyncVenuePriceApi

    @Binds
    @Singleton
    abstract fun bindManageSlotBlocksApi(impl: SupabaseManageSlotBlocksApi): ManageSlotBlocksApi
}

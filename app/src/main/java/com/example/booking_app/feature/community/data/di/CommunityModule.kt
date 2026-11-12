package com.example.booking_app.feature.community.data.di

import com.example.booking_app.feature.community.data.FirestoreGroupRepository
import com.example.booking_app.feature.community.data.GroupManageApi
import com.example.booking_app.feature.community.data.SupabaseGroupManageApi
import com.example.booking_app.feature.community.data.SupabaseGroupMediaRepository
import com.example.booking_app.feature.community.domain.GroupMediaRepository
import com.example.booking_app.feature.community.domain.GroupRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** T019: Hilt bind của spec 090. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CommunityModule {

    @Binds
    @Singleton
    abstract fun bindGroupRepository(impl: FirestoreGroupRepository): GroupRepository

    @Binds
    abstract fun bindGroupManageApi(impl: SupabaseGroupManageApi): GroupManageApi

    @Binds
    abstract fun bindGroupMediaRepository(impl: SupabaseGroupMediaRepository): GroupMediaRepository

    // TODO US3: bind PostRepository khi có cài đặt (group-post, bảng tin). Chưa có nơi nào inject PostRepository.
}

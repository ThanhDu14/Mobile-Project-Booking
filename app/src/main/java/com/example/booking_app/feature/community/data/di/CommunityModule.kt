package com.example.booking_app.feature.community.data.di

import com.example.booking_app.core.media.SignedUrlBatch
import com.example.booking_app.core.media.SignedUrlCache
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.community.data.FirestoreGroupRepository
import com.example.booking_app.feature.community.data.FirestorePostRepository
import com.example.booking_app.feature.community.data.GroupManageApi
import com.example.booking_app.feature.community.data.GroupMediaApi
import com.example.booking_app.feature.community.data.GroupPostApi
import com.example.booking_app.feature.community.data.SupabaseGroupManageApi
import com.example.booking_app.feature.community.data.SupabaseGroupMediaApi
import com.example.booking_app.feature.community.data.SupabaseGroupMediaRepository
import com.example.booking_app.feature.community.data.SupabaseGroupPostApi
import com.example.booking_app.feature.community.domain.GroupMediaRepository
import com.example.booking_app.feature.community.domain.GroupRepository
import com.example.booking_app.feature.community.domain.PostRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** T019 + T047/T048: Hilt bind của spec 090. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CommunityModule {

    @Binds
    @Singleton
    abstract fun bindGroupRepository(impl: FirestoreGroupRepository): GroupRepository

    @Binds
    abstract fun bindGroupManageApi(impl: SupabaseGroupManageApi): GroupManageApi

    @Binds
    @Singleton
    abstract fun bindPostRepository(impl: FirestorePostRepository): PostRepository

    @Binds
    abstract fun bindGroupPostApi(impl: SupabaseGroupPostApi): GroupPostApi

    @Binds
    abstract fun bindGroupMediaApi(impl: SupabaseGroupMediaApi): GroupMediaApi

    @Binds
    abstract fun bindGroupMediaRepository(impl: SupabaseGroupMediaRepository): GroupMediaRepository

    companion object {
        @Provides
        @Singleton
        fun provideSignedUrlCache(media: GroupMediaRepository, clock: AppClock): SignedUrlCache =
            SignedUrlCache(
                fetch = { paths ->
                    media.signedUrls(paths).map { SignedUrlBatch(it.urls, it.expiresAt) }
                },
                clock = clock,
            )
    }
}

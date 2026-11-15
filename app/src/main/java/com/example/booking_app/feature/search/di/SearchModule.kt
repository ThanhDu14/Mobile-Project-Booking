package com.example.booking_app.feature.search.di

import com.example.booking_app.core.session.SignOutHook
import com.example.booking_app.feature.search.data.FirestoreHomeVenueRepository
import com.example.booking_app.feature.search.data.PrefsAiQueryHistory
import com.example.booking_app.feature.search.domain.AiQueryHistory
import dagger.multibindings.IntoSet
import com.example.booking_app.feature.search.data.FirestoreCourtSearchRepository
import com.example.booking_app.feature.search.data.SupabaseCourtAiApi
import com.example.booking_app.feature.search.domain.CourtAiApi
import com.example.booking_app.feature.search.domain.CourtSearchRepository
import com.example.booking_app.feature.search.domain.HomeVenueRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SearchModule {

    @Binds
    abstract fun bindHomeVenueRepository(impl: FirestoreHomeVenueRepository): HomeVenueRepository

    @Binds
    abstract fun bindCourtSearchRepository(impl: FirestoreCourtSearchRepository): CourtSearchRepository

    @Binds
    abstract fun bindCourtAiApi(impl: SupabaseCourtAiApi): CourtAiApi

    @Binds
    abstract fun bindAiQueryHistory(impl: PrefsAiQueryHistory): AiQueryHistory

    /** Xóa lịch sử câu hỏi AI của tài khoản khi đăng xuất. */
    @Binds
    @IntoSet
    abstract fun bindAiQueryHistorySignOutHook(impl: PrefsAiQueryHistory): SignOutHook
}

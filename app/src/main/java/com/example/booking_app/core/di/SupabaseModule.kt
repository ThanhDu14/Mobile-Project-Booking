package com.example.booking_app.core.di

import com.example.booking_app.BuildConfig
import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.IdTokenProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.storage.Storage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    /** Third-party Auth: mọi request gửi Firebase ID token. Khách không có token thì dùng khóa publishable. */
    @Provides
    @Singleton
    fun provideSupabaseClient(idTokenProvider: IdTokenProvider): SupabaseClient =
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY) {
            accessToken = {
                try {
                    idTokenProvider.getToken(forceRefresh = false)
                } catch (e: AppError.NotSignedIn) {
                    null
                }
            }
            install(Functions)
            install(Storage)
        }
}

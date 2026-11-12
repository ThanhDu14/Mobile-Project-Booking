package com.example.booking_app.core.di

import com.example.booking_app.BuildConfig
import com.example.booking_app.core.image.AndroidImageCompressor
import com.example.booking_app.core.image.ImageCompressor
import com.example.booking_app.core.media.PhotoUrlResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    @Provides
    @Singleton
    fun providePhotoUrlResolver(): PhotoUrlResolver = PhotoUrlResolver(BuildConfig.SUPABASE_URL)

    @Provides
    fun provideImageCompressor(impl: AndroidImageCompressor): ImageCompressor = impl
}

package com.example.booking_app.core.di

import com.example.booking_app.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Địa chỉ máy host nhìn từ Android Emulator. */
private const val EMULATOR_HOST = "10.0.2.2"

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    private val useEmulator: Boolean
        get() = BuildConfig.USE_EMULATOR == "true"

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance().apply {
        if (useEmulator) useEmulator(EMULATOR_HOST, 9099)
    }

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance().apply {
        if (useEmulator) useEmulator(EMULATOR_HOST, 8080)
    }
}

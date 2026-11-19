package com.example.booking_app.feature.admin

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.example.booking_app.core.di.FirebaseModule
import com.example.booking_app.core.di.SupabaseModule
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.tasks.await
import javax.inject.Singleton

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [FirebaseModule::class, SupabaseModule::class])
object AdminLocalBackendModule {
    private var configuredFirestore: FirebaseFirestore? = null
    @Provides @Singleton
    fun app(@ApplicationContext context: Context): FirebaseApp = FirebaseApp.getApps(context)
        .firstOrNull { it.name == "admin-local-validation" } ?: FirebaseApp.initializeApp(context,
            FirebaseOptions.Builder().setProjectId("demo-admin-repair")
                .setApplicationId("1:123456789:android:adminlocal").setApiKey("fake-api-key").build(),
            "admin-local-validation")

    @Provides @Singleton
    fun auth(app: FirebaseApp): FirebaseAuth = FirebaseAuth.getInstance(app).apply { useEmulator("127.0.0.1", 19099) }

    @Provides @Singleton
    fun firestore(app: FirebaseApp): FirebaseFirestore = synchronized(this) {
        configuredFirestore ?: FirebaseFirestore.getInstance(app).apply {
            useEmulator("127.0.0.1", 18080)
        }.also { configuredFirestore = it }
    }

    @Provides @Singleton
    fun supabase(auth: FirebaseAuth): SupabaseClient {
        val key = requireNotNull(InstrumentationRegistry.getArguments().getString("localSupabaseKey")) {
            "Pass the local Supabase publishable key; no hosted fallback is allowed"
        }
        return createSupabaseClient("http://127.0.0.1:54321", key) {
            accessToken = { auth.currentUser?.getIdToken(false)?.await()?.token }
            install(Functions)
            install(Storage)
        }
    }
}

package com.example.booking_app

import android.app.Application
import com.example.booking_app.core.di.ApplicationScope
import com.example.booking_app.feature.account.data.EmailSyncObserver
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

@HiltAndroidApp
class SmashNowApp : Application() {

    @Inject
    lateinit var emailSyncObserver: EmailSyncObserver

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Spec 011 US7: tầng app được phụ thuộc feature/, core/ thì không.
        emailSyncObserver.start(applicationScope)
    }
}

package com.example.booking_app

import android.app.Application
import com.example.booking_app.core.di.ApplicationScope
import com.example.booking_app.feature.account.data.EmailSyncObserver
import com.example.booking_app.feature.notification.data.NotificationChannels
import com.example.booking_app.feature.notification.data.NotificationTokenCoordinator
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

@HiltAndroidApp
class SmashNowApp : Application() {

    @Inject
    lateinit var emailSyncObserver: EmailSyncObserver

    @Inject lateinit var notificationChannels: NotificationChannels
    @Inject lateinit var notificationTokens: NotificationTokenCoordinator

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        notificationChannels.ensureCreated()
        notificationTokens.start()
        // Spec 011 US7: tầng app được phụ thuộc feature/, core/ thì không.
        emailSyncObserver.start(applicationScope)
    }
}

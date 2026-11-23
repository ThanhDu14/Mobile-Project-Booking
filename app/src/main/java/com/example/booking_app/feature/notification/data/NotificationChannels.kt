package com.example.booking_app.feature.notification.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.example.booking_app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationChannels @Inject constructor(@ApplicationContext private val context: Context) {
    // minSdk is 29. Creating a channel grants no permission and displays no notification.
    internal fun ensureCreated(channelId: String = GENERAL_ID) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId) != null) return
        manager.createNotificationChannel(NotificationChannel(channelId, context.getString(R.string.notification_title),
            NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.notification_channel_description)
        })
    }

    companion object {
        const val GENERAL_ID = "smashnow_notifications"
    }
}

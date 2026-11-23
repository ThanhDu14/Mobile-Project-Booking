package com.example.booking_app.feature.notification.data

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.example.booking_app.MainActivity
import com.example.booking_app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Builds generic content only. Permission/session checks stay at dispatch and authenticated tap. */
class NotificationPushRenderer @Inject constructor(@ApplicationContext private val context: Context) {
    fun build(push: NotificationPush): Notification {
        val uri = Uri.Builder().scheme("smashnow").authority("notifications").appendPath(push.recipientUid).appendPath(push.notificationId).build()
        val pending = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).apply {
            data = uri
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun builder() = NotificationCompat.Builder(context, NotificationChannels.GENERAL_ID)
            .setSmallIcon(R.drawable.ic_notifications_none_24).setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notification_push_generic))
        return builder().setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(builder().build())
            .setAutoCancel(true).setContentIntent(pending).build()
    }
}

package com.example.booking_app.feature.notification.ui

/** Runtime permission and the app-wide OS switch are independent of inbox preferences. */
internal enum class NotificationPermissionAction { REQUEST, SETTINGS }

internal fun notificationPermissionAction(sdk: Int, runtimeGranted: Boolean): NotificationPermissionAction =
    if (sdk >= 33 && !runtimeGranted) NotificationPermissionAction.REQUEST
    else NotificationPermissionAction.SETTINGS

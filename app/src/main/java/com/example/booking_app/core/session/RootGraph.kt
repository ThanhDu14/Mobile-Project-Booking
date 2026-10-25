package com.example.booking_app.core.session

import androidx.annotation.IdRes
import com.example.booking_app.R

/** Graph gốc theo vai trò và chế độ (spec 011 R7). `MainActivity` chỉ điều hướng lại khi kết quả đổi. */
@IdRes
fun rootGraphFor(appRole: AppRole, mode: AppMode): Int = when (appRole) {
    AppRole.PLAYER -> R.id.nav_player
    AppRole.OWNER -> if (mode == AppMode.OWNER) R.id.nav_owner else R.id.nav_player
    AppRole.ADMIN -> R.id.nav_admin
}

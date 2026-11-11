package com.example.booking_app.feature.courtdetail.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.example.booking_app.R
import com.example.booking_app.feature.courtdetail.domain.model.PendingAction
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Hộp mời đăng nhập khi khách bấm "Đặt sân", trái tim hoặc chat (contracts/ui-screens.md §3, research R11). */
object LoginPromptDialog {

    fun show(context: Context, action: PendingAction, onLogin: (PendingAction) -> Unit): AlertDialog =
        MaterialAlertDialogBuilder(context)
            .setTitle(title(action))
            .setMessage(R.string.court_detail_login_prompt_message)
            .setNegativeButton(R.string.court_detail_later, null)
            .setPositiveButton(R.string.court_detail_login) { _, _ -> onLogin(action) }
            .show()

    @StringRes
    private fun title(action: PendingAction): Int = when (action) {
        PendingAction.BOOK -> R.string.court_detail_login_to_book
        PendingAction.FAVORITE -> R.string.court_detail_login_to_favorite
        PendingAction.CHAT -> R.string.court_detail_login_to_chat
    }
}

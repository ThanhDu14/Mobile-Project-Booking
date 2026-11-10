package com.example.booking_app.core.session

import androidx.navigation.NavController
import androidx.navigation.navOptions
import com.example.booking_app.R
import javax.inject.Inject

/**
 * Bấm avatar ở header dùng chung (spec 011 FR-029, FR-030). Tính năng khác gọi interface này, không
 * navigate thẳng tới `nav_account`.
 */
interface AccountEntryNavigator {
    fun open(navController: NavController)
}

class DefaultAccountEntryNavigator @Inject constructor(
    private val sessionManager: SessionManager,
    private val authNavigator: AuthNavigator,
) : AccountEntryNavigator {

    override fun open(navController: NavController) {
        val current = navController.currentDestination
        if (sessionManager.sessionState.value is SessionState.SignedIn) {
            // Bấm liên tiếp: không chồng nhiều trang Tài khoản (Edge Case).
            if (current?.id == R.id.accountFragment) return
            // Global action có ở cả nav_player và nav_owner; AccountFragment nằm trên back stack của tab đang đứng.
            navController.navigate(R.id.action_global_account, null, navOptions { launchSingleTop = true })
        } else {
            if (current?.parent?.id == R.id.nav_auth) return
            // Không returnTo: đăng nhập xong pop nav_auth là về đúng tab đang xem (FR-030).
            authNavigator.openLogin(navController, null)
        }
    }
}

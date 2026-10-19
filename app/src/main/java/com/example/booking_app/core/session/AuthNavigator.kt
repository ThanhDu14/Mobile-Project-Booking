package com.example.booking_app.core.session

import android.net.Uri
import androidx.navigation.NavController

/**
 * Mở đăng nhập/đăng ký từ chế độ khách. `nav_auth` nằm chồng lên màn hình hiện tại, đăng nhập
 * xong quay lại đúng chỗ (research R15). Tính năng khác dùng interface này, không import `feature/auth`.
 */
interface AuthNavigator {
    fun openLogin(navController: NavController, returnTo: Uri? = null)
    fun openRegister(navController: NavController, returnTo: Uri? = null)
}

package com.example.booking_app.core.session

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavDeepLinkRequest
import javax.inject.Inject

/** Mở `nav_auth` bằng deep link `smashnow://auth/...`, không `popUpTo`. */
class DeepLinkAuthNavigator @Inject constructor() : AuthNavigator {

    override fun openLogin(navController: NavController, returnTo: Uri?) {
        navController.navigate(request("login", returnTo))
    }

    override fun openRegister(navController: NavController, returnTo: Uri?) {
        navController.navigate(request("register", returnTo))
    }

    private fun request(path: String, returnTo: Uri?): NavDeepLinkRequest {
        val uri = Uri.Builder()
            .scheme(SCHEME)
            .authority(AUTHORITY)
            .appendPath(path)
            .apply { if (returnTo != null) appendQueryParameter("returnTo", returnTo.toString()) }
            .build()
        return NavDeepLinkRequest.Builder.fromUri(uri).build()
    }

    private companion object {
        const val SCHEME = "smashnow"
        const val AUTHORITY = "auth"
    }
}

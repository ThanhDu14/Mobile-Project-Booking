package com.example.booking_app

import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDeepLinkRequest
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.RouteDecision
import com.example.booking_app.core.session.SessionRouter
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.session.SignOutReason
import com.example.booking_app.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Single Activity. Thu thập `SessionState` và thực hiện quyết định của `SessionRouter`
 * (contracts/ui-screens.md §1, research R15).
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val navController: NavController by lazy {
        (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sessionState.collect { route(it) }
            }
        }
    }

    private suspend fun route(state: SessionState) {
        if (state == SessionState.Loading) return
        val previous = viewModel.lastRoutedState
        viewModel.lastRoutedState = state

        if (navController.currentDestination?.id == R.id.launcherFragment) {
            SessionRouter.startDestination(state, viewModel.welcomeSeen())?.let(::execute)
            return
        }
        // Mở lại sau khi hệ thống hủy process: NavController đã khôi phục back stack, chỉ ghi nhận trạng thái.
        if (previous == null || previous == state) return

        when (state) {
            is SessionState.SignedIn -> when {
                previous !is SessionState.SignedIn -> onSignedIn(state.appRole)
                previous.appRole != state.appRole -> execute(RouteDecision.ResetTo(state.appRole))
            }
            is SessionState.SignedOut -> when {
                previous is SessionState.SignedOut -> Unit
                // "Dùng tài khoản khác" ở bước Hoàn tất tài khoản: về màn hình đăng nhập.
                previous is SessionState.NeedsConsent && state.reason == null -> backToLogin()
                // Đăng xuất (spec 011) hoặc bị khóa: về trang chủ ở chế độ khách, không về màn hình chào.
                else -> execute(RouteDecision.PlayerGuest(showLockedLogin = state.reason == SignOutReason.LOCKED))
            }
            is SessionState.NeedsConsent -> openConsent()
            // TODO(US1 T047): Initializing / InitFailed → accountSetup.
            SessionState.Initializing, is SessionState.InitFailed -> Unit
            SessionState.Loading -> Unit
        }
    }

    private fun onSignedIn(appRole: AppRole) {
        val authOnStack = hasBackStackEntry(R.id.nav_auth)
        val playerOnStack = hasBackStackEntry(R.id.nav_player)
        if (!authOnStack && appRole == AppRole.PLAYER && playerOnStack) return
        val returnTo = runCatching { navController.getBackStackEntry(R.id.loginFragment) }.getOrNull()
            ?.arguments?.getString(ARG_RETURN_TO)?.let(Uri::parse)
        execute(SessionRouter.afterSignIn(appRole, hasPlayerGraphBelowAuth = authOnStack && playerOnStack, returnTo))
    }

    private fun execute(decision: RouteDecision) {
        when (decision) {
            RouteDecision.Welcome -> resetTo(R.id.nav_auth)
            is RouteDecision.PlayerGuest -> {
                resetTo(R.id.nav_player)
                // Bị khóa: trang chủ khách ở dưới, màn hình đăng nhập kèm thông báo khóa ở trên (research R15).
                if (decision.showLockedLogin) navController.navigate(LOCKED_LOGIN)
            }
            is RouteDecision.RoleHome -> resetTo(graphFor(decision.appRole))
            is RouteDecision.ResetTo -> resetTo(graphFor(decision.appRole))
            is RouteDecision.PopAuth -> {
                navController.popBackStack(R.id.nav_auth, inclusive = true)
                decision.returnTo?.let { navController.navigate(it) }
            }
            RouteDecision.Consent -> openConsent()
            // TODO(US1 T047): accountSetup chưa có; giữ màn hình khởi động.
            RouteDecision.AccountSetup -> Unit
        }
    }

    /**
     * Bước Hoàn tất tài khoản. Đi từ màn hình đăng nhập thì mở chồng lên (Back = dùng tài khoản khác);
     * mở app lại khi chưa xong bước này thì xóa back stack (Edge Case đóng app ở bước đồng ý).
     */
    private fun openConsent() {
        if (navController.currentDestination?.id == R.id.termsConsentFragment) return
        if (hasBackStackEntry(R.id.loginFragment)) {
            navController.navigate(R.id.termsConsentFragment)
        } else {
            navController.navigate(CONSENT, navOptions { popUpTo(R.id.nav_main) { inclusive = true } })
        }
    }

    private fun backToLogin() {
        if (!navController.popBackStack(R.id.loginFragment, false)) {
            resetTo(R.id.nav_player)
            navController.navigate(LOGIN)
        }
    }

    private fun resetTo(graphId: Int) {
        navController.navigate(graphId, null, navOptions { popUpTo(R.id.nav_main) { inclusive = true } })
    }

    private fun hasBackStackEntry(destinationId: Int): Boolean =
        runCatching { navController.getBackStackEntry(destinationId) }.isSuccess

    private fun graphFor(appRole: AppRole): Int = when (appRole) {
        AppRole.PLAYER -> R.id.nav_player
        AppRole.OWNER -> R.id.nav_owner
        AppRole.ADMIN -> R.id.nav_admin
    }

    private companion object {
        const val ARG_RETURN_TO = "returnTo"
        val LOGIN: NavDeepLinkRequest = deepLink("smashnow://auth/login")
        val LOCKED_LOGIN: NavDeepLinkRequest = deepLink("smashnow://auth/login?lockedNotice=true")
        val CONSENT: NavDeepLinkRequest = deepLink("smashnow://auth/consent")

        fun deepLink(uri: String): NavDeepLinkRequest = NavDeepLinkRequest.Builder.fromUri(Uri.parse(uri)).build()
    }
}

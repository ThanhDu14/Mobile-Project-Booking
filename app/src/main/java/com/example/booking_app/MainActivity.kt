package com.example.booking_app

import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDeepLinkRequest
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.GuestNotice
import com.example.booking_app.core.session.RouteDecision
import com.example.booking_app.core.session.SessionRouter
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.session.rootGraphFor
import com.example.booking_app.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Single Activity. Thu thập `SessionState` và thực hiện quyết định của `SessionRouter`
 * (contracts/ui-screens.md §1, research R15). Spec 011 (T018): graph gốc theo `(appRole, mode)`.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val navController: NavController by lazy {
        (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController
    }

    /** Menu đang gắn vào BottomNav; layout khởi tạo bằng menu người chơi. */
    private var bottomNavMenu = R.menu.menu_player_bottom_nav

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
                // Chế độ chủ sân đổi cũng phát lại trạng thái để điều hướng sang graph mới.
                combine(viewModel.sessionState, viewModel.mode) { state, _ -> state }.collect { route(it) }
            }
        }
        setUpBottomNav()
    }

    /**
     * Một BottomNav cho cả hai chế độ, đổi menu theo graph gốc: BottomNav người chơi trong `nav_player`, OwnerNav
     * trong `nav_owner`. Chỉ hiện ở màn gốc của tab. Người chơi mới nối Trang chủ và Tài khoản (Vãng lai, Lịch đặt,
     * Cộng đồng chờ spec 080/060/090); chủ sân mới nối Hôm nay (màn tạm) và Cơ sở (Đơn đặt, Lịch sân, Tin nhắn chờ
     * spec 112). Tab chưa có màn hình báo "đang phát triển".
     */
    private fun setUpBottomNav() {
        // Root đã chừa chỗ cho thanh hệ thống; không để BottomNav cộng thêm lần nữa.
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { _, insets -> insets }
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.tab_home -> {
                    navController.popBackStack(R.id.playerHomePlaceholderFragment, false)
                    true
                }
                R.id.tab_owner_today -> {
                    navController.popBackStack(R.id.ownerHomePlaceholderFragment, false)
                    true
                }
                R.id.tab_owner_venues -> {
                    openOwnerVenues()
                    true
                }
                R.id.tab_account -> {
                    if (viewModel.sessionState.value is SessionState.SignedIn) {
                        navController.navigate(
                            R.id.nav_account,
                            null,
                            navOptions {
                                launchSingleTop = true
                                popUpTo(R.id.playerHomePlaceholderFragment)
                            },
                        )
                        true
                    } else {
                        // Khách chưa có trang Tài khoản: mở đăng nhập, tab vẫn ở Trang chủ.
                        navController.navigate(LOGIN)
                        false
                    }
                }
                else -> {
                    Snackbar.make(binding.root, getString(R.string.nav_tab_coming_soon, item.title), Snackbar.LENGTH_SHORT)
                        .setAnchorView(binding.bottomNavContainer)
                        .show()
                    false
                }
            }
        }
        // Bấm lại tab đang chọn: không tạo lại màn hình. Chủ sân đang ở trang Tài khoản (mở từ tab đó) thì về màn gốc của tab.
        binding.bottomNav.setOnItemReselectedListener { item ->
            if (navController.currentDestination?.id != R.id.accountFragment) return@setOnItemReselectedListener
            when (item.itemId) {
                R.id.tab_owner_today -> navController.popBackStack(R.id.ownerHomePlaceholderFragment, false)
                R.id.tab_owner_venues -> openOwnerVenues()
            }
        }
        navController.addOnDestinationChangedListener { _, destination, _ -> updateBottomNav(destination) }
    }

    private fun openOwnerVenues() {
        navController.navigate(
            R.id.ownerVenueListFragment,
            null,
            navOptions {
                launchSingleTop = true
                popUpTo(R.id.ownerHomePlaceholderFragment)
            },
        )
    }

    private fun updateBottomNav(destination: NavDestination) {
        // Graph gốc quyết định menu; khách (lastRootGraph = null) dùng graph người chơi.
        val ownerMode = viewModel.lastRootGraph == R.id.nav_owner
        val tab = if (ownerMode) {
            when (destination.id) {
                R.id.ownerHomePlaceholderFragment -> R.id.tab_owner_today
                R.id.ownerVenueListFragment -> R.id.tab_owner_venues
                // OwnerNav không có tab Tài khoản: giữ sáng tab đã mở trang này (avatar ở Hôm nay → Hôm nay).
                R.id.accountFragment ->
                    if (navController.previousBackStackEntry?.destination?.id == R.id.ownerVenueListFragment) {
                        R.id.tab_owner_venues
                    } else {
                        R.id.tab_owner_today
                    }
                else -> null
            }
        } else {
            when (destination.id) {
                R.id.playerHomePlaceholderFragment -> R.id.tab_home
                R.id.accountFragment -> R.id.tab_account
                else -> null
            }
        }
        val visible = tab != null && hasBackStackEntry(if (ownerMode) R.id.nav_owner else R.id.nav_player)
        binding.bottomNavContainer.isVisible = visible
        if (!visible) return
        val menuRes = if (ownerMode) R.menu.menu_owner_bottom_nav else R.menu.menu_player_bottom_nav
        if (bottomNavMenu != menuRes) {
            binding.bottomNav.menu.clear()
            binding.bottomNav.inflateMenu(menuRes)
            bottomNavMenu = menuRes
        }
        binding.bottomNav.menu.findItem(tab!!).isChecked = true
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
        if (previous == null) {
            if (state is SessionState.SignedIn) viewModel.lastRootGraph = graphFor(state.appRole)
            // BottomNav đã vẽ theo destination khôi phục trước khi biết graph gốc: vẽ lại đúng menu.
            navController.currentDestination?.let(::updateBottomNav)
            return
        }
        if (previous == state) {
            // Cùng trạng thái nhưng chủ sân vừa đổi chế độ (spec 011 R7).
            if (state is SessionState.SignedIn) switchRootGraphIfNeeded(state.appRole)
            return
        }

        when (state) {
            is SessionState.SignedIn -> when {
                previous !is SessionState.SignedIn -> onSignedIn(state.appRole)
                previous.appRole != state.appRole -> execute(RouteDecision.ResetTo(state.appRole))
                else -> switchRootGraphIfNeeded(state.appRole)
            }
            is SessionState.SignedOut -> when {
                previous is SessionState.SignedOut -> Unit
                // "Dùng tài khoản khác" ở bước Hoàn tất tài khoản: về màn hình đăng nhập.
                previous is SessionState.NeedsConsent && state.reason == null -> backToLogin()
                // Đăng xuất, bị khóa, bị thu hồi phiên, tài khoản bị xóa (spec 011): về trang chủ ở chế độ khách.
                else -> execute(SessionRouter.afterSignOut(state.reason))
            }
            is SessionState.NeedsConsent -> openConsent()
            // Vừa bấm "Đăng ký": RegisterFragment đang gọi on-signup và tự báo lỗi, không chuyển màn hình.
            SessionState.Initializing ->
                if (navController.currentDestination?.id != R.id.registerFragment) openAccountSetup()
            is SessionState.InitFailed -> openAccountSetup()
            SessionState.Loading -> Unit
        }
    }

    private fun onSignedIn(appRole: AppRole) {
        // Mọi nhánh bên dưới đều kết thúc ở graph của vai trò (PopAuth: graph người chơi đang nằm dưới).
        viewModel.lastRootGraph = graphFor(appRole)
        val authOnStack = hasBackStackEntry(R.id.nav_auth)
        val playerOnStack = hasBackStackEntry(R.id.nav_player)
        if (!authOnStack && appRole == AppRole.PLAYER && playerOnStack) return
        val returnTo = listOf(R.id.loginFragment, R.id.registerFragment).firstNotNullOfOrNull { id ->
            runCatching { navController.getBackStackEntry(id) }.getOrNull()?.arguments?.getString(ARG_RETURN_TO)
        }?.let(Uri::parse)
        execute(SessionRouter.afterSignIn(appRole, hasPlayerGraphBelowAuth = authOnStack && playerOnStack, returnTo))
    }

    private fun execute(decision: RouteDecision) {
        when (decision) {
            RouteDecision.Welcome -> resetTo(R.id.nav_auth)
            is RouteDecision.PlayerGuest -> {
                viewModel.lastRootGraph = null
                resetTo(R.id.nav_player)
                // Bị khóa: trang chủ khách ở dưới, màn hình đăng nhập kèm thông báo khóa ở trên (research R15).
                if (decision.showLockedLogin) navController.navigate(LOCKED_LOGIN)
                when (decision.notice) {
                    GuestNotice.NONE -> Unit
                    GuestNotice.SESSION_REVOKED -> navController.navigate(SESSION_REVOKED_LOGIN)
                    GuestNotice.ACCOUNT_DELETED ->
                        Snackbar.make(binding.root, R.string.account_deleted, Snackbar.LENGTH_LONG).show()
                }
            }
            is RouteDecision.RoleHome -> resetToRoleGraph(decision.appRole)
            is RouteDecision.ResetTo -> resetToRoleGraph(decision.appRole)
            is RouteDecision.PopAuth -> {
                navController.popBackStack(R.id.nav_auth, inclusive = true)
                decision.returnTo?.let { navController.navigate(it) }
            }
            RouteDecision.Consent -> openConsent()
            RouteDecision.AccountSetup -> openAccountSetup()
        }
    }

    /** Chờ `on-signup` (FR-006): xóa back stack, chưa xong thì không vào được màn hình chính. */
    private fun openAccountSetup() {
        if (navController.currentDestination?.id == R.id.accountSetupFragment) return
        navController.navigate(ACCOUNT_SETUP, navOptions { popUpTo(R.id.nav_main) { inclusive = true } })
    }

    /**
     * Bước Hoàn tất tài khoản. Đi từ màn hình đăng nhập/đăng ký thì mở chồng lên (Back = dùng tài khoản khác);
     * mở app lại khi chưa xong bước này thì xóa back stack (Edge Case đóng app ở bước đồng ý).
     */
    private fun openConsent() {
        if (navController.currentDestination?.id == R.id.termsConsentFragment) return
        if (hasBackStackEntry(R.id.loginFragment) || hasBackStackEntry(R.id.registerFragment)) {
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

    private fun graphFor(appRole: AppRole): Int =
        rootGraphFor(appRole, viewModel.modeFor(viewModel.sessionState.value))

    private fun resetToRoleGraph(appRole: AppRole) {
        val graph = graphFor(appRole)
        viewModel.lastRootGraph = graph
        resetTo(graph)
    }

    /** Chủ sân chuyển "Người chơi" ↔ "Quản lý sân": chỉ điều hướng khi graph đích đổi (spec 011 T018). */
    private fun switchRootGraphIfNeeded(appRole: AppRole) {
        if (graphFor(appRole) == viewModel.lastRootGraph) return
        resetToRoleGraph(appRole)
        // Màn hình và thanh dưới đổi hẳn: báo cho người dùng biết vì sao.
        val message = if (viewModel.lastRootGraph == R.id.nav_owner) R.string.nav_mode_switched_owner
        else R.string.nav_mode_switched_player
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).setAnchorView(binding.bottomNavContainer).show()
    }

    private companion object {
        const val ARG_RETURN_TO = "returnTo"
        val LOGIN: NavDeepLinkRequest = deepLink("smashnow://auth/login")
        val LOCKED_LOGIN: NavDeepLinkRequest = deepLink("smashnow://auth/login?lockedNotice=true")
        val SESSION_REVOKED_LOGIN: NavDeepLinkRequest = deepLink("smashnow://auth/login?sessionRevokedNotice=true")
        val CONSENT: NavDeepLinkRequest = deepLink("smashnow://auth/consent")
        val ACCOUNT_SETUP: NavDeepLinkRequest = deepLink("smashnow://auth/setup")

        fun deepLink(uri: String): NavDeepLinkRequest = NavDeepLinkRequest.Builder.fromUri(Uri.parse(uri)).build()
    }
}

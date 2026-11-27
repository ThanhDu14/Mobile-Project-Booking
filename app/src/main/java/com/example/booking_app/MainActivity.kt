package com.example.booking_app

import android.content.Intent
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
import com.example.booking_app.core.chat.UnreadChatsReader
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.GuestNotice
import com.example.booking_app.core.session.RouteDecision
import com.example.booking_app.core.session.SessionRouter
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.session.rootGraphFor
import com.example.booking_app.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Single Activity. Thu thập `SessionState` và thực hiện quyết định của `SessionRouter`
 * (contracts/ui-screens.md §1, research R15). Spec 011 (T018): graph gốc theo `(appRole, mode)`.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val adminAppsViewModel: com.example.booking_app.feature.admin.ui.AdminApplicationsViewModel by viewModels()
    private val adminReportsViewModel: com.example.booking_app.feature.admin.ui.AdminReportsViewModel by viewModels()

    private val navController: NavController by lazy {
        (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController
    }

    @Inject lateinit var unreadChatsReader: UnreadChatsReader

    /** Số cuộc chưa đọc theo chế độ hiện tại: Cộng đồng của PLAYER hoặc Tin nhắn của OWNER. */
    private var unreadChats = 0

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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sessionState.collectLatest { session ->
                    if (session !is SessionState.SignedIn || session.appRole != AppRole.ADMIN) return@collectLatest
                    try {
                        // These ViewModels start protected listeners when instantiated.
                        // Create them only after the server-issued role is ADMIN.
                        adminAppsViewModel.loadQueue()
                        adminReportsViewModel.loadReports()
                        coroutineScope {
                            launch {
                                adminAppsViewModel.queueState.collect { state ->
                                    if (viewModel.lastRootGraph == R.id.nav_admin) {
                                        val count = if (state is com.example.booking_app.feature.admin.ui.AdminQueueState.Content) {
                                            state.applications.count { it.status == "PENDING" || it.status == "RESUBMITTED" }
                                        } else 0
                                        runCatching {
                                            val badge = binding.bottomNav.getOrCreateBadge(R.id.tab_admin_applications)
                                            badge.isVisible = count > 0
                                            badge.number = count
                                        }
                                    }
                                }
                            }
                            launch {
                                adminReportsViewModel.queueState.collect { state ->
                                    if (viewModel.lastRootGraph == R.id.nav_admin) {
                                        val count = if (state is com.example.booking_app.feature.admin.ui.AdminReportQueueState.Content) {
                                            state.reports.count { it.status == com.example.booking_app.feature.admin.domain.model.ReportStatus.OPEN }
                                        } else 0
                                        runCatching {
                                            val badge = binding.bottomNav.getOrCreateBadge(R.id.tab_admin_reports)
                                            badge.isVisible = count > 0
                                            badge.number = count
                                        }
                                    }
                                }
                            }
                        }
                    } finally {
                        adminAppsViewModel.stopQueue()
                        adminReportsViewModel.stopQueue()
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                unreadChatsReader.observeUnreadChatCount().catch { emit(0) }.collect { count ->
                    unreadChats = count
                    applyChatBadge()
                }
            }
        }
        setUpBottomNav()
    }

    private fun applyChatBadge() {
        val tab = when (bottomNavMenu) {
            R.menu.menu_player_bottom_nav -> R.id.tab_community
            R.menu.menu_owner_bottom_nav -> R.id.tab_owner_messages
            else -> return
        }
        if (unreadChats > 0) {
            binding.bottomNav.getOrCreateBadge(tab).apply {
                maxNumber = 99
                number = unreadChats
                isVisible = true
            }
        } else {
            binding.bottomNav.removeBadge(tab)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        com.example.booking_app.feature.booking.ui.BookingSlotGridFragment.clearViewCache()
    }

    /** Deep link `smashnow://...` tới khi app đang mở (thông báo, adb): điều hướng như lúc khởi động (spec 030 T005). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.data?.scheme == "smashnow") navController.handleDeepLink(intent)
    }

    /**
     * Một BottomNav cho cả hai chế độ, đổi menu theo graph gốc: BottomNav người chơi trong `nav_player`, OwnerNav
     * trong `nav_owner`. Chỉ hiện ở màn gốc của tab và ở trang Tài khoản. Người chơi đã nối Trang chủ, Vãng lai (Tìm kiếm,
     * Cộng đồng), Lịch đặt; chủ sân có Hôm nay, Đơn đặt, Lịch sân, Tin nhắn khách hàng và Cơ sở.
     * Tin nhắn dùng chung graph chat 091. Không có tab Tài khoản: trang
     * Tài khoản mở từ avatar ở header dùng chung (spec 011 US8) và giữ sáng tab đã mở nó.
     */
    private fun setUpBottomNav() {
        // Root đã chừa chỗ cho thanh hệ thống; không để BottomNav cộng thêm lần nữa.
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { _, insets -> insets }
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.tab_home -> {
                    navController.popBackStack(R.id.homeFragment, false, true)
                    true
                }
                R.id.tab_search -> {
                    openCourtSearch()
                    true
                }
                R.id.tab_bookings -> {
                    openMyBookings()
                    true
                }
                R.id.tab_drop_in -> {
                    openDropIn()
                    true
                }
                R.id.tab_community -> {
                    openCommunity()
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
                R.id.tab_owner_messages -> {
                    openOwnerMessages()
                    true
                }
                R.id.tab_owner_schedule -> {
                    navController.navigate(
                        R.id.ownerCalendarFragment,
                        null,
                        navOptions {
                            launchSingleTop = true
                            popUpTo(R.id.ownerHomePlaceholderFragment)
                        },
                    )
                    true
                }
                R.id.tab_owner_bookings -> {
                    navController.navigate(
                        R.id.ownerBookingsFragment,
                        null,
                        navOptions {
                            launchSingleTop = true
                            popUpTo(R.id.ownerHomePlaceholderFragment)
                        },
                    )
                    true
                }
                R.id.tab_admin_home -> {
                    navController.popBackStack(R.id.adminHomeFragment, false)
                    true
                }
                R.id.tab_admin_applications -> {
                    navController.navigate(
                        R.id.adminApplicationQueueFragment,
                        null,
                        navOptions {
                            launchSingleTop = true
                            popUpTo(R.id.adminHomeFragment)
                        },
                    )
                    true
                }
                R.id.tab_admin_reports -> {
                    navController.navigate(
                        R.id.adminReportQueueFragment,
                        null,
                        navOptions {
                            launchSingleTop = true
                            popUpTo(R.id.adminHomeFragment)
                        },
                    )
                    true
                }
                R.id.tab_admin_users -> {
                    navController.navigate(
                        R.id.adminUserManagementFragment,
                        null,
                        navOptions {
                            launchSingleTop = true
                            popUpTo(R.id.adminHomeFragment)
                        },
                    )
                    true
                }
                R.id.tab_admin_tools -> {
                    navController.navigate(
                        R.id.adminToolsDashboardFragment,
                        null,
                        navOptions {
                            launchSingleTop = true
                            popUpTo(R.id.adminHomeFragment)
                        },
                    )
                    true
                }
                else -> {
                    Snackbar.make(binding.root, getString(R.string.nav_tab_coming_soon, item.title), Snackbar.LENGTH_SHORT)
                        .setAnchorView(binding.bottomNavContainer)
                        .show()
                    false
                }
            }
        }
        // Bấm lại tab đang chọn: không tạo lại màn hình. Đang ở trang Tài khoản (mở từ avatar của tab đó) thì về màn
        // gốc của tab (spec 011 FR-029).
        binding.bottomNav.setOnItemReselectedListener { item ->
            if (navController.currentDestination?.id != R.id.accountFragment) return@setOnItemReselectedListener
            when (item.itemId) {
                R.id.tab_home -> navController.popBackStack(R.id.homeFragment, false)
                R.id.tab_search -> navController.popBackStack(R.id.courtSearchFragment, false)
                R.id.tab_bookings -> openMyBookings()
                R.id.tab_drop_in -> openDropIn()
                R.id.tab_community -> openCommunity()
                R.id.tab_owner_today -> navController.popBackStack(R.id.ownerHomePlaceholderFragment, false)
                R.id.tab_owner_venues -> openOwnerVenues()
                R.id.tab_owner_messages -> openOwnerMessages()
            }
        }
        navController.addOnDestinationChangedListener { _, destination, _ -> updateBottomNav(destination) }
    }

    private fun openMyBookings() {
        navController.navigate(
            R.id.myBookingsFragment,
            null,
            navOptions {
                launchSingleTop = true
                popUpTo(R.id.homeFragment) { saveState = true }
            },
        )
    }

    private fun openCourtSearch() {
        navController.navigate(R.id.courtSearchFragment, null, navOptions {
            launchSingleTop = true
            restoreState = true
            popUpTo(R.id.homeFragment) { saveState = true }
        })
    }

    /** Tab Vãng lai (spec 080 T015): danh sách buổi, màn gốc `dropInListFragment` của graph `nav_dropin`. */
    private fun openDropIn() {
        navController.navigate(
            R.id.nav_dropin,
            null,
            navOptions {
                launchSingleTop = true
                popUpTo(R.id.homeFragment) { saveState = true }
            },
        )
    }

    /** Tab Cộng đồng (spec 090 T019): màn gốc `communityHomeFragment` của graph `nav_community`. */
    private fun openCommunity() {
        navController.navigate(
            R.id.nav_community,
            null,
            navOptions {
                launchSingleTop = true
                popUpTo(R.id.homeFragment)
            },
        )
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

    private fun openOwnerMessages() {
        navController.navigate(
            deepLink("smashnow://chat/owner"),
            navOptions {
                launchSingleTop = true
                popUpTo(R.id.ownerHomePlaceholderFragment)
            },
        )
    }

    private fun updateBottomNav(destination: NavDestination) {
        // Graph gốc quyết định menu; khách (lastRootGraph = null) dùng graph người chơi.
        val ownerMode = viewModel.lastRootGraph == R.id.nav_owner
        val adminMode = viewModel.lastRootGraph == R.id.nav_admin
        val tab = when {
            adminMode -> when (destination.id) {
                R.id.adminHomeFragment -> R.id.tab_admin_home
                R.id.adminApplicationQueueFragment -> R.id.tab_admin_applications
                R.id.adminReportQueueFragment -> R.id.tab_admin_reports
                R.id.adminUserManagementFragment -> R.id.tab_admin_users
                R.id.adminToolsDashboardFragment -> R.id.tab_admin_tools
                else -> null
            }
            ownerMode -> when (destination.id) {
                R.id.ownerHomePlaceholderFragment -> R.id.tab_owner_today
                R.id.ownerCalendarFragment -> R.id.tab_owner_schedule
                R.id.ownerBookingsFragment -> R.id.tab_owner_bookings
                R.id.ownerVenueListFragment -> R.id.tab_owner_venues
                R.id.ownerChatListFragment -> R.id.tab_owner_messages
                // Không có tab Tài khoản: giữ sáng tab đã mở trang này (avatar ở Hôm nay → Hôm nay).
                R.id.accountFragment ->
                    when (navController.previousBackStackEntry?.destination?.id) {
                        R.id.ownerVenueListFragment -> R.id.tab_owner_venues
                        R.id.ownerChatListFragment -> R.id.tab_owner_messages
                        else -> R.id.tab_owner_today
                    }
                else -> null
            }
            else -> when (destination.id) {
                R.id.homeFragment -> R.id.tab_home
                R.id.courtSearchFragment -> R.id.tab_search
                R.id.myBookingsFragment -> R.id.tab_bookings
                R.id.dropInListFragment -> R.id.tab_drop_in
                R.id.communityHomeFragment -> R.id.tab_community
                // Giữ sáng tab đã mở trang Tài khoản.
                R.id.accountFragment -> when (navController.previousBackStackEntry?.destination?.id) {
                    R.id.courtSearchFragment -> R.id.tab_search
                    R.id.myBookingsFragment -> R.id.tab_bookings
                    R.id.dropInListFragment -> R.id.tab_drop_in
                    R.id.communityHomeFragment -> R.id.tab_community
                    else -> R.id.tab_home
                }
                else -> null
            }
        }
        val currentRootId = when {
            adminMode -> R.id.nav_admin
            ownerMode -> R.id.nav_owner
            else -> R.id.nav_player
        }
        val notificationInbox = destination.id == R.id.notificationFragment
        val visible = (tab != null || notificationInbox) && hasBackStackEntry(currentRootId)
        binding.bottomNavContainer.isVisible = visible
        if (!visible) return
        val menuRes = when {
            adminMode -> R.menu.menu_admin_bottom_nav
            ownerMode -> R.menu.menu_owner_bottom_nav
            else -> R.menu.menu_player_bottom_nav
        }
        if (bottomNavMenu != menuRes) {
            binding.bottomNav.removeBadge(R.id.tab_community)
            binding.bottomNav.removeBadge(R.id.tab_owner_messages)
            binding.bottomNav.menu.clear()
            binding.bottomNav.inflateMenu(menuRes)
            bottomNavMenu = menuRes
            applyChatBadge()
        }
        tab?.let { binding.bottomNav.menu.findItem(it)?.isChecked = true }
        if (adminMode && (viewModel.sessionState.value as? SessionState.SignedIn)?.appRole == AppRole.ADMIN) {
            adminAppsViewModel.loadQueue()
            adminReportsViewModel.loadReports()
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
        // M1: mở app lạnh bằng deep link, NavController dựng [launcher, màn deep link] mà không có graph gốc bên dưới
        // (đường điều hướng thường luôn popUpTo nav_main nên launcher không còn). Màn deep link có thể đã tự mở
        // Đăng nhập trước khi phiên về tới đây; dựng lại từ đầu rồi mở lại deep link (trừ link auth).
        if (previous == null && hasBackStackEntry(R.id.launcherFragment)) {
            val decision = SessionRouter.startDestination(state, viewModel.welcomeSeen()) ?: return
            // Bước đồng ý điều khoản: dọn [launcher, màn deep link] để openConsent không mở chồng lên.
            if (decision == RouteDecision.Consent) resetTo(R.id.nav_player)
            execute(decision)
            // Link auth (đăng nhập/đăng ký từ ngoài app) chỉ mở lại cho khách, không mở cho người đã đăng nhập.
            val deepLink = intent?.data
                ?.takeIf { it.scheme == "smashnow" && (it.host != "auth" || decision is RouteDecision.PlayerGuest) }
            if (deepLink != null && SessionRouter.replayDeepLinkAfter(decision)) {
                runCatching { navController.navigate(deepLink) }
            }
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
                // returnTo có thể đến từ deep link ngoài app (spec 030 T005): chỉ nhận URI smashnow:// có trong graph.
                // Màn vừa lộ ra được mở đúng bằng link returnTo (deep link lúc mở app) thì không mở chồng bản thứ hai.
                decision.returnTo?.takeIf { it.scheme == "smashnow" && it != currentDeepLinkUri() }?.let { uri ->
                    runCatching { navController.navigate(uri) }
                }
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

    @Suppress("DEPRECATION")
    private fun currentDeepLinkUri(): Uri? =
        navController.currentBackStackEntry?.arguments?.getParcelable<Intent>(NavController.KEY_DEEP_LINK_INTENT)?.data

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

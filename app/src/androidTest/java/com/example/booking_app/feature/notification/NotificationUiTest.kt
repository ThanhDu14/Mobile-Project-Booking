package com.example.booking_app.feature.notification

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import com.example.booking_app.R
import com.example.booking_app.feature.notification.data.NotificationModule
import com.example.booking_app.feature.notification.domain.*
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.testing.*
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.hamcrest.Matchers.allOf

class NotificationTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, "dagger.hilt.android.testing.HiltTestApplication", context)
}

object NotificationUiFixture {
    val uids = MutableStateFlow<String?>("test-user")
    var allItems: List<Notification>? = null
    var allReadCalls = 0
    data class Cursor(val offset: Int) : NotificationCursor
    var pushItem: Notification? = null
    val lookedUp = mutableListOf<String>()
    val marked = mutableListOf<String>()
    var response = CompletableDeferred<NotificationPage>()
    var failSave = false
    var fromCache = false
    var values = NewAccountNotificationPreferences.defaults()
    var preferences = CompletableDeferred<Map<NotificationType, Boolean>>().apply { complete(values) }
    fun reset() { uids.value = "test-user"; allItems = null; allReadCalls = 0; pushItem = null; lookedUp.clear(); marked.clear(); response = CompletableDeferred(); values = NewAccountNotificationPreferences.defaults(); failSave = false; fromCache = false; preferences = CompletableDeferred<Map<NotificationType, Boolean>>().apply { complete(values) } }
}
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [NotificationModule::class])
object NotificationUiModule {
    @Provides fun session(): NotificationSession = object : NotificationSession { override val uids = NotificationUiFixture.uids }
    @Provides fun repository(): NotificationRepository = object : NotificationRepository {
        override suspend fun loadPage(after: NotificationCursor?): NotificationPage {
            val items = NotificationUiFixture.allItems ?: return NotificationUiFixture.response.await()
            val offset = (after as? NotificationUiFixture.Cursor)?.offset ?: 0
            return NotificationPage(items.drop(offset).take(30),
                if (offset + 30 < items.size) NotificationUiFixture.Cursor(offset + 30) else null)
        }
        override suspend fun loadById(id: String): Notification? { NotificationUiFixture.lookedUp += id; return NotificationUiFixture.pushItem }
        override suspend fun markRead(id: String) { NotificationUiFixture.marked += id }
        override suspend fun markAllRead() {
            NotificationUiFixture.allReadCalls++
            NotificationUiFixture.allItems = NotificationUiFixture.allItems?.map { it.copy(isRead = true) }
        }
    }
    @Provides fun preferences(): NotificationPreferencesRepository = object : NotificationPreferencesRepository {
        override suspend fun load() = NotificationUiFixture.preferences.await()
        override suspend fun loadSnapshot() = NotificationPreferenceSnapshot(load(), NotificationUiFixture.fromCache)
        override suspend fun setEnabled(types: Set<NotificationType>, enabled: Boolean) {
            if (NotificationUiFixture.failSave) error("offline")
            NotificationUiFixture.values = NotificationUiFixture.values + types.associateWith { enabled }
        }
    }
    @Provides fun resolver(): NotificationRouteResolver = object : NotificationRouteResolver {
        override suspend fun resolve(item: Notification): String? = null
    }
}
@HiltAndroidTest
class NotificationUiTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @javax.inject.Inject lateinit var auth: com.google.firebase.auth.FirebaseAuth
    @javax.inject.Inject lateinit var sessionPrefs: com.example.booking_app.core.session.SessionPrefs
    private fun awaitDisplayed(matcher: org.hamcrest.Matcher<android.view.View>) {
        onView(isRoot()).perform(object : androidx.test.espresso.ViewAction {
            override fun getDescription() = "wait for notification screen state"
            override fun getConstraints(): org.hamcrest.Matcher<android.view.View> = isRoot()
            override fun perform(controller: androidx.test.espresso.UiController, root: android.view.View) {
                val deadline = android.os.SystemClock.uptimeMillis() + 8000
                while (android.os.SystemClock.uptimeMillis() < deadline) {
                    if (androidx.test.espresso.util.TreeIterables.breadthFirstViewTraversal(root).any { matcher.matches(it) && isDisplayed().matches(it) }) return
                    controller.loopMainThreadForAtLeast(50)
                }
                throw AssertionError("Notification screen did not reach expected state: $matcher")
            }
        })
    }
    private fun start(settings: Boolean = false, pending: Boolean = false, mixed: Boolean = false, navigation: Boolean = false): ActivityScenario<NotificationTestActivity> {
        hilt.inject()
        NotificationUiFixture.reset()
        if (pending) NotificationUiFixture.preferences = CompletableDeferred()
        if (mixed) {
            NotificationUiFixture.values = NotificationUiFixture.values + (NotificationType.BOOKING_CONFIRMED to false)
            NotificationUiFixture.preferences = CompletableDeferred<Map<NotificationType, Boolean>>().apply { complete(NotificationUiFixture.values) }
        }
        val dark = InstrumentationRegistry.getArguments().getString("notificationTheme") == "dark"
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setDefaultNightMode(if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        }
        return ActivityScenario.launch(Intent(ApplicationProvider.getApplicationContext<Context>(), NotificationTestActivity::class.java).putExtra("settings", settings).putExtra("navigation", navigation))
    }
    private fun screenshot(name: String) {
        onView(isRoot()).perform(object : androidx.test.espresso.ViewAction {
            override fun getDescription() = "wait for final window animation frame before capture"
            override fun getConstraints(): org.hamcrest.Matcher<android.view.View> = isRoot()
            override fun perform(controller: androidx.test.espresso.UiController, view: android.view.View) { controller.loopMainThreadForAtLeast(700) }
        })
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val config = instrumentation.targetContext.resources.configuration
        val theme = InstrumentationRegistry.getArguments().getString("notificationTheme", "light")
        val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "notification-${config.screenWidthDp}dp-$theme-$name.png")
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun inboxLoadingErrorRetryEmpty() {
        start().use {
            onView(withId(R.id.progress)).check(matches(isDisplayed()))
            NotificationUiFixture.response.completeExceptionally(IllegalStateException("offline"))
            awaitDisplayed(withId(R.id.retry))
            onView(withId(R.id.retry)).check(matches(isDisplayed()))
            NotificationUiFixture.response = CompletableDeferred<NotificationPage>().apply { complete(NotificationPage(emptyList(), null)) }
            onView(withId(R.id.retry)).perform(click())
            awaitDisplayed(withText(R.string.notification_empty))
            onView(withText(R.string.notification_empty)).check(matches(isDisplayed()))
        }
    }
    @Test fun inboxPagesThirtyThenSixtyThenSixtyFiveAndMarksUnloadedRowsRead() {
        start().use {
            val items = (1..65).map { n -> Notification("paged-$n", "SYSTEM", "Paged $n", "Saved detail $n", emptyMap(), false, 1791018000000L - n) }
            NotificationUiFixture.allItems = items
            NotificationUiFixture.response.complete(NotificationPage(items.take(30), NotificationUiFixture.Cursor(30)))
            awaitDisplayed(withText("Paged 1"))
            onView(withId(R.id.list)).check { view, _ -> org.junit.Assert.assertEquals(30, (view as androidx.recyclerview.widget.RecyclerView).adapter!!.itemCount) }
            onView(withId(R.id.more)).perform(click())
            awaitDisplayed(allOf(withId(R.id.unread), withText("60 chưa đọc")))
            onView(withText("Paged 1")).perform(click())
            awaitDisplayed(withText("Saved detail 1"))
            onView(withId(android.R.id.button1)).perform(click())
            org.junit.Assert.assertEquals(listOf("paged-1"), NotificationUiFixture.marked)
            onView(withId(R.id.markAll)).perform(click())
            awaitDisplayed(allOf(withId(R.id.unread), withText("0 chưa đọc")))
            onView(withId(R.id.more)).perform(click())
            onView(withId(R.id.list)).check { view, _ -> org.junit.Assert.assertEquals(65, (view as androidx.recyclerview.widget.RecyclerView).adapter!!.itemCount) }
            onView(withId(R.id.unread)).check(matches(withText("0 chưa đọc")))
            org.junit.Assert.assertEquals(1, NotificationUiFixture.allReadCalls)
        }
    }
    @Test fun logoutClearsInboxAndNewAccountCannotSeePreviousRows() {
        start().use {
            NotificationUiFixture.response.complete(NotificationPage(listOf(Notification("old", "SYSTEM", "Old account item", "Saved", emptyMap(), false, 1)), null))
            awaitDisplayed(withText("Old account item"))
            NotificationUiFixture.uids.value = null
            awaitDisplayed(withText(R.string.notification_empty))
            onView(withId(R.id.list)).check(matches(org.hamcrest.Matchers.not(isDisplayed())))
            onView(withId(R.id.list)).check { view, _ -> org.junit.Assert.assertEquals(0, (view as androidx.recyclerview.widget.RecyclerView).adapter!!.itemCount) }
            NotificationUiFixture.response = CompletableDeferred<NotificationPage>().apply { complete(NotificationPage(emptyList(), null)) }
            NotificationUiFixture.uids.value = "new-account"
            awaitDisplayed(withText(R.string.notification_empty))
            onView(withId(R.id.list)).check(matches(org.hamcrest.Matchers.not(isDisplayed())))
            onView(withId(R.id.list)).check { view, _ -> org.junit.Assert.assertEquals(0, (view as androidx.recyclerview.widget.RecyclerView).adapter!!.itemCount) }
        }
    }
    @Test fun cachedSettingsDisableWritesAndRetryRestoresEditing() {
        start(settings = true, pending = true).use {
            NotificationUiFixture.fromCache = true
            NotificationUiFixture.preferences.complete(NotificationUiFixture.values)
            awaitDisplayed(withText(R.string.notification_cached_settings))
            onView(withText(R.string.notification_cached_settings)).check(matches(isDisplayed()))
            onView(allOf(withId(R.id.check), withContentDescription(R.string.notification_promotion)))
                .check(matches(org.hamcrest.Matchers.not(isEnabled())))
            screenshot("settings-cache")
            NotificationUiFixture.fromCache = false
            onView(withId(R.id.retry)).perform(click())
            awaitDisplayed(allOf(withId(R.id.check), withContentDescription(R.string.notification_promotion), isEnabled()))
            onView(allOf(withId(R.id.check), withContentDescription(R.string.notification_promotion))).check(matches(isEnabled()))
            onView(withText(R.string.notification_promotion)).perform(click())
            org.junit.Assert.assertTrue(NotificationUiFixture.values.getValue(NotificationType.PROMOTION))
        }
    }
    @Test fun inboxContentReadAndScreenshot() {
        start().use {
            NotificationUiFixture.response.complete(NotificationPage(listOf(
                Notification("one", "BOOKING_CONFIRMED", "Đơn đặt sân đã được xác nhận", "Sân Kỳ Hòa Center · T5 08/10 · 18:00–19:30. Mở mã QR khi đến sân.", emptyMap(), false, 1791018000000),
                Notification("two", "DROPIN_SPOT_OPENED", "Đã có chỗ cho bạn", "Bạn được nhận vào buổi vãng lai Sân Nguyễn Du T6 09/10 18:30. Vé QR đã sẵn sàng.", emptyMap(), false, 1791017000000),
                Notification("three", "BOOKING_REJECTED", "Đơn bị từ chối · Quá hạn duyệt", "Chủ sân Sân Nguyễn Du không duyệt kịp. Đã hoàn 120.000 đ vào ví giả lập.", emptyMap(), false, 1791016000000),
                Notification("four", "BOOKING_REMINDER", "Sắp tới giờ chơi", "Còn 2 giờ: Sân Phú Thọ, Sân 2, 17:00.", emptyMap(), true, 1791015000000),
                Notification("five", "REVIEW_REMINDER", "Đánh giá buổi chơi của bạn", "Bạn vừa chơi ở Sân Phú Thọ. Đánh giá giúp người khác chọn sân (còn 7 ngày).", emptyMap(), true, 1791014000000),
                Notification("six", "GROUP_ACTIVITY", "CLB Cầu lông Q10 có sự kiện mới", "Giao lưu tối thứ Bảy 10/10, 19:00 tại Sân Hòa Bình.", emptyMap(), true, 1791013000000),
                Notification("seven", "SYSTEM", "Thông báo hệ thống", "SmashNow bảo trì 23:00–23:30 ngày 12/10.", emptyMap(), true, 1791012000000),
            ), null))
            awaitDisplayed(withText("Đơn đặt sân đã được xác nhận"))
            onView(withText("Đơn đặt sân đã được xác nhận")).check(matches(isDisplayed()))
            screenshot("inbox")
            onView(withText("Đơn đặt sân đã được xác nhận")).perform(click())
            awaitDisplayed(withId(android.R.id.button1))
            onView(withId(android.R.id.button1)).perform(click())
            awaitDisplayed(allOf(withId(R.id.unread), withText("2 chưa đọc")))
            onView(withId(R.id.markAll)).perform(click())
            onView(withId(R.id.unread)).check(matches(withText("0 chưa đọc")))
        }
    }
    @Test fun settingsGroupsMandatoryAndScreenshot() {
        start(true).use {
            awaitDisplayed(withText(R.string.notification_booking_result))
            onView(withText(R.string.notification_booking_result)).check(matches(isDisplayed()))
            screenshot("settings")
            onView(withText(R.string.notification_promotion)).perform(click())
            org.junit.Assert.assertTrue(NotificationUiFixture.values.getValue(NotificationType.PROMOTION))
            org.junit.Assert.assertTrue(NotificationUiFixture.values.getValue(NotificationType.BOOKING_NEW))
            onView(allOf(withId(R.id.check), withParent(hasDescendant(withText(R.string.notification_booking_new)))))
                .perform(scrollTo()).check(matches(org.hamcrest.Matchers.not(isEnabled())))
            onView(withText(R.string.notification_owner_result)).perform(scrollTo(), click())
            for (type in listOf(NotificationType.OWNER_APPROVED, NotificationType.OWNER_REJECTED, NotificationType.OWNER_REVOKED)) {
                org.junit.Assert.assertFalse(NotificationUiFixture.values.getValue(type))
            }
            onView(withText(R.string.notification_system_pref)).perform(scrollTo(), click())
            org.junit.Assert.assertFalse(NotificationUiFixture.values.getValue(NotificationType.SYSTEM))
            org.junit.Assert.assertTrue(NotificationUiFixture.values.getValue(NotificationType.BOOKING_NEW))
            screenshot("settings-extra")
        }
    }
    @Test fun settingsSaveFailureKeepsPreviousValueAndRetrySaves() {
        start(true).use {
            NotificationUiFixture.failSave = true
            onView(withText(R.string.notification_promotion)).perform(click())
            onView(withId(R.id.retry)).check(matches(isDisplayed()))
            org.junit.Assert.assertFalse(NotificationUiFixture.values.getValue(NotificationType.PROMOTION))
            NotificationUiFixture.failSave = false
            onView(withId(R.id.retry)).perform(click())
            org.junit.Assert.assertTrue(NotificationUiFixture.values.getValue(NotificationType.PROMOTION))
        }
    }
    @Test fun settingsInitialLoadingErrorAndRetry() {
        start(true, pending = true).use {
            onView(withId(R.id.progress)).check(matches(isDisplayed()))
            NotificationUiFixture.preferences.completeExceptionally(IllegalStateException("offline"))
            awaitDisplayed(withId(R.id.retry))
            NotificationUiFixture.preferences = CompletableDeferred<Map<NotificationType, Boolean>>().apply { complete(NotificationUiFixture.values) }
            onView(withId(R.id.retry)).perform(click())
            awaitDisplayed(withText(R.string.notification_booking_result))
        }
    }
    @Test fun settingsMixedGroupEnablesAllMembers() {
        start(true, mixed = true).use {
            val checkbox = allOf(withId(R.id.check), withParent(hasDescendant(withText(R.string.notification_booking_result))))
            val mixed = object : org.hamcrest.TypeSafeMatcher<android.view.View>() {
                override fun describeTo(description: org.hamcrest.Description) { description.appendText("indeterminate MaterialCheckBox") }
                override fun matchesSafely(view: android.view.View) = view is com.google.android.material.checkbox.MaterialCheckBox && view.checkedState == com.google.android.material.checkbox.MaterialCheckBox.STATE_INDETERMINATE
            }
            awaitDisplayed(allOf(checkbox, mixed))
            onView(checkbox).perform(click())
            org.junit.Assert.assertTrue(NotificationUiFixture.values.getValue(NotificationType.BOOKING_CONFIRMED))
            org.junit.Assert.assertTrue(NotificationUiFixture.values.getValue(NotificationType.BOOKING_REJECTED))
            org.junit.Assert.assertTrue(NotificationUiFixture.values.getValue(NotificationType.BOOKING_CANCELLED))
        }
    }
    @Test fun rootGraphInboxSettingsAndBack() {
        start(navigation = true).use { scenario ->
            scenario.onActivity { activity ->
                val nav = (activity.supportFragmentManager.fragments.single() as androidx.navigation.fragment.NavHostFragment).navController
                nav.navigate(android.net.Uri.parse("smashnow://notifications"))
            }
            awaitDisplayed(withContentDescription(R.string.notification_settings_title))
            onView(withContentDescription(R.string.notification_settings_title)).perform(click())
            awaitDisplayed(withText(R.string.notification_note))
            onView(withContentDescription(R.string.notification_back)).perform(click())
            scenario.onActivity { activity ->
                val nav = (activity.supportFragmentManager.fragments.single() as androidx.navigation.fragment.NavHostFragment).navController
                org.junit.Assert.assertEquals(R.id.notificationFragment, nav.currentDestination?.id)
                nav.navigate(android.net.Uri.parse("smashnow://notifications/settings"))
            }
            awaitDisplayed(withText(R.string.notification_note))
        }
    }
    @Test fun pushDeepLinkLoadsSavedItemOutsideFirstPageAndRejectsForeignAccount() {
        start(navigation = true).use { scenario ->
            NotificationUiFixture.response.complete(NotificationPage(emptyList(), null))
            NotificationUiFixture.pushItem = Notification("push-id", "SYSTEM", "Saved push title", "Saved push body", emptyMap(), false, 1)
            scenario.onActivity { activity ->
                val nav = (activity.supportFragmentManager.fragments.single() as androidx.navigation.fragment.NavHostFragment).navController
                nav.navigate(android.net.Uri.parse("smashnow://notifications/test-user/push-id"))
            }
            // The auto-open dialog owns focus while Espresso may still select the activity root.
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val deadline = android.os.SystemClock.uptimeMillis() + 8000
            var dialog = automation.rootInActiveWindow
            while (dialog?.findAccessibilityNodeInfosByText("Saved push body").isNullOrEmpty()
                && android.os.SystemClock.uptimeMillis() < deadline) {
                Thread.sleep(25)
                dialog = automation.rootInActiveWindow
            }
            val visibleDialog = checkNotNull(dialog)
            org.junit.Assert.assertTrue(visibleDialog.findAccessibilityNodeInfosByText("Saved push body").any { it.isVisibleToUser })
            org.junit.Assert.assertTrue(visibleDialog.findAccessibilityNodeInfosByText("Saved push title").any { it.isVisibleToUser })
            org.junit.Assert.assertEquals(listOf("push-id"), NotificationUiFixture.lookedUp)
            org.junit.Assert.assertEquals(listOf("push-id"), NotificationUiFixture.marked)
            val close = visibleDialog.findAccessibilityNodeInfosByViewId("android:id/button1").single()
            org.junit.Assert.assertTrue(close.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            awaitDisplayed(withText(R.string.notification_empty))
            scenario.onActivity { activity ->
                val nav = (activity.supportFragmentManager.fragments.single() as androidx.navigation.fragment.NavHostFragment).navController
                nav.navigate(android.net.Uri.parse("smashnow://notifications/foreign/other-id"))
            }
            awaitDisplayed(withText(R.string.notification_empty))
            org.junit.Assert.assertEquals(listOf("push-id"), NotificationUiFixture.lookedUp)
        }
    }
    @Test fun mainActivityNotificationEntryAndShell() {
        start().close()
        org.junit.Assert.assertEquals("demo-admin-repair", auth.app.options.projectId)
        auth.signOut()
        runBlocking { sessionPrefs.setWelcomeSeen() }
        NotificationUiFixture.response.complete(NotificationPage(listOf(
            Notification("one", "BOOKING_CONFIRMED", "Đơn đặt sân đã được xác nhận", "Sân Kỳ Hòa Center · T5 08/10 · 18:00–19:30. Mở mã QR khi đến sân.", emptyMap(), false, 1791018000000),
            Notification("two", "DROPIN_SPOT_OPENED", "Đã có chỗ cho bạn", "Bạn được nhận vào buổi vãng lai Sân Nguyễn Du T6 09/10 18:30. Vé QR đã sẵn sàng.", emptyMap(), false, 1791017000000),
            Notification("three", "BOOKING_REJECTED", "Đơn bị từ chối · Quá hạn duyệt", "Chủ sân Sân Nguyễn Du không duyệt kịp. Đã hoàn 120.000 đ vào ví giả lập.", emptyMap(), false, 1791016000000),
        ), null))
        ActivityScenario.launch(com.example.booking_app.MainActivity::class.java).use {
            awaitDisplayed(withContentDescription(R.string.notification_title))
            onView(withContentDescription(R.string.notification_title)).perform(click())
            awaitDisplayed(withText("Đơn đặt sân đã được xác nhận"))
            onView(withId(R.id.bottomNavContainer)).check(matches(isDisplayed()))
            screenshot("inbox-shell")
            onView(withContentDescription(R.string.notification_settings_title)).perform(click())
            awaitDisplayed(withText(R.string.notification_note))
            screenshot("settings-shell")
        }
    }
}

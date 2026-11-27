package com.example.booking_app.feature.notification

import android.Manifest
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.example.booking_app.R
import com.example.booking_app.feature.notification.domain.NotificationPage
import com.example.booking_app.feature.notification.domain.NotificationType
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.Matchers.not
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Run separately with POST_NOTIFICATIONS revoked before launching instrumentation (revocation kills the process). */
@HiltAndroidTest
class NotificationPermissionUiTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        }
    }
    private fun awaitCondition(description: String, condition: () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + 10000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        throw AssertionError(description)
    }
    private fun nodes(root: AccessibilityNodeInfo?): Sequence<AccessibilityNodeInfo> = sequence {
        if (root != null) {
            yield(root)
            for (i in 0 until root.childCount) yieldAll(nodes(root.getChild(i)))
        }
    }
    private fun capture(name: String) {
        // View assertions may pass before the OS compositor finishes a window transition.
        instrumentation.waitForIdleSync()
        Thread.sleep(800)
        instrumentation.waitForIdleSync()
        assertEquals("Capture only the isolated test app", context.packageName,
            instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString())
        val file = java.io.File(context.getExternalFilesDir(null), "notification-permission-$name.png")
        file.outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun grantedPermissionHidesNoticeWithoutChangingPreferences() {
        assertTrue(android.os.Build.VERSION.SDK_INT >= 33)
        assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        hilt.inject()
        NotificationUiFixture.reset()
        ActivityScenario.launch<NotificationTestActivity>(Intent(context, NotificationTestActivity::class.java).putExtra("settings", true)).use {
            awaitCondition("Preferences loaded") {
                runCatching { onView(withText(R.string.notification_booking_result)).check(matches(isDisplayed())); true }.getOrDefault(false)
            }
            onView(withId(R.id.osSettings)).perform(click())
            awaitCondition("Android permission grant button") {
                nodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull {
                    it.viewIdResourceName?.endsWith(":id/permission_allow_button") == true
                }?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            }
            awaitCondition("Permission notice hidden") {
                runCatching { onView(withId(R.id.permission)).check(matches(not(isDisplayed()))); true }.getOrDefault(false)
            }
            assertEquals(false, NotificationUiFixture.values[NotificationType.PROMOTION])
            assertEquals(true, NotificationUiFixture.values[NotificationType.BOOKING_NEW])
            capture("granted-prompt")
        }
    }

    @Test fun deniedPermissionKeepsPreferencesAndInboxAndRefreshesAfterSystemSettingsGrant() {
        assertTrue("This device scenario requires Android 13+", android.os.Build.VERSION.SDK_INT >= 33)
        assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        hilt.inject()
        NotificationUiFixture.reset()
        val intent = Intent(context, NotificationTestActivity::class.java).putExtra("settings", true)
        ActivityScenario.launch<NotificationTestActivity>(intent).use { scenario ->
            awaitCondition("Preferences loaded") {
                runCatching { onView(withText(R.string.notification_booking_result)).check(matches(isDisplayed())); true }.getOrDefault(false)
            }
            onView(withId(R.id.osSettings)).check(matches(withText(R.string.notification_permission_enable))).perform(click())
            awaitCondition("Android permission prompt") {
                val deny = nodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull {
                    it.viewIdResourceName?.endsWith(":id/permission_deny_button") == true
                }
                deny?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            }
            awaitCondition("Denial offers OS settings without blocking preferences") {
                runCatching { onView(withId(R.id.osSettings)).check(matches(withText(R.string.notification_permission_settings))); true }.getOrDefault(false)
            }
            onView(withId(R.id.permission)).check(matches(isDisplayed()))
            onView(withText(R.string.notification_promotion)).perform(click())
            awaitCondition("Preferences still writable when denied") { NotificationUiFixture.values[NotificationType.PROMOTION] == true }
            capture("denied")
            NotificationUiFixture.response.complete(NotificationPage(emptyList(), null))
            scenario.onActivity { activity ->
                activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.notification_test_container, com.example.booking_app.feature.notification.ui.NotificationFragment())
                    .addToBackStack("denied-inbox").commit()
            }
            awaitCondition("Inbox available while permission denied") {
                runCatching { onView(withText(R.string.notification_empty)).check(matches(isDisplayed())); true }.getOrDefault(false)
            }
            capture("denied-inbox")
            scenario.onActivity { it.supportFragmentManager.popBackStackImmediate() }
            // Open the real OS settings screen, grant via the shell test fixture, then return through onResume.
            scenario.recreate()
            onView(withId(R.id.osSettings)).check(matches(withText(R.string.notification_permission_settings))).perform(click())
            awaitCondition("OS settings screen") {
                val packageName = instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()
                // vivo delegates the Android settings intent to its system UI plugin.
                packageName?.contains("settings") == true || packageName == "com.vivo.systemuiplugin"
            }
            shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
            instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            awaitCondition("Settings return refreshes permission state") {
                runCatching { onView(withId(R.id.permission)).check(matches(not(isDisplayed()))); true }.getOrDefault(false)
            }
            capture("granted-return")
        }
        // The same inbox remains available after granting permission.
        ActivityScenario.launch<NotificationTestActivity>(Intent(context, NotificationTestActivity::class.java)).use {
            awaitCondition("Inbox available") {
                runCatching { onView(withText(R.string.notification_empty)).check(matches(isDisplayed())); true }.getOrDefault(false)
            }
        }
    }
}

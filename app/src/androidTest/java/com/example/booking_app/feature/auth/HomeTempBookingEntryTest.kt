package com.example.booking_app.feature.auth

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.booking_app.MainActivity
import com.example.booking_app.R
import org.hamcrest.Matcher
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Kiểm tra nút TẠM "Đặt sân theo giờ" tại Trang chủ người chơi (TODO(030) - Feature 040 entry).
 */
@RunWith(AndroidJUnit4::class)
class HomeTempBookingEntryTest {

    @Test
    fun testHomeTempBookingEntry_navigatesToGrid_andBack() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        // Nếu app đang ở màn hình Chào (WelcomeFragment), bấm "Khám phá không cần tài khoản" (browseButton)
        // để vào Trang chủ ở chế độ khách.
        waitUntil(5_000L) {
            runCatching { onView(withId(R.id.browseButton)).check(matches(isDisplayed())) }.isSuccess ||
                runCatching { onView(withId(R.id.title)).check(matches(isDisplayed())) }.isSuccess
        }
        if (runCatching { onView(withId(R.id.browseButton)).check(matches(isDisplayed())) }.isSuccess) {
            onView(withId(R.id.browseButton)).perform(click())
        }

        // 1. Chờ tới khi Trang chủ người chơi hiển thị
        waitUntilDisplayed(withId(R.id.title))
        onView(withId(R.id.title)).check(matches(withText(R.string.auth_home_placeholder_player)))

        // Nút tạm "Đặt sân theo giờ" xuất hiện và hiển thị đúng text
        onView(withId(R.id.btnTempBookingGrid))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.booking_home_temp_entry)))

        // BottomNav đang hiển thị ở Trang chủ
        onView(withId(R.id.bottomNavContainer)).check(matches(isDisplayed()))

        // 2. Bấm nút "Đặt sân theo giờ"
        onView(withId(R.id.btnTempBookingGrid)).perform(click())

        // 3. Vào màn "Chọn khung giờ" (Feature 040)
        waitUntilDisplayed(withText(R.string.booking_title))
        onView(withText(R.string.booking_title)).check(matches(isDisplayed()))

        // 4. Bấm Back từ lưới
        pressBack()

        // 5. Quay về lại Trang chủ, tiêu đề, nút tạm và BottomNav hiển thị lại đầy đủ
        waitUntilDisplayed(withId(R.id.btnTempBookingGrid))
        onView(withId(R.id.btnTempBookingGrid)).check(matches(isDisplayed()))
        onView(withId(R.id.bottomNavContainer)).check(matches(isDisplayed()))

        scenario.close()
    }

    private fun waitUntilDisplayed(matcher: Matcher<View>, timeoutMs: Long = 15_000L) =
        onView(matcher).also { interaction ->
            waitUntil(timeoutMs) { runCatching { interaction.check(matches(isDisplayed())) }.isSuccess }
        }

    private fun waitUntil(timeoutMs: Long = 15_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Hết $timeoutMs ms mà chưa đạt điều kiện" }
            Thread.sleep(250L)
        }
    }
}

package com.example.booking_app.feature.auth

import android.content.Context
import android.os.ParcelFileDescriptor
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.booking_app.BuildConfig
import com.example.booking_app.MainActivity
import com.example.booking_app.R
import com.google.firebase.auth.FirebaseAuth
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * T075: đăng ký email → màn hình chính; đăng xuất → đăng nhập lại; Back ở màn hình chính thoát app.
 *
 * Chỉ chạy với Firebase Emulator (`USE_EMULATOR=true` trong `local.properties`) và `on-signup` chạy local
 * (quickstart §2), để không tạo tài khoản trên project thật. Chạy trên máy ảo đã cài app mới:
 * `firebase emulators:start --only auth,firestore`, `supabase functions serve on-signup`, rồi
 * `./gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class RegisterAndLoginFlowTest {

    private val email = "e2e.${System.currentTimeMillis()}@example.com"
    private val password = "abc12345"
    private var autofillService: String? = null

    @Before
    fun setUp() {
        assumeTrue("Chỉ chạy với Firebase Emulator (USE_EMULATOR=true)", BuildConfig.USE_EMULATOR == "true")
        // Như cài mới: chưa qua màn hình chào (welcome_seen) và không còn PendingSignup của lần chạy trước.
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.filesDir, "datastore/session_prefs.preferences_pb").delete()
        // Hộp thoại "Lưu mật khẩu" của dịch vụ autofill che app sau khi bấm Đăng ký, Espresso không thấy view.
        autofillService = shell("settings get secure autofill_service")
        shell("settings put secure autofill_service null")
    }

    @After
    fun tearDown() {
        autofillService?.takeIf { it.isNotEmpty() && it != "null" }
            ?.let { shell("settings put secure autofill_service $it") }
    }

    @Test
    fun dangKy_dangXuat_dangNhapLai_backThoatApp() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        // Đăng ký từ màn hình chào.
        waitUntilDisplayed(withText(R.string.auth_welcome_create_account)).perform(click())
        onView(withId(R.id.nameInput)).perform(replaceText("Người Thử E2E"))
        onView(withId(R.id.emailInput)).perform(replaceText(email))
        onView(withId(R.id.passwordInput)).perform(replaceText(password))
        onView(withId(R.id.confirmInput)).perform(replaceText(password), closeSoftKeyboard())
        onView(withId(R.id.termsCheckbox)).perform(scrollTo(), click())
        onView(withId(R.id.submitButton)).perform(scrollTo(), click())

        // on-signup xong thì vào trang chủ người chơi, không ở chế độ khách.
        waitUntilDisplayed(withId(R.id.verifyEmailBanner))
        onView(withText(R.string.auth_home_placeholder_player)).check(matches(isDisplayed()))

        // Đăng xuất (spec 011 chưa có nút): app về trang chủ ở chế độ khách.
        InstrumentationRegistry.getInstrumentation().runOnMainSync { FirebaseAuth.getInstance().signOut() }
        waitUntilDisplayed(allOf(withId(R.id.loginButton), isDescendantOfA(withId(R.id.loginPrompt)))).perform(click())

        // Đăng nhập lại: nav_auth đóng, về đúng trang chủ bên dưới.
        waitUntilDisplayed(withId(R.id.emailInput)).perform(replaceText(email))
        onView(withId(R.id.passwordInput)).perform(replaceText(password), closeSoftKeyboard())
        onView(withId(R.id.loginButton)).perform(scrollTo(), click())
        waitUntilDisplayed(withId(R.id.verifyEmailBanner))

        // Back ở màn hình chính thì thoát app, không quay lại màn hình đăng nhập.
        pressBackUnconditionally()
        waitUntil { scenario.state == Lifecycle.State.DESTROYED }
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    private fun shell(command: String): String =
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
            .let { fd -> ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText().trim() } }

    /** Espresso không chờ coroutine/mạng; thử lại tới khi view hiện hoặc hết [timeoutMs]. */
    private fun waitUntilDisplayed(matcher: Matcher<View>, timeoutMs: Long = NETWORK_TIMEOUT_MS) =
        onView(matcher).also { interaction ->
            waitUntil(timeoutMs) { runCatching { interaction.check(matches(isDisplayed())) }.isSuccess }
        }

    private fun waitUntil(timeoutMs: Long = NETWORK_TIMEOUT_MS, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Hết $timeoutMs ms mà chưa đạt điều kiện" }
            Thread.sleep(POLL_MS)
        }
    }

    private companion object {
        const val NETWORK_TIMEOUT_MS = 20_000L
        const val POLL_MS = 250L
    }
}

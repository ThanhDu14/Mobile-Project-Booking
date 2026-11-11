package com.example.booking_app.feature.courtdetail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.booking_app.BuildConfig
import com.example.booking_app.MainActivity
import com.example.booking_app.R
import com.example.booking_app.testing.EmulatorAdmin
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * T070: luồng chi tiết sân của khách trên Firebase Emulator. Dữ liệu ghi bằng REST của Emulator (bỏ qua Rules).
 * Tự bỏ qua khi `USE_EMULATOR != "true"`. Chạy: `firebase emulators:start --only auth,firestore`, rồi
 * `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=` + tên lớp này.
 */
@RunWith(AndroidJUnit4::class)
class CourtDetailFlowTest {

    private lateinit var admin: EmulatorAdmin
    private var autofillService: String? = null
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        assumeTrue("Chỉ chạy với Firebase Emulator (USE_EMULATOR=true)", BuildConfig.USE_EMULATOR == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Bỏ qua màn hình chào để deep link mở thẳng chi tiết sân ở chế độ khách.
        File(context.filesDir, "datastore/session_prefs.preferences_pb").delete()
        autofillService = shell("settings get secure autofill_service")
        shell("settings put secure autofill_service null")
        InstrumentationRegistry.getInstrumentation().runOnMainSync { FirebaseAuth.getInstance().signOut() }
        val projectId = (FirebaseApp.initializeApp(context) ?: FirebaseApp.getInstance()).options.projectId!!
        admin = EmulatorAdmin(projectId)
        seed()
    }

    @After
    fun tearDown() {
        scenario?.close()
        if (::admin.isInitialized) {
            admin.delete("venues/$VENUE/courts/c1")
            admin.delete("venues/$VENUE/priceRules/r1")
            admin.delete("venues/$VENUE")
        }
        autofillService?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { shell("settings put secure autofill_service $it") }
    }

    @Test
    fun khungGioTuTimKiemHienGiaCuaKhung() {
        val date = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE)
        open("smashnow://venue/$VENUE?date=$date&start=1900&end=2100")
        waitUntil(withId(R.id.selectedWindowCard))
        onView(withText(R.string.court_detail_selected_window)).check(matches(isDisplayed()))
        onView(withId(R.id.selectedWindowPrice)).check(matches(withSubstring("130.000")))
    }

    @Test
    fun khachBamDatSanThiMoiDangNhapVaDeSauThiOLai() {
        open("smashnow://venue/$VENUE")
        waitUntil(withId(R.id.bookButton))
        onView(withId(R.id.bookButton)).perform(click())
        onView(withText(R.string.court_detail_login_to_book)).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText(R.string.court_detail_later)).inRoot(isDialog()).perform(click())
        onView(withId(R.id.bookButton)).check(matches(isDisplayed()))
    }

    @Test
    fun coSoKhongTonTaiThiHienManHinhRieng() {
        open("smashnow://venue/khong-co")
        waitUntil(withId(R.id.goneTitle))
        onView(withText(R.string.court_detail_gone)).check(matches(isDisplayed()))
    }

    private fun open(uri: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .setClass(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        scenario = ActivityScenario.launch(intent)
    }

    /** Firestore không báo Espresso khi nào rảnh: hỏi lại tới khi view hiện, tối đa [timeoutMs]. */
    private fun waitUntil(matcher: Matcher<View>, timeoutMs: Long = 15_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(matcher).check(matches(isDisplayed()))
                return
            } catch (e: NoMatchingViewException) {
                last = e
            } catch (e: AssertionError) {
                last = e
            }
            Thread.sleep(250)
        }
        throw AssertionError("Hết ${timeoutMs}ms mà chưa thấy view", last)
    }

    private fun seed() {
        admin.put(
            "venues/$VENUE",
            mapOf(
                "name" to "Sân T070",
                "address" to "70 Đường Thử",
                "districtId" to "q1",
                "location" to EmulatorAdmin.Geo(10.8, 106.7),
                "openTime" to "0500",
                "closeTime" to "2400",
                "ownerId" to "t070-owner",
                "amenityIds" to emptyList<String>(),
                "photoPaths" to emptyList<String>(),
                "ratingAvg" to 0.0,
                "ratingCount" to 0L,
                "status" to "ACTIVE",
            ),
        )
        admin.put("venues/$VENUE/courts/c1", mapOf("name" to "Sân 1", "surfaceType" to "WOOD", "isActive" to true))
        admin.put(
            "venues/$VENUE/priceRules/r1",
            mapOf(
                "daysOfWeek" to listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L),
                "startTime" to "0500",
                "endTime" to "2400",
                "pricePerHour" to 130000L,
                "label" to "ALL_DAY",
            ),
        )
    }

    private fun shell(command: String): String =
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().readText().trim()
        }

    private companion object {
        const val VENUE = "t070-venue"
    }
}

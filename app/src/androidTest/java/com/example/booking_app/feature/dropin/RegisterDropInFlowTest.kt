package com.example.booking_app.feature.dropin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
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
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.startsWith
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * T061: luồng đăng ký vãng lai của người chơi, từ danh sách đến vé có QR (FR-025, SC-001): danh sách → chi tiết buổi
 * → xác nhận (2 người, thanh toán giả lập MOCK_PAY) → vé. Test ghi lại các destination của NavController và khẳng
 * định luồng không quá 4 màn hình.
 *
 * Yêu cầu khi chạy (test tự bỏ qua nếu `USE_EMULATOR != "true"`):
 * - `local.properties`: `USE_EMULATOR=true`, `SUPABASE_URL=http://10.0.2.2:54321`, rồi cài lại app debug.
 * - Firebase Emulator: `firebase emulators:start --only auth,firestore` (Auth 9099, Firestore 8080).
 * - Một server Deno cục bộ cổng 54321 phục vụ `POST /functions/v1/register-drop-in` (handler `register-drop-in` trỏ
 *   vào Firestore Emulator), vì bước "Xác nhận đăng ký" gọi Edge Function đó qua `BuildConfig.SUPABASE_URL`.
 * - Chạy: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=` + tên lớp này.
 *
 * Dữ liệu (Auth Emulator, REST, bỏ qua Rules): tài khoản PLAYER có claim `appRole`; Firestore: `users/{uid}`, một
 * `venues` ACTIVE và một `dropInSessions` OPEN lúc 05:00 ngày mai (còn 10/12 chỗ). Tất cả được dọn ở `@After`, kể cả
 * đăng ký do Edge Function tạo.
 */
@RunWith(AndroidJUnit4::class)
class RegisterDropInFlowTest {

    private lateinit var admin: EmulatorAdmin
    private lateinit var projectId: String
    private var autofillService: String? = null
    private var scenario: ActivityScenario<MainActivity>? = null
    private var uid: String? = null

    private val email = "t061.${System.currentTimeMillis()}@example.com"
    private val visited = mutableListOf<Int>()

    @Before
    fun setUp() {
        assumeTrue("Chỉ chạy với Firebase Emulator (USE_EMULATOR=true)", BuildConfig.USE_EMULATOR == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Bỏ qua màn hình chào để deep link mở thẳng danh sách vãng lai.
        File(context.filesDir, "datastore/session_prefs.preferences_pb").delete()
        autofillService = shell("settings get secure autofill_service")
        shell("settings put secure autofill_service null")
        projectId = (FirebaseApp.initializeApp(context) ?: FirebaseApp.getInstance()).options.projectId!!
        admin = EmulatorAdmin(projectId)
        uid = createPlayerAccount()
        seed(uid!!)
    }

    @After
    fun tearDown() {
        scenario?.close()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { FirebaseAuth.getInstance().signOut() }
        if (::admin.isInitialized) {
            uid?.let {
                admin.delete("dropInSessions/$SESSION/registrations/$it")
                admin.delete("users/$it")
                deleteAccount(it)
            }
            admin.delete("dropInSessions/$SESSION")
            admin.delete("venues/$VENUE/courts/c1")
            admin.delete("venues/$VENUE")
        }
        autofillService?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { shell("settings put secure autofill_service $it") }
    }

    @Test
    fun danhSach_chiTiet_xacNhan2Nguoi_mockPay_veCoQr_khongQua4ManHinh() {
        // Vào danh sách bằng deep link của graph nav_dropin (cùng màn gốc với tab "Vãng lai").
        open("smashnow://dropin")
        trackDestinations()

        // Đăng nhập bằng FirebaseAuth của app (đã trỏ Emulator qua FirebaseModule) rồi chờ vai trò vào phiên.
        signIn()

        // 1. Danh sách: buổi seed hiện ra (khách/người dùng chưa có quận yêu thích nên không có chip quận).
        waitUntil(allOf(withId(R.id.venueDistrict), withSubstring(VENUE_NAME)))
        onView(allOf(withId(R.id.venueDistrict), withSubstring(VENUE_NAME))).perform(click())

        // 2. Chi tiết buổi → "Đăng ký". Phiên đăng nhập có thể chưa kịp vào SessionManager: nếu hiện hộp thoại
        // "Đăng nhập để đăng ký" thì đóng lại và bấm lại.
        waitUntil(withId(R.id.registerButton))
        waitUntil(withId(R.id.submitButton), timeoutMs = 30_000) {
            onView(withId(R.id.registerButton)).perform(click())
            Thread.sleep(1_000)
            if (isDisplayed(withText(R.string.dropin_detail_later))) {
                onView(withText(R.string.dropin_detail_later)).inRoot(isDialog()).perform(click())
                Thread.sleep(1_000)
            }
        }

        // 3. Xác nhận: tăng lên 2 người, thanh toán giả lập, gửi.
        onView(withId(R.id.partyIncrease)).perform(click())
        onView(withId(R.id.partyValue)).check(matches(withText(context().getString(R.string.dropin_confirm_party_value, 2))))
        onView(withId(R.id.payMockRow)).perform(click())
        onView(withId(R.id.submitButton)).perform(click())

        // 4. Vé: QR, mã DI-xxxxxxxx, 2 người. Cần Edge Function register-drop-in chạy cục bộ.
        waitUntil(withId(R.id.qrImage), timeoutMs = 30_000)
        onView(withId(R.id.qrFrame)).check(matches(isDisplayed()))
        onView(withId(R.id.qrImage)).check(matches(isDisplayed()))
        waitUntil(allOf(withId(R.id.ticketCode), withText(startsWith("DI-"))))
        onView(withId(R.id.partyValue)).check(matches(withText(context().getString(R.string.dropin_confirm_party_value, 2))))

        val screens = synchronized(visited) { visited.distinct() }
        assertEquals(
            listOf(R.id.dropInListFragment, R.id.dropInDetailFragment, R.id.dropInConfirmFragment, R.id.dropInTicketFragment),
            screens,
        )
        assertTrue("Luồng đăng ký phải không quá 4 màn hình (FR-025), thực tế ${screens.size}", screens.size <= 4)
    }

    // ---------- Điều hướng, Espresso ----------

    private fun open(uri: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .setClass(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        scenario = ActivityScenario.launch(intent)
    }

    /** Ghi lại mọi destination (fragment) NavController đi qua, kể cả destination đang hiện lúc đăng ký listener. */
    private fun trackDestinations() {
        scenario!!.onActivity { activity ->
            val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            val controller: NavController = host.navController
            controller.addOnDestinationChangedListener { _, destination, _ ->
                synchronized(visited) { visited += destination.id }
            }
        }
    }

    private fun context(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun isDisplayed(matcher: Matcher<View>): Boolean = try {
        onView(matcher).check(matches(isDisplayed()))
        true
    } catch (e: NoMatchingViewException) {
        false
    } catch (e: AssertionError) {
        false
    }

    /**
     * Firestore/Edge Function không báo Espresso khi nào rảnh: hỏi lại tới khi view hiện, tối đa [timeoutMs].
     * [beforeEachTry] chạy trước mỗi lần hỏi (dùng để bấm lại khi bước trước chưa chuyển màn hình).
     */
    private fun waitUntil(matcher: Matcher<View>, timeoutMs: Long = 15_000, beforeEachTry: (() -> Unit)? = null) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                beforeEachTry?.invoke()
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

    // ---------- Auth Emulator ----------

    /** Đăng nhập trên luồng nền (Tasks.await không được chạy ở main) rồi làm mới token để có claim `appRole`. */
    private fun signIn() {
        val auth = FirebaseAuth.getInstance()
        Tasks.await(auth.signInWithEmailAndPassword(email, PASSWORD), 20, TimeUnit.SECONDS)
        Tasks.await(auth.currentUser!!.getIdToken(true), 20, TimeUnit.SECONDS)
        // SessionManager nhận authState rồi đọc claims; chờ một nhịp ngắn, bước chi tiết còn có vòng bấm lại.
        Thread.sleep(1_500)
    }

    /** Tạo tài khoản qua REST của Auth Emulator và gắn claim `appRole=PLAYER` (việc on-signup làm ở môi trường thật). */
    private fun createPlayerAccount(): String {
        val signUp = authRequest(
            "identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key",
            JSONObject().put("email", email).put("password", PASSWORD).put("returnSecureToken", true),
            admin = false,
        )
        val localId = signUp.getString("localId")
        authRequest(
            "identitytoolkit.googleapis.com/v1/projects/$projectId/accounts:update",
            JSONObject()
                .put("localId", localId)
                .put("emailVerified", true)
                .put("customAttributes", JSONObject().put("appRole", "PLAYER").toString()),
            admin = true,
        )
        return localId
    }

    private fun deleteAccount(localId: String) {
        runCatching {
            authRequest(
                "identitytoolkit.googleapis.com/v1/projects/$projectId/accounts:delete",
                JSONObject().put("localId", localId),
                admin = true,
            )
        }
    }

    private fun authRequest(path: String, body: JSONObject, admin: Boolean): JSONObject {
        val connection = URL("http://10.0.2.2:9099/$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            if (admin) connection.setRequestProperty("Authorization", "Bearer owner")
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            check(code in 200..299) { "Auth Emulator $path → $code $text" }
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    // ---------- Firestore Emulator ----------

    private fun seed(uid: String) {
        admin.put(
            "users/$uid",
            mapOf(
                "displayName" to "Người Thử T061",
                "email" to email,
                "role" to "PLAYER",
                "accountStatus" to "ACTIVE",
                "level" to "INTERMEDIATE",
                "favoriteDistrictIds" to emptyList<String>(),
                "walletBalance" to 0L,
                "ownerStatus" to "NONE",
            ),
        )
        admin.put(
            "venues/$VENUE",
            mapOf(
                "name" to VENUE_NAME,
                "address" to "61 Đường Thử",
                "districtId" to "q1",
                "location" to EmulatorAdmin.Geo(10.8, 106.7),
                "openTime" to "0500",
                "closeTime" to "2400",
                "ownerId" to "t061-owner",
                "amenityIds" to emptyList<String>(),
                "photoPaths" to emptyList<String>(),
                "ratingAvg" to 0.0,
                "ratingCount" to 0L,
                "status" to "ACTIVE",
            ),
        )
        admin.put("venues/$VENUE/courts/c1", mapOf("name" to "Sân 1", "surfaceType" to "WOOD", "isActive" to true))

        // 05:00 ngày mai (giờ Việt Nam): xếp đầu danh sách "gần nhất trước", không phụ thuộc lúc chạy test.
        val tomorrow = LocalDate.now(ZONE).plusDays(1)
        val start = tomorrow.atTime(5, 0).atZone(ZONE).toInstant()
        admin.put(
            "dropInSessions/$SESSION",
            mapOf(
                "venueId" to VENUE,
                "ownerId" to "t061-owner",
                "districtId" to "q1",
                "courtIds" to listOf("c1"),
                "date" to DATE_KEY.format(tomorrow),
                "startAt" to start,
                "endAt" to start.plus(2, ChronoUnit.HOURS),
                "timeSlot" to "MORNING",
                "venueSnapshot" to mapOf("name" to VENUE_NAME, "address" to "61 Đường Thử", "districtName" to "Quận 1"),
                "level" to "ALL",
                "capacity" to 12L,
                "registeredCount" to 2L,
                "pricePerPerson" to 50_000L,
                "status" to "OPEN",
            ),
        )
    }

    private fun shell(command: String): String =
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().readText().trim()
        }

    private companion object {
        const val VENUE = "t061-venue"
        const val VENUE_NAME = "Sân T061"
        const val SESSION = "t061-session"
        const val PASSWORD = "abc12345"
        val ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
        val DATE_KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}

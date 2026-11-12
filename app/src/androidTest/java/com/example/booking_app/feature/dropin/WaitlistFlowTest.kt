package com.example.booking_app.feature.dropin

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
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * T049 (spec 081): luồng hàng chờ của người chơi. Chi tiết buổi `W1` đã đủ người → "Vào hàng chờ" → xác nhận 2 người
 * → thấy vị trí trong hàng chờ; một tài khoản khác (đang giữ 2 chỗ) hủy qua Edge Function `cancel-drop-in` → màn hình
 * lượt chờ tự chuyển sang vé có nhãn "Được nhận từ hàng chờ" (AC 1.1, 1.4, 2.4).
 *
 * Yêu cầu khi chạy (test tự bỏ qua nếu `USE_EMULATOR != "true"`), giống [RegisterDropInFlowTest]:
 * - `local.properties`: `USE_EMULATOR=true`, `SUPABASE_URL=http://10.0.2.2:54321`, rồi cài lại app debug.
 * - Firebase Emulator: `firebase emulators:start --only auth,firestore` (Auth 9099, Firestore 8080).
 * - Server Deno cục bộ cổng 54321 phục vụ `register-drop-in` và `cancel-drop-in` (đã có nhánh hàng chờ của spec 081,
 *   trỏ vào Firestore Emulator): bước xác nhận và bước hủy gọi hai function đó qua `BuildConfig.SUPABASE_URL`.
 * - Chạy: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=` + tên lớp này.
 *
 * Dữ liệu (Auth/Firestore Emulator, REST, bỏ qua Rules): hai tài khoản PLAYER (`ownerOfSeats` giữ 2 chỗ, `waiter` vào
 * hàng chờ), một `venues` ACTIVE và buổi `W1` `FULL` 12/12 chỗ, bắt đầu sau 2 ngày. Tất cả được dọn ở `@After`.
 */
@RunWith(AndroidJUnit4::class)
class WaitlistFlowTest {

    private class Account(val email: String, val uid: String)

    private lateinit var admin: EmulatorAdmin
    private lateinit var projectId: String
    private var autofillService: String? = null
    private var scenario: ActivityScenario<MainActivity>? = null
    private var waiter: Account? = null
    private var seatHolder: Account? = null

    @Before
    fun setUp() {
        assumeTrue("Chỉ chạy với Firebase Emulator (USE_EMULATOR=true)", BuildConfig.USE_EMULATOR == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Bỏ qua màn hình chào để deep link mở thẳng chi tiết buổi.
        File(context.filesDir, "datastore/session_prefs.preferences_pb").delete()
        autofillService = shell("settings get secure autofill_service")
        shell("settings put secure autofill_service null")
        projectId = (FirebaseApp.initializeApp(context) ?: FirebaseApp.getInstance()).options.projectId!!
        admin = EmulatorAdmin(projectId)
        waiter = createPlayerAccount("waiter")
        seatHolder = createPlayerAccount("holder")
        seed(waiter!!, seatHolder!!)
    }

    @After
    fun tearDown() {
        scenario?.close()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { FirebaseAuth.getInstance().signOut() }
        if (::admin.isInitialized) {
            listOfNotNull(waiter, seatHolder).forEach {
                admin.delete("dropInSessions/$SESSION/registrations/${it.uid}")
                admin.delete("users/${it.uid}")
                deleteAccount(it.uid)
            }
            admin.delete("dropInSessions/$SESSION")
            admin.delete("venues/$VENUE/courts/c1")
            admin.delete("venues/$VENUE")
        }
        autofillService?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { shell("settings put secure autofill_service $it") }
    }

    @Test
    fun chiTietBuoiDayNguoi_vaoHangCho2Nguoi_thayViTri_roiDuocDayLenThiSangVeCoNhan() {
        open("smashnow://dropin/session/$SESSION")
        signIn(waiter!!)

        // 1. Chi tiết buổi đã đủ người: thẻ hàng chờ và nút "Vào hàng chờ" (hàng chờ mở vì còn > 30 phút).
        waitUntil(withId(R.id.joinWaitlistButton))
        onView(withId(R.id.waitlistCard)).check(matches(isDisplayed()))
        waitUntil(withId(R.id.submitButton), timeoutMs = 30_000) {
            onView(withId(R.id.joinWaitlistButton)).perform(click())
            Thread.sleep(1_000)
            // Phiên đăng nhập chưa kịp vào SessionManager: đóng hộp thoại "Đăng nhập" và bấm lại.
            if (isDisplayed(withText(R.string.dropin_detail_later))) {
                onView(withText(R.string.dropin_detail_later)).inRoot(isDialog()).perform(click())
                Thread.sleep(1_000)
            }
        }

        // 2. Xác nhận ở chế độ hàng chờ: dòng "Chưa giữ chỗ, chưa thanh toán...", 2 người, "Vào hàng chờ".
        onView(withId(R.id.waitlistBox)).check(matches(isDisplayed()))
        onView(withId(R.id.partyIncrease)).perform(click())
        onView(withId(R.id.partyValue)).check(matches(withText(context().getString(R.string.dropin_confirm_party_value, 2))))
        onView(withId(R.id.payAtVenueRow)).perform(click())
        onView(withId(R.id.submitButton)).perform(click())

        // 3. Màn hình lượt chờ: vị trí 1 / 1, 2 người.
        waitUntil(allOf(withId(R.id.positionValue), withText(context().getString(R.string.dropin_waitlist_entry_position, 1, 1))), 30_000)
        onView(withId(R.id.partyValue)).check(matches(withText(context().getString(R.string.dropin_mine_party, 2))))
        onView(withId(R.id.leaveButton)).check(matches(isDisplayed()))

        // 4. Người đang giữ 2 chỗ hủy: server đẩy người chờ lên trong cùng transaction, app tự sang vé.
        cancelAs(seatHolder!!)
        waitUntil(withId(R.id.qrImage), timeoutMs = 30_000)
        waitUntil(allOf(withId(R.id.ticketCode), withText(startsWith("DI-"))))
        waitUntil(withText(R.string.dropin_waitlist_status_promoted))
    }

    // ---------- Điều hướng, Espresso ----------

    private fun open(uri: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .setClass(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        scenario = ActivityScenario.launch(intent)
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

    /** Hỏi lại tới khi view hiện, tối đa [timeoutMs]; [beforeEachTry] chạy trước mỗi lần hỏi. */
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

    // ---------- Edge Function và Auth Emulator ----------

    private fun signIn(account: Account) {
        val auth = FirebaseAuth.getInstance()
        Tasks.await(auth.signInWithEmailAndPassword(account.email, PASSWORD), 20, TimeUnit.SECONDS)
        Tasks.await(auth.currentUser!!.getIdToken(true), 20, TimeUnit.SECONDS)
        Thread.sleep(1_500)
    }

    /** Người đang giữ chỗ hủy lượt của mình bằng `cancel-drop-in` (ID token lấy từ Auth Emulator). */
    private fun cancelAs(account: Account) {
        val signIn = authRequest(
            "identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake-api-key",
            JSONObject().put("email", account.email).put("password", PASSWORD).put("returnSecureToken", true),
            admin = false,
        )
        val idToken = signIn.getString("idToken")
        val connection = URL("${BuildConfig.SUPABASE_URL}/functions/v1/cancel-drop-in").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $idToken")
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            connection.doOutput = true
            val body = JSONObject().put("sessionId", SESSION).put("requestId", UUID.randomUUID().toString())
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            check(code in 200..299) { "cancel-drop-in → $code $text" }
        } finally {
            connection.disconnect()
        }
    }

    private fun createPlayerAccount(name: String): Account {
        val email = "t081.$name.${System.currentTimeMillis()}@example.com"
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
        return Account(email, localId)
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

    private fun seed(waiter: Account, holder: Account) {
        listOf(waiter to "Người Chờ T081", holder to "Người Giữ Chỗ T081").forEach { (account, name) ->
            admin.put(
                "users/${account.uid}",
                mapOf(
                    "displayName" to name,
                    "email" to account.email,
                    "role" to "PLAYER",
                    "accountStatus" to "ACTIVE",
                    "level" to "INTERMEDIATE",
                    "favoriteDistrictIds" to emptyList<String>(),
                    "walletBalance" to 0L,
                    "ownerStatus" to "NONE",
                ),
            )
        }
        admin.put(
            "venues/$VENUE",
            mapOf(
                "name" to VENUE_NAME,
                "address" to "81 Đường Thử",
                "districtId" to "q1",
                "location" to EmulatorAdmin.Geo(10.8, 106.7),
                "openTime" to "0500",
                "closeTime" to "2400",
                "ownerId" to "t081-owner",
                "amenityIds" to emptyList<String>(),
                "photoPaths" to emptyList<String>(),
                "ratingAvg" to 0.0,
                "ratingCount" to 0L,
                "status" to "ACTIVE",
            ),
        )
        admin.put("venues/$VENUE/courts/c1", mapOf("name" to "Sân 1", "surfaceType" to "WOOD", "isActive" to true))

        // 19:00 sau 2 ngày (giờ Việt Nam): hàng chờ còn mở (> 30 phút), buổi đủ 12/12 chỗ.
        val day = LocalDate.now(ZONE).plusDays(2)
        val start = day.atTime(19, 0).atZone(ZONE).toInstant()
        val end = start.plus(2, ChronoUnit.HOURS)
        admin.put(
            "dropInSessions/$SESSION",
            mapOf(
                "venueId" to VENUE,
                "ownerId" to "t081-owner",
                "districtId" to "q1",
                "courtIds" to listOf("c1"),
                "date" to DATE_KEY.format(day),
                "startAt" to start,
                "endAt" to end,
                "timeSlot" to "EVENING",
                "venueSnapshot" to mapOf("name" to VENUE_NAME, "address" to "81 Đường Thử", "districtName" to "Quận 1"),
                "level" to "ALL",
                "capacity" to 12L,
                "registeredCount" to 12L,
                "pricePerPerson" to 50_000L,
                "status" to "FULL",
                "waitlistNextTicket" to 1L,
                "waitlistTickets" to emptyList<Long>(),
                "waitlistCount" to 0L,
            ),
        )
        // Người giữ 2 chỗ trong 12 chỗ; 10 chỗ còn lại do người khác (chỉ cần registeredCount).
        admin.put(
            "dropInSessions/$SESSION/registrations/${holder.uid}",
            mapOf(
                "userId" to holder.uid,
                "sessionId" to SESSION,
                "venueId" to VENUE,
                "sessionStartAt" to start,
                "sessionEndAt" to end,
                "partySize" to 2L,
                "status" to "REGISTERED",
                "paymentMethod" to "AT_VENUE",
                "paymentStatus" to "UNPAID",
                "pricePerPerson" to 50_000L,
                "amount" to 100_000L,
                "requestId" to UUID.randomUUID().toString(),
                "qrToken" to "t081-holder-qr-token-0123456789abcdefghijklmnopqrstuv",
                "ticketCode" to "DI-T0810002",
                "registeredAt" to Instant.now(),
            ),
        )
    }

    private fun shell(command: String): String =
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().readText().trim()
        }

    private companion object {
        const val VENUE = "t081-venue"
        const val VENUE_NAME = "Sân T081"
        const val SESSION = "t081-w1"
        const val PASSWORD = "abc12345"
        val ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
        val DATE_KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}

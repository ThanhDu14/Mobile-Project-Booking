package com.example.booking_app.feature.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
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
import org.json.JSONObject
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * T054: người chơi nhắn chủ sân từ chi tiết sân, chủ sân thấy cuộc trò chuyện ở "Tin nhắn khách hàng".
 * Chỉ chạy với Firebase Emulator (`USE_EMULATOR=true`) và Supabase local chạy `chat-action`, `chat-send`
 * trỏ vào Emulator (gọi Edge Function thật, không mock). Tự seed owner/người chơi/sân với id `t054_`.
 * Chạy: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=`
 * + `com.example.booking_app.feature.chat.PlayerOwnerChatFlowTest`.
 */
@RunWith(AndroidJUnit4::class)
class PlayerOwnerChatFlowTest {

    private lateinit var projectId: String
    private lateinit var admin: EmulatorAdmin
    private var scenario: ActivityScenario<MainActivity>? = null
    private var autofillService: String? = null
    private var animationScales: List<String> = emptyList()
    private val stamp = System.currentTimeMillis()
    private val ownerEmail = "t054.owner.$stamp@example.com"
    private val playerEmail = "t054.player.$stamp@example.com"
    private var ownerUid: String? = null
    private var playerUid: String? = null

    @Before
    fun setUp() {
        assumeTrue("Chỉ chạy với Firebase Emulator (USE_EMULATOR=true)", BuildConfig.USE_EMULATOR == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Bỏ qua màn hình chào; tắt autofill để hộp thoại mật khẩu không che app.
        File(context.filesDir, "datastore/session_prefs.preferences_pb").delete()
        autofillService = shell("settings get secure autofill_service")
        shell("settings put secure autofill_service null")
        // Tắt animation: hiệu ứng đổi item của RecyclerView giữ 2 view cùng nội dung trong lúc chạy.
        animationScales = ANIMATION_KEYS.map { shell("settings get global $it") }
        ANIMATION_KEYS.forEach { shell("settings put global $it 0") }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { FirebaseAuth.getInstance().signOut() }
        projectId = (FirebaseApp.initializeApp(context) ?: FirebaseApp.getInstance()).options.projectId!!
        admin = EmulatorAdmin(projectId)
        seed()
    }

    @After
    fun tearDown() {
        scenario?.close()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { FirebaseAuth.getInstance().signOut() }
        val owner = ownerUid
        val player = playerUid
        if (::admin.isInitialized) {
            if (owner != null && player != null) {
                val chatId = listOf(owner, player).sorted().joinToString("_")
                // Tin nhắn nằm ở subcollection do server ghi, không biết id nên để Emulator dọn khi tắt.
                runCatching { admin.delete("chats/$chatId/participants/$owner") }
                runCatching { admin.delete("chats/$chatId/participants/$player") }
                runCatching { admin.delete("chats/$chatId") }
            }
            runCatching { admin.delete("venues/$VENUE/courts/c1") }
            runCatching { admin.delete("venues/$VENUE/priceRules/r1") }
            runCatching { admin.delete("venues/$VENUE") }
            owner?.let { runCatching { admin.delete("users/$it") }; deleteAccount(it) }
            player?.let { runCatching { admin.delete("users/$it") }; deleteAccount(it) }
        }
        ANIMATION_KEYS.zip(animationScales).forEach { (key, value) ->
            shell("settings put global $key ${value.takeIf { it.isNotBlank() && it != "null" } ?: "1"}")
        }
        autofillService?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { shell("settings put secure autofill_service $it") }
    }

    @Test
    fun nguoiChoiNhanChuSanRoiChuSanThayTrongTinNhanKhachHang() {
        val message = "Sân còn trống tối nay không? $stamp"

        // Người chơi: chi tiết sân -> "Nhắn tin cho chủ sân" -> gửi tin.
        signIn(playerEmail)
        open("smashnow://venue/$VENUE")
        waitUntil(withId(R.id.chatButton))
        onView(withId(R.id.chatButton)).perform(click())
        // Chờ openDirect xong (có tên sân ở thẻ "Đang hỏi về") rồi mới gõ, nếu không nút gửi chưa bật.
        waitUntil(allOf(withId(R.id.venueName), withText(VENUE_NAME)))
        onView(withId(R.id.messageInput)).perform(replaceText(message), closeSoftKeyboard())
        waitUntil(allOf(withId(R.id.sendButton), isEnabled()))
        onView(withId(R.id.sendButton)).perform(click())
        // Tin phải nằm trong danh sách tin (không phải chữ còn trong ô nhập), rồi hết "Đang gửi".
        waitUntil(allOf(withText(message), isDescendantOfA(withId(R.id.messageList))))
        waitUntilGone(withText(R.string.chat_delivery_pending))

        // Chủ sân: đăng nhập lại, mở "Tin nhắn khách hàng".
        scenario?.close()
        signIn(ownerEmail)
        open("smashnow://chat/owner")
        waitUntil(withText(R.string.chat_title_owner))
        waitUntil(withText("Hỏi về $VENUE_NAME"))
        onView(withText(message)).check(matches(isDisplayed()))
    }

    private fun signIn(email: String) {
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        Tasks.await(auth.signInWithEmailAndPassword(email, PASSWORD))
        Tasks.await(auth.currentUser!!.getIdToken(true))
    }

    private fun open(uri: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .setClass(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        scenario = ActivityScenario.launch(intent)
    }

    /** Firestore/Edge Function không báo Espresso khi nào rảnh: hỏi lại tới khi view hiện. */
    private fun waitUntil(matcher: Matcher<View>, timeoutMs: Long = 20_000) =
        poll(timeoutMs, "Hết ${timeoutMs}ms mà chưa thấy view") {
            onView(matcher).check(matches(isDisplayed()))
        }

    private fun waitUntilGone(matcher: Matcher<View>, timeoutMs: Long = 20_000) =
        poll(timeoutMs, "Hết ${timeoutMs}ms mà view vẫn còn") {
            val gone = try {
                onView(matcher).check(matches(isDisplayed()))
                false
            } catch (_: NoMatchingViewException) {
                true
            } catch (_: AssertionError) {
                true
            }
            if (!gone) throw AssertionError("view còn hiện")
        }

    private fun poll(timeoutMs: Long, message: String, check: () -> Unit) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                check()
                return
            } catch (e: NoMatchingViewException) {
                last = e
            } catch (e: AssertionError) {
                last = e
            }
            Thread.sleep(250)
        }
        throw AssertionError(message, last)
    }

    // ---------- Seed ----------

    private fun seed() {
        val owner = createAccount(ownerEmail, "OWNER").also { ownerUid = it }
        val player = createAccount(playerEmail, "PLAYER").also { playerUid = it }
        admin.put(
            "users/$owner",
            mapOf(
                "displayName" to "Chủ sân T054", "email" to ownerEmail, "role" to "OWNER",
                "accountStatus" to "ACTIVE", "ownerStatus" to "APPROVED",
            ),
        )
        admin.put(
            "users/$player",
            mapOf(
                "displayName" to "Người chơi T054", "email" to playerEmail, "role" to "PLAYER",
                "accountStatus" to "ACTIVE", "ownerStatus" to "NONE",
            ),
        )
        // Có sân con và bảng giá để nút "Nhắn tin cho chủ sân" hiện (chỉ hiện khi sân đặt được).
        admin.put(
            "venues/$VENUE",
            mapOf(
                "name" to VENUE_NAME,
                "address" to "54 Đường Thử",
                "districtId" to "q1",
                "location" to EmulatorAdmin.Geo(10.8, 106.7),
                "openTime" to "0500",
                "closeTime" to "2400",
                "ownerId" to owner,
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

    private fun createAccount(email: String, role: String): String {
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
                .put("customAttributes", JSONObject().put("appRole", role).toString()),
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

    private fun shell(command: String): String =
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().readText().trim()
        }

    private companion object {
        const val VENUE = "t054_venue"
        const val VENUE_NAME = "Sân T054"
        const val PASSWORD = "abc12345"
        val ANIMATION_KEYS = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
    }
}

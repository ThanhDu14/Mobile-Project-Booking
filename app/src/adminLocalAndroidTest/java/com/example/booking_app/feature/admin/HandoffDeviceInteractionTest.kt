package com.example.booking_app.feature.admin

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.util.TreeIterables
import androidx.test.platform.app.InstrumentationRegistry
import com.example.booking_app.MainActivity
import com.example.booking_app.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/** Actual UI/repositories/HTTP on a physical device, with explicitly local test services. */
@HiltAndroidTest
class HandoffDeviceInteractionTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var auth: FirebaseAuth
    @Inject lateinit var firestore: FirebaseFirestore

    @Test fun moderationHidesContentLocksGroupAndDeniesOwnerDeletion() = runBlocking {
        hilt.inject()
        login("device-admin")
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(withId(R.id.cardPendingApplications))
            onView(withId(R.id.tab_admin_reports)).perform(click())
            for ((report, choice) in listOf("device-hide" to 0, "device-message" to -1, "device-lock" to 1)) {
                waitFor(withText(report))
                onView(withText(report)).perform(click())
                waitFor(allOf(withId(R.id.textReportReason), withText(report)))
                val expectedContent = if (report == "device-message") "Device reported message" else "Device reported content"
                val expectedAuthor = if (report == "device-message") "Device Message Sender" else "Device Post Author"
                waitFor(allOf(withId(R.id.textTargetBody), withText(expectedContent)))
                waitFor(allOf(withId(R.id.textTargetAuthorName), withText(expectedAuthor)))
                screenshot("$report-detail")
                onView(withId(R.id.radioActionTarget)).perform(scrollTo(), click())
                onView(withId(R.id.buttonSubmitConclusion)).perform(click())
                if (choice >= 0) {
                    screenshot("$report-actions")
                    val resource = if (choice == 0) R.string.admin_report_action_hide_post else R.string.admin_report_action_lock_group
                    onView(withText(resource)).inRoot(isDialog()).perform(click())
                }
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
                waitFor(withId(R.id.buttonTabClosed))
                awaitField("reports/$report", "status", "RESOLVED")
                screenshot("$report-resolved")
            }
            onView(withId(R.id.buttonTabClosed)).perform(click())
            waitFor(withText("device-hide"))
            onView(withText("device-hide")).perform(click())
            waitFor(allOf(withId(R.id.textTargetBody), withText("Device reported content")))
            assertEquals("HIDDEN", firestore.document("groups/device-group/posts/device-hide")
                .get(com.google.firebase.firestore.Source.SERVER).await().getString("status"))
            screenshot("hidden-post-preview")
            androidx.test.espresso.Espresso.pressBack()
            waitFor(withText("device-message"))
            onView(withText("device-message")).perform(click())
            waitFor(allOf(withId(R.id.textTargetAuthorName), withText("Device Message Sender")))
            onView(withId(R.id.textTargetBody)).check(androidx.test.espresso.assertion.ViewAssertions.matches(withText("")))
            assertEquals("", firestore.document("chats/device-chat/messages/device-message")
                .get(com.google.firebase.firestore.Source.SERVER).await().getString("text"))
            screenshot("hidden-message-cleared-preview")
        }
        val post = fields("groups/device-group/posts/device-hide")
        assertEquals("SYSTEM", post.getJSONObject("hiddenBy").getString("stringValue"))
        assertTrue(post.getJSONObject("hiddenByUid").has("nullValue"))
        assertEquals("", fields("chats/device-chat/messages/device-message").getJSONObject("text").getString("stringValue"))
        assertEquals("LOCKED", fields("groups/device-group").getJSONObject("status").getString("stringValue"))
        assertEquals("VISIBLE", fields("groups/device-group/posts/device-lock").getJSONObject("status").getString("stringValue"))
        login("device-owner")
        try {
            firestore.document("groups/device-group").delete().await()
            fail("A locked group's owner must not delete it")
        } catch (error: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error.code)
        }
        login("device-admin")
        assertEquals(200, invoke("admin-manage-group", """{"groupId":"device-group","status":"ACTIVE"}"""))
        awaitField("groups/device-group", "status", "ACTIVE")
    }

    @Test fun ownerCapacityIncreasePromotesThenCancellationClosesWaitingEntries() = runBlocking {
        hilt.inject()
        login("device-owner")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openRoster(scenario, "device-increase")
            waitFor(allOf(withId(R.id.registeredCount), withText("3")))
            screenshot("owner-before-increase")
            onView(withId(R.id.editSession)).perform(click())
            waitFor(allOf(withId(R.id.capacity), withText("3")))
            onView(withId(R.id.capacity)).perform(scrollTo(), replaceText("4"), closeSoftKeyboard())
            screenshot("owner-capacity-four")
            onView(withId(R.id.save)).perform(click())
            waitFor(withId(R.id.cancelSession))
            awaitField("dropInSessions/device-increase/registrations/device-party-b", "status", "REGISTERED")
            waitFor(allOf(withId(R.id.registeredCount), withText("4")))
            assertEquals("WAITLISTED", fields("dropInSessions/device-increase/registrations/device-party-a").getJSONObject("status").getString("stringValue"))
            val promoted = fields("dropInSessions/device-increase/registrations/device-party-b")
            assertEquals("UNPAID", promoted.getJSONObject("paymentStatus").getString("stringValue"))
            assertEquals(43, promoted.getJSONObject("qrToken").getString("stringValue").length)
            screenshot("owner-after-promotion")
            onView(withId(R.id.cancelSession)).perform(click())
            onView(isAssignableFrom(EditText::class.java)).inRoot(isDialog()).perform(replaceText("Physical device local cancellation"), closeSoftKeyboard())
            screenshot("owner-cancel-confirmation")
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            awaitField("dropInSessions/device-increase", "status", "CANCELLED")
            waitFor(withId(R.id.createSession))
            val waiting = fields("dropInSessions/device-increase/registrations/device-party-a")
            assertEquals("SESSION_CANCELLED", waiting.getJSONObject("waitlistEndReason").getString("stringValue"))
            assertTrue(waiting.getJSONObject("paymentStatus").has("nullValue"))
            val session = fields("dropInSessions/device-increase")
            assertEquals("0", session.getJSONObject("registeredCount").getString("integerValue"))
            assertEquals("0", session.getJSONObject("waitlistCount").getString("integerValue"))
            assertEquals("150000", fields("dropInSessions/device-increase/registrations/device-existing").getJSONObject("refundAmount").getString("integerValue"))
            screenshot("owner-session-cancelled")
        }
    }

    @Test fun ownerCapacityDecreaseKeepsWaitingPartyInPlace() = runBlocking {
        hilt.inject()
        login("device-owner")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openRoster(scenario, "device-decrease")
            onView(withId(R.id.editSession)).perform(click())
            waitFor(allOf(withId(R.id.capacity), withText("6")))
            onView(withId(R.id.capacity)).perform(scrollTo(), replaceText("5"), closeSoftKeyboard())
            onView(withId(R.id.save)).perform(click())
            waitFor(withId(R.id.cancelSession))
            val session = fields("dropInSessions/device-decrease")
            assertEquals("5", session.getJSONObject("capacity").getString("integerValue"))
            assertEquals("4", session.getJSONObject("registeredCount").getString("integerValue"))
            assertEquals("1", session.getJSONObject("waitlistCount").getString("integerValue"))
            assertEquals("WAITLISTED", fields("dropInSessions/device-decrease/registrations/device-party-a").getJSONObject("status").getString("stringValue"))
            screenshot("owner-decrease-preserves-waitlist")
        }
    }

    private suspend fun login(uid: String) {
        assertEquals("demo-admin-repair", auth.app.options.projectId)
        auth.signOut()
        auth.signInWithEmailAndPassword("$uid@example.test", "Test123456").await()
        auth.currentUser!!.getIdToken(true).await()
    }

    private fun openRoster(scenario: ActivityScenario<MainActivity>, sessionId: String) {
        val end = System.currentTimeMillis() + 30000
        var ready = false
        while (!ready && System.currentTimeMillis() < end) {
            scenario.onActivity { activity ->
                val controller = (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController
                ready = generateSequence(controller.currentDestination) { it.parent }.any { it.id == R.id.nav_owner }
            }
            if (!ready) Thread.sleep(100)
        }
        assertTrue("Owner graph must load", ready)
        waitFor(withId(R.id.calendarButton))
        scenario.onActivity { activity ->
            (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController.navigate(R.id.ownerDropInFragment)
        }
        waitFor(withId(R.id.createSession))
        scenario.onActivity { activity ->
            (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController.navigate(
                R.id.ownerDropInRosterFragment, Bundle().apply { putString("venueId", "device-venue"); putString("sessionId", sessionId) })
        }
        waitFor(withId(R.id.editSession))
    }

    private fun fields(path: String): JSONObject {
        // Read-only assertion against this fixed local emulator. App mutations still use real UI/API.
        val connection = URL("http://127.0.0.1:18080/v1/projects/demo-admin-repair/databases/(default)/documents/$path").openConnection() as HttpURLConnection
        return try {
            connection.setRequestProperty("Authorization", "Bearer owner")
            connection.connectTimeout = 10000; connection.readTimeout = 10000
            assertEquals(200, connection.responseCode)
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONObject("fields")
        } finally { connection.disconnect() }
    }

    private fun awaitField(path: String, field: String, expected: String) {
        val end = System.currentTimeMillis() + 25000
        while (System.currentTimeMillis() < end) {
            if (fields(path).optJSONObject(field)?.optString("stringValue") == expected) return
            Thread.sleep(100)
        }
        fail("$path.$field did not become $expected")
    }

    private suspend fun invoke(name: String, body: String): Int {
        val connection = URL("http://127.0.0.1:54321/functions/v1/$name").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"; connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${auth.currentUser!!.getIdToken(false).await().token}")
            connection.setRequestProperty("apikey", InstrumentationRegistry.getArguments().getString("localSupabaseKey"))
            connection.outputStream.use { it.write(body.toByteArray()) }
            connection.responseCode.also { connection.inputStream.close() }
        } finally { connection.disconnect() }
    }

    private fun waitFor(matcher: Matcher<View>) {
        onView(isRoot()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isRoot()
            override fun getDescription() = "Wait for device UI: $matcher"
            override fun perform(uiController: UiController, view: View) {
                val end = System.currentTimeMillis() + 30000
                do {
                    if (TreeIterables.breadthFirstViewTraversal(view).any { matcher.matches(it) && it.isShown }) return
                    uiController.loopMainThreadForAtLeast(100)
                } while (System.currentTimeMillis() < end)
                throw AssertionError("Missing device UI: $matcher")
            }
        })
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val evidenceRun = InstrumentationRegistry.getArguments().getString("handoffEvidenceRun").orEmpty()
        require(evidenceRun.matches(Regex("[a-zA-Z0-9_-]*")))
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null),
            if (evidenceRun.isBlank()) "handoff-device" else "handoff-device/$evidenceRun").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}

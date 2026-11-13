package com.example.booking_app.feature.admin

import android.graphics.Bitmap
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.UiController
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.util.TreeIterables
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import com.example.booking_app.MainActivity
import com.example.booking_app.R
import com.example.booking_app.feature.admin.domain.repository.AdminToolsRepository
import com.example.booking_app.feature.admin.domain.model.AdminCategoryKind
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import javax.inject.Inject

/** Opt-in real fragments/repositories with local services; no view inflation substitutes. */
@HiltAndroidTest
class AdminLocalInteractionTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var auth: FirebaseAuth
    @Inject lateinit var firestore: FirebaseFirestore
    @Inject lateinit var tools: AdminToolsRepository
    @Inject lateinit var session: com.example.booking_app.core.session.SessionManager

    @Test fun specialEmailWithPlayerClaimDoesNotEnterAdmin() = runBlocking {
        hilt.inject()
        assertEquals("demo-admin-repair", auth.app.options.projectId)
        auth.signInWithEmailAndPassword("eganvenven@gmail.com", "Test123456").await()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var graph = 0
            val deadline = System.currentTimeMillis() + 25000
            while (System.currentTimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    val controller = (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as androidx.navigation.fragment.NavHostFragment).navController
                    graph = generateSequence<androidx.navigation.NavDestination>(controller.currentDestination) { it.parent }
                        .firstOrNull { it.id in setOf(R.id.nav_player, R.id.nav_owner, R.id.nav_admin) }?.id ?: 0
                }
                if (graph == R.id.nav_player) break
                Thread.sleep(100)
            }
            assertEquals(R.id.nav_player, graph)
            assertEquals(com.example.booking_app.core.session.AppRole.PLAYER, session.currentAppRole())
            scenario.onActivity { activity ->
                assertTrue("PLAYER must not initialize admin query ViewModels",
                    activity.viewModelStore.keys().none { it.toString().contains("feature.admin") })
            }
            onView(withId(R.id.cardPendingApplications)).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
            try {
                firestore.collection("ownerApplications").get(com.google.firebase.firestore.Source.SERVER).await()
                throw AssertionError("PLAYER unexpectedly read admin applications")
            } catch (error: com.google.firebase.firestore.FirebaseFirestoreException) {
                assertEquals(com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED, error.code)
            }
            screenshot("00-player-no-admin")
        }
        auth.signOut()
    }

    @Test fun approveAndCategoryAndVenueNavigation() = runBlocking {
        hilt.inject()
        assertEquals("demo-admin-repair", auth.app.options.projectId)
        auth.signInWithEmailAndPassword("admin-local@example.test", "Test123456").await()
        val variant = InstrumentationRegistry.getArguments().getString("captureVariant").orEmpty()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                if (variant.contains("dark")) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            )
        }
        Intents.init()
        Intents.intending(hasAction(Intent.ACTION_VIEW)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(withId(R.id.cardPendingApplications))
            screenshot("01-home")
            onView(withId(R.id.cardPendingApplications)).perform(click())
            waitFor(withText("UI Local Venue"))
            screenshot("02-queue")
            onView(withText("UI Local Venue")).perform(click())
            waitFor(withText("ui-player@example.test"))
            screenshot("03-detail")
            onView(withId(R.id.cardDocBusinessLicense)).perform(scrollTo(), click())
            waitForIntentCount(1)
            onView(withId(R.id.cardDocSignage)).perform(scrollTo(), click())
            waitForIntentCount(2)
            val documents = Intents.getIntents().filter { it.action == Intent.ACTION_VIEW }.mapNotNull { it.data }
            assertTrue(documents.any { it.path?.endsWith("/one.jpg") == true && it.getQueryParameter("token") != null })
            assertTrue(documents.any { it.path?.endsWith("/two.jpg") == true && it.getQueryParameter("token") != null })
            for (document in documents) {
                assertEquals("127.0.0.1", document.host)
                val connection = java.net.URL(document.toString()).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                assertEquals(200, connection.responseCode)
                connection.inputStream.use { assertTrue(it.readBytes().isNotEmpty()) }
                connection.disconnect()
            }
            screenshot("03-documents")
            onView(withId(R.id.buttonReject)).perform(click())
            waitFor(withId(R.id.inputRejectReason))
            onView(withId(R.id.inputRejectReason)).perform(replaceText("Retained local draft"), closeSoftKeyboard())
            screenshot("04-rejection-draft")
            onView(withId(R.id.buttonCloseDialog)).perform(click())
            onView(withId(R.id.buttonReject)).perform(click())
            onView(withId(R.id.inputRejectReason)).check(matches(withText("Retained local draft")))
            onView(withId(R.id.buttonCloseDialog)).perform(click())
            onView(withId(R.id.buttonApprove)).perform(click())
            onView(withId(android.R.id.button1)).perform(click())
            waitFor(withId(R.id.recyclerApplications))
            assertEquals("APPROVED", firestore.collection("ownerApplications").document("ui-application").get().await().getString("status"))
            onView(withId(R.id.chipApproved)).perform(click())
            waitFor(withText("UI Local Venue"))
            screenshot("05-approved")
            onView(withId(R.id.tab_admin_reports)).perform(click())
            waitFor(withText("UI Local Report"))
            screenshot("05-reports")
            onView(withText("UI Local Report")).perform(click())
            waitFor(withId(R.id.radioActionDismiss))
            onView(withId(R.id.layoutSameTargetNotice)).check(matches(org.hamcrest.Matchers.not(isDisplayed())))
            screenshot("06-report-detail")
            onView(withId(R.id.radioActionDismiss)).perform(scrollTo(), click())
            onView(withId(R.id.buttonSubmitConclusion)).perform(click())
            onView(withId(android.R.id.button1)).perform(click())
            waitFor(allOf(withId(R.id.buttonTabClosed), isDisplayingAtLeast(90)))
            onView(withId(R.id.buttonTabClosed)).perform(click())
            waitFor(withText("UI Local Report"))
            onView(withId(R.id.tab_admin_users)).perform(click())
            waitFor(withId(R.id.inputUserSearch))
            onView(withId(R.id.inputUserSearch)).perform(replaceText("ui-player@example.test"), pressImeActionButton(), closeSoftKeyboard())
            waitFor(withText("UI Applicant"))
            screenshot("07-users-search")
            onView(withId(R.id.tab_admin_tools)).perform(click())
            waitFor(withId(R.id.chipCategoryAmenities))
            onView(withId(R.id.chipCategoryAmenities)).perform(click())
            waitFor(withText("UI Local Amenity"))
            assertAmenityHint()
            screenshot("10-amenities")
            onView(withId(R.id.inputAddCategory)).perform(scrollTo(), replaceText("UI Added Amenity"), closeSoftKeyboard())
            onView(withId(R.id.buttonAddCategory)).perform(scrollTo(), click())
            waitFor(withText("UI Added Amenity"))
            assertTrue(firestore.collection("districts").whereEqualTo("name", "UI Added Amenity").get().await().isEmpty)
            for (kind in AdminCategoryKind.entries) {
                val id = if (kind == AdminCategoryKind.DISTRICTS) "ui-district" else "ui-amenity"
                val row = tools.getCategories(kind).getOrThrow().first { it.id == id }
                assertEquals(kind, row.kind)
                tools.saveDistrict(row.copy(name = row.name + " edited")).getOrThrow()
                assertEquals(row.name + " edited", firestore.collection(kind.collection).document(id).get().await().getString("name"))
                tools.saveDistrict(row).getOrThrow()
            }
            onView(withId(R.id.cardNavBroadcast)).perform(scrollTo(), click())
            waitFor(withId(R.id.inputBroadcastTitle))
            onView(withId(R.id.inputBroadcastTitle)).perform(replaceText("UI local broadcast"), closeSoftKeyboard())
            onView(withId(R.id.inputBroadcastBody)).perform(replaceText("Local only"), closeSoftKeyboard())
            screenshot("11-broadcast")
            onView(withId(R.id.buttonSendBroadcast)).perform(click())
            onView(withId(android.R.id.button1)).perform(click())
            waitFor(allOf(withId(R.id.inputBroadcastTitle), withText("")))
            androidx.test.espresso.Espresso.pressBack()
            waitFor(withId(R.id.cardNavVouchers))
            onView(withId(R.id.chipCategoryAmenities)).check(matches(isChecked()))
            assertAmenityHint()
            onView(withId(R.id.cardNavVouchers)).perform(scrollTo(), click())
            waitFor(withId(R.id.inputVoucherCode))
            screenshot("12-voucher-form")
            onView(withId(R.id.inputVoucherPerUserLimit)).perform(scrollTo())
            screenshot("12-voucher-validation-fields")
            androidx.test.espresso.Espresso.pressBack()
            onView(withId(R.id.buttonManageVenues)).perform(scrollTo(), click())
            waitFor(withId(R.id.inputVenueSearch))
            onView(withId(R.id.inputVenueSearch)).perform(replaceText("ui-application"), closeSoftKeyboard())
            onView(withId(R.id.buttonSearchVenue)).perform(click())
            waitFor(withId(R.id.buttonLockVenue))
            onView(withId(R.id.buttonLockVenue)).perform(scrollTo(), click())
            onView(withId(android.R.id.button1)).perform(click())
            waitFor(withId(R.id.buttonUnlockActive))
            onView(withId(R.id.buttonUnlockActive)).perform(scrollTo(), click())
            waitFor(withId(R.id.buttonConfirmUnlock))
            screenshot("09-venue-unlock")
            onView(withId(R.id.radioOptionActive)).perform(click())
            onView(withId(R.id.buttonConfirmUnlock)).perform(click())
            waitFor(withId(R.id.buttonLockVenue))
        }
        Intents.release()
        auth.signOut()
    }

    private fun waitForIntentCount(count: Int) {
        val end = System.currentTimeMillis() + 20000
        do {
            if (Intents.getIntents().count { it.action == Intent.ACTION_VIEW } >= count) return
            Thread.sleep(100)
        } while (System.currentTimeMillis() < end)
        throw AssertionError("Signed document intent did not arrive")
    }

    private fun waitFor(matcher: Matcher<View>) {
        onView(isRoot()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isRoot()
            override fun getDescription() = "Wait for local backend UI: $matcher"
            override fun perform(uiController: UiController, view: View) {
                val end = System.currentTimeMillis() + 25000
                do {
                    if (TreeIterables.breadthFirstViewTraversal(view).any { matcher.matches(it) && it.isShown }) return
                    uiController.loopMainThreadForAtLeast(100)
                } while (System.currentTimeMillis() < end)
                throw AssertionError("Missing local UI: $matcher")
            }
        })
    }

    private fun assertAmenityHint() {
        onView(withId(R.id.layoutAddCategory)).check { view, error ->
            if (error != null) throw error
            assertEquals(view.context.getString(R.string.admin_tools_category_add_amenity_hint),
                (view as com.google.android.material.textfield.TextInputLayout).hint.toString())
        }
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // Let transient success toasts expire so comparison images show the controls.
        android.os.SystemClock.sleep(3600)
        val suffix = InstrumentationRegistry.getArguments().getString("captureVariant") ?: "phone-light"
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "admin-validation/$suffix").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}

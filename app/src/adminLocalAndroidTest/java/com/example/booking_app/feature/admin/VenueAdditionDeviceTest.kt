package com.example.booking_app.feature.admin

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.core.net.toUri
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.util.TreeIterables
import androidx.test.platform.app.InstrumentationRegistry
import com.example.booking_app.MainActivity
import com.example.booking_app.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/** Real fragments, repositories, Storage and Edge Functions; only the OS file picker is supplied a TEST image. */
@HiltAndroidTest
class VenueAdditionDeviceTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var auth: FirebaseAuth
    @Inject lateinit var firestore: FirebaseFirestore

    @Test fun additionalVenueRejectResubmitApprovePreservesExistingOwnerAndSurvivesRelaunch() = runBlocking {
        hilt.inject()
        InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("owner-onboarding-drafts",0).edit().clear().commit()
        login("venue-test-owner")
        var id = ""
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openVenues(scenario)
            waitFor(withText("TEST Existing Venue"))
            screenshot("01-existing-venue")
            onView(withId(R.id.addVenue)).perform(click())
            fillAndSubmit("TEST Additional Venue")
            id = application("venue-test-owner", "VENUE_ADDITION")
            assertOwnerPreserved()
            assertEquals(1,firestore.collection("venues").whereEqualTo("ownerId","venue-test-owner").get(Source.SERVER).await().size())
            screenshot("04-addition-pending")
            // The applicant uses the actual private signed-download/preview path.
            onView(withId(R.id.statusDocuments)).check(matches(isDisplayed()))
            onView(withText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.owner_view_document_number,1))).perform(scrollTo(),click())
            waitFor(withText(R.string.owner_document_preview), dialog = true)
            screenshot("04-private-document-preview")
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
        }
        review("TEST Additional Venue", reject = true)
        login("venue-test-owner")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openVenues(scenario)
            onView(withId(R.id.venueApplications)).perform(click())
            waitFor(withSubstring("TEST Additional Venue"))
            screenshot("06-application-history")
            onView(withSubstring("TEST Additional Venue")).perform(click())
            waitFor(withText("TEST Bổ sung giấy tờ"))
            screenshot("07-addition-rejected")
            assertOwnerPreserved()
            onView(withId(R.id.statusAction)).perform(click())
            waitFor(allOf(withId(R.id.venueName),withText("TEST Additional Venue")))
            onView(withId(R.id.venueName)).perform(scrollTo(),replaceText("TEST Additional Venue Revised"),closeSoftKeyboard())
            onView(withId(R.id.reviewButton)).perform(click())
            waitFor(withId(R.id.submitButton))
            onView(withId(R.id.submitButton)).perform(click())
            waitFor(withId(R.id.statusDocuments))
            waitFor(allOf(withId(R.id.statusTitle),withText(R.string.owner_status_pending)))
            assertEquals(id,application("venue-test-owner","VENUE_ADDITION"))
            assertOwnerPreserved()
            screenshot("08-addition-resubmitted")
        }
        review("TEST Additional Venue Revised", reject = false)
        login("venue-test-owner")
        repeat(2) { attempt ->
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                openVenues(scenario)
                waitFor(withText("TEST Additional Venue Revised"))
                screenshot("approved-new-venue-$attempt")
                onView(withId(R.id.venues)).perform(object:ViewAction {
                    override fun getConstraints():Matcher<View> = isAssignableFrom(androidx.recyclerview.widget.RecyclerView::class.java)
                    override fun getDescription()="Scroll to preserved existing venue"
                    override fun perform(uiController:UiController,view:View) {
                        ((view as androidx.recyclerview.widget.RecyclerView).layoutManager as androidx.recyclerview.widget.LinearLayoutManager).scrollToPositionWithOffset(1,0)
                        uiController.loopMainThreadForAtLeast(300)
                    }
                })
                waitFor(allOf(withText("TEST Existing Venue"), isDisplayed()))
                assertOwnerPreserved()
                val venues=firestore.collection("venues").whereEqualTo("ownerId","venue-test-owner").get(Source.SERVER).await()
                assertEquals(2,venues.size())
                assertEquals("venue-test-owner",firestore.document("venues/$id").get(Source.SERVER).await().getString("ownerId"))
                screenshot(if(attempt==0) "10-two-venues-after-approval" else "11-two-venues-after-relaunch")
            }
        }
    }

    @Test fun initialOnboardingStillSubmitsAndGrantsOwnerAfterAdminApproval() = runBlocking {
        hilt.inject()
        InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("owner-onboarding-drafts",0).edit().clear().commit()
        login("venue-test-player")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            navigateWhenReady(scenario,R.id.nav_player) { it.navigate("smashnow://owner/apply?mode=new".toUri()) }
            fillAndSubmit("TEST First Venue")
            assertEquals("PLAYER",firestore.document("users/venue-test-player").get(Source.SERVER).await().getString("role"))
            assertEquals("PENDING",firestore.document("users/venue-test-player").get(Source.SERVER).await().getString("ownerStatus"))
            screenshot("12-initial-onboarding-pending")
        }
        review("TEST First Venue",reject=false)
        login("venue-test-player")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openVenues(scenario)
            waitFor(withText("TEST First Venue"))
            assertEquals("OWNER",firestore.document("users/venue-test-player").get(Source.SERVER).await().getString("role"))
            assertEquals("APPROVED",firestore.document("users/venue-test-player").get(Source.SERVER).await().getString("ownerStatus"))
            screenshot("13-initial-onboarding-approved")
        }
    }

    private fun fillAndSubmit(name: String) {
        waitFor(allOf(withId(R.id.venueName),isEnabled()))
        for ((field,value) in listOf(R.id.venueName to name,R.id.address to "TEST 88 Su Van Hanh",R.id.venuePhone to "0900000000",R.id.courtCount to "2",R.id.openTime to "06:00",R.id.closeTime to "22:00",R.id.representativeName to "TEST Owner",R.id.representativePhone to "0900000000")) {
            onView(withId(field)).perform(scrollTo(),replaceText(value),closeSoftKeyboard())
        }
        onView(withId(R.id.district)).perform(scrollTo(),click())
        onView(withText("TEST District")).inRoot(isPlatformPopup()).perform(click())
        onView(withId(R.id.changePin)).perform(scrollTo(),click())
        onView(withId(R.id.latitude)).inRoot(isDialog()).perform(scrollTo(),replaceText("10.77"),closeSoftKeyboard())
        onView(withId(R.id.longitude)).inRoot(isDialog()).perform(scrollTo(),replaceText("106.66"),closeSoftKeyboard())
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val resolver=context.contentResolver
        val values=ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME,"TEST-venue.jpg");put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");put(MediaStore.Images.Media.IS_PENDING,1) }
        val uri=checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values))
        val bitmap=Bitmap.createBitmap(480,320,Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply { drawColor(Color.WHITE);drawText("TEST VENUE / DOCUMENT",20f,160f,Paint().apply { color=Color.BLACK;textSize=28f }) }
        checkNotNull(resolver.openOutputStream(uri)).use { bitmap.compress(Bitmap.CompressFormat.JPEG,90,it) };bitmap.recycle()
        resolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)
        Intents.init()
        try {
            Intents.intending(hasAction(Intent.ACTION_GET_CONTENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK,Intent().apply { data=uri;clipData=ClipData.newRawUri("TEST",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }))
            onView(withId(R.id.addPhoto)).perform(scrollTo(),click())
            waitFor(allOf(withId(R.id.addPhoto),isEnabled()))
            onView(withId(R.id.addDocument)).perform(scrollTo(),click())
            waitFor(allOf(withId(R.id.addDocument),isEnabled()))
            screenshot("02-form-with-test-uploads-$name")
        } finally { Intents.release();resolver.delete(uri,null,null) }
        onView(withId(R.id.reviewButton)).perform(click())
        waitFor(withId(R.id.submitButton))
        screenshot("03-review-$name")
        onView(withId(R.id.submitButton)).perform(click())
        waitFor(allOf(withId(R.id.statusTitle),withText(R.string.owner_status_pending)))
    }

    private suspend fun review(name: String, reject: Boolean) {
        login("venue-test-admin")
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(withId(R.id.cardPendingApplications))
            onView(withId(R.id.cardPendingApplications)).perform(click())
            waitFor(withText(name))
            onView(withText(name)).perform(click())
            waitFor(withId(R.id.buttonApprove))
            screenshot(if(reject) "05-admin-addition-review" else "09-admin-approve-$name")
            if(reject) {
                onView(withId(R.id.buttonReject)).perform(click())
                waitFor(withId(R.id.inputRejectReason), dialog = true)
                onView(withId(R.id.inputRejectReason)).perform(replaceText("TEST Bổ sung giấy tờ"),closeSoftKeyboard())
                onView(withId(R.id.buttonConfirmReject)).perform(click())
            } else {
                onView(withId(R.id.buttonApprove)).perform(click())
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            }
            waitFor(withId(R.id.chipPending))
        }
    }

    private suspend fun application(uid: String,purpose: String): String = firestore.collection("ownerApplications").whereEqualTo("uid",uid).get(Source.SERVER).await().documents.single { it.getString("purpose")==purpose }.id
    private suspend fun assertOwnerPreserved() {
        val profile=firestore.document("users/venue-test-owner").get(Source.SERVER).await()
        assertEquals("OWNER",profile.getString("role"));assertEquals("APPROVED",profile.getString("ownerStatus"))
        assertEquals("ACTIVE",firestore.document("venues/venue-test-existing").get(Source.SERVER).await().getString("status"))
        assertEquals("OWNER",auth.currentUser!!.getIdToken(true).await().claims["appRole"])
    }
    private suspend fun login(uid:String) {
        assertEquals("demo-admin-repair",auth.app.options.projectId)
        auth.signOut();auth.signInWithEmailAndPassword("$uid@example.test","Test123456").await();auth.currentUser!!.getIdToken(true).await()
    }
    private fun openVenues(scenario:ActivityScenario<MainActivity>) = navigateWhenReady(scenario,R.id.nav_owner) { it.navigate(R.id.ownerVenueListFragment) }
    private fun navigateWhenReady(scenario:ActivityScenario<MainActivity>,graph:Int,action:(androidx.navigation.NavController)->Unit) {
        val end=System.currentTimeMillis()+30000
        var ready=false
        while(!ready && System.currentTimeMillis()<end) {
            scenario.onActivity { activity ->
                val controller=(activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController
                ready=generateSequence(controller.currentDestination){it.parent}.any { it.id==graph }
                if(ready && graph != R.id.nav_owner) action(controller)
            }
            if(!ready) Thread.sleep(100)
        }
        assertTrue("Role graph must load",ready)
        if(graph == R.id.nav_owner) {
            waitFor(withId(R.id.calendarButton))
            scenario.onActivity {activity -> action((activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController)}
        }
    }
    private fun waitFor(matcher:Matcher<View>, dialog:Boolean = false) {
        val target = onView(isRoot())
        if (dialog) target.inRoot(isDialog())
        target.perform(object:ViewAction {
            override fun getConstraints():Matcher<View> = isRoot()
            override fun getDescription()="Wait for venue flow: $matcher"
            override fun perform(uiController:UiController,view:View) {
                val end=System.currentTimeMillis()+35000
                do {
                    if(TreeIterables.breadthFirstViewTraversal(view).any { matcher.matches(it) && it.isShown }) return
                    uiController.loopMainThreadForAtLeast(100)
                } while(System.currentTimeMillis()<end)
                screenshot("failure")
                throw AssertionError("Missing venue UI: $matcher")
            }
        })
    }
    private fun screenshot(name:String) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val directory=File(instrumentation.targetContext.getExternalFilesDir(null),"venue-addition-device").apply { mkdirs() }
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        File(directory,name.replace(Regex("[^A-Za-z0-9_-]"),"_")+".png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
}

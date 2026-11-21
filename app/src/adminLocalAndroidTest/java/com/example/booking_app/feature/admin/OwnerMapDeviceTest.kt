package com.example.booking_app.feature.admin

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import android.widget.EditText
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.Press
import androidx.test.platform.app.InstrumentationRegistry
import com.example.booking_app.MainActivity
import com.example.booking_app.R
import com.example.booking_app.core.map.OsmdroidSetup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.hamcrest.Matcher
import org.hamcrest.Matchers.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.osmdroid.views.MapView

@HiltAndroidTest
class OwnerMapDeviceTest {
    @get:Rule val hilt=HiltAndroidRule(this)
    @Inject lateinit var auth:FirebaseAuth
    @Inject lateinit var firestore:FirebaseFirestore

    @Test fun additionalApplicationMapPinSurvivesActualRotation() = runBlocking {
        login()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            open(scenario,R.id.ownerVenueListFragment)
            waitFor(allOf(withId(R.id.addVenue),isEnabled()))
            onView(withId(R.id.addVenue)).perform(click())
            waitFor(allOf(withId(R.id.changePin),isEnabled()))
            onView(withId(R.id.changePin)).perform(scrollTo(),click())
            tilesReady(dialog=true)
            assertEquals(OsmdroidSetup.USER_AGENT,org.osmdroid.config.Configuration.getInstance().userAgentValue)
            pickOnMap()
            val latitude=read(R.id.latitude);val longitude=read(R.id.longitude)
            shot("20-application-map-pin")
            scenario.onActivity {it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            orientation(scenario,Configuration.ORIENTATION_LANDSCAPE)
            waitFor(allOf(withId(R.id.latitude),withText(latitude)),dialog=true)
            onView(withId(R.id.longitude)).inRoot(isDialog()).check(matches(withText(longitude)))
            shot("21-map-pin-dialog-landscape")
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            waitFor(allOf(withId(R.id.locationValue),withText(containsString(latitude))))
            scenario.onActivity {it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
            orientation(scenario,Configuration.ORIENTATION_PORTRAIT)
            onView(withId(R.id.changePin)).perform(scrollTo())
            tilesReady(dialog=false)
            shot("22-application-map-preview")
            assertEquals(3,firestore.collection("venues").whereEqualTo("ownerId","feature11-owner").get(Source.SERVER).await().size())
        }
    }

    @Test fun venueMapPinPersistsAndCancelDoesNotWriteCoordinates() = runBlocking {
        login();var latitude="";var longitude=""
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            open(scenario,R.id.ownerVenueDetailFragment)
            waitFor(allOf(withId(R.id.location),isEnabled()))
            onView(withId(R.id.location)).perform(scrollTo(),click())
            tilesReady(dialog=true);pickOnMap()
            latitude=read(R.id.latitude);longitude=read(R.id.longitude)
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.save)).perform(scrollTo(),click())
            val deadline=System.currentTimeMillis()+30000;var saved=false
            while(!saved&&System.currentTimeMillis()<deadline) {
                val location=firestore.document("venues/feature11-a").get(Source.SERVER).await().getGeoPoint("location")
                saved=location!=null&&kotlin.math.abs(location.latitude-latitude.toDouble())<0.000001&&kotlin.math.abs(location.longitude-longitude.toDouble())<0.000001
                if(!saved)Thread.sleep(100)
            }
            assertTrue("Map-selected coordinates must be confirmed by backend",saved)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            open(scenario,R.id.ownerVenueDetailFragment)
            waitFor(allOf(withId(R.id.location),withText(containsString(latitude))))
            onView(withId(R.id.location)).perform(scrollTo())
            tilesReady(dialog=false);shot("23-venue-map-server-reload")
            onView(withId(R.id.location)).perform(click())
            onView(withId(R.id.latitude)).inRoot(isDialog()).perform(scrollTo(),replaceText("0"),closeSoftKeyboard())
            onView(withId(R.id.longitude)).inRoot(isDialog()).perform(scrollTo(),replaceText("0"),closeSoftKeyboard())
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.latitude)).inRoot(isDialog()).check(matches(hasErrorText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.owner_location_outside_vietnam))))
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            val location=firestore.document("venues/feature11-a").get(Source.SERVER).await().getGeoPoint("location")!!
            assertEquals(latitude.toDouble(),location.latitude,0.000001);assertEquals(longitude.toDouble(),location.longitude,0.000001)
        }
    }
    private suspend fun login() {
        hilt.inject();assertEquals("demo-admin-repair",auth.app.options.projectId)
        auth.signOut();auth.signInWithEmailAndPassword("feature11-owner@example.test","Test123456").await();auth.currentUser!!.getIdToken(true).await()
    }
    private fun open(scenario:ActivityScenario<MainActivity>,destination:Int) {
        waitFor(withId(R.id.calendarButton))
        scenario.onActivity {activity ->
            (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController.navigate(destination,android.os.Bundle().apply {putString("venueId","feature11-a")})
        }
    }
    private fun pickOnMap() {
        onView(isAssignableFrom(MapView::class.java)).inRoot(isDialog()).perform(GeneralClickAction(Tap.SINGLE,{view ->
            val position=IntArray(2);view.getLocationOnScreen(position)
            floatArrayOf(position[0]+view.width*0.65f,position[1]+view.height*0.45f)
        },Press.FINGER))
        waitFor(allOf(withId(R.id.latitude),withText(not(isEmptyString()))),dialog=true)
        onView(isRoot()).inRoot(isDialog()).perform(object:ViewAction {
            override fun getConstraints():Matcher<View> = isRoot()
            override fun getDescription()="Wait for osmdroid single-tap confirmation"
            override fun perform(controller:UiController,view:View) {controller.loopMainThreadForAtLeast(400)}
        })
    }
    private fun read(id:Int):String {var value="";onView(withId(id)).inRoot(isDialog()).check {view,_->value=(view as EditText).text.toString()};return value}
    private fun tilesReady(dialog:Boolean) = waitFor(allOf(withId(R.id.mapStatus),withEffectiveVisibility(Visibility.GONE)),dialog,shown=false)
    private fun orientation(scenario:ActivityScenario<MainActivity>,expected:Int) {
        val deadline=System.currentTimeMillis()+15000;var ready=false
        while(!ready&&System.currentTimeMillis()<deadline){scenario.onActivity {ready=it.resources.configuration.orientation==expected};if(!ready)Thread.sleep(100)}
        assertTrue("Map dialog must survive actual orientation change",ready)
    }
    private fun waitFor(matcher:Matcher<View>,dialog:Boolean=false,shown:Boolean=true) {
        val interaction=onView(isRoot());val target=if(dialog)interaction.inRoot(isDialog())else interaction
        target.perform(object:ViewAction {
            override fun getConstraints():Matcher<View> = isRoot()
            override fun getDescription()="Wait for owner map UI: $matcher"
            override fun perform(controller:UiController,view:View) {
                val deadline=System.currentTimeMillis()+45000
                do{if(androidx.test.espresso.util.TreeIterables.breadthFirstViewTraversal(view).any {matcher.matches(it)&&(!shown||it.isShown)})return;controller.loopMainThreadForAtLeast(100)}while(System.currentTimeMillis()<deadline)
                shot("map-ui-timeout")
                val statuses=androidx.test.espresso.util.TreeIterables.breadthFirstViewTraversal(view)
                    .filter {it.id==R.id.mapStatus}.joinToString {"visibility=${it.visibility}, text=${(it as? android.widget.TextView)?.text}"}
                throw AssertionError("Missing owner map UI: $matcher; $statuses")
            }
        })
    }
    private fun shot(name:String) {
        val instrument=InstrumentationRegistry.getInstrumentation()
        if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper())instrument.waitForIdleSync()
        val dir=File(instrument.targetContext.getExternalFilesDir(null),"feature11-device").apply {mkdirs()}
        instrument.uiAutomation.takeScreenshot().let {bitmap ->File(dir,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    }
}

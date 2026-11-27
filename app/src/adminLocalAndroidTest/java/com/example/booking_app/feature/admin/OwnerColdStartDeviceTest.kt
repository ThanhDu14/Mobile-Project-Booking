package com.example.booking_app.feature.admin

import android.graphics.Bitmap
import android.view.View
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
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
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.hamcrest.Matcher
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Run in a NEW instrumentation process after force-stop; no sign-in or fixture mutation here. */
@HiltAndroidTest
class OwnerColdStartDeviceTest {
    @get:Rule val hilt=HiltAndroidRule(this)
    @Inject lateinit var auth:FirebaseAuth
    @Inject lateinit var firestore:FirebaseFirestore

    @Test fun persistedOwnerSessionReloadsAllVenuesAfterForceStop() = runBlocking {
        hilt.inject()
        assertEquals("demo-admin-repair",auth.app.options.projectId)
        assertEquals("feature11-owner",auth.currentUser?.uid)
        val profile=firestore.document("users/feature11-owner").get(Source.SERVER).await()
        assertEquals("OWNER",profile.getString("role"));assertEquals("APPROVED",profile.getString("ownerStatus"))
        val expected=firestore.collection("venues").whereEqualTo("ownerId","feature11-owner").get(Source.SERVER).await().documents
        assertEquals(3,expected.size)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var ready=false;val deadline=System.currentTimeMillis()+30000
            while(!ready&&System.currentTimeMillis()<deadline) {
                scenario.onActivity { activity ->
                    val nav=(activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController
                    ready=generateSequence(nav.currentDestination){it.parent}.any {it.id==R.id.nav_owner}
                    if(ready) nav.navigate(R.id.ownerVenueListFragment)
                }
                if(!ready) Thread.sleep(100)
            }
            assertTrue("Persisted session must resolve OWNER graph",ready)
            onView(isRoot()).perform(object:ViewAction {
                override fun getConstraints():Matcher<View> = isRoot()
                override fun getDescription()="Wait for server-reloaded TEST venues"
                override fun perform(controller:UiController,view:View) {
                    val end=System.currentTimeMillis()+30000
                    do {
                        val list=TreeIterables.breadthFirstViewTraversal(view).filterIsInstance<androidx.recyclerview.widget.RecyclerView>().firstOrNull()
                        if(list?.adapter?.itemCount==3) return
                        controller.loopMainThreadForAtLeast(100)
                    } while(System.currentTimeMillis()<end)
                    throw AssertionError("Expected all three venues after cold start")
                }
            })
            val instrument=InstrumentationRegistry.getInstrumentation()
            val dir=File(instrument.targetContext.getExternalFilesDir(null),"feature11-device").apply {mkdirs()}
            instrument.uiAutomation.takeScreenshot().let {bitmap ->
                File(dir,"17-cold-start-owner-venues.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            }
            openAccount(scenario)
            waitFor(org.hamcrest.Matchers.allOf(withId(R.id.modePlayerButton),isEnabled()))
            onView(withId(R.id.modePlayerButton)).perform(scrollTo(),click())
            waitForGraph(scenario,R.id.nav_player)
            assertEquals("OWNER",firestore.document("users/feature11-owner").get(Source.SERVER).await().getString("role"))
            openAccount(scenario)
            waitFor(org.hamcrest.Matchers.allOf(withId(R.id.modeOwnerButton),isEnabled()))
            onView(withId(R.id.modeOwnerButton)).perform(scrollTo(),click())
            waitForGraph(scenario,R.id.nav_owner)
            scenario.onActivity { activity ->
                activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("smashnow://owner/venues")).setPackage(activity.packageName))
            }
            waitFor(withId(R.id.addVenue))
            instrument.uiAutomation.takeScreenshot().let {bitmap ->
                File(dir,"18-owner-mode-deep-link.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            }
            pressBack()
            assertEquals("feature11-owner",auth.currentUser?.uid)
        }
    }

    private fun openAccount(scenario:ActivityScenario<MainActivity>) {
        scenario.onActivity { activity ->
            (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController.navigate(R.id.nav_account)
        }
    }
    private fun waitForGraph(scenario:ActivityScenario<MainActivity>,graph:Int) {
        val deadline=System.currentTimeMillis()+30000;var ready=false
        while(!ready&&System.currentTimeMillis()<deadline) {
            scenario.onActivity { activity ->
                val nav=(activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController
                ready=generateSequence(nav.currentDestination){it.parent}.any {it.id==graph}
            }
            if(!ready) Thread.sleep(100)
        }
        assertTrue("Expected mode graph $graph",ready)
    }
    private fun waitFor(matcher:Matcher<View>) {
        onView(isRoot()).perform(object:ViewAction {
            override fun getConstraints():Matcher<View> = isRoot()
            override fun getDescription()="Wait for local mode/deep-link UI"
            override fun perform(controller:UiController,view:View) {
                val deadline=System.currentTimeMillis()+30000
                do {if(TreeIterables.breadthFirstViewTraversal(view).any {matcher.matches(it)&&it.isShown}) return
                    controller.loopMainThreadForAtLeast(100)
                } while(System.currentTimeMillis()<deadline)
                throw AssertionError("Missing local mode/deep-link UI: $matcher")
            }
        })
    }
}

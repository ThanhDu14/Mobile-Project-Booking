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
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.util.TreeIterables
import androidx.test.platform.app.InstrumentationRegistry
import com.example.booking_app.MainActivity
import com.example.booking_app.R
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.hamcrest.Matcher
import org.hamcrest.Matchers.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** UI commands use the production repositories against explicitly local TEST fixtures. */
@HiltAndroidTest
class OwnerFeature11DeviceTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var auth: FirebaseAuth
    @Inject lateinit var firestore: com.google.firebase.firestore.FirebaseFirestore
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val tomorrow get() = LocalDate.now(zone).plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE)

    @Test fun venuePricingBookingsCheckInVoucherAndStatistics() = runBlocking {
        login()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            navigate(scenario,R.id.ownerVenueDetailFragment,"feature11-a")
            waitFor(allOf(withId(R.id.name),withText("TEST Facility A")))
            fill(R.id.courtName,"TEST New Court")
            onView(withId(R.id.addCourt)).perform(scrollTo(),click())
            waitFor(withText("TEST New Court")); shot("01-court-created")
            awaitValue("venues/feature11-a","courtCount","3")
            val courtPath="venues/feature11-a/courts"
            val created=firestore.collection(courtPath).get(com.google.firebase.firestore.Source.SERVER).await().documents.single {it.getString("name")=="TEST New Court"}
            rowAction(R.id.edit,"TEST New Court");fill(R.id.courtName,"TEST Updated Court")
            scenario.recreate();waitFor(allOf(withId(R.id.courtName),withText("TEST Updated Court")))
            onView(withId(R.id.addCourt)).perform(scrollTo(),click())
            awaitValue("$courtPath/${created.id}","name","TEST Updated Court")
            assertEquals(3,firestore.collection(courtPath).get(com.google.firebase.firestore.Source.SERVER).await().size())
            rowAction(R.id.deactivate,"TEST Updated Court")
            awaitValue("$courtPath/${created.id}","isActive","false")
            awaitValue("venues/feature11-a","courtCount","2")
            rowAction(R.id.up,"TEST Updated Court")
            awaitValue("$courtPath/${created.id}","sortOrder","1")
            shot("01-court-edit-rotation-order-inactive")
            rowAction(R.id.deactivate,"TEST Court 1")
            awaitValue("venues/feature11-a","courtCount","1")
            rowAction(R.id.deactivate,"TEST Court 2")
            awaitValue("venues/feature11-a","courtCount","0")
            waitFor(allOf(withId(R.id.active),withText(R.string.owner_status_inactive),
                isDescendantOfA(allOf(isAssignableFrom(com.google.android.material.card.MaterialCardView::class.java),hasDescendant(withText("TEST Court 2"))))))
            onView(withId(R.id.save)).perform(scrollTo(),click())
            waitFor(withText("Đã cập nhật cơ sở"));shot("01-zero-active-courts-venue-edit")
            rowAction(R.id.deactivate,"TEST Court 1")
            awaitValue("venues/feature11-a","courtCount","1")
            rowAction(R.id.deactivate,"TEST Court 2")
            awaitValue("venues/feature11-a","courtCount","2")
            onView(withId(R.id.managePrices)).perform(scrollTo(),click())
            waitFor(allOf(withId(R.id.saveRule),isEnabled()))
            fill(R.id.daysInput,"1,2,3,4,5,6,7");fill(R.id.startTimeInput,"0600");fill(R.id.endTimeInput,"2300");fill(R.id.priceInput,"60000")
            onView(withId(R.id.saveRule)).perform(scrollTo(),click())
            awaitValue("venues/feature11-a","minPricePerHour","60000")
            shot("02-atomic-pricing")

            navigate(scenario,R.id.ownerBookingsFragment,"feature11-a")
            waitFor(withText("TEST approve"));shot("03-booking-deadlines")
            rowAction(R.id.approve,"TEST approve")
            awaitValue("bookings/feature11-approve","status","CONFIRMED")
            rowAction(R.id.reject,"TEST reject");reason("TEST reject reason")
            awaitValue("bookings/feature11-reject","status","REJECTED")
            onView(withId(R.id.filterUpcoming)).perform(click())
            waitFor(withText("TEST cancel"));rowAction(R.id.cancelBooking,"TEST cancel");reason("TEST owner cancellation")
            awaitValue("bookings/feature11-cancel","status","CANCELLED")
            assertEquals("130000",fields("users/feature11-player").getJSONObject("walletBalance").getString("integerValue"))
            shot("04-decisions-and-refunds")

            navigate(scenario,R.id.ownerBookingCheckInFragment,"feature11-b")
            fill(R.id.bookingCode,"feature11-checkin");onView(withId(R.id.submitCheckIn)).perform(scrollTo(),click())
            waitFor(withId(R.id.confirmPaymentButton));shot("05-booking-payment-confirmation")
            onView(withId(R.id.confirmPaymentButton)).perform(scrollTo(),click())
            awaitValue("bookings/feature11-checkin","paymentStatus","PAID")
            assertTrue(fields("bookings/feature11-checkin").has("checkedInAt"))
            shot("06-booking-checked-in")
            fill(R.id.bookingCode,"feature11-checkin");onView(withId(R.id.submitCheckIn)).perform(scrollTo(),click())
            waitFor(allOf(withId(R.id.errorText),isDisplayed()));shot("07-checkin-replay-denied")

            navigate(scenario,R.id.ownerSlotBlockFragment,"feature11-a",Bundle().apply {putString("courtId","feature11-a-c1")})
            waitFor(allOf(withId(R.id.submit),isEnabled()))
            fill(R.id.date,tomorrow);fill(R.id.startTime,"1800");fill(R.id.endTime,"1900")
            onView(withId(R.id.submit)).perform(scrollTo(),click())
            awaitValue("slots/feature11-a-c1_${tomorrow}_1800","status","BLOCKED");shot("08-slots-locked")
            navigate(scenario,R.id.ownerCalendarFragment,"feature11-a")
            waitFor(withId(R.id.nextPeriod));onView(withId(R.id.nextPeriod)).perform(click())
            waitFor(withText("Đã khóa"));shot("09-calendar-day")
            onView(withId(R.id.weekMode)).perform(click());shot("09-calendar-week")
            navigate(scenario,R.id.ownerSlotBlockFragment,"feature11-a",Bundle().apply {putString("courtId","feature11-a-c1")})
            fill(R.id.date,tomorrow)
            val firstBlock=withContentDescription(startsWith("18:00 - 18:30"))
            val secondBlock=withContentDescription(startsWith("18:30 - 19:00"))
            waitForGrid()
            onView(withId(R.id.slotGrid)).perform(object:ViewAction {
                override fun getConstraints():Matcher<View> = isAssignableFrom(androidx.recyclerview.widget.RecyclerView::class.java)
                override fun getDescription()="Scroll canonical TEST slot 18:00 into view"
                override fun perform(controller:UiController,view:View) {
                    val grid=view as androidx.recyclerview.widget.RecyclerView
                    assertTrue((grid.adapter?.itemCount?:0)>25)
                    grid.scrollToPosition(24) // Fixture opens at 06:00; each row represents 30 minutes.
                    controller.loopMainThreadForAtLeast(100)
                }
            })
            onView(firstBlock).perform(scrollTo(),click())
            onView(secondBlock).perform(scrollTo(),click())
            onView(withId(R.id.unlockSelected)).perform(scrollTo(),click())
            awaitMissing("slots/feature11-a-c1_${tomorrow}_1800")
            awaitMissing("slots/feature11-a-c1_${tomorrow}_1830")
            shot("09-slots-unlocked")

            navigate(scenario,R.id.ownerVouchersFragment,"feature11-b")
            waitFor(allOf(withId(R.id.createVoucher),isEnabled()))
            onView(withId(R.id.venueSpinner)).check(matches(withSpinnerText(containsString("Facility B"))))
            onView(withId(R.id.createVoucher)).perform(click())
            waitFor(allOf(withId(R.id.save),isEnabled()))
            fill(R.id.code,"FEATURE11TEST");fill(R.id.value,"10");fill(R.id.maxDiscount,"40000");fill(R.id.minOrder,"100000")
            fill(R.id.validFrom,LocalDate.now(zone).toString());fill(R.id.validTo,LocalDate.now(zone).plusDays(10).toString())
            fill(R.id.usageLimit,"10");fill(R.id.perUserLimit,"1")
            onView(withId(R.id.save)).perform(scrollTo(),click())
            awaitValue("venues/feature11-b/vouchers/FEATURE11TEST","usedCount","0")
            waitFor(withText("FEATURE11TEST"));shot("14-voucher-created")
            onView(withText("FEATURE11TEST")).perform(click())
            waitFor(allOf(withId(R.id.value),withText("10")))
            fill(R.id.value,"15");scenario.recreate()
            waitFor(allOf(withId(R.id.value),withText("15")))
            onView(withId(R.id.save)).perform(scrollTo(),click())
            awaitValue("venues/feature11-b/vouchers/FEATURE11TEST","value","15")
            waitFor(allOf(withId(R.id.isActive),isEnabled()))
            onView(withId(R.id.isActive)).perform(click())
            awaitValue("venues/feature11-b/vouchers/FEATURE11TEST","isActive","false")
            assertEquals("0",fields("venues/feature11-b/vouchers/FEATURE11TEST").getJSONObject("usedCount").getString("integerValue"))
            shot("14-voucher-edited-disabled")

            navigate(scenario,R.id.ownerStatisticsFragment,"feature11-stats")
            waitFor(withId(R.id.custom));onView(withId(R.id.custom)).perform(click())
            fill(R.id.fromDate,LocalDate.now(zone).minusDays(1).toString());fill(R.id.toDate,LocalDate.now(zone).minusDays(1).toString())
            onView(withId(R.id.refresh)).perform(click())
            waitFor(allOf(withId(R.id.revenue),withText(containsString("100.000"))))
            shot("15-statistics-paid-deposit-refund")
            onView(withId(R.id.occupancy)).perform(scrollTo())
            val expectedOccupancy=InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.owner_statistics_occupancy,2.0/15.0*100.0)
            onView(withId(R.id.occupancy)).check(matches(withText(expectedOccupancy)))
            shot("16-statistics-occupancy-peaks")
            scenario.recreate()
            waitFor(allOf(withId(R.id.venueSpinner),withSpinnerText(containsString("Statistics"))))
        }
    }

    @Test fun secondVenueDropInDraftRotationCreationAndCheckIn() = runBlocking {
        login()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            navigate(scenario,R.id.ownerDropInFragment,"feature11-b")
            waitFor(withId(R.id.createSession))
            onView(withId(R.id.venueSpinner)).check(matches(withSpinnerText(containsString("Facility B"))))
            onView(withId(R.id.createSession)).perform(click())
            waitFor(allOf(withId(R.id.save),isEnabled()))
            fill(R.id.date,tomorrow);fill(R.id.startTime,"18:00");fill(R.id.endTime,"19:00");fill(R.id.capacity,"9");fill(R.id.price,"0");fill(R.id.note,"TEST retained draft")
            onView(allOf(withText("TEST Court 2"),isAssignableFrom(com.google.android.material.chip.Chip::class.java))).perform(scrollTo(),click())
            shot("10-dropin-two-courts-draft")
            try {
                scenario.onActivity { it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                waitOrientation(scenario,android.content.res.Configuration.ORIENTATION_LANDSCAPE)
                waitFor(allOf(withId(R.id.capacity),withText("9")))
                shot("11-draft-landscape")
            } finally {
                scenario.onActivity { it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                waitOrientation(scenario,android.content.res.Configuration.ORIENTATION_PORTRAIT)
            }
            waitFor(allOf(withId(R.id.capacity),withText("9")))
            onView(withId(R.id.venueSpinner)).check(matches(withSpinnerText(containsString("Facility B"))))
            onView(allOf(withText("TEST Court 2"),isAssignableFrom(com.google.android.material.chip.Chip::class.java))).check(matches(isChecked()))
            shot("11-draft-restored")
            onView(withId(R.id.save)).perform(scrollTo(),click())
            val id="dropin_feature11-b-c1_${tomorrow}_1800"
            awaitValue("dropInSessions/$id","capacity","9")
            assertEquals("feature11-b",fields("dropInSessions/$id").getJSONObject("venueId").getString("stringValue"))
            navigate(scenario,R.id.ownerDropInRosterFragment,"feature11-b",Bundle().apply {putString("sessionId",id)})
            waitFor(withId(R.id.editSession));onView(withId(R.id.editSession)).perform(click())
            waitFor(allOf(withId(R.id.capacity),withText("9")))
            fill(R.id.capacity,"10");onView(withId(R.id.save)).perform(scrollTo(),click());awaitValue("dropInSessions/$id","capacity","10")
            shot("12-second-venue-edited")
            navigate(scenario,R.id.ownerDropInRosterFragment,"feature11-b",Bundle().apply {putString("sessionId","feature11-checkin-session")})
            waitFor(allOf(withId(R.id.registeredCount),withText("1")))
            fill(R.id.credential,"DI-F22TEST2");onView(withId(R.id.checkIn)).perform(scrollTo(),click())
            awaitValue("dropInSessions/feature11-checkin-session/registrations/feature11-player","status","CHECKED_IN")
            shot("13-dropin-ticket-checkin")
        }
    }

    private suspend fun login() {
        hilt.inject();assertEquals("demo-admin-repair",auth.app.options.projectId)
        auth.signOut();auth.signInWithEmailAndPassword("feature11-owner@example.test","Test123456").await()
        auth.currentUser!!.getIdToken(true).await()
    }
    private fun navigate(scenario:ActivityScenario<MainActivity>,destination:Int,venue:String,extra:Bundle=Bundle()) {
        val deadline=System.currentTimeMillis()+30000;var ready=false
        while(!ready&&System.currentTimeMillis()<deadline) {
            scenario.onActivity { activity ->
                val nav=(activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController
                ready=generateSequence(nav.currentDestination){it.parent}.any {it.id==R.id.nav_owner}
                if(ready) nav.navigate(destination,Bundle(extra).apply {putString("venueId",venue)})
            }
            if(!ready) Thread.sleep(100)
        }
        assertTrue("Owner graph must load",ready)
    }
    private fun fill(id:Int,value:String) {
        if(id==R.id.fromDate||id==R.id.toDate) onView(withId(id)).perform(replaceText(value),closeSoftKeyboard())
        else onView(withId(id)).perform(scrollTo(),replaceText(value),closeSoftKeyboard())
    }
    private fun waitOrientation(scenario:ActivityScenario<MainActivity>,orientation:Int) {
        var ready=false;val deadline=System.currentTimeMillis()+15000
        while(!ready&&System.currentTimeMillis()<deadline) {
            scenario.onActivity { ready=it.resources.configuration.orientation==orientation }
            if(!ready) Thread.sleep(100)
        }
        assertTrue("Expected actual configuration orientation $orientation",ready)
    }
    private fun waitForGrid() {
        onView(isRoot()).perform(object:ViewAction {
            override fun getConstraints():Matcher<View> = isRoot()
            override fun getDescription()="Wait for fresh owner slot grid"
            override fun perform(controller:UiController,view:View) {
                val deadline=System.currentTimeMillis()+30000
                do {
                    val grid=view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.slotGrid)
                    if(view.findViewById<View>(R.id.progress).visibility!=View.VISIBLE&&(grid.adapter?.itemCount?:0)>0) return
                    controller.loopMainThreadForAtLeast(100)
                } while(System.currentTimeMillis()<deadline)
                throw AssertionError("Owner slot grid did not finish loading")
            }
        })
    }
    private fun rowAction(id:Int,customer:String) {
        val target = allOf(withId(id),isDescendantOfA(allOf(isAssignableFrom(com.google.android.material.card.MaterialCardView::class.java),hasDescendant(withText(customer)))))
        waitFor(allOf(target,isEnabled()))
        onView(target).perform(scrollTo(),click())
    }
    private fun reason(value:String) {
        onView(isAssignableFrom(EditText::class.java)).inRoot(isDialog()).perform(replaceText(value),closeSoftKeyboard())
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
    }
    private fun fields(path:String):JSONObject {
        val connection=URL("http://127.0.0.1:18080/v1/projects/demo-admin-repair/databases/(default)/documents/$path").openConnection() as HttpURLConnection
        return try {connection.setRequestProperty("Authorization","Bearer owner");connection.connectTimeout=10000;connection.readTimeout=10000
            if (connection.responseCode == 404) JSONObject() else {
                assertEquals(200,connection.responseCode);JSONObject(connection.inputStream.bufferedReader().use {it.readText()}).getJSONObject("fields")
            }
        } finally {connection.disconnect()}
    }
    private fun awaitValue(path:String,key:String,value:String) {
        val deadline=System.currentTimeMillis()+30000
        while(System.currentTimeMillis()<deadline) {val data=fields(path).optJSONObject(key)
            if(data?.optString("stringValue")==value||data?.optString("integerValue")==value) return
            if(data?.has("booleanValue")==true&&data.optBoolean("booleanValue").toString()==value) return
            Thread.sleep(100)
        }
        shot("failure-backend-$key")
        fail("$path.$key did not become $value")
    }
    private fun awaitMissing(path:String) {
        val deadline=System.currentTimeMillis()+30000
        while(System.currentTimeMillis()<deadline) {
            if(fields(path).length()==0) return
            Thread.sleep(100)
        }
        fail("Expected released slot: $path")
    }
    private fun waitFor(matcher:Matcher<View>) {
        onView(isRoot()).perform(object:ViewAction {
            override fun getConstraints():Matcher<View> = isRoot()
            override fun getDescription()="Wait for TEST UI: $matcher"
            override fun perform(controller:UiController,view:View) {
                val deadline=System.currentTimeMillis()+30000
                do {if(TreeIterables.breadthFirstViewTraversal(view).any {matcher.matches(it)&&it.isShown}) return
                    controller.loopMainThreadForAtLeast(100)
                } while(System.currentTimeMillis()<deadline)
                throw AssertionError("Missing TEST UI: $matcher")
            }
        })
    }
    private fun shot(name:String) {
        val instrument=InstrumentationRegistry.getInstrumentation();instrument.waitForIdleSync()
        val dir=File(instrument.targetContext.getExternalFilesDir(null),"feature11-device").apply {mkdirs()}
        instrument.uiAutomation.takeScreenshot().let {bitmap -> File(dir,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    }
}

package com.example.booking_app.feature.admin

import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.closeSoftKeyboard as dismissKeyboard
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.util.TreeIterables
import androidx.test.platform.app.InstrumentationRegistry
import com.example.booking_app.MainActivity
import com.example.booking_app.R
import com.example.booking_app.feature.notification.NotificationUiFixture
import com.example.booking_app.feature.notification.domain.Notification
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

    @Test fun auditVenueAndAccountLayouts() = runBlocking {
        login()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            navigate(scenario, R.id.ownerVenueListFragment, "feature11-a")
            waitFor(withText("TEST Facility A"))
            waitForVenueListToSettle()
            auditShot("venues")
            onView(allOf(withId(R.id.info), isDescendantOfA(allOf(
                isAssignableFrom(com.google.android.material.card.MaterialCardView::class.java),
                hasDescendant(withText("TEST Facility A")),
            )))).perform(click())
            waitFor(allOf(withId(R.id.name), withText("TEST Facility A"))); auditShot("venue-edit")
            onView(allOf(withId(R.id.name), isAssignableFrom(EditText::class.java)))
                .perform(scrollTo(), click(), replaceText("Sân cầu lông demo khu liên hợp thể thao phía Đông thành phố"))
            checkInputAboveKeyboard(R.id.name)
            auditShot("venue-edit-long-name")
            dismissKeyboard()
            onView(withText("TEST Court 1")).perform(scrollTo()).check(matches(isDisplayed()))
            InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(200, 3_000)
            auditShot("courts")
            onView(withId(R.id.managePrices)).perform(scrollTo(), click())
            waitFor(allOf(withId(R.id.saveRule), isEnabled())); auditShot("prices")
            onView(withId(R.id.priceInput)).perform(scrollTo(), click(), typeText("60000"))
            checkInputAboveKeyboard(R.id.priceInput); auditShot("prices-keyboard")
            dismissKeyboard()
            navigate(scenario, R.id.ownerSlotBlockFragment, "feature11-a", Bundle().apply { putString("courtId", "feature11-a-c1") })
            waitFor(allOf(withId(R.id.submit), isEnabled())); auditShot("slot-block")
            onView(withId(R.id.startTime)).perform(scrollTo(), click(), typeText("1000"))
            checkInputAboveKeyboard(R.id.startTime); auditShot("slot-block-keyboard")
            dismissKeyboard()
            openDeepLink(scenario, "smashnow://owner/venue-applications")
            waitFor(withId(R.id.backButton)); auditShot("venue-applications")
        }
    }

    @Test fun auditSharedOwnerLayouts() = runBlocking {
        login()
        val message = "Tôi muốn hỏi lịch trống cho buổi cầu lông cuối tuần này."
        NotificationUiFixture.reset()
        NotificationUiFixture.allItems = listOf(Notification(
            "owner-audit", "SYSTEM", "Lịch vận hành cơ sở cầu lông cuối tuần",
            "Kiểm tra lịch sân và xác nhận lại các khung giờ đông khách trước cuối tuần.",
            emptyMap(), false, System.currentTimeMillis(),
        ))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            navigate(scenario, R.id.ownerHomePlaceholderFragment, "feature11-a")
            onView(withId(R.id.tab_owner_messages)).perform(click())
            val chatVenue = allOf(withId(R.id.venueSubtitle), withText(containsString("TEST Facility B")))
            waitFor(chatVenue); auditShot("owner-chat-inbox")
            onView(chatVenue).perform(click())
            waitFor(withText(message)); auditShot("owner-chat-conversation")
            onView(withId(R.id.messageInput)).perform(click(), typeText("Test reply"))
            auditShot("owner-chat-keyboard"); checkInputAboveKeyboard(R.id.messageInput)
            dismissKeyboard()
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            navigate(scenario, R.id.ownerHomePlaceholderFragment, "feature11-a")
            onView(withContentDescription(R.string.notification_title)).perform(click())
            waitFor(withContentDescription(R.string.notification_settings_title)); auditShot("notifications")
            onView(withText("Lịch vận hành cơ sở cầu lông cuối tuần")).perform(click())
            onView(withText("Kiểm tra lịch sân và xác nhận lại các khung giờ đông khách trước cuối tuần."))
                .inRoot(isDialog()).check(matches(isDisplayed()))
            auditShot("notification-detail")
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            onView(withContentDescription(R.string.notification_settings_title)).perform(click())
            waitFor(withId(R.id.groups)); auditShot("notification-settings")
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            navigate(scenario, R.id.ownerHomePlaceholderFragment, "feature11-a")
            openDeepLink(scenario, "smashnow://review/owner")
            waitFor(withId(R.id.filters)); auditShot("owner-reviews")
        }
    }

    @Test fun auditSharedAccountLayouts() = runBlocking {
        login()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            navigate(scenario, R.id.ownerHomePlaceholderFragment, "feature11-a")
            onView(withId(R.id.accountAvatar)).perform(click())
            waitFor(withId(R.id.editProfileButton)); auditShot("account")
            listOf(R.id.modePlayerButton, R.id.modeOwnerButton).forEach(::checkFullButtonLabel)
            onView(withId(R.id.editProfileButton)).perform(scrollTo(), click())
            waitFor(withId(R.id.nameInput)); auditShot("account-edit")
            listOf(R.id.levelBeginner, R.id.levelIntermediate, R.id.levelAdvanced).forEach(::checkFullButtonLabel)
            checkEqualButtonHeights(R.id.levelGroup)
            onView(withId(R.id.nameInput)).perform(scrollTo(), click(), typeText(" Demo"))
            auditShot("account-edit-keyboard")
            checkInputAboveKeyboard(R.id.nameInput)
            checkInputAboveKeyboard(R.id.saveButton, requireFocus = false)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            navigate(scenario, R.id.ownerHomePlaceholderFragment, "feature11-a")
            openDeepLink(scenario, "smashnow://account/password")
            waitFor(withId(R.id.currentInput)); auditShot("account-password")
            openDeepLink(scenario, "smashnow://account/email")
            waitFor(withId(R.id.emailInput)); auditShot("account-email")
            openDeepLink(scenario, "smashnow://account/delete")
            waitFor(withId(R.id.keepButton)); auditShot("account-delete")
        }
    }

    @Test fun auditOperationsLayouts() = runBlocking {
        login()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val venue = "feature11-b"
            navigate(scenario, R.id.ownerHomePlaceholderFragment, venue)
            waitFor(withId(R.id.header)); auditShot("today")
            navigate(scenario, R.id.ownerBookingsFragment, "feature11-a")
            waitFor(withText("TEST approve")); auditShot("bookings")
            navigate(scenario, R.id.ownerCalendarFragment, venue)
            waitFor(withId(R.id.nextPeriod)); awaitHidden(R.id.loading); auditShot("calendar-day")
            onView(withId(R.id.weekMode)).perform(click()); awaitHidden(R.id.loading); auditShot("calendar-week")
            navigate(scenario, R.id.ownerBookingCheckInFragment, venue)
            waitFor(withId(R.id.bookingCode)); auditShot("booking-checkin")
            onView(withId(R.id.bookingCode)).perform(scrollTo(), click())
            auditShot("booking-checkin-focused")
            checkInputAboveKeyboard(R.id.bookingCode)
            onView(withId(R.id.bookingCode)).perform(typeText("feature11-checkin"))
            auditShot("booking-checkin-keyboard"); checkInputAboveKeyboard(R.id.bookingCode)
            dismissKeyboard()
            navigate(scenario, R.id.ownerDropInFragment, venue)
            waitFor(withId(R.id.createSession)); awaitHidden(R.id.loading); auditShot("dropin-list")
            onView(withId(R.id.createSession)).perform(click())
            waitFor(withId(R.id.note)); auditShot("dropin-form")
            onView(withId(R.id.note)).perform(scrollTo(), click())
            checkInputAboveKeyboard(R.id.note)
            onView(withId(R.id.note)).perform(typeText("Test note"))
            auditShot("dropin-form-keyboard"); checkInputAboveKeyboard(R.id.note)
            scrollToSaveAboveKeyboard()
            auditShot("dropin-save-keyboard")
            dismissKeyboard()
            navigate(scenario, R.id.ownerDropInRosterFragment, venue, Bundle().apply { putString("sessionId", "feature11-checkin-session") })
            waitFor(allOf(withId(R.id.registeredCount), withText("1")))
            waitFor(withId(R.id.sessionSummary)); auditShot("dropin-roster")
            onView(withId(R.id.credential)).perform(scrollTo(), click(), typeText("DI-F22TEST2"))
            checkInputAboveKeyboard(R.id.credential); auditShot("dropin-roster-keyboard")
            dismissKeyboard()
            navigate(scenario, R.id.ownerVouchersFragment, venue)
            waitFor(allOf(withId(R.id.createVoucher), isEnabled())); auditShot("voucher-list")
            onView(withId(R.id.createVoucher)).perform(click())
            waitFor(withId(R.id.code)); auditShot("voucher-form")
            onView(withId(R.id.perUserLimit)).perform(scrollTo(), click())
            checkInputAboveKeyboard(R.id.perUserLimit)
            onView(withId(R.id.perUserLimit)).perform(typeText("1"))
            auditShot("voucher-form-keyboard"); checkInputAboveKeyboard(R.id.perUserLimit)
            scrollToSaveAboveKeyboard()
            auditShot("voucher-save-keyboard")
            dismissKeyboard()
            navigate(scenario, R.id.ownerStatisticsFragment, "feature11-stats")
            waitFor(withId(R.id.custom)); awaitHidden(R.id.loading); auditShot("statistics")
            onView(withId(R.id.custom)).perform(click())
            checkFullButtonLabel(R.id.custom)
            checkEqualButtonHeights(R.id.periodGroup)
            auditShot("statistics-custom")
        }
    }

    @Test fun auditBookingCountdownContrast() = runBlocking {
        login()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            navigate(scenario, R.id.ownerBookingsFragment, "feature11-a")
            waitFor(withText("TEST approve"))
            val booking = allOf(isAssignableFrom(com.google.android.material.card.MaterialCardView::class.java),
                hasDescendant(withText("TEST approve")))
            onView(allOf(withId(R.id.deadline), isDescendantOfA(booking))).check { view, error ->
                if (error != null) throw error
                val badge = view as TextView
                val background = (badge.background as GradientDrawable).color!!.defaultColor
                val contrast = ColorUtils.calculateContrast(badge.currentTextColor, background)
                assertTrue("Countdown text contrast was $contrast", contrast >= 4.5)
            }
            auditShot("bookings")
        }
    }

    @Test fun auditOwnerDialogsAndSheets() = runBlocking {
        login()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            navigate(scenario, R.id.ownerBookingsFragment, "feature11-a")
            waitFor(withText("TEST approve"))
            val firstBooking = isDescendantOfA(allOf(
                isAssignableFrom(com.google.android.material.card.MaterialCardView::class.java),
                hasDescendant(withText("TEST approve")),
            ))
            onView(allOf(withId(R.id.details), firstBooking)).perform(scrollTo(), click())
            onView(withText(R.string.owner_bookings_details_title)).inRoot(isDialog()).check(matches(isDisplayed()))
            auditShot("booking-details")
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            onView(allOf(withId(R.id.reject), firstBooking)).perform(scrollTo(), click())
            onView(isAssignableFrom(EditText::class.java)).inRoot(isDialog()).perform(click(), typeText("Demo reason"))
            checkDialogInputInsets()
            auditShot("booking-reject-keyboard")
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            navigate(scenario, R.id.ownerDropInRosterFragment, "feature11-b", Bundle().apply {
                putString("sessionId", "feature11-checkin-session")
            })
            waitFor(allOf(withId(R.id.registeredCount), withText("1")))
            onView(withId(R.id.cancelSession)).perform(scrollTo(), click())
            onView(isAssignableFrom(EditText::class.java)).inRoot(isDialog()).perform(click(), typeText("Demo reason"))
            checkDialogInputInsets()
            auditShot("dropin-cancel-keyboard")
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            navigate(scenario, R.id.ownerHomePlaceholderFragment, "feature11-a")
            onView(withId(R.id.accountAvatar)).perform(click())
            waitFor(withId(R.id.editProfileButton))
            onView(withId(R.id.editProfileButton)).perform(scrollTo(), click())
            waitFor(withId(R.id.addDistrictButton))
            onView(withId(R.id.addDistrictButton)).perform(scrollTo(), click())
            onView(withId(R.id.doneButton)).check(matches(isDisplayed()))
            auditShot("account-district-picker")
            onView(withId(R.id.doneButton)).perform(click())
            onView(withId(R.id.changeAvatarButton)).perform(scrollTo(), click())
            onView(withId(R.id.galleryOption)).check(matches(isDisplayed()))
            auditShot("account-avatar-source")
        }
    }

    private fun checkDialogInputInsets() {
        onView(isAssignableFrom(EditText::class.java)).inRoot(isDialog()).check { view, error ->
            if (error != null) throw error
            val container = view.parent as View
            val inset = view.resources.getDimensionPixelSize(R.dimen.space_6)
            assertEquals(inset, container.paddingStart)
            assertEquals(inset, container.paddingEnd)
        }
    }

    private fun checkFullButtonLabel(id: Int) {
        onView(withId(id)).check { view, error ->
            if (error != null) throw error
            val button = view as TextView
            val layout = button.layout
            assertTrue("Button label ${button.text} must fit", layout.getLineEnd(layout.lineCount - 1) >= button.text.length &&
                (0 until layout.lineCount).none { layout.getEllipsisCount(it) != 0 })
        }
    }

    private fun checkEqualButtonHeights(id: Int) {
        onView(withId(id)).check { view, error ->
            if (error != null) throw error
            val group = view as android.view.ViewGroup
            val heights = (0 until group.childCount).map { group.getChildAt(it).height }
            assertEquals("Buttons must share a height: $heights", 1, heights.distinct().size)
        }
    }

    private fun waitForVenueListToSettle() {
        onView(withId(R.id.venues)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(androidx.recyclerview.widget.RecyclerView::class.java)
            override fun getDescription() = "Wait for loaded venue cards to finish animating"
            override fun perform(controller: UiController, view: View) {
                val list = view as androidx.recyclerview.widget.RecyclerView
                val deadline = android.os.SystemClock.uptimeMillis() + 4_000
                while (android.os.SystemClock.uptimeMillis() < deadline) {
                    if (!list.hasPendingAdapterUpdates() && list.itemAnimator?.isRunning != true &&
                        list.childCount > 0 && list.getChildAt(0).alpha == 1f) return
                    controller.loopMainThreadForAtLeast(50)
                }
                throw AssertionError("Venue cards did not become visible")
            }
        })
    }

    private fun scrollToSaveAboveKeyboard() {
        val position = IntArray(2)
        val swipe = GeneralSwipeAction(
            Swipe.FAST,
            { root ->
                root.getLocationOnScreen(position)
                floatArrayOf(position[0] + root.width * .45f, keyboardTop(root) * .87f)
            },
            { root ->
                root.getLocationOnScreen(position)
                floatArrayOf(position[0] + root.width * .45f, keyboardTop(root) * .28f)
            },
            Press.FINGER,
        )
        repeat(2) { onView(isRoot()).perform(swipe) }
        checkInputAboveKeyboard(R.id.save, requireFocus = false)
    }

    private fun keyboardTop(root: View): Int {
        val insets = ViewCompat.getRootWindowInsets(root)
        if (android.os.Build.VERSION.SDK_INT >= 30 && insets != null) {
            val windowManager = root.context.getSystemService(android.view.WindowManager::class.java)
            return windowManager.currentWindowMetrics.bounds.bottom - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        }
        val visible = android.graphics.Rect()
        root.getWindowVisibleDisplayFrame(visible)
        return visible.bottom
    }

    private fun checkInputAboveKeyboard(id: Int, requireFocus: Boolean = true) {
        onView(isRoot()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isRoot()
            override fun getDescription() = "Wait until input $id fits above the visible keyboard"
            override fun perform(controller: UiController, root: View) {
                val deadline = android.os.SystemClock.uptimeMillis() + 4_000
                val field = root.findViewById<View>(id)
                val position = IntArray(2)
                var imeVisible = false
                var keyboardTop = 0
                while (android.os.SystemClock.uptimeMillis() < deadline) {
                    val insets = ViewCompat.getRootWindowInsets(root)
                    imeVisible = insets?.isVisible(WindowInsetsCompat.Type.ime()) == true
                    keyboardTop = keyboardTop(root)
                    field.getLocationOnScreen(position)
                    if (imeVisible && (!requireFocus || field.isFocused) && field.isShown &&
                        position[1] >= 0 && position[1] + field.height <= keyboardTop) return
                    controller.loopMainThreadForAtLeast(50)
                }
                throw AssertionError("Input $id bottom=${position[1] + field.height}, keyboard top=$keyboardTop, ime=$imeVisible, focused=${field.isFocused}")
            }
        })
    }

    private fun auditShot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val run = InstrumentationRegistry.getArguments().getString("ownerUiAuditRun") ?: "device"
        require(run.matches(Regex("[A-Za-z0-9-]+")))
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "owner-ui-audit/$run").apply { mkdirs() }
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }

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
            waitFor(allOf(withId(R.id.venueSpinner), withSpinnerText(containsString("Facility B"))))
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
            waitFor(allOf(withId(R.id.venueSpinner), withSpinnerText(containsString("Facility B"))))
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

    private fun openDeepLink(scenario: ActivityScenario<MainActivity>, uri: String) {
        scenario.onActivity { activity ->
            val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            host.navController.navigate(Uri.parse(uri))
        }
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
        val card = isAssignableFrom(com.google.android.material.card.MaterialCardView::class.java)
        val row = allOf(card, hasDescendant(withText(customer)), not(hasDescendant(card)))
        val target = allOf(withId(id), isDescendantOfA(row))
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
    private fun awaitHidden(id: Int) = waitFor(allOf(withId(id), not(isDisplayed())), requireShown = false)

    private fun waitFor(matcher:Matcher<View>, requireShown: Boolean = true) {
        onView(isRoot()).perform(object:ViewAction {
            override fun getConstraints():Matcher<View> = isRoot()
            override fun getDescription()="Wait for TEST UI: $matcher"
            override fun perform(controller:UiController,view:View) {
                val deadline=System.currentTimeMillis()+30000
                do {if(TreeIterables.breadthFirstViewTraversal(view).any {matcher.matches(it) && (!requireShown || it.isShown)}) return
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

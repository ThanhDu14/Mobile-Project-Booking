package com.example.booking_app.feature.booking_management

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.booking_app.R
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Layout-level device UI tests for Feature 060 My Bookings screens:
 * - fragment_my_bookings_060: 5 tab filter pills, list, progress, empty/error layouts, accessibility
 * - fragment_booking_detail_060: QR container, checked-in card, actions, venue info, policy
 * - fragment_cancel_booking_060: refund card, reason choices, other reason input, confirm/keep actions
 * - item_my_booking_060: card, status badge, details, primary/secondary action buttons
 * Verified across Phone Light (360dp), Phone Dark (360dp), and Tablet (600dp).
 */
@RunWith(AndroidJUnit4::class)
class MyBookingsScreensUiTest {

    @Test
    fun sampleScreensInflateWithTheirPrimaryActionsInLightAndDarkPhoneAndTabletModes() {
        val application = ApplicationProvider.getApplicationContext<Context>()
        val configurations = listOf(
            Configuration(application.resources.configuration).apply {
                smallestScreenWidthDp = 360
                screenWidthDp = 360
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
            },
            Configuration(application.resources.configuration).apply {
                smallestScreenWidthDp = 360
                screenWidthDp = 360
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            },
            Configuration(application.resources.configuration).apply {
                smallestScreenWidthDp = 600
                screenWidthDp = 600
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
            },
            Configuration(application.resources.configuration).apply {
                smallestScreenWidthDp = 600
                screenWidthDp = 600
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            },
        )

        configurations.forEach { configuration ->
            val themedContext = ContextThemeWrapper(
                application.createConfigurationContext(configuration),
                R.style.Theme_Bookingapp
            )

            var myBookingsView: View? = null
            var detailView: View? = null
            var cancelView: View? = null
            var itemView: View? = null

            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val inflater = LayoutInflater.from(themedContext)
                myBookingsView = inflater.inflate(R.layout.fragment_my_bookings_060, null, false)
                detailView = inflater.inflate(R.layout.fragment_booking_detail_060, null, false)
                cancelView = inflater.inflate(R.layout.fragment_cancel_booking_060, null, false)
                itemView = inflater.inflate(R.layout.item_my_booking_060, null, false)
            }

            // 1. My Bookings list screen verification (T024, T025, T043)
            assertNotNull(myBookingsView)
            val list = myBookingsView!!
            assertNotNull("Filter group must exist", list.findViewById<View>(R.id.bookingFilterGroup))
            assertNotNull("Payment filter pill must exist", list.findViewById<View>(R.id.filterPayment))
            assertNotNull("Pending filter pill must exist", list.findViewById<View>(R.id.filterPending))
            assertNotNull("Upcoming filter pill must exist", list.findViewById<View>(R.id.filterUpcoming))
            assertNotNull("Completed filter pill must exist", list.findViewById<View>(R.id.filterCompleted))
            assertNotNull("Cancelled filter pill must exist", list.findViewById<View>(R.id.filterCancelled))
            assertNotNull("Bookings list recycler view must exist", list.findViewById<View>(R.id.listBookings))
            assertNotNull("Loading progress bar must exist", list.findViewById<View>(R.id.progressBookings))
            assertNotNull("Empty layout must exist", list.findViewById<View>(R.id.layoutBookingsEmpty))
            assertNotNull("Error layout must exist", list.findViewById<View>(R.id.layoutBookingsError))
            assertNotNull("Retry button must exist", list.findViewById<View>(R.id.buttonRetry))
            assertNotNull("Policy footer must exist", list.findViewById<View>(R.id.textBookingsPolicy))

            // 2. Booking Detail screen verification (T027, T030, T043)
            assertNotNull(detailView)
            val detail = detailView!!
            assertNotNull("Back button must exist", detail.findViewById<View>(R.id.buttonBack))
            assertNotNull("QR card must exist", detail.findViewById<View>(R.id.cardQr))
            assertNotNull("QR image view must exist", detail.findViewById<View>(R.id.imageBookingQr))
            assertNotNull("Checked in card must exist", detail.findViewById<View>(R.id.cardCheckedIn))
            assertNotNull("Venue name view must exist", detail.findViewById<View>(R.id.textDetailVenue))
            assertNotNull("Time view must exist", detail.findViewById<View>(R.id.textDetailTime))
            assertNotNull("Court name view must exist", detail.findViewById<View>(R.id.textDetailCourt))
            assertNotNull("Address view must exist", detail.findViewById<View>(R.id.textDetailAddress))
            assertNotNull("Directions button must exist", detail.findViewById<View>(R.id.buttonDirections))
            assertNotNull("Call venue button must exist", detail.findViewById<View>(R.id.buttonCallVenue))
            assertNotNull("Cancel booking button must exist", detail.findViewById<View>(R.id.buttonCancelBooking))
            assertNotNull("Cancellation policy view must exist", detail.findViewById<View>(R.id.textCancellationPolicy))

            // 3. Cancel Booking screen verification (T029, T043)
            assertNotNull(cancelView)
            val cancel = cancelView!!
            assertNotNull("Back button must exist", cancel.findViewById<View>(R.id.buttonBack))
            assertNotNull("Refund card must exist", cancel.findViewById<View>(R.id.cardRefund))
            assertNotNull("Refund amount text must exist", cancel.findViewById<View>(R.id.textRefundAmount))
            assertNotNull("Refund explanation text must exist", cancel.findViewById<View>(R.id.textRefundExplanation))
            assertNotNull("Cancel reason group must exist", cancel.findViewById<View>(R.id.groupCancelReasons))
            assertNotNull("Reason busy must exist", cancel.findViewById<View>(R.id.reasonBusy))
            assertNotNull("Reason weather must exist", cancel.findViewById<View>(R.id.reasonWeather))
            assertNotNull("Reason players must exist", cancel.findViewById<View>(R.id.reasonPlayers))
            assertNotNull("Reason wrong booking must exist", cancel.findViewById<View>(R.id.reasonWrongBooking))
            assertNotNull("Reason other must exist", cancel.findViewById<View>(R.id.reasonOther))
            assertNotNull("Other reason input must exist", cancel.findViewById<View>(R.id.inputOtherReason))
            assertNotNull("Keep booking button must exist", cancel.findViewById<View>(R.id.buttonKeepBooking))
            assertNotNull("Confirm cancel button must exist", cancel.findViewById<View>(R.id.buttonConfirmCancel))
            assertNotNull("Cancel policy text must exist", cancel.findViewById<View>(R.id.textCancelPolicy))

            // 4. Booking item layout verification (T026, T038, T043)
            assertNotNull(itemView)
            val item = itemView!!
            assertNotNull("Booking card must exist", item.findViewById<View>(R.id.cardBooking))
            assertNotNull("Status badge container must exist", item.findViewById<View>(R.id.badgeStatusContainer))
            assertNotNull("Status badge text must exist", item.findViewById<View>(R.id.textStatusBadge))
            assertNotNull("Venue name text must exist", item.findViewById<View>(R.id.textVenue))
            assertNotNull("Booking time text must exist", item.findViewById<View>(R.id.textBookingTime))
            assertNotNull("Primary action button must exist", item.findViewById<View>(R.id.buttonPrimaryAction))
            assertNotNull("Secondary action button must exist", item.findViewById<View>(R.id.buttonSecondaryAction))

            // 5. Touch target accessibility checks (minHeight >= 48dp)
            assertTrue(list.findViewById<View>(R.id.buttonRetry).minimumHeight >= dp(themedContext, 48))
            assertTrue(detail.findViewById<View>(R.id.buttonCancelBooking).minimumHeight >= dp(themedContext, 48))
            assertTrue(cancel.findViewById<View>(R.id.buttonConfirmCancel).minimumHeight >= dp(themedContext, 48))
            assertTrue(cancel.findViewById<View>(R.id.buttonKeepBooking).minimumHeight >= dp(themedContext, 48))
        }
    }

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
}

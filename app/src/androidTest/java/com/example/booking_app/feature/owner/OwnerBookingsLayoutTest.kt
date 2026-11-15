package com.example.booking_app.feature.owner

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.booking_app.R
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnerBookingsLayoutTest {
    @Test
    fun bookingQueueInflatesInLightDarkPhoneAndWideConfigurationsWithAccessibleActions() {
        val application = ApplicationProvider.getApplicationContext<Context>()
        val variants = listOf(
            Configuration(application.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
                smallestScreenWidthDp = 360
            },
            Configuration(application.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
                smallestScreenWidthDp = 360
            },
            Configuration(application.resources.configuration).apply { smallestScreenWidthDp = 600 },
        )

        variants.forEach { configuration ->
            val themed = ContextThemeWrapper(application.createConfigurationContext(configuration), R.style.Theme_Bookingapp)
            var screen: View? = null
            var checkIn: View? = null
            var row: View? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                screen = LayoutInflater.from(themed).inflate(R.layout.fragment_owner_bookings, null, false)
                checkIn = LayoutInflater.from(themed).inflate(R.layout.fragment_owner_booking_checkin, null, false)
                row = LayoutInflater.from(themed).inflate(R.layout.item_owner_booking, null, false)
                val time = row!!.findViewById<TextView>(R.id.time)
                val deadline = row!!.findViewById<TextView>(R.id.deadline)
                time.text = "Th 2, 16/11 · 22:30–23:00"
                deadline.text = "Hạn xử lý: 23:44 15/11/2026 · Còn 46 phút 34 giây"
                row!!.measure(View.MeasureSpec.makeMeasureSpec(dp(themed, 320), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                row!!.layout(0, 0, row!!.measuredWidth, row!!.measuredHeight)
                assertTrue("Countdown must not squeeze the booking time", time.width >= dp(themed, 240))
                assertTrue("Booking time must remain readable", time.height < dp(themed, 80))
                assertTrue("Countdown must be below the booking time", deadline.top >= time.bottom)
            }
            assertNotNull(screen!!.findViewById<View>(R.id.bookingScroll))
            assertNotNull(screen!!.findViewById<View>(R.id.venueSpinner))
            assertTrue(screen!!.findViewById<View>(R.id.refresh).minimumHeight >= dp(themed, 48))
            assertTrue(row!!.findViewById<View>(R.id.approve).minimumHeight >= dp(themed, 48))
            assertTrue(row!!.findViewById<View>(R.id.reject).minimumHeight >= dp(themed, 48))
            assertNotNull(row!!.findViewById<View>(R.id.details).contentDescription)
            assertTrue(checkIn!!.findViewById<View>(R.id.scanQr).minimumHeight >= dp(themed, 48))
            assertTrue(checkIn!!.findViewById<View>(R.id.submitCheckIn).minimumHeight >= dp(themed, 48))
            assertNotNull(checkIn!!.findViewById<View>(R.id.bookingCode))
        }
    }

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
}

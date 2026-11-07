package com.example.booking_app.feature.owner

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
            val screen = LayoutInflater.from(themed).inflate(R.layout.fragment_owner_bookings, null, false)
            val checkIn = LayoutInflater.from(themed).inflate(R.layout.fragment_owner_booking_checkin, null, false)
            val row = LayoutInflater.from(themed).inflate(R.layout.item_owner_booking, null, false)
            assertNotNull(screen.findViewById<View>(R.id.bookingScroll))
            assertNotNull(screen.findViewById<View>(R.id.venueSpinner))
            assertTrue(screen.findViewById<View>(R.id.refresh).minimumHeight >= dp(themed, 48))
            assertTrue(row.findViewById<View>(R.id.approve).minimumHeight >= dp(themed, 48))
            assertTrue(row.findViewById<View>(R.id.reject).minimumHeight >= dp(themed, 48))
            assertNotNull(row.findViewById<View>(R.id.details).contentDescription)
            assertTrue(checkIn.findViewById<View>(R.id.scanQr).minimumHeight >= dp(themed, 48))
            assertTrue(checkIn.findViewById<View>(R.id.submitCheckIn).minimumHeight >= dp(themed, 48))
            assertNotNull(checkIn.findViewById<View>(R.id.bookingCode))
        }
    }

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
}

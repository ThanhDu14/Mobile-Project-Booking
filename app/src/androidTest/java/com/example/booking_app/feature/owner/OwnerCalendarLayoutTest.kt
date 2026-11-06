package com.example.booking_app.feature.owner

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

@RunWith(AndroidJUnit4::class)
class OwnerCalendarLayoutTest {
    @Test
    fun ownerCalendarInflatesInLightDarkPhoneAndWideConfigurations() {
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
            Configuration(application.resources.configuration).apply {
                smallestScreenWidthDp = 600
            },
        )

        variants.forEach { configuration ->
            val variantContext = ContextThemeWrapper(application.createConfigurationContext(configuration), R.style.Theme_Bookingapp)
            var calendar: View? = null
            var home: View? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                calendar = LayoutInflater.from(variantContext).inflate(R.layout.fragment_owner_calendar, null, false)
                home = LayoutInflater.from(variantContext).inflate(R.layout.fragment_home_placeholder_owner, null, false)
            }
            assertNotNull(calendar!!.findViewById<View>(R.id.calendarScroll))
            assertNotNull(calendar!!.findViewById<View>(R.id.venueSpinner))
            assertNotNull(home!!.findViewById<View>(R.id.calendarButton))
            assertTrue(calendar!!.findViewById<View>(R.id.previousPeriod).minimumHeight >= dp(variantContext, 48))
            assertTrue(calendar!!.findViewById<View>(R.id.nextPeriod).minimumHeight >= dp(variantContext, 48))
        }
    }

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
}

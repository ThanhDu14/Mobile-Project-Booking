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
class OwnerDropInLayoutTest {
    @Test fun sessionFormListAndRosterInflateAcrossPhoneDarkAndWideLayouts() {
        val application = ApplicationProvider.getApplicationContext<Context>()
        val configurations = listOf(
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
        configurations.forEach { configuration ->
            val themed = ContextThemeWrapper(application.createConfigurationContext(configuration), R.style.Theme_Bookingapp)
            val list = LayoutInflater.from(themed).inflate(R.layout.fragment_owner_dropin, null, false)
            val form = LayoutInflater.from(themed).inflate(R.layout.fragment_owner_dropin_form, null, false)
            val roster = LayoutInflater.from(themed).inflate(R.layout.fragment_owner_dropin_roster, null, false)
            val vouchers = LayoutInflater.from(themed).inflate(R.layout.fragment_owner_vouchers, null, false)
            val voucherForm = LayoutInflater.from(themed).inflate(R.layout.fragment_owner_voucher_form, null, false)
            val statistics = LayoutInflater.from(themed).inflate(R.layout.fragment_owner_statistics, null, false)
            assertNotNull(list.findViewById<View>(R.id.sessionList))
            assertTrue(list.findViewById<View>(R.id.createSession).minimumHeight >= dp(themed, 48))
            assertNotNull(form.findViewById<View>(R.id.levelSpinner))
            assertTrue(form.findViewById<View>(R.id.save).minimumHeight >= dp(themed, 48))
            assertTrue(roster.findViewById<View>(R.id.scanQr).minimumHeight >= dp(themed, 48))
            assertTrue(roster.findViewById<View>(R.id.checkIn).minimumHeight >= dp(themed, 48))
            assertTrue(vouchers.findViewById<View>(R.id.createVoucher).minimumHeight >= dp(themed, 48))
            assertTrue(voucherForm.findViewById<View>(R.id.save).minimumHeight >= dp(themed, 48))
            assertTrue(statistics.findViewById<View>(R.id.refresh).minimumHeight >= dp(themed, 48))
            assertNotNull(statistics.findViewById<View>(R.id.peakChart))
        }
    }

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
}

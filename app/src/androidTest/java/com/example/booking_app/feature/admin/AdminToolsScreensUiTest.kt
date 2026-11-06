package com.example.booking_app.feature.admin

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.booking_app.R
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Device UI smoke tests for Feature 120 US4 Admin Tools, Dashboard, Vouchers and Broadcast screens. */
@RunWith(AndroidJUnit4::class)
class AdminToolsScreensUiTest {

    @Test
    fun adminToolsScreensInflateWithPrimaryActionsInLightDarkPhoneAndTabletModes() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val configurations = listOf(
            Configuration(target.resources.configuration).apply {
                smallestScreenWidthDp = 360
                screenWidthDp = 360
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
            },
            Configuration(target.resources.configuration).apply {
                smallestScreenWidthDp = 360
                screenWidthDp = 360
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            },
            Configuration(target.resources.configuration).apply {
                smallestScreenWidthDp = 600
                screenWidthDp = 600
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
            },
            Configuration(target.resources.configuration).apply {
                smallestScreenWidthDp = 600
                screenWidthDp = 600
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            },
        )

        val screens = listOf(
            R.layout.fragment_admin_home to listOf(
                R.id.cardPendingApplications,
                R.id.cardOpenReports,
                R.id.textStatNewUsers,
                R.id.textStatBookings,
                R.id.textStatActiveVenues,
                R.id.textStatDropins,
            ),
            R.layout.fragment_admin_tools_dashboard to listOf(
                R.id.cardNavVouchers,
                R.id.cardNavBroadcast,
                R.id.chipGroupCategoryTabs,
                R.id.inputAddCategory,
                R.id.buttonAddCategory,
                R.id.recyclerCategories,
            ),
            R.layout.fragment_admin_system_vouchers to listOf(
                R.id.toolbar,
                R.id.progressVouchers,
                R.id.textVouchersEmpty,
                R.id.recyclerVouchers,
                R.id.buttonCreateVoucher,
            ),
            R.layout.item_admin_system_voucher to listOf(
                R.id.textVoucherCode,
                R.id.textVoucherDetail,
                R.id.textVoucherValidity,
                R.id.textVoucherUsage,
                R.id.switchVoucherActive,
            ),
            R.layout.fragment_admin_broadcast to listOf(
                R.id.toolbar,
                R.id.layoutBroadcastTitle,
                R.id.inputBroadcastTitle,
                R.id.layoutBroadcastBody,
                R.id.inputBroadcastBody,
                R.id.buttonSendBroadcast,
                R.id.progressBroadcastSending,
            ),
        )

        configurations.forEach { configuration ->
            val configuredContext: Context = target.createConfigurationContext(configuration)
            val themedContext = ContextThemeWrapper(configuredContext, R.style.Theme_Bookingapp)
            screens.forEach { (layoutId, actionIds) ->
                var root: View? = null
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    root = LayoutInflater.from(themedContext).inflate(layoutId, null, false)
                }
                assertNotNull("Layout $layoutId must inflate for $configuration", root)
                actionIds.forEach { id ->
                    val action = root!!.findViewById<View>(id)
                    assertNotNull("View $id missing in layout $layoutId", action)
                }
            }
        }
    }
}

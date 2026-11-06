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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Layout-level device smoke tests for the approved Feature 120 Admin screen references. */
@RunWith(AndroidJUnit4::class)
class AdminApplicationScreensUiTest {

    @Test
    fun sampleScreensInflateWithTheirPrimaryActionsInLightAndDarkPhoneAndTabletModes() {
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
            R.layout.fragment_admin_application_queue to listOf(
                R.id.progressBar,
                R.id.recyclerApplications,
                R.id.layoutEmpty,
                R.id.layoutError,
                R.id.buttonRetry,
            ),
            R.layout.fragment_admin_application_detail to listOf(
                R.id.cardVenueInfo,
                R.id.cardApplicantInfo,
                R.id.containerDocuments,
                R.id.buttonApprove,
                R.id.buttonReject,
                R.id.detailVenueName,
            ),
            R.layout.dialog_admin_reject_application to listOf(
                R.id.inputRejectReason,
                R.id.buttonCloseDialog,
                R.id.buttonConfirmReject,
            ),
            R.layout.item_admin_application to listOf(
                R.id.textVenueName,
                R.id.textApplicantAndDistrict,
                R.id.chipStatus,
                R.id.textSubmittedAt,
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

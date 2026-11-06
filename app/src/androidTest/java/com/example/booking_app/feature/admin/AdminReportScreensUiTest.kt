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

/** Layout-level device smoke tests for the approved Feature 120 Admin Report screens. */
@RunWith(AndroidJUnit4::class)
class AdminReportScreensUiTest {

    @Test
    fun sampleReportScreensInflateWithTheirPrimaryActionsInLightAndDarkPhoneAndTabletModes() {
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
            R.layout.fragment_admin_report_queue to listOf(
                R.id.progressBar,
                R.id.recyclerReports,
                R.id.layoutEmpty,
                R.id.layoutError,
                R.id.buttonRetry,
            ),
            R.layout.fragment_admin_report_detail to listOf(
                R.id.cardReportOverview,
                R.id.cardTargetContent,
                R.id.chipReportStatus,
                R.id.chipReportTargetType,
                R.id.textReportReason,
                R.id.radioGroupConclusion,
                R.id.buttonSubmitConclusion,
            ),
            R.layout.item_admin_report to listOf(
                R.id.cardReport,
                R.id.textTargetTypeHeader,
                R.id.textReason,
                R.id.textDetail,
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

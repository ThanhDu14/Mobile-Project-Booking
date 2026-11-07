package com.example.booking_app.feature.owner

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

/** Layout-level device smoke tests for the three approved Feature 110 screen references. */
@RunWith(AndroidJUnit4::class)
class OwnerApplicationScreensUiTest {
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
            R.layout.fragment_owner_application_form to listOf(R.id.reviewButton, R.id.addPhoto, R.id.addDocument, R.id.changePin),
            R.layout.fragment_owner_application_review to listOf(R.id.submitButton, R.id.editVenue, R.id.editRepresentative, R.id.editFiles),
            R.layout.fragment_owner_application_status to listOf(R.id.statusAction, R.id.statusDocuments, R.id.timeline),
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
                    assertTrue("View $id must remain visible in its initial layout", action.visibility == View.VISIBLE || id == R.id.statusDocuments)
                }
            }
        }
    }
}

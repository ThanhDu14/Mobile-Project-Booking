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

/** Device UI smoke tests for Feature 120 US3 User and Venue Management screens. */
@RunWith(AndroidJUnit4::class)
class AdminUserVenueScreensUiTest {

    @Test
    fun userAndVenueScreensInflateWithPrimaryActionsInLightDarkPhoneAndTabletModes() {
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
            R.layout.fragment_admin_user_management to listOf(
                R.id.toolbar,
                R.id.layoutUserSearch,
                R.id.inputUserSearch,
                R.id.buttonSearchUser,
                R.id.progressUserLoading,
                R.id.textUserNotFound,
                R.id.textUserError,
                R.id.cardUserResult,
                R.id.textUserFullName,
                R.id.textUserPhone,
                R.id.textUserUid,
                R.id.chipUserRole,
                R.id.chipOwnerStatus,
                R.id.chipAccountStatus,
                R.id.buttonToggleLock,
                R.id.buttonRevokeOwner,
            ),
            R.layout.dialog_admin_revoke_owner to listOf(
                R.id.textRevokeDialogTitle,
                R.id.textRevokeDialogMessage,
                R.id.layoutRevokeReason,
                R.id.inputRevokeReason,
                R.id.buttonCancelRevoke,
                R.id.buttonConfirmRevoke,
            ),
            R.layout.fragment_admin_venue_management to listOf(
                R.id.toolbar,
                R.id.layoutVenueSearch,
                R.id.inputVenueSearch,
                R.id.buttonSearchVenue,
                R.id.progressVenueLoading,
                R.id.textVenueNotFound,
                R.id.textVenueError,
                R.id.cardVenueResult,
                R.id.textVenueName,
                R.id.textVenueAddress,
                R.id.textVenueOwnerId,
                R.id.chipVenueStatus,
                R.id.buttonLockVenue,
                R.id.layoutUnlockOptions,
                R.id.buttonUnlockActive,
                R.id.buttonUnlockHidden,
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

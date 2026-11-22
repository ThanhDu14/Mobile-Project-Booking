package com.example.booking_app.feature.notification.ui

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentNotificationSettingsBinding
import com.example.booking_app.databinding.ItemNotificationPreferenceBinding
import com.example.booking_app.feature.notification.domain.*
import com.example.booking_app.feature.notification.domain.NotificationType.*
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

data class PreferenceGroup(val section: Int, val title: Int, val subtitle: Int?, val types: Set<NotificationType>)

val notificationGroups = listOf(
    PreferenceGroup(R.string.notification_booking_section, R.string.notification_booking_result, R.string.notification_booking_result_hint, setOf(BOOKING_CONFIRMED, BOOKING_REJECTED, BOOKING_CANCELLED)),
    PreferenceGroup(R.string.notification_booking_section, R.string.notification_reminder, R.string.notification_reminder_hint, setOf(BOOKING_REMINDER)),
    PreferenceGroup(R.string.notification_dropin_section, R.string.notification_dropin, R.string.notification_dropin_hint, setOf(DROPIN_REGISTERED, DROPIN_SPOT_OPENED, DROPIN_CHANGED, DROPIN_WAITLIST_CLOSED)),
    PreferenceGroup(R.string.notification_promotion_section, R.string.notification_promotion, R.string.notification_promotion_hint, setOf(PROMOTION)),
    PreferenceGroup(R.string.notification_community_section, R.string.notification_review, null, setOf(REVIEW_REMINDER, REVIEW_REPLIED)),
    PreferenceGroup(R.string.notification_community_section, R.string.notification_group, R.string.notification_group_hint, setOf(GROUP_ACTIVITY)),
    PreferenceGroup(R.string.notification_community_section, R.string.notification_chat, R.string.notification_chat_hint, setOf(CHAT_MESSAGE)),
    PreferenceGroup(R.string.notification_owner_section, R.string.notification_booking_new, R.string.notification_booking_new_hint, setOf(BOOKING_NEW)),
    PreferenceGroup(R.string.notification_owner_section, R.string.notification_review_new, null, setOf(REVIEW_NEW)),
    PreferenceGroup(R.string.notification_owner_section, R.string.notification_owner_result, R.string.notification_owner_result_hint, setOf(OWNER_APPROVED, OWNER_REJECTED, OWNER_REVOKED)),
    PreferenceGroup(0, R.string.notification_system_pref, null, setOf(SYSTEM)),
)

@AndroidEntryPoint
class NotificationSettingsFragment : Fragment(R.layout.fragment_notification_settings) {
    private val model: NotificationSettingsViewModel by viewModels()
    private var permissionDenied = false
    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        updatePermissionStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionDenied = savedInstanceState?.getBoolean("notification.permissionDenied") ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("notification.permissionDenied", permissionDenied)
    }

    private fun permissionAction(): NotificationPermissionAction {
        if (permissionDenied || Build.VERSION.SDK_INT < 33) return NotificationPermissionAction.SETTINGS
        return notificationPermissionAction(Build.VERSION.SDK_INT,
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }

    private fun updatePermissionStatus() {
        view?.let {
            val b = FragmentNotificationSettingsBinding.bind(it)
            val denied = !NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
            b.permission.isVisible = denied
            b.osSettings.isVisible = denied
            b.osSettings.setText(if (permissionAction() == NotificationPermissionAction.REQUEST)
                R.string.notification_permission_enable else R.string.notification_permission_settings)
        }
    }

    private fun openSystemSettings() {
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName))
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentNotificationSettingsBinding.bind(view)
        androidx.appcompat.content.res.AppCompatResources.getDrawable(requireContext(), R.drawable.ic_info_24)?.let { icon ->
            androidx.core.graphics.drawable.DrawableCompat.setTint(icon, requireContext().getColor(R.color.sn_ink_muted))
            val size = resources.getDimensionPixelSize(R.dimen.space_4)
            icon.setBounds(0, 0, size, size)
            b.note.setCompoundDrawablesRelative(icon, null, null, null)
            b.note.compoundDrawablePadding = resources.getDimensionPixelSize(R.dimen.space_3)
        }
        b.toolbar.setNavigationOnClickListener { androidx.navigation.fragment.NavHostFragment.findNavController(this).navigateUp() }
        b.retry.setOnClickListener { model.retry() }
        b.osSettings.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33 && permissionAction() == NotificationPermissionAction.REQUEST) {
                requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else openSystemSettings()
        }
        val rows = mutableListOf<Pair<PreferenceGroup, ItemNotificationPreferenceBinding>>()
        for ((section, groups) in notificationGroups.groupBy { it.section }) {
            if (section != 0) {
            val heading = TextView(requireContext()).apply {
                setText(section); setTextAppearance(R.style.TextAppearance_SmashNow_Label)
                setTextColor(requireContext().getColor(R.color.sn_ink_muted))
                val gap = resources.getDimensionPixelSize(R.dimen.space_4)
                setPadding(0, gap, 0, resources.getDimensionPixelSize(R.dimen.space_2))
                androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
            }
            b.groups.addView(heading)
            }
            val card = MaterialCardView(requireContext()).apply {
                if (section == 0) layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = resources.getDimensionPixelSize(R.dimen.space_4) }
                radius = resources.getDimension(R.dimen.radius_md)
                strokeWidth = resources.getDimensionPixelSize(R.dimen.notification_stroke)
                strokeColor = requireContext().getColor(R.color.sn_line)
                setCardBackgroundColor(requireContext().getColor(R.color.sn_surface_raised))
                cardElevation = 0f
            }
            val column = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            card.addView(column); b.groups.addView(card)
            groups.forEachIndexed { index, group ->
                if (index > 0) column.addView(View(requireContext()).apply { setBackgroundResource(R.color.sn_line) }, LinearLayout.LayoutParams(-1, resources.getDimensionPixelSize(R.dimen.notification_stroke)))
                val row = ItemNotificationPreferenceBinding.inflate(LayoutInflater.from(requireContext()), column, true)
                row.title.setText(group.title)
                row.subtitle.isVisible = group.subtitle != null
                group.subtitle?.let(row.subtitle::setText)
                row.check.contentDescription = getString(group.title)
                row.check.setOnClickListener { model.toggle(group.types) }
                row.root.setOnClickListener { model.toggle(group.types) }
                rows += group to row
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    b.progress.isVisible = state.loading || state.busy
                    b.error.isVisible = state.error
                    b.retry.isVisible = state.error
                    b.scroll.isVisible = state.values.isNotEmpty()
                    updatePermissionStatus()
                    rows.forEach { (group, row) ->
                        val locked = BOOKING_NEW in group.types
                        row.check.isEnabled = !locked && !state.busy
                        row.root.isEnabled = !locked && !state.busy
                        if (state.values.isNotEmpty()) row.check.checkedState = when (NotificationPreferences.groupState(group.types, state.values)) {
                            GroupState.ON -> MaterialCheckBox.STATE_CHECKED
                            GroupState.OFF -> MaterialCheckBox.STATE_UNCHECKED
                            GroupState.MIXED -> MaterialCheckBox.STATE_INDETERMINATE
                        }
                    }
                }
            }
        }
    }
}

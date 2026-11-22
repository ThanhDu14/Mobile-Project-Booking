package com.example.booking_app.feature.notification.ui

import android.os.Bundle
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.*
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentNotificationBinding
import com.example.booking_app.databinding.ItemNotificationBinding
import com.example.booking_app.feature.notification.domain.Notification
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class NotificationFragment : Fragment(R.layout.fragment_notification) {
    private val model: NotificationViewModel by viewModels()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentNotificationBinding.bind(view)
        val adapter = NotificationAdapter(model::tap)
        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.adapter = adapter
        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        b.toolbar.menu.add(R.string.notification_settings_title).apply {
            setIcon(R.drawable.ic_notification_settings_24)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        b.toolbar.setOnMenuItemClickListener {
            findNavController().navigate(android.net.Uri.parse("smashnow://notifications/settings"))
            true
        }
        b.markAll.setOnClickListener { model.markAll() }
        b.more.setOnClickListener { model.more() }
        b.retry.setOnClickListener { model.retry() }
        b.refresh.setOnClickListener { model.refresh() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.state.collect { s ->
                    adapter.submitList(s.items)
                    b.progress.isVisible = s.loading || s.busy
                    b.message.isVisible = s.error || (!s.loading && s.items.isEmpty())
                    b.message.setText(if (s.error) R.string.notification_error else R.string.notification_empty)
                    b.retry.isVisible = s.error
                    b.more.isVisible = s.hasMore
                    b.more.isEnabled = !s.busy
                    b.refresh.isEnabled = !s.busy
                    b.markAll.isEnabled = !s.busy && s.items.any { !it.isRead }
                    b.unread.text = getString(R.string.notification_unread_count, s.items.count { !it.isRead })
                } }
                launch { model.open.collect { event ->
                    val item = event.item
                    if (event.route != null) {
                        try {
                            findNavController().navigate(android.net.Uri.parse(event.route))
                            return@collect
                        } catch (_: IllegalArgumentException) { /* Unavailable destination uses saved detail. */ }
                    }
                    MaterialAlertDialogBuilder(requireContext()).setTitle(item.title).setMessage(item.body)
                        .setPositiveButton(android.R.string.ok, null).show()
                } }
            }
        }
    }
}

class NotificationAdapter(private val tap: (Notification) -> Unit) :
    ListAdapter<Notification, NotificationAdapter.Holder>(object : DiffUtil.ItemCallback<Notification>() {
        override fun areItemsTheSame(a: Notification, b: Notification) = a.id == b.id
        override fun areContentsTheSame(a: Notification, b: Notification) = a == b
    }) {
    class Holder(val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            title.text = item.title
            body.text = item.body
            val now = System.currentTimeMillis()
            time.text = if (now >= item.createdAtMillis && now - item.createdAtMillis < android.text.format.DateUtils.DAY_IN_MILLIS) {
                android.text.format.DateUtils.getRelativeTimeSpanString(item.createdAtMillis, now, android.text.format.DateUtils.MINUTE_IN_MILLIS)
            } else DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(Instant.ofEpochMilli(item.createdAtMillis))
            root.setBackgroundResource(if (item.isRead) R.color.sn_surface_raised else R.color.sn_court_container)
            unreadDot.isVisible = !item.isRead
            icon.setImageResource(when {
                item.type.startsWith("REVIEW") -> R.drawable.ic_star_16
                item.type == "GROUP_ACTIVITY" -> R.drawable.ic_groups_24
                item.type == "BOOKING_CONFIRMED" -> R.drawable.ic_notification_confirm
                item.type == "BOOKING_REJECTED" || item.type == "BOOKING_CANCELLED" -> R.drawable.ic_notification_rejected
                item.type == "DROPIN_SPOT_OPENED" -> R.drawable.ic_notification_spot
                item.type == "BOOKING_REMINDER" -> R.drawable.ic_schedule_24
                item.type == "SYSTEM" -> R.drawable.ic_notification_system
                else -> R.drawable.ic_notifications_none_24
            })
            val iconColor = when {
                item.type.startsWith("REVIEW") -> R.color.sn_lamp
                item.type == "BOOKING_CONFIRMED" || item.type == "DROPIN_SPOT_OPENED" -> R.color.sn_court
                item.type == "BOOKING_REJECTED" || item.type == "BOOKING_CANCELLED" -> R.color.sn_danger
                else -> R.color.sn_ink
            }
            icon.imageTintList = android.content.res.ColorStateList.valueOf(root.context.getColor(iconColor))
            row.contentDescription = listOf(item.title, item.body, time.text, root.context.getString(if (item.isRead) R.string.notification_read else R.string.notification_unread)).joinToString(". ")
            row.setOnClickListener { tap(item) }
        }
    }
}

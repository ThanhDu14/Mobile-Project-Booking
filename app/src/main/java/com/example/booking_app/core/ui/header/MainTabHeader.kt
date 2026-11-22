package com.example.booking_app.core.ui.header

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.core.session.AccountAvatarSource
import com.example.booking_app.core.session.AccountEntryNavigator
import com.example.booking_app.databinding.ViewMainTabHeaderBinding
import kotlinx.coroutines.launch

/**
 * Gắn header dùng chung vào màn gốc của một tab (spec 011 US8, contracts/ui-screens.md §5). Tab chỉ đặt
 * `tabTitle`/`tabSubtitle` rồi gọi hàm này trong `onViewCreated`; avatar và việc bấm avatar do 011 lo.
 */
object MainTabHeader {

    fun bind(
        fragment: Fragment,
        header: ViewMainTabHeaderBinding,
        avatarSource: AccountAvatarSource,
        accountEntryNavigator: AccountEntryNavigator,
    ) {
        if (header.tabActions.findViewWithTag<android.view.View>("notification-entry") == null) {
            header.tabActions.addView(androidx.appcompat.widget.AppCompatImageButton(fragment.requireContext(), null, android.R.attr.borderlessButtonStyle).apply {
                tag = "notification-entry"
                setImageResource(com.example.booking_app.R.drawable.ic_notifications_none_24)
                contentDescription = context.getString(com.example.booking_app.R.string.notification_title)
                val size = resources.getDimensionPixelSize(com.example.booking_app.R.dimen.touch_min)
                layoutParams = android.widget.LinearLayout.LayoutParams(size, size)
                setOnClickListener { fragment.findNavController().navigate(android.net.Uri.parse("smashnow://notifications")) }
            })
        }
        header.accountAvatar.setOnClickListener { accountEntryNavigator.open(fragment.findNavController()) }
        val owner = fragment.viewLifecycleOwner
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                avatarSource.state.collect(header.accountAvatar::render)
            }
        }
    }
}

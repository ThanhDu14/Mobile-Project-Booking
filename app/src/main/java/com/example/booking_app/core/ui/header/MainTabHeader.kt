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
        header.accountAvatar.setOnClickListener { accountEntryNavigator.open(fragment.findNavController()) }
        val owner = fragment.viewLifecycleOwner
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                avatarSource.state.collect(header.accountAvatar::render)
            }
        }
    }
}

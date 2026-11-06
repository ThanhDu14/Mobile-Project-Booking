package com.example.booking_app.feature.auth.ui.home

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.booking_app.R
import com.example.booking_app.core.session.AccountAvatarSource
import com.example.booking_app.core.session.AccountEntryNavigator
import com.example.booking_app.core.ui.header.MainTabHeader
import com.example.booking_app.databinding.FragmentHomePlaceholderOwnerBinding
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * Tab Hôm nay tạm của `nav_owner`. Avatar ở header dùng chung (spec 011 US8) mở trang Tài khoản, nơi chủ sân
 * chuyển sang chế độ người chơi. Spec 112 thay phần thân bằng màn Hôm nay thật.
 */
@AndroidEntryPoint
class OwnerHomePlaceholderFragment : Fragment(R.layout.fragment_home_placeholder_owner) {

    @Inject lateinit var avatarSource: AccountAvatarSource

    @Inject lateinit var accountEntryNavigator: AccountEntryNavigator

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentHomePlaceholderOwnerBinding.bind(view)
        binding.header.tabSubtitle.isVisible = true
        binding.header.tabSubtitle.text = LocalDate.now().format(DATE_FORMAT).replaceFirstChar { it.uppercase() }
        binding.header.tabTitle.setText(R.string.nav_owner_tab_today)
        MainTabHeader.bind(this, binding.header, avatarSource, accountEntryNavigator)
    }

    private companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, dd/MM", Locale.forLanguageTag("vi"))
    }
}

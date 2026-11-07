package com.example.booking_app.feature.auth.ui.home

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.core.session.AccountAvatarSource
import com.example.booking_app.core.session.AccountEntryNavigator
import com.example.booking_app.core.ui.header.MainTabHeader
import com.example.booking_app.databinding.FragmentHomePlaceholderOwnerBinding
import com.example.booking_app.feature.owner.ui.home.OwnerHomeDashboardViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * Tab Hôm nay của `nav_owner` (spec 112). Avatar ở header dùng chung (spec 011 US8) mở trang Tài khoản, nơi chủ
 * sân chuyển sang chế độ người chơi.
 */
@AndroidEntryPoint
class OwnerHomePlaceholderFragment : Fragment(R.layout.fragment_home_placeholder_owner) {

    @Inject lateinit var avatarSource: AccountAvatarSource

    @Inject lateinit var accountEntryNavigator: AccountEntryNavigator

    private val viewModel: OwnerHomeDashboardViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentHomePlaceholderOwnerBinding.bind(view)
        binding.header.tabSubtitle.isVisible = true
        binding.header.tabSubtitle.text = LocalDate.now().format(DATE_FORMAT).replaceFirstChar { it.uppercase() }
        binding.header.tabTitle.setText(R.string.owner_home_title)
        MainTabHeader.bind(this, binding.header, avatarSource, accountEntryNavigator)
        binding.retry.setOnClickListener { viewModel.refresh() }
        binding.pendingBannerCard.setOnClickListener { findNavController().navigate(R.id.ownerBookingsFragment) }
        binding.actionScanQrCard.setOnClickListener { findNavController().navigate(R.id.ownerBookingCheckInFragment) }
        binding.actionLockHoursCard.setOnClickListener { findNavController().navigate(R.id.ownerCalendarFragment) }
        binding.calendarButton.setOnClickListener { findNavController().navigate(R.id.ownerCalendarFragment) }
        binding.dropInButton.setOnClickListener { findNavController().navigate(R.id.ownerDropInFragment) }
        binding.vouchersButton.setOnClickListener { findNavController().navigate(R.id.ownerVouchersFragment) }
        binding.statisticsButton.setOnClickListener { findNavController().navigate(R.id.ownerStatisticsFragment) }
        binding.venuesButton.setOnClickListener { findNavController().navigate(R.id.ownerVenueListFragment) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        binding.loading.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                        binding.errorText.visibility = if (state.error != null) View.VISIBLE else View.GONE
                        binding.errorText.text = state.error
                        binding.retry.visibility = if (state.error != null) View.VISIBLE else View.GONE
                        binding.emptyText.visibility = if (!state.isLoading && state.error == null && state.isEmpty) View.VISIBLE else View.GONE
                        binding.header.tabTitle.text = state.venues.firstOrNull()?.name
                            ?: getString(R.string.owner_home_title)
                    }
                }
            }
        }
    }

    private companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, dd/MM", Locale.forLanguageTag("vi"))
    }
}

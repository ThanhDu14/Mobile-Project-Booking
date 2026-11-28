package com.example.booking_app.feature.owner.ui

import androidx.core.view.isVisible
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.core.net.toUri
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentOwnerVenueListBinding
import com.example.booking_app.feature.owner.domain.model.OwnerVenue
import com.example.booking_app.feature.owner.domain.OwnerErrorKind
import com.example.booking_app.core.media.PhotoUrlResolver
import com.example.booking_app.core.session.AccountAvatarSource
import com.example.booking_app.core.session.AccountEntryNavigator
import com.example.booking_app.core.ui.header.MainTabHeader
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OwnerVenueListFragment : Fragment(R.layout.fragment_owner_venue_list) {
    private var _binding: FragmentOwnerVenueListBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: OwnerVenueListViewModel by viewModels()

    @Inject lateinit var avatarSource: AccountAvatarSource

    @Inject lateinit var accountEntryNavigator: AccountEntryNavigator

    @Inject lateinit var photoUrlResolver: PhotoUrlResolver
    private val adapter = OwnerVenueAdapter(
        onOpen = { venue -> findNavController().navigate(OwnerVenueListFragmentDirections.actionOwnerVenueListToDetail(venue.id)) },
        onHide = { viewModel.hide(it.id) },
        onToggleRecurring = { viewModel.toggleRecurring(it) },
        onNavigate = { venue, section ->
            val destination = when(section) {
                "prices" -> R.id.ownerPriceRuleFragment
                "slots" -> R.id.ownerSlotBlockFragment
                "courts" -> R.id.ownerVenueDetailFragment
                "dropin" -> R.id.ownerDropInFragment
                "vouchers" -> R.id.ownerVouchersFragment
                else -> R.id.ownerStatisticsFragment
            }
            findNavController().navigate(destination,Bundle().apply { putString("venueId",venue.id); putString("section",section); putString("courtId","") })
        },
        photoUrl = { venue -> photoUrlResolver.venuePhoto(venue.photoPaths.firstOrNull { it.isNotBlank() }) },
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentOwnerVenueListBinding.bind(view)
        binding.venues.adapter = adapter
        binding.retry.setOnClickListener { viewModel.load() }
        binding.header.tabTitle.setText(R.string.nav_owner_tab_venues)
        MainTabHeader.bind(this, binding.header, avatarSource, accountEntryNavigator)
        addVenueButton()
        binding.venueApplications.setOnClickListener { findNavController().navigate("smashnow://owner/venue-applications".toUri()) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    override fun onResume() { super.onResume(); viewModel.load() }

    /** "Thêm cơ sở" ở khu vực hành động của header dùng chung, như "Tạo nhóm" ở tab Cộng đồng. */
    private fun addVenueButton() {
        val button = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialIconButtonFilledTonalStyle)
        button.setText(R.string.owner_venue_add)
        button.setIconResource(R.drawable.ic_add_24)
        button.minimumHeight = resources.getDimensionPixelSize(R.dimen.touch_min)
        button.setOnClickListener { findNavController().navigate("smashnow://owner/apply?mode=addition".toUri()) }
        binding.header.tabActions.addView(button)
    }

    private fun render(state: OwnerVenueListUiState) {
        binding.progress.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        binding.retry.visibility = if (state.error != null) View.VISIBLE else View.GONE
        binding.formMessage.text = when (state.errorKind) {
            OwnerErrorKind.PERMISSION -> getString(R.string.owner_permission_error)
            OwnerErrorKind.NETWORK -> getString(R.string.owner_network_error)
            else -> state.error ?: state.message.orEmpty()
        }
        binding.formMessage.isVisible = binding.formMessage.text.isNotEmpty()
        binding.freshness.text = if (!state.isFresh && state.error != null) getString(R.string.owner_data_not_fresh) else ""
        binding.freshness.isVisible = binding.freshness.text.isNotEmpty()
        binding.empty.visibility = if (state.isEmpty) View.VISIBLE else View.GONE
        adapter.submitList(state.venues)
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}

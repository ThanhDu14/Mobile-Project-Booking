package com.example.booking_app.feature.booking_management.ui

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.booking_app.R
import com.example.booking_app.core.session.AuthNavigator
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.databinding.FragmentMyBookings060Binding
import com.example.booking_app.feature.booking_management.domain.BookingTab
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MyBookingsFragment : Fragment(R.layout.fragment_my_bookings_060) {
    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var authNavigator: AuthNavigator
    private val viewModel: MyBookingsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentMyBookings060Binding.bind(view)
        if (sessionManager.sessionState.value is SessionState.SignedOut) {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.my_bookings_060_login_required)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.my_bookings_060_login) { _, _ ->
                    authNavigator.openLogin(findNavController(), Uri.parse("smashnow://bookings/mine"))
                }.show()
            return
        }
        val adapter = MyBookingAdapter { booking ->
            findNavController().navigate(
                R.id.action_myBookings_to_detail,
                bundleOf("bookingId" to booking.bookingId)
            )
        }
        binding.listBookings.layoutManager = LinearLayoutManager(requireContext())
        binding.listBookings.adapter = adapter
        binding.bookingFilterGroup.addOnButtonCheckedListener { _, checkedId, checked ->
            if (checked) viewModel.selectTab(when (checkedId) {
                R.id.filterPending -> BookingTab.PENDING
                R.id.filterUpcoming -> BookingTab.UPCOMING
                R.id.filterCompleted -> BookingTab.COMPLETED
                R.id.filterCancelled -> BookingTab.CANCELLED
                else -> BookingTab.HOLD
            })
        }
        if (binding.bookingFilterGroup.checkedButtonId == View.NO_ID) {
            binding.bookingFilterGroup.check(R.id.filterPayment)
        } else {
            viewModel.selectTab(when (binding.bookingFilterGroup.checkedButtonId) {
                R.id.filterPending -> BookingTab.PENDING
                R.id.filterUpcoming -> BookingTab.UPCOMING
                R.id.filterCompleted -> BookingTab.COMPLETED
                R.id.filterCancelled -> BookingTab.CANCELLED
                else -> BookingTab.HOLD
            })
        }
        binding.buttonRetry.setOnClickListener { viewModel.loadFirstPage() }
        binding.listBookings.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                val manager = recyclerView.layoutManager as LinearLayoutManager
                if (dy > 0 && manager.findLastVisibleItemPosition() >= adapter.itemCount - 3) viewModel.loadNextPage()
            }
        })
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.progressBookings.isVisible = state is MyBookingsState.Loading
                    binding.listBookings.isVisible = state is MyBookingsState.Content
                    binding.textBookingsEmpty.isVisible = state is MyBookingsState.Empty
                    binding.buttonExplore.isVisible = state is MyBookingsState.Empty
                    binding.textBookingsError.isVisible = state is MyBookingsState.Error
                    binding.buttonRetry.isVisible = state is MyBookingsState.Error
                    when (state) {
                        is MyBookingsState.Content -> adapter.submitList(state.bookings)
                        MyBookingsState.Empty -> binding.textBookingsEmpty.setText(R.string.my_bookings_060_empty)
                        is MyBookingsState.Error -> binding.textBookingsError.setText(
                            if (state.unsupported) R.string.my_bookings_060_unsupported else R.string.my_bookings_060_load_error
                        )
                        MyBookingsState.Loading -> Unit
                    }
                }
            }
        }
    }
}

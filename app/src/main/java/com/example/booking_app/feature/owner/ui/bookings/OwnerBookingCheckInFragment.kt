package com.example.booking_app.feature.owner.ui.bookings

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentOwnerBookingCheckinBinding
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OwnerBookingCheckInFragment : Fragment(R.layout.fragment_owner_booking_checkin) {
    private val viewModel: BookingCheckInViewModel by viewModels()
    private var _binding: FragmentOwnerBookingCheckinBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var scanner: GmsBarcodeScanner? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentOwnerBookingCheckinBinding.bind(view)
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        scanner = GmsBarcodeScanning.getClient(requireActivity(), options)
        binding.scanQr.setOnClickListener {
            scanner?.startScan()
                ?.addOnSuccessListener { barcode -> barcode.rawValue?.let { binding.bookingCode.setText(it) } }
                ?.addOnFailureListener { failure ->
                    _binding?.let { current ->
                        current.errorText.text = failure.localizedMessage ?: getString(R.string.owner_booking_checkin_scan_error)
                        current.errorText.isVisible = true
                    }
                }
        }
        binding.submitCheckIn.setOnClickListener {
            viewModel.submit(binding.bookingCode.text?.toString().orEmpty(), binding.paymentCollected.isChecked)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.loading.isVisible = state.isSubmitting
                    binding.submitCheckIn.isEnabled = !state.isSubmitting
                    binding.errorText.isVisible = state.error != null
                    binding.errorText.text = state.error
                    binding.successCard.isVisible = state.result != null
                    binding.successText.isVisible = state.result != null
                    state.result?.let { bookingId ->
                        binding.successText.text = getString(R.string.owner_booking_checkin_success, bookingId)
                        val isPaid = state.checkInResult?.paymentStatus == "PAID"
                        if (isPaid) {
                            binding.remainingAmountText.text = "0 đ"
                            binding.confirmPaymentButton.isEnabled = false
                        } else {
                            binding.confirmPaymentButton.isEnabled = true
                            binding.confirmPaymentButton.setOnClickListener {
                                binding.paymentCollected.isChecked = true
                                viewModel.submit(binding.bookingCode.text?.toString().orEmpty(), paymentCollected = true)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        scanner = null
        _binding = null
        super.onDestroyView()
    }
}

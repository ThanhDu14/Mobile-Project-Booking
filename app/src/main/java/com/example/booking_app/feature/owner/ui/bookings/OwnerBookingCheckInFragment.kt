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
import com.example.booking_app.core.ui.keepFormAboveKeyboard
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
        view.keepFormAboveKeyboard()
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
            viewModel.submit(binding.bookingCode.text?.toString().orEmpty(), paymentCollected = false)
        }
        binding.paymentCollected.isVisible = false
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.loading.isVisible = state.isSubmitting
                    binding.submitCheckIn.isEnabled = !state.isSubmitting
                    binding.errorText.isVisible = state.error != null
                    binding.errorText.text = state.error
                    binding.successCard.isVisible = state.checkInResult != null
                    binding.successText.isVisible = state.checkInResult != null
                    state.checkInResult?.let { checked ->
                        binding.successTitle.setText(if (checked.paymentConfirmationRequired)
                            R.string.owner_checkin_payment_pending else R.string.owner_checkin_success_title)
                        binding.successText.text = if (checked.paymentConfirmationRequired)
                            getString(R.string.owner_checkin_match_found, checked.bookingId)
                            else getString(R.string.owner_booking_checkin_success, checked.bookingId)
                        binding.remainingAmountText.text = checked.remainingAmountVnd?.let {
                            getString(R.string.owner_booking_remaining, java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("vi")).format(it))
                        } ?: getString(R.string.owner_bookings_amount_unknown)
                        val time = java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                        binding.successDetails.text = listOfNotNull(
                            checked.courtNames.takeIf { it.isNotEmpty() }?.joinToString(", "),
                            if (checked.startAt != null && checked.endAt != null) "${time.format(checked.startAt)}–${time.format(checked.endAt)}" else null
                        ).joinToString(" · ")
                        binding.confirmPaymentButton.isVisible = checked.paymentConfirmationRequired
                        binding.confirmPaymentButton.isEnabled = checked.paymentConfirmationRequired && !state.isSubmitting
                        binding.confirmPaymentButton.setOnClickListener {
                            viewModel.submit(checked.bookingId, paymentCollected = true)
                        }
                        if (!checked.paymentConfirmationRequired) binding.bookingCode.text?.clear()
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

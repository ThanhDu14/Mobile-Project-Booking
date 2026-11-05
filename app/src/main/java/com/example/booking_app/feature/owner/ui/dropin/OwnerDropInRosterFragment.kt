package com.example.booking_app.feature.owner.ui.dropin

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentOwnerDropinRosterBinding
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OwnerDropInRosterFragment : Fragment(R.layout.fragment_owner_dropin_roster) {
    private val viewModel: OwnerDropInViewModel by viewModels()
    private var _binding: FragmentOwnerDropinRosterBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var scanner: GmsBarcodeScanner? = null
    private val venueId get() = requireArguments().getString(ARG_VENUE_ID).orEmpty()
    private val sessionId get() = requireArguments().getString(ARG_SESSION_ID).orEmpty()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentOwnerDropinRosterBinding.bind(view)
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        scanner = GmsBarcodeScanning.getClient(requireActivity(), options)
        binding.scanQr.setOnClickListener {
            scanner?.startScan()
                ?.addOnSuccessListener { barcode -> barcode.rawValue?.let { binding.credential.setText(it) } }
                ?.addOnFailureListener { failure ->
                    _binding?.let { current -> current.errorText.text = failure.localizedMessage ?: getString(R.string.owner_dropin_scan_error); current.errorText.isVisible = true }
                }
        }
        binding.checkIn.setOnClickListener { viewModel.checkIn(venueId, sessionId, binding.credential.text?.toString().orEmpty()) }
        binding.retry.setOnClickListener { viewModel.loadRoster(venueId, sessionId) }
        viewModel.loadRoster(venueId, sessionId)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.loading.isVisible = state.isLoading || state.isSaving
                    binding.errorText.isVisible = state.error != null
                    binding.errorText.text = state.error?.let { error ->
                        if (error.contains("Hợp đồng", true)) getString(R.string.owner_dropin_contract_error) else error
                    }
                    binding.emptyText.isVisible = !state.isLoading && state.error == null && state.isEmpty
                    binding.rosterScroll.isVisible = !state.isLoading && state.error == null && state.roster.isNotEmpty()
                    if (binding.rosterScroll.isVisible) renderRoster(state.roster)
                }
            }
        }
    }

    override fun onDestroyView() {
        scanner = null
        _binding = null
        super.onDestroyView()
    }

    private fun renderRoster(entries: List<com.example.booking_app.feature.owner.domain.dropin.OwnerDropInRegistration>) {
        binding.rosterList.removeAllViews()
        entries.forEach { entry ->
            val label = TextView(requireContext()).apply {
                text = getString(R.string.owner_dropin_roster_entry, entry.displayName, entry.partySize, entry.status)
                textSize = 16f
                setTextColor(resources.getColor(R.color.sn_ink, requireContext().theme))
                minHeight = (64 * resources.displayMetrics.density).toInt()
                setPadding(16, 12, 16, 12)
            }
            binding.rosterList.addView(label, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private companion object {
        const val ARG_VENUE_ID = "venueId"
        const val ARG_SESSION_ID = "sessionId"
    }
}

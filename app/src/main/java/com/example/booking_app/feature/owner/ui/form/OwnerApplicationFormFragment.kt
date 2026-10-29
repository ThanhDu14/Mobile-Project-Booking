package com.example.booking_app.feature.owner.ui.form

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentOwnerApplicationFormBinding
import com.example.booking_app.feature.owner.data.OwnerDistrict
import com.example.booking_app.feature.owner.data.OwnerDistrictCatalogApi
import com.example.booking_app.feature.owner.domain.OwnerApplicationDraft
import com.example.booking_app.feature.owner.domain.OwnerApplicationField
import com.example.booking_app.feature.owner.domain.VenueDraft
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Form-only interaction. Upload and submit remain separate tasks with unresolved contracts. */
@AndroidEntryPoint
class OwnerApplicationFormFragment : Fragment(R.layout.fragment_owner_application_form) {
    private val viewModel: OwnerApplicationFormViewModel by activityViewModels()
    @Inject lateinit var districtCatalogApi: OwnerDistrictCatalogApi
    private var _binding: FragmentOwnerApplicationFormBinding? = null
    private val binding get() = requireNotNull(_binding)
    private var districts: List<OwnerDistrict> = emptyList()
    private var selectedDistrictId: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentOwnerApplicationFormBinding.bind(view)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        selectedDistrictId = viewModel.uiState.value.draft?.venue?.districtId?.takeIf(String::isNotBlank)
        binding.districtInput.setOnClickListener { showDistrictPicker() }
        loadDistricts()
        restoreDraft()
        bindDraftUpdates()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        OwnerApplicationFormEvent.NavigateToReview ->
                            findNavController().navigate(
                                R.id.action_ownerApplicationFormFragment_to_ownerApplicationReviewFragment,
                            )
                    }
                }
            }
        }
        binding.continueButton.setOnClickListener {
            updateDraft()
            if (districts.none { it.id == selectedDistrictId }) {
                binding.districtLayout.error = getString(R.string.owner_form_district_unavailable)
                return@setOnClickListener
            }
            val validation = viewModel.continueToReview() ?: return@setOnClickListener
            showValidation(validation.errors)
        }
    }

    private fun restoreDraft() {
        viewModel.uiState.value.rejectReason?.let { reason ->
            binding.formContextNote.text = getString(R.string.owner_form_rejection_reason, reason)
        }
        val draft = viewModel.uiState.value.draft ?: return
        binding.venueNameInput.setText(draft.venue.name)
        binding.addressInput.setText(draft.venue.address)
        selectedDistrictId = draft.venue.districtId.takeIf(String::isNotBlank)
        binding.districtInput.setText(districts.firstOrNull { it.id == selectedDistrictId }?.name.orEmpty())
        binding.venuePhoneInput.setText(draft.venue.phone)
        binding.latitudeInput.setText(draft.venue.latitude?.toString().orEmpty())
        binding.longitudeInput.setText(draft.venue.longitude?.toString().orEmpty())
        binding.courtCountInput.setText(draft.venue.courtCount?.toString().orEmpty())
        binding.openTimeInput.setText(draft.venue.openTime)
        binding.closeTimeInput.setText(draft.venue.closeTime)
        binding.representativeNameInput.setText(draft.representativeName)
        binding.representativePhoneInput.setText(draft.representativePhone)
    }

    private fun bindDraftUpdates() {
        val inputs = listOf(
            binding.venueNameInput,
            binding.addressInput,
            binding.venuePhoneInput,
            binding.latitudeInput,
            binding.longitudeInput,
            binding.courtCountInput,
            binding.openTimeInput,
            binding.closeTimeInput,
            binding.representativeNameInput,
            binding.representativePhoneInput,
        )
        inputs.forEach { input -> input.doAfterTextChanged { updateDraft() } }
    }

    private fun updateDraft() {
        val previous = viewModel.uiState.value.draft
        viewModel.onDraftChanged(
            OwnerApplicationDraft(
                venue = VenueDraft(
                    name = binding.venueNameInput.text?.toString().orEmpty(),
                    address = binding.addressInput.text?.toString().orEmpty(),
                    districtId = selectedDistrictId.orEmpty(),
                    latitude = binding.latitudeInput.text?.toString()?.toDoubleOrNull(),
                    longitude = binding.longitudeInput.text?.toString()?.toDoubleOrNull(),
                    phone = binding.venuePhoneInput.text?.toString().orEmpty(),
                    courtCount = binding.courtCountInput.text?.toString()?.toIntOrNull(),
                    openTime = binding.openTimeInput.text?.toString().orEmpty(),
                    closeTime = binding.closeTimeInput.text?.toString().orEmpty(),
                    photoPaths = previous?.venue?.photoPaths.orEmpty(),
                ),
                representativeName = binding.representativeNameInput.text?.toString().orEmpty(),
                representativePhone = binding.representativePhoneInput.text?.toString().orEmpty(),
                documentPaths = previous?.documentPaths.orEmpty(),
            ),
        )
    }

    private fun loadDistricts() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                districts = districtCatalogApi.fetch()
                val selected = districts.firstOrNull { it.id == selectedDistrictId }
                binding.districtInput.setAdapter(
                    ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, districts.map(OwnerDistrict::name)),
                )
                binding.districtInput.setText(selected?.name.orEmpty(), false)
                binding.districtLayout.error = if (selectedDistrictId != null && selected == null) {
                    getString(R.string.owner_form_district_unavailable)
                } else null
                binding.districtInput.setOnItemClickListener { _, _, position, _ ->
                    selectedDistrictId = districts.getOrNull(position)?.id
                    updateDraft()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                binding.districtLayout.error = getString(R.string.owner_form_district_load_error)
                Snackbar.make(binding.root, R.string.owner_form_district_unavailable, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.owner_status_retry) { loadDistricts() }
                    .show()
            }
        }
    }

    private fun showDistrictPicker() {
        if (districts.isEmpty()) {
            binding.districtLayout.error = getString(R.string.owner_form_district_unavailable)
            loadDistricts()
            return
        }
        val names = districts.map(OwnerDistrict::name).toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.owner_form_district_id)
            .setItems(names) { _, index ->
                districts.getOrNull(index)?.let { district ->
                    selectedDistrictId = district.id
                    binding.districtInput.setText(district.name, false)
                    binding.districtLayout.error = null
                    updateDraft()
                }
            }
            .show()
    }

    private fun showValidation(errors: Map<OwnerApplicationField, String>) {
        val layouts = mapOf(
            OwnerApplicationField.VENUE_NAME to binding.venueNameLayout,
            OwnerApplicationField.ADDRESS to binding.addressLayout,
            OwnerApplicationField.DISTRICT to binding.districtLayout,
            OwnerApplicationField.VENUE_PHONE to binding.venuePhoneLayout,
            OwnerApplicationField.LOCATION to binding.latitudeLayout,
            OwnerApplicationField.COURT_COUNT to binding.courtCountLayout,
            OwnerApplicationField.OPENING_TIME to binding.openTimeLayout,
            OwnerApplicationField.CLOSING_TIME to binding.closeTimeLayout,
            OwnerApplicationField.REPRESENTATIVE_NAME to binding.representativeNameLayout,
            OwnerApplicationField.REPRESENTATIVE_PHONE to binding.representativePhoneLayout,
        )
        layouts.forEach { (field, layout) -> layout.error = errors[field]?.let(::errorMessage) }
        val locationMessage = errors[OwnerApplicationField.LOCATION]?.let(::errorMessage)
        binding.longitudeLayout.error = locationMessage
        val attachmentError = errors[OwnerApplicationField.VENUE_PHOTOS] != null ||
            errors[OwnerApplicationField.DOCUMENTS] != null
        binding.attachmentPlaceholder.setText(
            if (attachmentError) R.string.owner_form_attachments_required else R.string.owner_form_attachments_pending,
        )
    }

    private fun errorMessage(code: String): CharSequence = getString(
        when (code) {
            "invalid_location" -> R.string.owner_form_invalid_location
            "positive_integer_required" -> R.string.owner_form_invalid_court_count
            "invalid_time" -> R.string.owner_form_invalid_time
            else -> R.string.owner_form_required
        },
    )

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

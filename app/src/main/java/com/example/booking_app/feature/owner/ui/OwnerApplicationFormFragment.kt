package com.example.booking_app.feature.owner.ui

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.net.toUri
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentOwnerApplicationFormBinding
import com.example.booking_app.feature.owner.domain.OwnerApplicationState
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OwnerApplicationFormFragment : Fragment(R.layout.fragment_owner_application_form) {
    private var _binding: FragmentOwnerApplicationFormBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: OwnerApplicationViewModel by activityViewModels()
    private var bindingFields = false
    private var redirected = false
    private val photos = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> handlePicked(uris, document = false) }
    private val documents = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> handlePicked(uris, document = true) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentOwnerApplicationFormBinding.bind(view)
        val mode = arguments?.getString("mode") ?: "new"
        viewModel.initialize(resubmit = mode == "resubmit", forceNew = mode == "new")
        wireTextFields()
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.changePin.setOnClickListener { showCoordinateDialog() }
        binding.addPhoto.setOnClickListener { photos.launch("image/*") }
        binding.addDocument.setOnClickListener { documents.launch("image/*") }
        binding.reviewButton.setOnClickListener {
            if (viewModel.validate()) findNavController().navigate(R.id.action_ownerApplicationForm_to_review)
            else binding.formScroll.smoothScrollTo(0, 0)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun wireTextFields() {
        binding.venueName.doAfterTextChanged { if (!bindingFields) viewModel.update("venueName", it?.toString().orEmpty()) }
        binding.address.doAfterTextChanged { if (!bindingFields) viewModel.update("address", it?.toString().orEmpty()) }
        binding.venuePhone.doAfterTextChanged { if (!bindingFields) viewModel.update("venuePhone", it?.toString().orEmpty()) }
        binding.courtCount.doAfterTextChanged { if (!bindingFields) viewModel.update("courtCount", it?.toString().orEmpty()) }
        binding.openTime.doAfterTextChanged { if (!bindingFields) viewModel.update("openTime", it?.toString().orEmpty()) }
        binding.closeTime.doAfterTextChanged { if (!bindingFields) viewModel.update("closeTime", it?.toString().orEmpty()) }
        binding.representativeName.doAfterTextChanged { if (!bindingFields) viewModel.update("representativeName", it?.toString().orEmpty()) }
        binding.representativePhone.doAfterTextChanged { if (!bindingFields) viewModel.update("representativePhone", it?.toString().orEmpty()) }
    }

    private fun render(state: OwnerApplicationUiState) {
        if (_binding == null) return
        binding.uploadProgress.isVisible = state.busy
        binding.reviewButton.isEnabled = !state.busy && !state.loading
        binding.addPhoto.isEnabled = !state.busy
        binding.addDocument.isEnabled = !state.busy
        binding.formMessage.isVisible = state.message != null
        binding.formMessage.text = state.message.orEmpty()
        val draft = state.draft
        bindingFields = true
        if (binding.venueName.text?.toString() != draft.venue.name) binding.venueName.setText(draft.venue.name)
        if (binding.address.text?.toString() != draft.venue.address) binding.address.setText(draft.venue.address)
        if (binding.venuePhone.text?.toString() != draft.venue.phone) binding.venuePhone.setText(draft.venue.phone)
        if (binding.courtCount.text?.toString() != draft.venue.courtCount?.toString().orEmpty()) binding.courtCount.setText(draft.venue.courtCount?.toString().orEmpty())
        if (binding.openTime.text?.toString() != draft.venue.openTime) binding.openTime.setText(draft.venue.openTime)
        if (binding.closeTime.text?.toString() != draft.venue.closeTime) binding.closeTime.setText(draft.venue.closeTime)
        if (binding.representativeName.text?.toString() != draft.representativeName) binding.representativeName.setText(draft.representativeName)
        if (binding.representativePhone.text?.toString() != draft.representativePhone) binding.representativePhone.setText(draft.representativePhone)
        binding.district.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, state.districts.map { it.name }))
        state.districts.firstOrNull { it.id == draft.venue.districtId }?.let { district ->
            if (binding.district.text?.toString() != district.name) binding.district.setText(district.name, false)
        }
        binding.district.setOnItemClickListener { _, _, position, _ -> state.districts.getOrNull(position)?.let { viewModel.update("districtId", it.id) } }
        binding.locationValue.text = draft.venue.location?.let { "${it.latitude}, ${it.longitude}" } ?: getString(R.string.owner_location_not_selected)
        renderAttachments(binding.photoList, draft.venue.photoPaths, document = false)
        renderAttachments(binding.documentList, draft.documentPaths, document = true)
        binding.venueNameLayout.error = state.errors.firstOrNull { it.field == "venueName" }?.message
        binding.addressLayout.error = state.errors.firstOrNull { it.field == "address" }?.message
        binding.districtLayout.error = state.errors.firstOrNull { it.field == "districtId" }?.message
        binding.venuePhoneLayout.error = state.errors.firstOrNull { it.field == "venuePhone" }?.message
        binding.courtCountLayout.error = state.errors.firstOrNull { it.field == "courtCount" }?.message
        binding.openTimeLayout.error = state.errors.firstOrNull { it.field == "openTime" }?.message
        binding.closeTimeLayout.error = state.errors.firstOrNull { it.field == "closeTime" }?.message
        binding.representativeNameLayout.error = state.errors.firstOrNull { it.field == "representativeName" }?.message
        binding.representativePhoneLayout.error = state.errors.firstOrNull { it.field == "representativePhone" }?.message
        bindingFields = false
        val ownerStatus = state.status?.ownerStatus
        if (!redirected && ownerStatus == OwnerApplicationState.PENDING) {
            redirected = true
            findNavController().navigate(R.id.action_ownerApplicationForm_to_status)
        } else if (!redirected && ownerStatus == OwnerApplicationState.APPROVED) {
            redirected = true
            findNavController().navigate("smashnow://owner/venues".toUri())
        }
    }

    private fun renderAttachments(container: LinearLayout, paths: List<String>, document: Boolean) {
        container.removeAllViews()
        if (paths.isEmpty()) {
            container.addView(TextView(requireContext()).apply {
                text = getString(if (document) R.string.owner_no_documents else R.string.owner_no_photos)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                minHeight = resources.getDimensionPixelSize(R.dimen.touch_target_min)
                gravity = android.view.Gravity.CENTER_VERTICAL
            })
            return
        }
        paths.forEach { path ->
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; minimumHeight = resources.getDimensionPixelSize(R.dimen.touch_target_min) }
            row.addView(TextView(requireContext()).apply {
                text = "${if (document) "▣" else "▤"}  ${path.substringAfterLast('/')}"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                contentDescription = path.substringAfterLast('/')
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(MaterialButton(requireContext()).apply {
                text = getString(R.string.owner_remove_file)
                minHeight = resources.getDimensionPixelSize(R.dimen.touch_target_min)
                setOnClickListener { viewModel.removeAttachment(path, document) }
            })
            container.addView(row)
        }
    }

    private fun handlePicked(uris: List<Uri>, document: Boolean) {
        if (uris.isEmpty()) return
        viewLifecycleOwner.lifecycleScope.launch {
            for (uri in uris) {
                val type = requireContext().contentResolver.getType(uri).orEmpty().lowercase()
                val name = displayName(uri)
                val bytes = runCatching { requireContext().contentResolver.openInputStream(uri)?.use(::readBounded) }.getOrNull()
                if (bytes == null || bytes.size > 2 * 1024 * 1024) {
                    Snackbar.make(binding.root, R.string.owner_file_invalid, Snackbar.LENGTH_LONG).show()
                    continue
                }
                viewModel.addAttachment(name, type, bytes, document).join()
            }
        }
    }

    private fun displayName(uri: Uri): String {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "document"
        val cursor: Cursor? = requireContext().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use { if (it.moveToFirst()) name = it.getString(0) ?: name }
        return name
    }

    private fun readBounded(input: java.io.InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > 2 * 1024 * 1024) return ByteArray(total)
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }

    private fun showCoordinateDialog() {
        val state = viewModel.uiState.value
        val group = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 8, 32, 0) }
        val lat = EditText(requireContext()).apply { hint = getString(R.string.owner_latitude); inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED; setText(state.draft.venue.location?.latitude?.toString().orEmpty()) }
        val lng = EditText(requireContext()).apply { hint = getString(R.string.owner_longitude); inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED; setText(state.draft.venue.location?.longitude?.toString().orEmpty()) }
        group.addView(lat); group.addView(lng)
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.owner_location_title).setView(group)
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.owner_save_location) { _, _ ->
                val latitude = lat.text.toString().toDoubleOrNull()
                val longitude = lng.text.toString().toDoubleOrNull()
                if (latitude == null || longitude == null || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
                    Snackbar.make(binding.root, R.string.owner_location_invalid, Snackbar.LENGTH_LONG).show()
                } else viewModel.updateLocation(latitude, longitude)
            }.show()
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}

package com.example.booking_app.feature.owner.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.core.ui.keepFormAboveKeyboard
import com.example.booking_app.databinding.FragmentOwnerVenueDetailBinding
import com.example.booking_app.feature.owner.domain.model.CourtFields
import com.example.booking_app.feature.owner.domain.model.VenueEditableFields
import com.example.booking_app.feature.owner.domain.OwnerErrorKind
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.android.material.chip.Chip
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.example.booking_app.core.ui.LocationPickerDialog
import com.example.booking_app.core.media.PhotoUrlResolver
import coil3.load
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class OwnerVenueDetailFragment : Fragment(R.layout.fragment_owner_venue_detail) {
    private var _binding: FragmentOwnerVenueDetailBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: OwnerVenueDetailViewModel by viewModels()
    private var editingCourtId: String? = null
    private var rendering = false
    private var renderedPhotos: List<String>? = null
    private var locationMap: OwnerLocationMapController? = null
    @Inject lateinit var photoUrls: PhotoUrlResolver
    private val photoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewLifecycleOwner.lifecycleScope.launch {
            val mime = requireContext().contentResolver.getType(uri).orEmpty()
            val bytes = withContext(Dispatchers.IO) {
                requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(16384)
                    while (output.size() <= 2*1024*1024) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer,0,count)
                    }
                    output.toByteArray()
                }
            }
            if (bytes != null) viewModel.uploadPhoto(mime,bytes)
        }
    }
    private val courtAdapter = OwnerCourtAdapter(
        onEdit = { court ->
            editingCourtId = court.id
            binding.courtName.setText(court.name)
            binding.courtSurface.setText(court.surfaceType)
        },
        onDeactivate = { viewModel.updateCourt(it.id, CourtFields(it.name,it.surfaceType,!it.isActive,it.sortOrder)) },
        onMove = { court, direction -> viewModel.moveCourt(court.id, direction) }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.keepFormAboveKeyboard()
        _binding = FragmentOwnerVenueDetailBinding.bind(view)
        editingCourtId = savedInstanceState?.getString("editingCourtId") ?: editingCourtId
        renderedPhotos = null
        LocationPickerDialog.bind(this) { point -> readFields()?.let { viewModel.updateDraft(it.copy(location=point)) } }
        locationMap = OwnerLocationMapController(binding.locationPreview,viewLifecycleOwner,::pickLocation)
        binding.courts.adapter = courtAdapter
        binding.courtSurface.setAdapter(ArrayAdapter(requireContext(),android.R.layout.simple_list_item_1,listOf("MAT","PU","WOOD")))
        binding.courtSurface.threshold = 0
        binding.courtSurface.setText("MAT",false)
        binding.courtSurface.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.owner_court_surface).setItems(arrayOf("MAT","PU","WOOD")) { _, index ->
                    binding.courtSurface.setText(listOf("MAT","PU","WOOD")[index],false)
                }.show()
        }
        val courtsOnly = arguments?.getString("section") == "courts"
        binding.venueFields.isVisible = !courtsOnly
        binding.screenTitle.setText(if (courtsOnly) R.string.owner_courts_title else R.string.owner_venue_info)
        binding.addPhoto.setOnClickListener { photoPicker.launch("image/*") }
        binding.location.setOnClickListener { pickLocation() }
        binding.district.setOnItemClickListener { _,_,position,_ ->
            viewModel.uiState.value.districts.getOrNull(position)?.let { district -> readFields()?.let { viewModel.updateDraft(it.copy(districtId=district.id)) } }
        }
        listOf(binding.name,binding.address,binding.phone).forEach { field -> field.doAfterTextChanged {
            if (!rendering) readFields()?.let(viewModel::updateDraft)
        } }
        binding.recurring.setOnCheckedChangeListener { _,_ -> if (!rendering) readFields()?.let(viewModel::updateDraft) }
        binding.visibility.setOnCheckedChangeListener { _,_ -> if (!rendering) readFields()?.let(viewModel::updateDraft) }
        listOf(binding.openTime,binding.closeTime).forEach { field ->
            field.showSoftInputOnFocus = false
            field.setOnClickListener {
                val value = field.text?.toString().orEmpty().replace(":", "").padEnd(4,'0')
                val picker=MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(value.take(2).toIntOrNull() ?: 6).setMinute(value.takeLast(2).toIntOrNull() ?: 0).build()
                picker.addOnPositiveButtonClickListener {
                    field.setText("%02d%02d".format(picker.hour,picker.minute))
                    readFields()?.let(viewModel::updateDraft)
                }
                picker.show(parentFragmentManager,"venue-time")
            }
        }
        binding.retry.setOnClickListener { viewModel.load() }
        binding.save.setOnClickListener { saveVenue() }
        binding.addCourt.setOnClickListener { addCourt() }
        binding.managePrices.setOnClickListener {
            findNavController().navigate(R.id.action_ownerVenueDetail_to_priceRule, Bundle().apply {
                putString("venueId", requireArguments().getString("venueId"))
            })
        }
        binding.blockSlots.setOnClickListener {
            viewModel.uiState.value.courts.firstOrNull()?.let { court ->
                findNavController().navigate(R.id.action_ownerVenueDetail_to_slotBlock, Bundle().apply {
                    putString("venueId", requireArguments().getString("venueId"))
                    putString("courtId", court.id)
                })
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.uiState.collect(::render) }
        }
    }

    private fun saveVenue() {
        readFields()?.let(viewModel::save)
    }

    private fun readFields(): VenueEditableFields? {
        val current = viewModel.uiState.value.venue ?: return null
        val draft = viewModel.uiState.value.draft
        return VenueEditableFields(
            name = binding.name.text.toString(), address = binding.address.text.toString(),
            districtId = draft?.districtId ?: current.districtId, location = draft?.location ?: current.location,
            openTime = binding.openTime.text.toString(), closeTime = binding.closeTime.text.toString(),
            phone = binding.phone.text.toString(), amenityIds = draft?.amenityIds ?: current.amenityIds,
            photoPaths = draft?.photoPaths ?: current.photoPaths, courtCount = current.courtCount,
            acceptsRecurring = binding.recurring.isChecked, status = if (current.status == "LOCKED") "LOCKED" else if(binding.hidden.isChecked) "HIDDEN" else "ACTIVE"
        )
    }

    private fun addCourt() {
        val name = binding.courtName.text.toString()
        val surface = binding.courtSurface.text.toString().uppercase()
        val id = editingCourtId
        if (id == null) viewModel.addCourt(CourtFields(name, surface, true, viewModel.uiState.value.courts.size))
        else {
            val court = viewModel.uiState.value.courts.firstOrNull { it.id == id }
            if (court != null) viewModel.updateCourt(id, CourtFields(name, surface, court.isActive, court.sortOrder))
        }
    }

    private fun render(state: OwnerVenueDetailUiState) {
        rendering = true
        if (state.message == "Đã lưu sân" && !state.isSubmitting) {
            editingCourtId = null
            binding.courtName.text?.clear()
            binding.courtSurface.setText("MAT", false)
            viewModel.acknowledgeCourtSaved()
        }
        binding.progress.visibility = if (state.isLoading || state.isSubmitting) View.VISIBLE else View.GONE
        binding.retry.visibility = if (state.error != null) View.VISIBLE else View.GONE
        binding.message.text = when (state.errorKind) {
            OwnerErrorKind.PERMISSION -> getString(R.string.owner_permission_error)
            OwnerErrorKind.NETWORK -> getString(R.string.owner_network_error)
            else -> if (state.isSubmitting) getString(R.string.owner_processing) else state.error ?: state.message.orEmpty()
        }
        binding.freshness.text = if (state.isFresh) getString(R.string.owner_data_fresh) else if (state.error != null) getString(R.string.owner_data_not_fresh) else ""
        val canPickLocation = state.venue != null && !state.isLoading && !state.isSubmitting && state.venue.status != "LOCKED"
        binding.location.isEnabled = canPickLocation
        locationMap?.setEditable(canPickLocation)
        state.venue?.let {
            val draft = state.draft
            if (!binding.name.hasFocus()) binding.name.setText(draft?.name ?: it.name)
            if (!binding.address.hasFocus()) binding.address.setText(draft?.address ?: it.address)
            if (!binding.openTime.hasFocus()) binding.openTime.setText(draft?.openTime ?: it.openTime)
            if (!binding.closeTime.hasFocus()) binding.closeTime.setText(draft?.closeTime ?: it.closeTime)
            if (!binding.phone.hasFocus()) binding.phone.setText(draft?.phone ?: it.phone)
            binding.recurring.isChecked = draft?.acceptsRecurring ?: it.acceptsRecurring
            binding.hidden.isChecked = (draft?.status ?: it.status) == "HIDDEN"
            binding.active.isChecked = (draft?.status ?: it.status) == "ACTIVE"
            binding.save.isEnabled = !state.isSubmitting && it.status != "LOCKED"
            binding.addCourt.isEnabled = !state.isSubmitting && it.status != "LOCKED"
            binding.courtName.isEnabled = !state.isSubmitting && it.status != "LOCKED"
            binding.courtSurface.isEnabled = !state.isSubmitting && it.status != "LOCKED"
            binding.addPhoto.isEnabled = !state.isSubmitting && it.status != "LOCKED"
            binding.location.text = (draft?.location ?: it.location)?.let { point -> "${getString(R.string.owner_map_location)}\n${point.latitude}, ${point.longitude}" } ?: getString(R.string.owner_map_location)
            locationMap?.show(draft?.location ?: it.location,draft?.name ?: it.name)
            val district = draft?.districtId ?: it.districtId
            binding.district.setAdapter(ArrayAdapter(requireContext(),android.R.layout.simple_list_item_1,state.districts.map { entry -> entry.name }))
            binding.district.setText(state.districts.firstOrNull { entry -> entry.id == district }?.name ?: district,false)
            binding.amenities.removeAllViews()
            val selected = draft?.amenityIds ?: it.amenityIds
            state.amenities.forEach { amenity -> binding.amenities.addView(Chip(requireContext()).apply {
                text=amenity.name; isCheckable=true; isChecked=amenity.id in selected
                setOnCheckedChangeListener { _,checked -> if (!rendering) readFields()?.let { fields ->
                    viewModel.updateDraft(fields.copy(amenityIds=if(checked) (fields.amenityIds+amenity.id).distinct() else fields.amenityIds-amenity.id))
                } }
            }) }
            val paths = draft?.photoPaths ?: it.photoPaths
            if (renderedPhotos != paths) {
                renderedPhotos=paths
                binding.photos.removeAllViews()
                paths.forEach { path -> binding.photos.addView(ImageView(requireContext()).apply {
                    val size=(88*resources.displayMetrics.density).toInt()
                    layoutParams=LinearLayout.LayoutParams(size,size).apply { marginEnd=(8*resources.displayMetrics.density).toInt() }
                    scaleType=ImageView.ScaleType.CENTER_CROP
                    contentDescription=getString(R.string.owner_photo_remove_hint)
                    load(photoUrls.venuePhoto(path))
                    setOnLongClickListener { readFields()?.let { fields -> viewModel.updateDraft(fields.copy(photoPaths=fields.photoPaths-path)) }; true }
                }) }
            }
        }
        courtAdapter.submitList(state.courts)
        rendering = false
    }

    override fun onDestroyView() { locationMap=null;_binding = null; super.onDestroyView() }

    private fun pickLocation() {
        val state=viewModel.uiState.value
        if (state.isLoading || state.isSubmitting || state.venue == null || state.venue.status == "LOCKED") return
        readFields()?.let { LocationPickerDialog.show(this,it.location) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("editingCourtId", editingCourtId)
    }
}

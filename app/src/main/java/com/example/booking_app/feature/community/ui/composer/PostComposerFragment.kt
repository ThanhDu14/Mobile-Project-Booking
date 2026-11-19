package com.example.booking_app.feature.community.ui.composer

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.booking_app.R
import com.example.booking_app.core.format.TimeFormat
import com.example.booking_app.core.ui.EmptyStateSpec
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.core.ui.render
import com.example.booking_app.databinding.FragmentPostComposerBinding
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.ui.common.CommunityUiText
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat as PickerTimeFormat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Màn hình soạn/sửa bài đăng và sự kiện giao lưu (T049 + T056). Dùng Photo Picker (`PickMultipleVisualMedia(4)`),
 * MaterialDatePicker + MaterialTimePicker theo `Asia/Ho_Chi_Minh`, hỏi "Bỏ bài đang viết?" khi thoát có nội dung.
 */
@AndroidEntryPoint
class PostComposerFragment : Fragment(R.layout.fragment_post_composer) {

    private val viewModel: PostComposerViewModel by viewModels()
    private var binding: FragmentPostComposerBinding? = null
    private var pendingPickedDate: LocalDate? = null

    private val pickPhotos = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(PostComposerViewModel.MAX_PHOTOS),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.onPhotosPicked(uris.map { it.toString() })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentPostComposerBinding.bind(view)
        this.binding = binding

        binding.toolbar.setNavigationOnClickListener { viewModel.onBackPressed() }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = viewModel.onBackPressed()
            },
        )

        val photoAdapter = DraftPhotoAdapter(
            onRetry = { viewModel.onRetryPhoto(it) },
            onRemove = { viewModel.onRemovePhoto(it) },
        )
        binding.photoGrid.layoutManager = GridLayoutManager(requireContext(), PHOTO_GRID_SPAN)
        binding.photoGrid.adapter = photoAdapter

        binding.eventTitleInput.doAfterTextChanged { viewModel.onEventTitleChanged(it?.toString().orEmpty()) }
        binding.eventLocationInput.doAfterTextChanged { viewModel.onEventLocationChanged(it?.toString().orEmpty()) }
        binding.eventExpectedInput.doAfterTextChanged { viewModel.onExpectedCountChanged(it?.toString().orEmpty()) }
        binding.eventTimeButton.setOnClickListener { openDateTimePicker() }
        pendingPickedDate = savedInstanceState?.getString(KEY_PENDING_PICKED_DATE)
            ?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
        reattachPickers()

        binding.contentInput.doAfterTextChanged { viewModel.onContentChanged(it?.toString().orEmpty()) }
        binding.pickPhotosButton.setOnClickListener {
            if (!viewModel.state.value.canAddMorePhotos) {
                Snackbar.make(binding.root, R.string.community_composer_max_photos, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.publishButton.setOnClickListener { viewModel.submit() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(binding, photoAdapter, it) } }
                launch { viewModel.events.collect { handle(binding, it) } }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingPickedDate?.let { outState.putString(KEY_PENDING_PICKED_DATE, it.toString()) }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun reattachPickers() {
        @Suppress("UNCHECKED_CAST")
        val existingDatePicker = childFragmentManager.findFragmentByTag(TAG_DATE_PICKER) as? MaterialDatePicker<Long>
        existingDatePicker?.let { attachDatePickerListener(it) }

        val existingTimePicker = childFragmentManager.findFragmentByTag(TAG_TIME_PICKER) as? MaterialTimePicker
        existingTimePicker?.let { attachTimePickerListener(it) }
    }

    private fun openDateTimePicker() {
        if (childFragmentManager.findFragmentByTag(TAG_DATE_PICKER) != null) return
        val currentZoned = (viewModel.state.value.eventAt ?: Instant.now().plusSeconds(3600)).atZone(TimeFormat.ZONE)
        val initialSelectionUtc = currentZoned.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val todayVnUtcMillis = LocalDate.now(TimeFormat.ZONE).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val constraints = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointForward.from(todayVnUtcMillis))
            .build()
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.community_field_event_time)
            .setSelection(initialSelectionUtc)
            .setCalendarConstraints(constraints)
            .build()
        attachDatePickerListener(datePicker)
        datePicker.show(childFragmentManager, TAG_DATE_PICKER)
    }

    private fun attachDatePickerListener(datePicker: MaterialDatePicker<Long>) {
        datePicker.clearOnPositiveButtonClickListeners()
        datePicker.addOnPositiveButtonClickListener { utcMillis ->
            val currentZoned = (viewModel.state.value.eventAt ?: Instant.now().plusSeconds(3600)).atZone(TimeFormat.ZONE)
            val pickedDate: LocalDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
            openTimePicker(pickedDate, currentZoned.hour, currentZoned.minute)
        }
    }

    private fun openTimePicker(date: LocalDate, hour: Int, minute: Int) {
        if (childFragmentManager.findFragmentByTag(TAG_TIME_PICKER) != null) return
        pendingPickedDate = date
        val timePicker = MaterialTimePicker.Builder()
            .setTimeFormat(PickerTimeFormat.CLOCK_24H)
            .setHour(hour)
            .setMinute(minute)
            .setTitleText(R.string.community_field_event_time)
            .build()
        attachTimePickerListener(timePicker)
        timePicker.show(childFragmentManager, TAG_TIME_PICKER)
    }

    private fun attachTimePickerListener(timePicker: MaterialTimePicker) {
        timePicker.clearOnPositiveButtonClickListeners()
        timePicker.addOnPositiveButtonClickListener {
            val date = pendingPickedDate
                ?: (viewModel.state.value.eventAt ?: Instant.now()).atZone(TimeFormat.ZONE).toLocalDate()
            val zoned = ZonedDateTime.of(date, LocalTime.of(timePicker.hour, timePicker.minute), TimeFormat.ZONE)
            viewModel.onEventAtChanged(zoned.toInstant())
            pendingPickedDate = null
        }
    }

    private fun render(
        binding: FragmentPostComposerBinding,
        photoAdapter: DraftPhotoAdapter,
        state: PostComposerState,
    ) {
        val coreState: UiState<Unit> = when {
            state.loadError is CommunityError.PostNotFound -> UiState.Empty
            state.loadError != null -> UiState.Error(CommunityUiText.error(requireContext(), state.loadError), viewModel::loadPost)
            state.loading -> UiState.Loading
            else -> UiState.Content(Unit)
        }
        binding.stateViews.render(coreState, binding.content, EmptyStateSpec(message = R.string.community_error_post_not_found))

        binding.toolbar.setTitle(
            when {
                state.isEdit -> R.string.community_title_composer_edit
                state.isEvent -> R.string.community_title_composer_event
                else -> R.string.community_title_composer_post
            },
        )

        binding.eventFields.isVisible = state.isEvent
        if (state.isEvent) {
            if (binding.eventTitleInput.text?.toString() != state.eventTitle) {
                binding.eventTitleInput.setText(state.eventTitle)
            }
            if (binding.eventLocationInput.text?.toString() != state.eventLocation) {
                binding.eventLocationInput.setText(state.eventLocation)
            }
            if (binding.eventExpectedInput.text?.toString() != state.expectedCountText) {
                binding.eventExpectedInput.setText(state.expectedCountText)
            }
            binding.eventTitleLayout.error = when (state.eventTitleError) {
                PostContentError.REQUIRED -> getString(R.string.community_event_title_required)
                PostContentError.TOO_LONG -> getString(R.string.community_event_title_too_long)
                null -> null
            }
            binding.eventTimeButton.text = state.eventAt?.let {
                "${TimeFormat.dayOfWeekDate(it)} · ${TimeFormat.time(it)}"
            } ?: getString(R.string.community_event_pick_time)
            binding.eventTimeError.isVisible = state.eventTimeError != null
            binding.eventTimeError.text = when (state.eventTimeError) {
                EventTimeError.REQUIRED -> getString(R.string.community_event_time_required)
                EventTimeError.PAST -> getString(R.string.community_event_time_past)
                null -> null
            }
            binding.eventLocationLayout.error = when (state.eventLocationError) {
                PostContentError.REQUIRED -> getString(R.string.community_event_location_required)
                PostContentError.TOO_LONG -> getString(R.string.community_event_location_too_long)
                null -> null
            }
            binding.eventExpectedLayout.error =
                if (state.expectedCountError) getString(R.string.community_event_expected_invalid) else null
        }

        binding.contentInput.setHint(
            if (state.isEvent) R.string.community_event_description_hint else R.string.community_field_content_hint,
        )
        if (binding.contentInput.text?.toString() != state.content) {
            binding.contentInput.setText(state.content)
        }
        binding.contentLayout.error = when (state.contentError) {
            PostContentError.REQUIRED -> getString(R.string.community_composer_content_required)
            PostContentError.TOO_LONG -> getString(R.string.community_composer_content_too_long)
            null -> null
        }

        val items = state.photos.map { photo ->
            val source = if (photo.remotePath != null && photo.localUri == photo.remotePath) {
                state.remotePhotoUrls[photo.remotePath]
            } else {
                photo.localUri
            }
            DraftPhotoItem(photo = photo, displaySource = source)
        }
        photoAdapter.submitList(items)
        binding.photoGrid.isVisible = items.isNotEmpty()
        binding.photoCountLabel.text = getString(R.string.community_counter, state.photos.size, PostComposerViewModel.MAX_PHOTOS)

        val editable = !state.saving
        binding.eventTitleInput.isEnabled = editable
        binding.eventTimeButton.isEnabled = editable
        binding.eventLocationInput.isEnabled = editable
        binding.eventExpectedInput.isEnabled = editable
        binding.contentInput.isEnabled = editable
        binding.pickPhotosButton.isEnabled = editable
        binding.publishButton.isEnabled = state.canPublish
        binding.publishButton.setText(
            when {
                state.saving -> R.string.community_composer_publishing
                state.isEdit -> R.string.community_action_save
                else -> R.string.community_action_publish
            },
        )
    }

    private fun handle(binding: FragmentPostComposerBinding, event: PostComposerEvent) {
        when (event) {
            PostComposerEvent.MaxPhotosReached ->
                Snackbar.make(binding.root, R.string.community_composer_max_photos, Snackbar.LENGTH_SHORT).show()
            PostComposerEvent.ConfirmDiscard -> confirmDiscard()
            PostComposerEvent.Close -> findNavController().navigateUp()
            is PostComposerEvent.Posted -> {
                setFragmentResult(RESULT_KEY_POST_SAVED, bundleOf(RESULT_POST_ID to event.post.id))
                findNavController().navigateUp()
            }
            is PostComposerEvent.ShowError ->
                Snackbar.make(binding.root, CommunityUiText.error(requireContext(), event.error), Snackbar.LENGTH_LONG).show()
        }
    }

    private fun confirmDiscard() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.community_confirm_discard_draft_title)
            .setMessage(R.string.community_confirm_discard_draft_message)
            .setNegativeButton(R.string.community_action_keep_writing, null)
            .setPositiveButton(R.string.community_action_discard_draft) { _, _ -> viewModel.onDiscardConfirmed() }
            .show()
    }

    companion object {
        const val RESULT_KEY_POST_SAVED = "community_post_saved"
        const val RESULT_POST_ID = "postId"
        private const val PHOTO_GRID_SPAN = 2
        private const val TAG_DATE_PICKER = "community_event_date"
        private const val TAG_TIME_PICKER = "community_event_time"
        private const val KEY_PENDING_PICKED_DATE = "community_pending_picked_date"
    }
}

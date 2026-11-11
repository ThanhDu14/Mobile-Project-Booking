package com.example.booking_app.feature.review.ui.write

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RatingBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil3.load
import com.example.booking_app.R
import com.example.booking_app.core.ui.EmptyStateSpec
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.core.ui.render
import com.example.booking_app.databinding.FragmentReviewWriteBinding
import com.example.booking_app.feature.review.domain.ReviewEligibility
import com.example.booking_app.feature.review.domain.ReviewPhotoUploader
import com.example.booking_app.feature.review.domain.model.ReviewCriterion
import com.example.booking_app.feature.review.domain.model.ReviewTarget
import com.example.booking_app.feature.review.ui.common.messageRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Viết đánh giá cho một đơn đã hoàn thành (US1), kèm tối đa 3 ảnh (US2). Vào từ chi tiết đơn (spec 060)
 * hoặc deep link `smashnow://review/write/{bookingId}`. Đơn đã có đánh giá thì hiện lại nội dung đã gửi;
 * còn hạn thì bấm "Sửa" (hoặc mở với `mode = EDIT`), xóa được bất cứ lúc nào (US5).
 */
@AndroidEntryPoint
class WriteReviewFragment : Fragment(R.layout.fragment_review_write) {

    private val viewModel: WriteReviewViewModel by viewModels()

    private val locale = Locale.forLanguageTag("vi-VN")
    private val zone = ZoneId.systemDefault()
    private val dayFormat = DateTimeFormatter.ofPattern("EEE, dd/MM", locale)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val deadlineFormat = DateTimeFormatter.ofPattern("dd/MM", locale)
    private val scoreFormat = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }

    /** Ảnh chụp đang chờ camera trả về; giữ qua process death. */
    private var pendingCameraUri: Uri? = null

    // Photo Picker không cần quyền đọc ảnh (research R4). PickMultipleVisualMedia yêu cầu tối đa > 1,
    // nên còn 1 chỗ thì dùng PickVisualMedia; chọn quá chỗ trống thì ViewModel cắt và báo review_photo_limit.
    private val pickOne = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.addPhotos(listOf(it.toString())) }
    }

    private val pickMany =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(ReviewPhotoUploader.MAX_PHOTOS)) {
            if (it.isNotEmpty()) viewModel.addPhotos(it.map(Uri::toString))
        }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (saved && uri != null) viewModel.addPhotos(listOf(uri.toString()))
    }

    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else showCameraDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingCameraUri = savedInstanceState?.getString(KEY_CAMERA_URI)?.let(Uri::parse)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingCameraUri?.let { outState.putString(KEY_CAMERA_URI, it.toString()) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentReviewWriteBinding.bind(view)

        val photoAdapter = DraftPhotoAdapter(
            onRetry = { viewModel.retryPhoto(it.localUri) },
            onRemove = { viewModel.removePhoto(it.localUri) },
        )
        binding.photoList.adapter = photoAdapter
        binding.addFromGalleryButton.setOnClickListener { launchGallery() }
        binding.takePhotoButton.setOnClickListener { onCameraChosen() }
        binding.deleteButton.setOnClickListener { viewModel.requestDelete() }

        // Bấm quay lại khi đang có thay đổi: hỏi "Bỏ đánh giá đang viết?" (T036).
        val discardCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = confirmDiscard()
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, discardCallback)
        binding.toolbar.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }

        val ratingBars = mapOf(
            ReviewCriterion.COURT to binding.ratingCourt,
            ReviewCriterion.CLEANLINESS to binding.ratingCleanliness,
            ReviewCriterion.SERVICE to binding.ratingService,
        )
        ratingBars.forEach { (criterion, bar) ->
            bar.setOnRatingBarChangeListener { _, rating, fromUser ->
                if (fromUser) viewModel.onRatingChanged(criterion, rating.toInt().coerceAtLeast(1))
            }
        }
        binding.commentInput.doAfterTextChanged { viewModel.onCommentChanged(it?.toString().orEmpty()) }
        binding.submitButton.setOnClickListener {
            val state = viewModel.uiState.value
            if (state.isReadOnly && state.canEdit) viewModel.startEditing() else viewModel.submit()
        }

        // Màn hình này không có trạng thái rỗng; spec chỉ để thỏa chữ ký của render().
        val empty = EmptyStateSpec(message = R.string.review_load_failed)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        discardCallback.isEnabled = state.hasUnsavedChanges && !state.isSubmitting && !state.isDeleting
                        val screenState: UiState<Unit> = when {
                            state.isLoading -> UiState.Loading
                            state.loadError != null -> UiState.Error(
                                getString(R.string.review_load_failed) + "\n" + getString(state.loadError.messageRes()),
                                retry = viewModel::retryLoad,
                            )
                            else -> UiState.Content(Unit)
                        }
                        binding.stateViews.render(screenState, binding.content, empty)
                        if (screenState is UiState.Content) bind(binding, state, ratingBars, photoAdapter)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is WriteReviewEvent.Submitted -> {
                                Toast.makeText(
                                    requireContext(),
                                    getString(R.string.review_submitted, scoreFormat.format(event.overall)),
                                    Toast.LENGTH_SHORT,
                                ).show()
                                findNavController().navigateUp()
                            }
                            is WriteReviewEvent.Saved -> {
                                Toast.makeText(
                                    requireContext(),
                                    getString(R.string.review_saved, scoreFormat.format(event.overall)),
                                    Toast.LENGTH_SHORT,
                                ).show()
                                findNavController().navigateUp()
                            }
                            WriteReviewEvent.PhotoLimit -> showSnackbar(binding, R.string.review_photo_limit)
                            WriteReviewEvent.PhotoTooLarge -> showSnackbar(binding, R.string.review_photo_too_large)
                            WriteReviewEvent.ConfirmDelete -> confirmDelete()
                            WriteReviewEvent.Deleted -> {
                                Toast.makeText(requireContext(), R.string.review_deleted, Toast.LENGTH_SHORT).show()
                                findNavController().navigateUp()
                            }
                            is WriteReviewEvent.DeleteFailed -> showSnackbar(binding, event.error.messageRes())
                            is WriteReviewEvent.SubmitFailed -> {
                                val snackbar = Snackbar.make(
                                    binding.root,
                                    event.error.messageRes(),
                                    if (event.canRetry) Snackbar.LENGTH_INDEFINITE else Snackbar.LENGTH_LONG,
                                )
                                if (event.canRetry) snackbar.setAction(R.string.review_retry) { viewModel.submit() }
                                snackbar.anchorView = binding.bottomBar
                                snackbar.show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun bind(
        binding: FragmentReviewWriteBinding,
        state: WriteReviewUiState,
        ratingBars: Map<ReviewCriterion, RatingBar>,
        photoAdapter: DraftPhotoAdapter,
    ) {
        state.target?.let { bindTarget(binding, it) }
        binding.toolbar.setTitle(if (state.isEditing) R.string.review_edit_title else R.string.review_write_title)

        val draft = state.draft
        val values = mapOf(
            ReviewCriterion.COURT to draft.court,
            ReviewCriterion.CLEANLINESS to draft.cleanliness,
            ReviewCriterion.SERVICE to draft.service,
        )
        ratingBars.forEach { (criterion, bar) ->
            val stars = values[criterion]
            if (bar.rating.toInt() != (stars ?: 0)) bar.rating = (stars ?: 0).toFloat()
            bar.setIsIndicator(state.isReadOnly)
            bar.contentDescription = if (stars == null) {
                getString(R.string.review_rating_none, getString(criterion.labelRes()))
            } else {
                getString(R.string.review_rating_description, getString(criterion.labelRes()), stars)
            }
        }

        binding.missingCriteria.isVisible = state.missingCriteria.isNotEmpty()
        if (state.missingCriteria.isNotEmpty()) {
            val names = state.missingCriteria.joinToString(", ") { "\"${getString(it.labelRes())}\"" }
            binding.missingCriteria.text = getString(R.string.review_missing_named, names)
        }

        if (binding.commentInput.text?.toString() != draft.comment) binding.commentInput.setText(draft.comment)
        binding.commentInput.isEnabled = !state.isReadOnly
        binding.commentLayout.error = if (state.commentTooLong) getString(R.string.review_comment_too_long) else null

        photoAdapter.editable = !state.isReadOnly
        photoAdapter.submitList(draft.photos)
        binding.photoSection.isVisible = !state.isReadOnly || draft.photos.isNotEmpty()
        binding.photoList.isVisible = draft.photos.isNotEmpty()
        binding.photoActions.isVisible = !state.isReadOnly && state.remainingPhotoSlots > 0
        binding.photoPending.isVisible = !state.isReadOnly && !state.photosReady

        val banner = if (state.isEditing) null else when (state.eligibility) {
            ReviewEligibility.CAN_VIEW -> getString(
                if (state.canEdit) R.string.review_already_submitted else R.string.review_edit_window_closed,
            )
            ReviewEligibility.EXPIRED -> getString(R.string.review_window_closed)
            ReviewEligibility.NOT_ELIGIBLE -> getString(
                if (state.target?.isDropIn == true) {
                    R.string.review_error_not_checked_in
                } else {
                    R.string.review_error_booking_not_completed
                },
            )
            ReviewEligibility.CAN_WRITE, null -> null
        }
        binding.statusBanner.isVisible = banner != null
        binding.statusBanner.text = banner

        binding.publicNotice.isVisible = state.canEditUntil != null && state.eligibility != ReviewEligibility.NOT_ELIGIBLE
        state.canEditUntil?.let {
            binding.publicNotice.text = getString(R.string.review_public_notice, deadlineFormat.format(it.atZone(zone)))
        }

        binding.overallValue.text = state.overall
            ?.let { getString(R.string.review_overall_value, scoreFormat.format(it)) }
            ?: getString(R.string.review_overall_empty)

        // Thanh dưới: "Gửi đánh giá" (viết mới), "Lưu" (đang sửa) hoặc "Sửa" (đang xem, còn hạn).
        val showEdit = state.isReadOnly && state.canEdit
        binding.bottomBar.isVisible = state.eligibility == ReviewEligibility.CAN_WRITE || state.isEditing || showEdit
        binding.submitButton.isEnabled = if (showEdit) !state.isDeleting else state.canSubmit
        val label = when {
            showEdit -> R.string.review_edit
            state.isEditing -> R.string.review_save
            else -> R.string.review_submit
        }
        binding.submitButton.text = if (state.isSubmitting) "" else getString(label)
        binding.submitProgress.isVisible = state.isSubmitting

        binding.deleteButton.isVisible = state.canDelete
        binding.deleteButton.isEnabled = !state.isDeleting && !state.isSubmitting
    }

    private fun bindTarget(binding: FragmentReviewWriteBinding, target: ReviewTarget) {
        binding.venueName.text = target.venueName
        val start = target.startAt?.atZone(zone)
        val end = target.endAt?.atZone(zone)
        binding.bookingTime.text = if (start != null && end != null) {
            listOfNotNull(
                getString(
                    R.string.review_booking_time,
                    dayFormat.format(start),
                    timeFormat.format(start),
                    timeFormat.format(end),
                ),
                target.courtNames.takeIf { it.isNotEmpty() }?.joinToString(", "),
            ).joinToString(" · ")
        } else {
            target.courtNames.joinToString(", ")
        }
        binding.venuePhoto.load(target.venuePhotoUrl)
    }

    private fun confirmDiscard() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.review_discard_title)
            .setMessage(R.string.review_discard_message)
            .setNegativeButton(R.string.review_discard_keep, null)
            .setPositiveButton(R.string.review_discard_confirm) { _, _ ->
                viewModel.discardDraft()
                findNavController().navigateUp()
            }
            .show()
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.review_delete_title)
            .setMessage(R.string.review_delete_message)
            .setNegativeButton(R.string.review_delete_cancel, null)
            .setPositiveButton(R.string.review_delete_confirm) { _, _ -> viewModel.confirmDelete() }
            .show()
    }

    private fun showSnackbar(binding: FragmentReviewWriteBinding, message: Int) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .apply { if (binding.bottomBar.isVisible) anchorView = binding.bottomBar }
            .show()
    }

    private fun launchGallery() {
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        if (viewModel.uiState.value.remainingPhotoSlots > 1) pickMany.launch(request) else pickOne.launch(request)
    }

    private fun onCameraChosen() {
        val granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        when {
            granted -> launchCamera()
            // Đã từ chối một lần: giải thích trước khi hỏi lại.
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.review_camera_rationale_title)
                    .setMessage(R.string.review_camera_rationale)
                    .setNegativeButton(R.string.review_camera_cancel, null)
                    .setPositiveButton(R.string.review_camera_allow) { _, _ ->
                        requestCamera.launch(Manifest.permission.CAMERA)
                    }
                    .show()
            // Chỉ xin quyền camera lúc bấm "Chụp ảnh".
            else -> requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val dir = File(requireContext().cacheDir, CAMERA_DIR).apply { mkdirs() }
        val file = File(dir, "review_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
        pendingCameraUri = uri
        takePicture.launch(uri)
    }

    /** Từ chối quyền camera vẫn chọn được ảnh từ thư viện. */
    private fun showCameraDenied() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.review_camera_denied)
            .setNegativeButton(R.string.review_camera_cancel, null)
            .setPositiveButton(R.string.review_photo_add_gallery) { _, _ -> launchGallery() }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ảnh chụp tạm chỉ cần tới khi tải lên xong; rời hẳn màn hình thì dọn.
        if (isRemoving) File(requireContext().cacheDir, CAMERA_DIR).deleteRecursively()
    }

    private companion object {
        const val KEY_CAMERA_URI = "review_camera_uri"

        /** Khớp `cache-path name="review"` trong `res/xml/file_paths.xml`. */
        const val CAMERA_DIR = "review"
    }

    private fun ReviewCriterion.labelRes(): Int = when (this) {
        ReviewCriterion.COURT -> R.string.review_criterion_court
        ReviewCriterion.CLEANLINESS -> R.string.review_criterion_cleanliness
        ReviewCriterion.SERVICE -> R.string.review_criterion_service
    }
}

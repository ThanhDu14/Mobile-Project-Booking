package com.example.booking_app.feature.account.ui.edit

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil3.load
import com.example.booking_app.R
import com.example.booking_app.core.media.StorageUrls
import com.example.booking_app.databinding.FragmentAccountEditProfileBinding
import com.example.booking_app.feature.account.data.ImageProcessor
import com.example.booking_app.feature.account.domain.model.PlayerLevel
import com.example.booking_app.feature.account.domain.validation.AvatarImageRule
import com.example.booking_app.feature.account.domain.validation.DistrictSelectionRule
import com.example.booking_app.feature.account.ui.AccountResults
import com.example.booking_app.feature.account.ui.accountErrorMessage
import com.example.booking_app.feature.account.ui.account.initialsOf
import com.example.booking_app.feature.account.ui.isRetryable
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File

/** Sửa hồ sơ (spec 011 US1, wireframe `ui/01_account_buoc2_sua-ho-so.png`). */
@AndroidEntryPoint
class EditProfileFragment : Fragment(R.layout.fragment_account_edit_profile) {

    private val viewModel: EditProfileViewModel by viewModels()
    private var binding: FragmentAccountEditProfileBinding? = null

    /** Đường dẫn ảnh chụp đang chờ camera trả về; giữ qua process death. */
    private var pendingCameraUri: Uri? = null
    private var renderedChipIds: List<String>? = null
    private var isRendering = false

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(::openCrop)
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (saved && uri != null) openCrop(uri)
    }

    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else showCameraDenied()
    }

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = viewModel.onBack()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingCameraUri = savedInstanceState?.getString(KEY_CAMERA_URI)?.let(Uri::parse)
        setFragmentResultListener(AccountResults.DISTRICTS) { _, result ->
            viewModel.onDistrictsPicked(result.getStringArray(AccountResults.DISTRICT_IDS).orEmpty().toList())
        }
        setFragmentResultListener(AccountResults.AVATAR_SOURCE) { _, result ->
            when (result.getString(AccountResults.SOURCE)) {
                AccountResults.SOURCE_CAMERA -> onCameraChosen()
                AccountResults.SOURCE_GALLERY -> launchGallery()
            }
        }
        setFragmentResultListener(AccountResults.AVATAR_CROP) { _, result ->
            result.getString(AccountResults.CROPPED_URI)?.let(viewModel::onAvatarCropped)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentAccountEditProfileBinding.bind(view).also { binding = it }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        renderedChipIds = null

        binding.toolbar.setNavigationOnClickListener { viewModel.onBack() }
        binding.changeAvatarButton.setOnClickListener {
            findNavController().navigate(R.id.action_editProfile_to_avatarSource)
        }
        binding.removeAvatarButton.setOnClickListener { viewModel.onRemoveAvatar() }
        binding.nameInput.doAfterTextChanged { if (!isRendering) viewModel.onNameChange(it?.toString().orEmpty()) }
        binding.levelGroup.addOnButtonCheckedListener { group, _, _ ->
            if (!isRendering) viewModel.onLevelSelected(levelFor(group.checkedButtonId))
        }
        binding.addDistrictButton.setOnClickListener {
            val ids = viewModel.uiState.value.draft.districtIds.toTypedArray()
            findNavController().navigate(EditProfileFragmentDirections.actionEditProfileToDistrictPicker(ids))
        }
        binding.districtsRetryButton.setOnClickListener { viewModel.retryDistricts() }
        binding.saveButton.setOnClickListener { viewModel.onSave() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.events.collect(::handle) }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingCameraUri?.let { outState.putString(KEY_CAMERA_URI, it.toString()) }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun render(state: EditProfileUiState) {
        val binding = binding ?: return
        isRendering = true
        backCallback.isEnabled = state.isDirty

        binding.avatarInitials.text = initialsOf(state.draft.displayName.ifBlank { state.saved.displayName })
        binding.avatarImage.isVisible = state.avatarPath != null
        state.avatarPath?.let { binding.avatarImage.load(StorageUrls.publicUrl(AVATAR_BUCKET, it)) }
        binding.avatarImage.contentDescription = getString(R.string.account_avatar_description, state.saved.displayName)
        binding.avatarProgress.isVisible = state.isAvatarBusy
        binding.changeAvatarButton.isEnabled = !state.isAvatarBusy && !state.isLoading
        binding.removeAvatarButton.isVisible = state.avatarPath != null
        binding.removeAvatarButton.isEnabled = !state.isAvatarBusy
        (state.avatar as? AvatarState.Error)?.let {
            Snackbar.make(binding.root, accountErrorMessage(it.error), Snackbar.LENGTH_LONG).show()
            viewModel.onAvatarErrorShown()
        }

        if (binding.nameInput.text?.toString() != state.draft.displayName) {
            binding.nameInput.setText(state.draft.displayName)
            binding.nameInput.setSelection(binding.nameInput.length())
        }
        binding.nameLayout.error = if (state.nameError) getString(R.string.auth_error_name_length) else null

        binding.emailGroup.isVisible = state.email != null
        binding.emailValue.text = state.email
        binding.emailUnverifiedChip.isVisible = !state.emailVerified

        val levelButton = buttonFor(state.draft.level)
        if (levelButton == null) binding.levelGroup.clearChecked() else binding.levelGroup.check(levelButton)

        binding.districtsCounter.text =
            getString(R.string.account_districts_counter, state.draft.districtIds.size, DistrictSelectionRule.MAX)
        renderChips(binding, state)
        binding.addDistrictButton.isEnabled = state.canAddDistrict && state.districtsState is DistrictsState.Content
        binding.districtsError.isVisible = state.districtsState is DistrictsState.Error

        binding.unsavedLabel.visibility = if (state.isDirty) View.VISIBLE else View.INVISIBLE
        binding.saveButton.isEnabled = state.isDirty && !state.isSaving && !state.isLoading
        binding.saveButton.text = if (state.isSaving) "" else getString(R.string.account_save)
        binding.saveProgress.isVisible = state.isSaving
        isRendering = false
    }

    private fun renderChips(binding: FragmentAccountEditProfileBinding, state: EditProfileUiState) {
        val districts = state.selectedDistricts
        val ids = districts.map { "${it.id}:${it.isActive}" }
        if (ids == renderedChipIds) return
        renderedChipIds = ids
        binding.districtChips.removeAllViews()
        districts.forEach { district ->
            val chip = Chip(requireContext()).apply {
                text = if (district.isActive) district.name else getString(R.string.account_district_chip_inactive, district.name)
                isCloseIconVisible = true
                closeIconContentDescription = getString(R.string.account_districts_remove, district.name)
                setOnCloseIconClickListener { viewModel.onRemoveDistrict(district.id) }
            }
            binding.districtChips.addView(chip)
        }
    }

    private fun handle(event: EditProfileEvent) {
        val binding = binding ?: return
        when (event) {
            EditProfileEvent.Saved -> Snackbar.make(binding.root, R.string.account_saved, Snackbar.LENGTH_SHORT).show()
            is EditProfileEvent.SaveFailed -> {
                val snackbar = Snackbar.make(binding.root, accountErrorMessage(event.error), Snackbar.LENGTH_LONG)
                if (isRetryable(event.error)) snackbar.setAction(R.string.account_retry) { viewModel.onSave() }
                snackbar.show()
            }
            EditProfileEvent.ConfirmDiscard -> MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.account_discard_title)
                .setPositiveButton(R.string.account_discard_confirm) { _, _ -> viewModel.onDiscardConfirmed() }
                .setNegativeButton(R.string.account_keep_editing, null)
                .show()
            EditProfileEvent.Exit -> findNavController().popBackStack()
        }
    }

    private fun onCameraChosen() {
        val granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        // Chỉ xin quyền camera lúc bấm "Chụp ảnh" (FR-024).
        if (granted) launchCamera() else requestCamera.launch(Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        val file = File(ImageProcessor.tempDir(requireContext()), "camera_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
        pendingCameraUri = uri
        takePicture.launch(uri)
    }

    private fun launchGallery() {
        // Photo Picker không cần quyền đọc ảnh (research R4).
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    /** Từ chối quyền camera vẫn cho chọn từ thư viện (US1-4); từ chối hẳn thì có nút mở Cài đặt. */
    private fun showCameraDenied() {
        val permanentlyDenied = !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.account_camera_permission_denied)
            .setPositiveButton(R.string.account_avatar_source_gallery) { _, _ -> launchGallery() }
            .setNegativeButton(R.string.account_cancel, null)
            .apply {
                if (permanentlyDenied) {
                    setNeutralButton(R.string.account_open_settings) { _, _ -> openAppSettings() }
                }
            }
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", requireContext().packageName, null))
        startActivity(intent)
    }

    private fun openCrop(source: Uri) {
        val mimeError = AvatarImageRule.mimeError(requireContext().contentResolver.getType(source))
        if (mimeError != null) {
            binding?.let { Snackbar.make(it.root, accountErrorMessage(mimeError), Snackbar.LENGTH_LONG).show() }
            return
        }
        findNavController().navigate(EditProfileFragmentDirections.actionEditProfileToAvatarCrop(source.toString()))
    }

    private fun levelFor(buttonId: Int): PlayerLevel? = when (buttonId) {
        R.id.levelBeginner -> PlayerLevel.BEGINNER
        R.id.levelIntermediate -> PlayerLevel.INTERMEDIATE
        R.id.levelAdvanced -> PlayerLevel.ADVANCED
        else -> null
    }

    private fun buttonFor(level: PlayerLevel?): Int? = when (level) {
        PlayerLevel.BEGINNER -> R.id.levelBeginner
        PlayerLevel.INTERMEDIATE -> R.id.levelIntermediate
        PlayerLevel.ADVANCED -> R.id.levelAdvanced
        null -> null
    }

    private companion object {
        const val AVATAR_BUCKET = "avatars"
        const val KEY_CAMERA_URI = "camera_uri"
    }
}

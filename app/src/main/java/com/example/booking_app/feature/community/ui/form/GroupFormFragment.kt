package com.example.booking_app.feature.community.ui.form

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil3.load
import coil3.request.error
import com.example.booking_app.R
import com.example.booking_app.core.catalog.District
import com.example.booking_app.core.ui.EmptyStateSpec
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.core.ui.render
import com.example.booking_app.databinding.FragmentGroupFormBinding
import com.example.booking_app.feature.community.domain.model.GroupVisibility
import com.example.booking_app.feature.community.ui.common.CommunityUiText
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Tạo/sửa nhóm (T027). `groupId` null là tạo. Thành công khi tạo thì mở trang nhóm và bỏ form khỏi back stack; khi sửa
 * thì quay lại. Chọn ảnh bằng Photo Picker (không cần quyền đọc bộ nhớ).
 */
@AndroidEntryPoint
class GroupFormFragment : Fragment(R.layout.fragment_group_form) {

    private val viewModel: GroupFormViewModel by viewModels()
    private var binding: FragmentGroupFormBinding? = null
    private var shownDistricts: List<District>? = null

    private val pickAvatar = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onAvatarPicked(uri.toString())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentGroupFormBinding.bind(view)
        this.binding = binding
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.setTitle(if (viewModel.groupId == null) R.string.community_title_form_create else R.string.community_title_form_edit)
        binding.saveButton.setText(if (viewModel.groupId == null) R.string.community_title_form_create else R.string.community_action_save)

        val launchPicker = View.OnClickListener {
            pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.avatarImage.setOnClickListener(launchPicker)
        binding.avatarPickButton.setOnClickListener(launchPicker)
        binding.avatarRemoveButton.setOnClickListener { viewModel.onAvatarRemoved() }

        binding.nameInput.doAfterTextChanged { viewModel.onNameChanged(it?.toString().orEmpty()) }
        binding.descriptionInput.doAfterTextChanged { viewModel.onDescriptionChanged(it?.toString().orEmpty()) }
        binding.visibilityToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.onVisibilityChanged(if (checkedId == R.id.visibilityPrivate) GroupVisibility.PRIVATE else GroupVisibility.PUBLIC)
        }
        binding.saveButton.setOnClickListener { viewModel.submit() }
        binding.deleteGroupButton.setOnClickListener { confirmDeleteGroup() }
        binding.retryAvatarButton.setOnClickListener { viewModel.retryAvatar() }
        binding.defaultAvatarButton.setOnClickListener { viewModel.useDefaultAvatar() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(binding, it) } }
                launch { viewModel.events.collect { handle(it, binding) } }
            }
        }
    }

    override fun onDestroyView() {
        binding = null
        shownDistricts = null
        super.onDestroyView()
    }

    private fun render(binding: FragmentGroupFormBinding, state: GroupFormState) {
        val coreState: UiState<Unit> = when {
            state.loadError != null -> UiState.Error(CommunityUiText.error(requireContext(), state.loadError), viewModel::loadGroup)
            state.loading -> UiState.Loading
            else -> UiState.Content(Unit)
        }
        binding.stateViews.render(coreState, binding.content, EmptyStateSpec(message = R.string.community_error_unknown))

        if (binding.nameInput.text?.toString() != state.name) binding.nameInput.setText(state.name)
        if (binding.descriptionInput.text?.toString() != state.description) binding.descriptionInput.setText(state.description)
        binding.nameLayout.error = state.nameError?.let { getString(nameErrorRes(it)) }
        binding.descriptionLayout.error = state.descriptionError?.let { getString(R.string.community_form_description_too_long) }

        renderDistricts(binding, state)
        binding.districtLayout.error = if (state.districtError) getString(R.string.community_form_district_required) else null

        val visibilityId = if (state.visibility == GroupVisibility.PRIVATE) R.id.visibilityPrivate else R.id.visibilityPublic
        if (binding.visibilityToggle.checkedButtonId != visibilityId) binding.visibilityToggle.check(visibilityId)
        binding.visibilityDescription.setText(CommunityUiText.visibilityDescription(state.visibility))

        renderAvatar(binding, state)

        val editable = !state.saving
        binding.nameInput.isEnabled = editable
        binding.descriptionInput.isEnabled = editable
        binding.districtInput.isEnabled = editable
        binding.visibilityToggle.isEnabled = editable
        binding.avatarPickButton.isEnabled = editable
        binding.avatarRemoveButton.isEnabled = editable
        binding.avatarImage.isEnabled = editable
        binding.saveButton.isEnabled = editable
        binding.saveButton.setText(
            when {
                state.saving -> R.string.community_form_saving
                state.isEdit -> R.string.community_action_save
                else -> R.string.community_title_form_create
            },
        )
        binding.avatarFailureBox.isVisible = state.avatarFailure != null
        binding.retryAvatarButton.isEnabled = editable
        binding.defaultAvatarButton.isEnabled = editable
        binding.deleteGroupButton.isVisible = state.isEdit && !state.loading && state.loadError == null
        binding.deleteGroupButton.isEnabled = editable
    }

    private fun renderDistricts(binding: FragmentGroupFormBinding, state: GroupFormState) {
        if (shownDistricts != state.districts) {
            shownDistricts = state.districts
            val names = state.districts.map { it.name }
            binding.districtInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, names))
            binding.districtInput.setOnItemClickListener { _, _, position, _ ->
                state.districts.getOrNull(position)?.let { viewModel.onDistrictSelected(it.id) }
            }
        }
        val selected = state.districts.firstOrNull { it.id == state.districtId }
        // false: đặt chữ mà không lọc lại danh sách thả xuống.
        binding.districtInput.setText(selected?.name.orEmpty(), false)
    }

    private fun renderAvatar(binding: FragmentGroupFormBinding, state: GroupFormState) {
        val source: Any? = state.avatarUri
            ?: CommunityUiText.avatarUrl(state.existingAvatarPath).takeIf { !state.removeAvatar }
        val image = binding.avatarImage
        if (source == null) {
            image.setImageResource(R.drawable.ic_add_photo_24)
            image.imageTintList = android.content.res.ColorStateList.valueOf(MaterialColors.getColor(image, com.google.android.material.R.attr.colorOnSurfaceVariant))
            image.setPadding(PLACEHOLDER_PADDING_PX.dp(), PLACEHOLDER_PADDING_PX.dp(), PLACEHOLDER_PADDING_PX.dp(), PLACEHOLDER_PADDING_PX.dp())
            image.contentDescription = getString(R.string.community_form_avatar_picker_cd)
        } else {
            image.imageTintList = null
            image.setPadding(0, 0, 0, 0)
            image.contentDescription = getString(R.string.community_form_avatar_preview_cd)
            image.load(source) { error(R.drawable.ic_add_photo_24) }
        }
        binding.avatarPickButton.setText(if (source == null) R.string.community_form_pick_avatar else R.string.community_form_change_avatar)
        binding.avatarRemoveButton.isVisible = source != null
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private fun nameErrorRes(error: FieldError): Int = when (error) {
        FieldError.REQUIRED -> R.string.community_form_name_required
        FieldError.TOO_SHORT -> R.string.community_form_name_too_short
        FieldError.TOO_LONG -> R.string.community_form_name_too_long
    }

    private fun confirmDeleteGroup() {
        val groupName = viewModel.state.value.originalName.ifBlank { viewModel.state.value.name }
        val context = requireContext()
        val inputLayout = TextInputLayout(context).apply {
            hint = getString(R.string.community_field_confirm_name)
            val pad = 20.dp()
            setPadding(pad, 8.dp(), pad, 0)
        }
        val editText = TextInputEditText(context).apply {
            minHeight = 48.dp()
            isSingleLine = true
        }
        inputLayout.addView(editText)
        val message = getString(R.string.community_confirm_delete_group_message) + "\n\n" +
            getString(R.string.community_delete_group_hint, groupName)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.community_confirm_delete_group_title)
            .setMessage(message)
            .setView(inputLayout)
            .setNegativeButton(R.string.community_action_cancel, null)
            .setPositiveButton(R.string.community_action_delete_group) { _, _ ->
                viewModel.deleteGroup(editText.text?.toString().orEmpty())
            }
            .show()
    }

    private fun handle(event: GroupFormEvent, binding: FragmentGroupFormBinding) {
        val nav = findNavController()
        when (event) {
            is GroupFormEvent.OpenGroup -> {
                if (nav.currentDestination?.id != R.id.groupFormFragment) return
                nav.navigate(GroupFormFragmentDirections.actionGroupFormToGroupDetail(event.groupId))
            }
            is GroupFormEvent.Saved -> nav.navigateUp()
            GroupFormEvent.Deleted -> {
                Snackbar.make(binding.root, R.string.community_msg_group_deleted, Snackbar.LENGTH_SHORT).show()
                if (!nav.popBackStack(R.id.communityHomeFragment, false)) {
                    nav.navigateUp()
                }
            }
            is GroupFormEvent.ShowError ->
                Snackbar.make(binding.root, CommunityUiText.errorRes(event.error), Snackbar.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val PLACEHOLDER_PADDING_PX = 24
    }
}

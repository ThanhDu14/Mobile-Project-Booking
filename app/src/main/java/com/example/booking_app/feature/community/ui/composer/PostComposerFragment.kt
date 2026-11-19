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
import com.example.booking_app.core.ui.EmptyStateSpec
import com.example.booking_app.core.ui.UiState
import com.example.booking_app.core.ui.render
import com.example.booking_app.databinding.FragmentPostComposerBinding
import com.example.booking_app.feature.community.ui.common.CommunityUiText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Màn hình soạn/sửa bài đăng (T049). Dùng Photo Picker (`PickMultipleVisualMedia(4)`), hỏi "Bỏ bài đang viết?" khi
 * thoát có nội dung.
 */
@AndroidEntryPoint
class PostComposerFragment : Fragment(R.layout.fragment_post_composer) {

    private val viewModel: PostComposerViewModel by viewModels()
    private var binding: FragmentPostComposerBinding? = null

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

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun render(
        binding: FragmentPostComposerBinding,
        photoAdapter: DraftPhotoAdapter,
        state: PostComposerState,
    ) {
        val coreState: UiState<Unit> = when {
            state.loadError != null -> UiState.Error(CommunityUiText.error(requireContext(), state.loadError), viewModel::loadPost)
            state.loading -> UiState.Loading
            else -> UiState.Content(Unit)
        }
        binding.stateViews.render(coreState, binding.content, EmptyStateSpec(message = R.string.community_error_post_not_found))

        binding.toolbar.setTitle(
            if (state.isEdit) R.string.community_title_composer_edit else R.string.community_title_composer_post,
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

        binding.contentInput.isEnabled = !state.saving
        binding.pickPhotosButton.isEnabled = !state.saving
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
    }
}

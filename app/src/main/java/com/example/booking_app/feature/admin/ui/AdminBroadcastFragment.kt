package com.example.booking_app.feature.admin.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentAdminBroadcastBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminBroadcastFragment : Fragment() {

    private var _binding: FragmentAdminBroadcastBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminToolsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAdminBroadcastBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.inputBroadcastTitle.doAfterTextChanged { text ->
            val str = text?.toString().orEmpty()
            binding.textPreviewTitle.text = if (str.isNotBlank()) str else getString(R.string.admin_broadcast_title_hint)
        }

        binding.inputBroadcastBody.doAfterTextChanged { text ->
            val str = text?.toString().orEmpty()
            binding.textPreviewBody.text = if (str.isNotBlank()) str else "Nội dung thông báo sẽ hiển thị ở đây."
        }

        binding.buttonSendBroadcast.setOnClickListener {
            confirmAndSendBroadcast()
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.broadcastActionState.collect { state ->
                    renderBroadcastAction(state)
                }
            }
        }
    }

    private fun renderBroadcastAction(state: ToolsActionUiState) {
        when (state) {
            ToolsActionUiState.Idle -> {
                binding.buttonSendBroadcast.isEnabled = true
                binding.progressBroadcastSending.isVisible = false
            }
            ToolsActionUiState.Loading -> {
                binding.buttonSendBroadcast.isEnabled = false
                binding.progressBroadcastSending.isVisible = true
            }
            is ToolsActionUiState.Success -> {
                binding.buttonSendBroadcast.isEnabled = true
                binding.progressBroadcastSending.isVisible = false
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                binding.inputBroadcastTitle.text?.clear()
                binding.inputBroadcastBody.text?.clear()
                viewModel.clearBroadcastActionState()
            }
            is ToolsActionUiState.Error -> {
                binding.buttonSendBroadcast.isEnabled = true
                binding.progressBroadcastSending.isVisible = false
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                viewModel.clearBroadcastActionState()
            }
        }
    }

    private fun confirmAndSendBroadcast() {
        val title = binding.inputBroadcastTitle.text?.toString().orEmpty()
        val body = binding.inputBroadcastBody.text?.toString().orEmpty()

        if (title.trim().isEmpty()) {
            binding.layoutBroadcastTitle.error = getString(R.string.admin_broadcast_title_hint)
            return
        }
        binding.layoutBroadcastTitle.error = null

        if (body.trim().isEmpty()) {
            binding.layoutBroadcastBody.error = getString(R.string.admin_broadcast_body_hint)
            return
        }
        binding.layoutBroadcastBody.error = null

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.admin_confirm_broadcast_title)
            .setMessage(R.string.admin_confirm_broadcast_message)
            .setPositiveButton(R.string.admin_broadcast_send_button) { _, _ ->
                viewModel.broadcastNotification(title, body)
            }
            .setNegativeButton(R.string.admin_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
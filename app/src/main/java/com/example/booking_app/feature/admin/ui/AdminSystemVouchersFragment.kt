package com.example.booking_app.feature.admin.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.booking_app.databinding.FragmentAdminSystemVouchersBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminSystemVouchersFragment : Fragment() {

    private var _binding: FragmentAdminSystemVouchersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminToolsViewModel by viewModels()
    private lateinit var adapter: AdminSystemVoucherAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAdminSystemVouchersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        adapter = AdminSystemVoucherAdapter { voucher, isChecked ->
            viewModel.toggleVoucher(voucher.code, isChecked)
        }

        binding.recyclerVouchers.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerVouchers.adapter = adapter

        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.vouchersState.collect { state ->
                        renderVouchers(state)
                    }
                }
                launch {
                    viewModel.voucherActionState.collect { state ->
                        renderAction(state)
                    }
                }
            }
        }
    }

    private fun renderVouchers(state: SystemVouchersUiState) {
        when (state) {
            SystemVouchersUiState.Loading -> {
                binding.progressVouchers.isVisible = true
                binding.textVouchersEmpty.isVisible = false
                binding.recyclerVouchers.isVisible = false
            }
            SystemVouchersUiState.Empty -> {
                binding.progressVouchers.isVisible = false
                binding.textVouchersEmpty.isVisible = true
                binding.recyclerVouchers.isVisible = false
            }
            is SystemVouchersUiState.Error -> {
                binding.progressVouchers.isVisible = false
                binding.textVouchersEmpty.isVisible = true
                binding.textVouchersEmpty.text = state.message
                binding.recyclerVouchers.isVisible = false
            }
            is SystemVouchersUiState.Content -> {
                binding.progressVouchers.isVisible = false
                binding.textVouchersEmpty.isVisible = false
                binding.recyclerVouchers.isVisible = true
                adapter.submitList(state.vouchers)
            }
        }
    }

    private fun renderAction(state: ToolsActionUiState) {
        when (state) {
            ToolsActionUiState.Idle -> Unit
            ToolsActionUiState.Loading -> Unit
            is ToolsActionUiState.Success -> {
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                viewModel.clearVoucherActionState()
            }
            is ToolsActionUiState.Error -> {
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                viewModel.clearVoucherActionState()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

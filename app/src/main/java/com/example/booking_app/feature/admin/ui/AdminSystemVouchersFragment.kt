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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentAdminSystemVouchersBinding
import com.example.booking_app.feature.admin.domain.model.AdminSystemVoucher
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

        // Tải danh sách voucher thực từ Firestore
        viewModel.loadVouchers()

        binding.inputVoucherCode.doAfterTextChanged { text ->
            val code = text?.toString().orEmpty().trim().uppercase()
            val currentVouchers = (viewModel.vouchersState.value as? SystemVouchersUiState.Content)?.vouchers.orEmpty()
            val isDuplicate = currentVouchers.any { it.code.equals(code, ignoreCase = true) }
            binding.textVoucherCodeError.isVisible = isDuplicate && code.isNotBlank()
            if (isDuplicate) {
                binding.layoutVoucherCode.error = "Mã đã tồn tại"
            } else {
                binding.layoutVoucherCode.error = null
            }
        }

        binding.buttonCreateVoucher.setOnClickListener {
            createVoucherFromForm()
        }

        observeViewModel()
    }

    private fun createVoucherFromForm() {
        val code = binding.inputVoucherCode.text?.toString()?.trim()?.uppercase().orEmpty()
        val discountStr = binding.inputVoucherDiscount.text?.toString()?.trim().orEmpty()
        val minOrderStr = binding.inputVoucherMinOrder.text?.toString()?.trim().orEmpty()

        if (code.isBlank()) {
            binding.layoutVoucherCode.error = "Vui lòng nhập mã"
            return
        }

        val discount = discountStr.toLongOrNull() ?: 30000L
        val minOrder = minOrderStr.toLongOrNull() ?: 200000L

        val newVoucher = AdminSystemVoucher(
            code = code,
            type = "FIXED",
            value = discount,
            maxDiscount = discount,
            minOrderTotal = minOrder,
            validFrom = "01/10",
            validTo = "31/12",
            usageLimit = 500,
            perUserLimit = 1,
            usedCount = 0,
            isActive = true,
        )

        viewModel.createVoucher(newVoucher)
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
                adapter.submitList(emptyList())
            }
            is SystemVouchersUiState.Error -> {
                binding.progressVouchers.isVisible = false
                binding.textVouchersEmpty.isVisible = true
                binding.textVouchersEmpty.text = state.message
                binding.recyclerVouchers.isVisible = false
                adapter.submitList(emptyList())
            }
            is SystemVouchersUiState.Content -> {
                binding.progressVouchers.isVisible = false
                binding.textVouchersEmpty.isVisible = state.vouchers.isEmpty()
                binding.recyclerVouchers.isVisible = state.vouchers.isNotEmpty()
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
                binding.inputVoucherCode.text?.clear()
                binding.inputVoucherDiscount.text?.clear()
                binding.inputVoucherMinOrder.text?.clear()
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

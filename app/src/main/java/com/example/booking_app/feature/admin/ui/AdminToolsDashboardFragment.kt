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
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentAdminToolsDashboardBinding
import com.example.booking_app.feature.admin.domain.model.AdminDistrict
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminToolsDashboardFragment : Fragment() {

    private var _binding: FragmentAdminToolsDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminToolsViewModel by viewModels()
    private lateinit var categoryAdapter: AdminCategoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAdminToolsDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cardNavBroadcast.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_broadcast)
        }

        binding.cardNavVouchers.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_vouchers)
        }

        categoryAdapter = AdminCategoryAdapter { item ->
            showEditCategoryDialog(item)
        }

        binding.recyclerCategories.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerCategories.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        binding.recyclerCategories.adapter = categoryAdapter

        // Tải dữ liệu thực từ Firestore
        viewModel.loadDistricts()
        viewModel.loadVouchers()

        binding.chipGroupCategoryTabs.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.contains(R.id.chipCategoryAmenities)) {
                binding.inputAddCategory.hint = getString(R.string.admin_tools_category_add_amenity_hint)
            } else {
                binding.inputAddCategory.hint = getString(R.string.admin_tools_category_add_hint)
            }
        }

        binding.buttonAddCategory.setOnClickListener {
            val name = binding.inputAddCategory.text?.toString()?.trim().orEmpty()
            if (name.isNotBlank()) {
                val newDistrict = AdminDistrict(
                    id = "cat_${System.currentTimeMillis()}",
                    name = name,
                    sortOrder = 1,
                )
                viewModel.saveDistrict(newDistrict)
                binding.inputAddCategory.text?.clear()
                Toast.makeText(requireContext(), "Đang thêm $name...", Toast.LENGTH_SHORT).show()
            }
        }

        observeViewModel()
    }

    private fun showEditCategoryDialog(item: AdminDistrict) {
        val input = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            setText(item.name)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Chỉnh sửa danh mục")
            .setView(input)
            .setPositiveButton("Lưu") { _, _ ->
                val newName = input.text?.toString()?.trim().orEmpty()
                if (newName.isNotBlank()) {
                    viewModel.saveDistrict(item.copy(name = newName))
                }
            }
            .setNegativeButton(R.string.admin_cancel, null)
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.vouchersState.collect { state ->
                        if (state is SystemVouchersUiState.Content) {
                            val activeCount = state.vouchers.count { it.isActive }
                            binding.textToolsVoucherSub.text = getString(R.string.admin_tools_vouchers_desc, activeCount)
                        }
                    }
                }
                launch {
                    viewModel.districtsState.collect { state ->
                        when (state) {
                            DistrictsUiState.Loading -> {
                                binding.progressCategories.isVisible = true
                            }
                            is DistrictsUiState.Content -> {
                                binding.progressCategories.isVisible = false
                                categoryAdapter.submitList(state.districts)
                            }
                            else -> {
                                binding.progressCategories.isVisible = false
                            }
                        }
                    }
                }
                launch {
                    viewModel.districtActionState.collect { state ->
                        if (state is ToolsActionUiState.Success) {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                            viewModel.clearDistrictActionState()
                            viewModel.loadDistricts()
                        } else if (state is ToolsActionUiState.Error) {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                            viewModel.clearDistrictActionState()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

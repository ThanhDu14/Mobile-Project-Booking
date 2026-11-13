package com.example.booking_app.feature.admin.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemAdminCategoryBinding
import com.example.booking_app.feature.admin.domain.model.AdminDistrict

class AdminCategoryAdapter(
    private val onEditClick: (AdminDistrict) -> Unit,
) : ListAdapter<AdminDistrict, AdminCategoryAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdminCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding, onEditClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemAdminCategoryBinding,
        private val onEditClick: (AdminDistrict) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(item: AdminDistrict) {
            binding.textCategoryName.text = item.name
            binding.textCategoryVenueCount.text = ""

            binding.imageEditCategory.setOnClickListener {
                onEditClick(item)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<AdminDistrict>() {
        override fun areItemsTheSame(oldItem: AdminDistrict, newItem: AdminDistrict): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AdminDistrict, newItem: AdminDistrict): Boolean {
            return oldItem == newItem
        }
    }
}
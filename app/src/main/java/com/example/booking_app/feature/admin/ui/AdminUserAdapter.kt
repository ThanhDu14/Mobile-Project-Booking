package com.example.booking_app.feature.admin.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemAdminUserBinding
import com.example.booking_app.feature.admin.domain.model.AdminUserLookup

class AdminUserAdapter(
    private val onItemClick: (AdminUserLookup) -> Unit,
) : ListAdapter<AdminUserLookup, AdminUserAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdminUserBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemAdminUserBinding,
        private val onItemClick: (AdminUserLookup) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(item: AdminUserLookup) {
            val name = item.fullName.ifBlank { "Người dùng" }
            binding.textUserName.text = name

            val initials = name.split(" ")
                .filter { it.isNotBlank() }
                .takeLast(2)
                .map { it.first() }
                .joinToString("")
                .uppercase()
            binding.textUserAvatar.text = if (initials.isNotBlank()) initials else "ND"

            binding.textUserContact.text = item.phone.ifBlank { "0912 334 551" }

            when {
                item.isLocked -> {
                    binding.textUserTag.isVisible = true
                    binding.textUserTag.setBackgroundResource(R.drawable.bg_admin_chip_rejected)
                    binding.textUserTag.setTextColor(Color.parseColor("#991B1B"))
                    binding.textUserTag.text = binding.root.context.getString(R.string.admin_user_tag_locked)
                }
                item.isOwnerOrApproved -> {
                    binding.textUserTag.isVisible = true
                    binding.textUserTag.setBackgroundResource(R.drawable.bg_admin_chip_approved)
                    binding.textUserTag.setTextColor(Color.parseColor("#065F46"))
                    binding.textUserTag.text = binding.root.context.getString(R.string.admin_user_tag_owner)
                }
                else -> {
                    binding.textUserTag.isVisible = false
                }
            }

            binding.layoutUserItem.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<AdminUserLookup>() {
        override fun areItemsTheSame(oldItem: AdminUserLookup, newItem: AdminUserLookup): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: AdminUserLookup, newItem: AdminUserLookup): Boolean {
            return oldItem == newItem
        }
    }
}
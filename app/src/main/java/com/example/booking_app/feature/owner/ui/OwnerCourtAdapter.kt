package com.example.booking_app.feature.owner.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemOwnerCourtBinding
import com.example.booking_app.feature.owner.domain.model.OwnerCourt

class OwnerCourtAdapter(
    private val onEdit: (OwnerCourt) -> Unit,
    private val onDeactivate: (OwnerCourt) -> Unit,
    private val onMove: (OwnerCourt, Int) -> Unit
) : ListAdapter<OwnerCourt, OwnerCourtAdapter.Holder>(DIFF) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemOwnerCourtBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
    inner class Holder(private val binding: ItemOwnerCourtBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: OwnerCourt) {
            val context = binding.root.context
            binding.name.text = item.name
            binding.surface.text = item.surfaceType
            binding.active.setText(if (item.isActive) R.string.owner_status_active else R.string.owner_status_inactive)
            binding.active.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, if (item.isActive) R.color.sn_court_container else R.color.sn_surface_sunken)
            )
            binding.active.setTextColor(
                ContextCompat.getColor(context, if (item.isActive) R.color.sn_on_court_container else R.color.sn_ink_muted)
            )
            binding.up.setOnClickListener { onMove(item, -1) }
            binding.down.setOnClickListener { onMove(item, 1) }
            binding.more.setOnClickListener { anchor ->
                PopupMenu(context, anchor).apply {
                    menu.add(0, MENU_EDIT, 0, R.string.edit)
                    menu.add(0, MENU_TOGGLE, 1, if (item.isActive) R.string.owner_court_deactivate else R.string.owner_court_activate)
                    setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            MENU_EDIT -> onEdit(item)
                            MENU_TOGGLE -> onDeactivate(item)
                        }
                        true
                    }
                }.show()
            }
        }
    }
    companion object {
        private const val MENU_EDIT = 1
        private const val MENU_TOGGLE = 2
        private val DIFF = object : DiffUtil.ItemCallback<OwnerCourt>() {
            override fun areItemsTheSame(a: OwnerCourt, b: OwnerCourt) = a.id == b.id
            override fun areContentsTheSame(a: OwnerCourt, b: OwnerCourt) = a == b
        }
    }
}

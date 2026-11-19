package com.example.booking_app.feature.community.ui.members

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import com.example.booking_app.R
import com.example.booking_app.core.media.StorageUrls
import com.example.booking_app.databinding.ItemGroupMemberBinding
import com.example.booking_app.feature.community.domain.model.GroupMember
import com.example.booking_app.feature.community.domain.model.GroupRole

interface MemberActions {
    fun onMakeAdmin(member: GroupMember)
    fun onRemoveAdmin(member: GroupMember)
    fun onTransferOwner(member: GroupMember)
    fun onRemoveMember(member: GroupMember)
}

/** Danh sách thành viên (T062): ảnh, tên, vai trò; menu thao tác theo quyền hoặc chọn trực tiếp để chuyển quyền. */
class GroupMemberAdapter(
    private val actions: MemberActions,
) : ListAdapter<MemberItem, GroupMemberAdapter.ViewHolder>(Diff) {

    var selectTransferTarget: Boolean = false
    var actionsEnabled: Boolean = true

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemGroupMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemGroupMemberBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MemberItem) {
            val context = binding.root.context
            val member = item.member
            val name = member.displayName.ifBlank { context.getString(R.string.community_join_request_default_name) }

            binding.memberName.text = name
            binding.memberRole.setText(roleRes(member.role))
            binding.memberAvatar.contentDescription = context.getString(R.string.community_member_avatar_cd, name)
            binding.memberAvatar.load(
                member.avatarPath?.takeIf { it.isNotBlank() }?.let { StorageUrls.publicUrl("avatars", it) },
            ) {
                placeholder(R.drawable.ic_person_outline_24)
                error(R.drawable.ic_person_outline_24)
            }

            if (selectTransferTarget) {
                binding.moreButton.isVisible = false
                binding.root.isClickable = item.canTransferOwner && actionsEnabled
                binding.root.setOnClickListener {
                    if (item.canTransferOwner && actionsEnabled) actions.onTransferOwner(member)
                }
            } else {
                binding.root.isClickable = false
                binding.root.setOnClickListener(null)
                binding.moreButton.isVisible = item.hasActions
                binding.moreButton.isEnabled = actionsEnabled
                binding.moreButton.contentDescription = context.getString(R.string.community_member_more_cd, name)
                binding.moreButton.setOnClickListener { showMenu(it, item) }
            }
        }
    }

    private fun showMenu(anchor: View, item: MemberItem) {
        val popup = PopupMenu(anchor.context, anchor)
        if (item.canMakeAdmin) popup.menu.add(0, MENU_MAKE_ADMIN, 0, R.string.community_action_make_admin)
        if (item.canRemoveAdmin) popup.menu.add(0, MENU_REMOVE_ADMIN, 1, R.string.community_action_remove_admin)
        if (item.canTransferOwner) popup.menu.add(0, MENU_TRANSFER, 2, R.string.community_action_transfer_owner)
        if (item.canRemove) popup.menu.add(0, MENU_REMOVE, 3, R.string.community_action_remove_member)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                MENU_MAKE_ADMIN -> actions.onMakeAdmin(item.member)
                MENU_REMOVE_ADMIN -> actions.onRemoveAdmin(item.member)
                MENU_TRANSFER -> actions.onTransferOwner(item.member)
                MENU_REMOVE -> actions.onRemoveMember(item.member)
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popup.show()
    }

    private fun roleRes(role: GroupRole): Int = when (role) {
        GroupRole.OWNER -> R.string.community_role_owner
        GroupRole.ADMIN -> R.string.community_role_admin
        GroupRole.MEMBER -> R.string.community_role_member
    }

    private object Diff : DiffUtil.ItemCallback<MemberItem>() {
        override fun areItemsTheSame(oldItem: MemberItem, newItem: MemberItem) =
            oldItem.member.userId == newItem.member.userId

        override fun areContentsTheSame(oldItem: MemberItem, newItem: MemberItem) = oldItem == newItem
    }

    private companion object {
        const val MENU_MAKE_ADMIN = 1
        const val MENU_REMOVE_ADMIN = 2
        const val MENU_TRANSFER = 3
        const val MENU_REMOVE = 4
    }
}

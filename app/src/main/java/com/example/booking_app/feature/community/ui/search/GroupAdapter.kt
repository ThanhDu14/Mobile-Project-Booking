package com.example.booking_app.feature.community.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import com.example.booking_app.R
import com.example.booking_app.databinding.ItemGroupBinding
import com.example.booking_app.feature.community.domain.model.GroupVisibility
import com.example.booking_app.feature.community.ui.common.CommunityUiText
import com.example.booking_app.feature.community.ui.home.GroupCard

/**
 * Thẻ nhóm (T028): ảnh đại diện (bucket public `group-avatars` hoặc ảnh mặc định), tên, chế độ, số thành viên,
 * nhãn chủ nhóm/yêu cầu chờ duyệt, nhãn nhóm tạm khóa. Dùng lại cho tab "Khám phá" ở US2.
 */
class GroupAdapter(
    private val onClick: (GroupCard) -> Unit,
) : ListAdapter<GroupCard, GroupAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemGroupBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(card: GroupCard) {
            val context = binding.root.context
            val group = card.group
            binding.groupName.text = group.name

            val members = context.resources.getQuantityString(R.plurals.community_member_count, group.memberCount, group.memberCount)
            val visibility = context.getString(CommunityUiText.visibilityLabel(group.visibility))
            val meta = context.getString(R.string.community_group_meta, visibility, members)
            binding.groupMeta.text = meta
            binding.lockIcon.isVisible = group.visibility == GroupVisibility.PRIVATE

            val extra = when {
                card.isLocked -> context.getString(R.string.community_label_group_locked)
                card.isOwner && group.pendingRequestCount > 0 ->
                    context.getString(R.string.community_group_owner_pending, group.pendingRequestCount)
                card.isOwner -> context.getString(R.string.community_group_owner)
                else -> null
            }
            val extraLine = listOfNotNull(card.districtName, extra).joinToString(" · ").ifEmpty { null }
            binding.groupExtra.isVisible = extraLine != null
            binding.groupExtra.text = extraLine

            binding.groupAvatar.load(CommunityUiText.avatarUrl(group.avatarPath)) {
                placeholder(R.drawable.ic_groups_24)
                error(R.drawable.ic_groups_24)
            }
            binding.root.contentDescription = context.getString(
                if (card.isLocked) R.string.community_group_item_cd_locked else R.string.community_group_item_cd,
                group.name,
                meta,
            )
            binding.root.setOnClickListener { onClick(card) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<GroupCard>() {
        override fun areItemsTheSame(oldItem: GroupCard, newItem: GroupCard) = oldItem.group.id == newItem.group.id
        override fun areContentsTheSame(oldItem: GroupCard, newItem: GroupCard) = oldItem == newItem
    }
}

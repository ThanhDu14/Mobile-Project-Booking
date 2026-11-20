package com.example.booking_app.feature.community.ui.members

import android.text.format.DateUtils
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
import com.example.booking_app.core.media.StorageUrls
import com.example.booking_app.databinding.ItemJoinRequestBinding
import com.example.booking_app.feature.community.domain.model.JoinRequest

/** Danh sách yêu cầu tham gia (T039): tên, ảnh (bucket `avatars`), thời gian gửi, nhãn "Từng bị mời ra", Duyệt/Từ chối. */
class JoinRequestAdapter(
    private val onApprove: (JoinRequest) -> Unit,
    private val onReject: (JoinRequest) -> Unit,
) : ListAdapter<JoinRequest, JoinRequestAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemJoinRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemJoinRequestBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(request: JoinRequest) {
            val context = binding.root.context
            val name = request.displayName.ifBlank { context.getString(R.string.community_join_request_default_name) }
            binding.requestName.text = name
            binding.requestAvatar.contentDescription = context.getString(R.string.community_join_request_avatar_cd, name)
            binding.requestAvatar.load(request.avatarPath?.takeIf { it.isNotBlank() }?.let { StorageUrls.publicUrl(AVATAR_BUCKET, it) }) {
                placeholder(R.drawable.ic_person_outline_24)
                error(R.drawable.ic_person_outline_24)
            }
            val sent = request.createdAt?.let {
                context.getString(
                    R.string.community_join_requests_sent,
                    DateUtils.getRelativeTimeSpanString(it.toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
                )
            }
            binding.requestTime.isVisible = sent != null
            binding.requestTime.text = sent
            binding.requestRemovedLabel.isVisible = request.wasRemoved
            binding.root.contentDescription = null
            binding.approveButton.contentDescription = context.getString(R.string.community_join_request_approve_cd, name)
            binding.rejectButton.contentDescription = context.getString(R.string.community_join_request_reject_cd, name)
            binding.approveButton.setOnClickListener { onApprove(request) }
            binding.rejectButton.setOnClickListener { onReject(request) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<JoinRequest>() {
        override fun areItemsTheSame(oldItem: JoinRequest, newItem: JoinRequest) = oldItem.userId == newItem.userId
        override fun areContentsTheSame(oldItem: JoinRequest, newItem: JoinRequest) = oldItem == newItem
    }

    private companion object {
        const val AVATAR_BUCKET = "avatars"
    }
}

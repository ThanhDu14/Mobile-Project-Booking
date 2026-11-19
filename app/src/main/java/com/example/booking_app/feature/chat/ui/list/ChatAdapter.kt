package com.example.booking_app.feature.chat.ui.list

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.dispose
import coil3.load
import coil3.request.error
import com.example.booking_app.R
import com.example.booking_app.core.session.initialsOf
import com.example.booking_app.databinding.ItemChatBinding
import com.example.booking_app.feature.chat.domain.model.ChatMode
import com.example.booking_app.feature.chat.domain.model.ChatSummary
import com.example.booking_app.feature.chat.domain.model.ChatType
import com.example.booking_app.feature.chat.domain.model.MessageType
import com.example.booking_app.feature.chat.ui.common.ChatUiText

/** Adapter danh sách cuộc trò chuyện (`ListAdapter` + `DiffUtil`, T030, T038). */
class ChatAdapter(
    private val mode: ChatMode,
    private val currentUidProvider: () -> String?,
    private val onChatClick: (ChatSummary) -> Unit,
) : ListAdapter<ChatSummary, ChatAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class Holder(private val b: ItemChatBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: ChatSummary) {
            val ctx = b.root.context
            val rawName = when {
                item.type == ChatType.GROUP -> item.title.ifBlank { ctx.getString(R.string.chat_default_group_name) }
                item.isReadOnly && item.title.isBlank() -> ctx.getString(R.string.chat_deleted_user)
                else -> item.title.ifBlank { ctx.getString(R.string.chat_default_user_name) }
            }
            val displayTitle = if (item.type == ChatType.DIRECT && mode == ChatMode.PLAYER && !item.isReadOnly) {
                ctx.getString(R.string.chat_role_owner_suffix, rawName)
            } else {
                rawName
            }
            b.chatTitle.text = displayTitle
            val isDeletedDirect = item.type == ChatType.DIRECT && item.isReadOnly && item.title.isBlank()
            b.avatarImage.contentDescription = ctx.getString(R.string.chat_cd_avatar, rawName)
            if (isDeletedDirect) {
                b.avatarInitials.isVisible = false
                b.avatarImage.isVisible = true
                b.avatarImage.dispose()
                b.avatarImage.setImageResource(R.drawable.ic_account_circle_24)
            } else {
                val bucket = item.avatarBucket ?: if (item.type == ChatType.GROUP) "group-avatars" else "avatars"
                val avatarUrl = ChatUiText.publicImageUrl(bucket, item.avatarPath)
                b.avatarInitials.text = initialsOf(rawName).ifEmpty { "?" }
                b.avatarInitials.isVisible = avatarUrl == null
                b.avatarImage.isVisible = avatarUrl != null
                if (avatarUrl != null) {
                    b.avatarImage.load(avatarUrl) {
                        error(if (item.type == ChatType.GROUP) R.drawable.ic_groups_24 else R.drawable.ic_account_circle_24)
                    }
                } else {
                    b.avatarImage.dispose()
                }
            }

            // Cơ sở được hỏi gần nhất cho chat riêng
            val showVenue = item.type == ChatType.DIRECT && !item.venueName.isNullOrBlank()
            b.venueSubtitle.isVisible = showVenue
            if (showVenue) {
                b.venueSubtitle.text = if (mode == ChatMode.OWNER) {
                    ctx.getString(R.string.chat_venue_prefix, item.venueName)
                } else {
                    item.venueName
                }
            }

            // Tin nhắn cuối
            val basePreview = when {
                item.lastMessageType == MessageType.IMAGE -> ctx.getString(R.string.chat_preview_image)
                !item.lastMessagePreview.isNullOrBlank() -> item.lastMessagePreview
                else -> ""
            }
            val currentUid = currentUidProvider()
            val recalledText = ctx.getString(R.string.chat_message_recalled)
            val preview = if (
                basePreview.isNotEmpty() &&
                basePreview != recalledText &&
                item.lastMessageSenderId != null &&
                item.lastMessageSenderId == currentUid
            ) {
                ctx.getString(R.string.chat_preview_mine, basePreview)
            } else {
                basePreview
            }
            b.lastMessagePreview.text = preview
            b.lastMessagePreview.setTypeface(null, if (item.unreadCount > 0) Typeface.BOLD else Typeface.NORMAL)

            // Thời gian, chuông tắt, nhãn chỉ đọc / đã chặn, số chưa đọc
            b.chatTime.text = ChatUiText.listTime(ctx, item.updatedAt)
            b.mutedIcon.isVisible = item.muted
            b.readOnlyBadge.isVisible = item.isReadOnly
            b.blockedBadge.isVisible = !item.isReadOnly && item.blockedOther

            val badgeText = ChatUiText.unreadBadge(item.unreadCount)
            b.unreadBadge.isVisible = badgeText != null
            if (badgeText != null) b.unreadBadge.text = badgeText

            b.root.setOnClickListener { onChatClick(item) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<ChatSummary>() {
            override fun areItemsTheSame(oldItem: ChatSummary, newItem: ChatSummary): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ChatSummary, newItem: ChatSummary): Boolean =
                oldItem == newItem
        }
    }
}

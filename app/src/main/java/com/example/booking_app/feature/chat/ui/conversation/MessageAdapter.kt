package com.example.booking_app.feature.chat.ui.conversation

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
import coil3.request.placeholder
import com.example.booking_app.R
import com.example.booking_app.core.session.initialsOf
import com.example.booking_app.databinding.ItemMessageMineBinding
import com.example.booking_app.databinding.ItemMessageOtherBinding
import com.example.booking_app.feature.chat.domain.model.ChatMessage
import com.example.booking_app.feature.chat.domain.model.Delivery
import com.example.booking_app.feature.chat.domain.model.MessageStatus
import com.example.booking_app.feature.chat.ui.common.ChatUiText
import com.google.android.material.color.MaterialColors

/**
 * Danh sách tin nhắn trong cuộc trò chuyện (`ListAdapter` + `DiffUtil`, T029, T035, T046, T052).
 */
class MessageAdapter(
    private val onRetryClick: (ChatMessage) -> Unit,
    private val onDeleteClick: (ChatMessage) -> Unit,
    private val onPhotoClick: (ChatMessage) -> Unit,
    private val onMessageLongClick: (ChatMessage) -> Unit,
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(DIFF) {

    var isGroupChat: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    var imageUrls: Map<String, String> = emptyMap()
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).isMine) TYPE_MINE else TYPE_OTHER

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_MINE) {
            MineHolder(ItemMessageMineBinding.inflate(inflater, parent, false))
        } else {
            OtherHolder(ItemMessageOtherBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is MineHolder -> holder.bind(item)
            is OtherHolder -> holder.bind(item)
        }
    }

    inner class MineHolder(private val b: ItemMessageMineBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(msg: ChatMessage) {
            val ctx = b.root.context
            val isSpecial = msg.status == MessageStatus.RECALLED || msg.status == MessageStatus.HIDDEN
            b.bubbleContainer.setBackgroundResource(
                if (isSpecial) R.drawable.bg_chat_bubble_special else R.drawable.bg_chat_bubble_mine,
            )
            b.bubbleContainer.alpha = if (msg.delivery == Delivery.SENT) 1f else 0.72f

            // Ảnh (chỉ hiển thị với tin NORMAL; tin RECALLED/HIDDEN không hiện ảnh).
            val photoSource = if (msg.status == MessageStatus.NORMAL) {
                msg.imagePath?.let { imageUrls[it] } ?: msg.localImageUri
            } else {
                null
            }
            val hasPhoto = msg.status == MessageStatus.NORMAL && (!msg.imagePath.isNullOrBlank() || !msg.localImageUri.isNullOrBlank())
            b.messageImage.isVisible = hasPhoto
            if (hasPhoto) {
                b.messageImage.load(photoSource) {
                    placeholder(R.drawable.bg_venue_placeholder)
                    error(R.drawable.bg_venue_placeholder)
                }
                b.messageImage.setOnClickListener { onPhotoClick(msg) }
                b.messageImage.setOnLongClickListener {
                    onMessageLongClick(msg)
                    true
                }
            } else {
                b.messageImage.setOnClickListener(null)
                b.messageImage.setOnLongClickListener(null)
            }

            // Nội dung chữ
            val displayText = when (msg.status) {
                MessageStatus.RECALLED -> ctx.getString(R.string.chat_message_recalled)
                MessageStatus.HIDDEN -> ctx.getString(R.string.chat_message_hidden)
                MessageStatus.NORMAL -> msg.text
            }
            b.messageText.isVisible = displayText.isNotEmpty()
            b.messageText.text = displayText
            b.messageText.setTypeface(null, if (isSpecial) Typeface.ITALIC else Typeface.NORMAL)
            b.messageText.setTextColor(
                MaterialColors.getColor(
                    b.messageText,
                    if (isSpecial) {
                        com.google.android.material.R.attr.colorOnSurfaceVariant
                    } else {
                        com.google.android.material.R.attr.colorOnPrimaryContainer
                    },
                ),
            )

            // Thời gian + trạng thái gửi
            b.messageTime.text = ChatUiText.messageTime(msg.createdAt)
            when (msg.delivery) {
                Delivery.SENT -> {
                    b.deliveryStatus.isVisible = false
                    b.retryAction.isVisible = false
                    b.deleteAction.isVisible = false
                }
                Delivery.PENDING -> {
                    b.deliveryStatus.isVisible = true
                    b.deliveryStatus.setText(R.string.chat_delivery_pending)
                    b.retryAction.isVisible = false
                    b.deleteAction.isVisible = false
                }
                Delivery.FAILED -> {
                    b.deliveryStatus.isVisible = true
                    b.deliveryStatus.setText(R.string.chat_delivery_failed)
                    b.retryAction.isVisible = true
                    b.deleteAction.isVisible = true
                    b.retryAction.setOnClickListener { onRetryClick(msg) }
                    b.deleteAction.setOnClickListener { onDeleteClick(msg) }
                }
            }

            b.bubbleContainer.setOnLongClickListener {
                onMessageLongClick(msg)
                true
            }
        }
    }

    inner class OtherHolder(private val b: ItemMessageOtherBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(msg: ChatMessage) {
            val ctx = b.root.context
            val isSpecial = msg.status == MessageStatus.RECALLED || msg.status == MessageStatus.HIDDEN

            // Trong chat nhóm hiển thị tên và ảnh đại diện người gửi (T035).
            b.avatarContainer.isVisible = isGroupChat
            b.senderName.isVisible = isGroupChat
            if (isGroupChat) {
                val displayName = if (msg.senderDeleted) {
                    ctx.getString(R.string.chat_deleted_user)
                } else {
                    msg.senderName?.takeIf { it.isNotBlank() } ?: ctx.getString(R.string.chat_default_user_name)
                }
                b.senderName.text = displayName
                val avatarCd = ctx.getString(R.string.chat_cd_avatar, displayName)
                b.senderAvatar.contentDescription = avatarCd
                b.senderAvatarInitials.contentDescription = avatarCd
                if (msg.senderDeleted) {
                    b.senderAvatarInitials.isVisible = false
                    b.senderAvatar.isVisible = true
                    b.senderAvatar.dispose()
                    b.senderAvatar.setImageResource(R.drawable.ic_account_circle_24)
                } else {
                    val avatarUrl = ChatUiText.publicImageUrl("avatars", msg.senderAvatarPath)
                    b.senderAvatarInitials.text = initialsOf(displayName).ifEmpty { "?" }
                    b.senderAvatarInitials.isVisible = avatarUrl == null
                    b.senderAvatar.isVisible = avatarUrl != null
                    if (avatarUrl != null) {
                        b.senderAvatar.load(avatarUrl) {
                            error(R.drawable.ic_account_circle_24)
                        }
                    } else {
                        b.senderAvatar.dispose()
                    }
                }
            }

            b.bubbleContainer.setBackgroundResource(
                if (isSpecial) R.drawable.bg_chat_bubble_special else R.drawable.bg_chat_bubble_other,
            )

            // Ảnh
            val photoSource = if (msg.status == MessageStatus.NORMAL) {
                msg.imagePath?.let { imageUrls[it] }
            } else {
                null
            }
            val hasPhoto = msg.status == MessageStatus.NORMAL && !msg.imagePath.isNullOrBlank()
            b.messageImage.isVisible = hasPhoto
            if (hasPhoto) {
                b.messageImage.load(photoSource) {
                    placeholder(R.drawable.bg_venue_placeholder)
                    error(R.drawable.bg_venue_placeholder)
                }
                b.messageImage.setOnClickListener { onPhotoClick(msg) }
                b.messageImage.setOnLongClickListener {
                    onMessageLongClick(msg)
                    true
                }
            } else {
                b.messageImage.setOnClickListener(null)
                b.messageImage.setOnLongClickListener(null)
            }

            // Nội dung chữ
            val displayText = when (msg.status) {
                MessageStatus.RECALLED -> ctx.getString(R.string.chat_message_recalled)
                MessageStatus.HIDDEN -> ctx.getString(R.string.chat_message_hidden)
                MessageStatus.NORMAL -> msg.text
            }
            b.messageText.isVisible = displayText.isNotEmpty()
            b.messageText.text = displayText
            b.messageText.setTypeface(null, if (isSpecial) Typeface.ITALIC else Typeface.NORMAL)
            b.messageText.setTextColor(
                MaterialColors.getColor(
                    b.messageText,
                    if (isSpecial) {
                        com.google.android.material.R.attr.colorOnSurfaceVariant
                    } else {
                        com.google.android.material.R.attr.colorOnSurface
                    },
                ),
            )

            b.messageTime.text = ChatUiText.messageTime(msg.createdAt)
            b.bubbleContainer.setOnLongClickListener {
                onMessageLongClick(msg)
                true
            }
        }
    }

    private companion object {
        const val TYPE_MINE = 1
        const val TYPE_OTHER = 2

        val DIFF = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
                oldItem == newItem
        }
    }
}

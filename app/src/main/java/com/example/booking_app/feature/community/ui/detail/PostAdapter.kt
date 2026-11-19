package com.example.booking_app.feature.community.ui.detail

import android.text.format.DateUtils
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
import com.example.booking_app.databinding.ItemPostBinding
import com.example.booking_app.feature.community.domain.model.HiddenBy
import com.google.android.material.imageview.ShapeableImageView

/** Hành động trên một bài đăng (T050; US4/US6 sẽ bổ sung sự kiện và kiểm duyệt/báo cáo). */
interface PostActions {
    fun onPhotoClick(urls: List<String>, startIndex: Int)
    fun onEditPost(postId: String)
    fun onDeletePost(postId: String)
    fun onHidePost(postId: String) {}
    fun onUnhidePost(postId: String) {}
    fun onReportPost(postId: String) {}
}

/** Bảng tin nhóm (T050): `ListAdapter` + `DiffUtil`, ảnh dùng signed URL từ `SignedUrlCache`. */
class PostAdapter(
    private val actions: PostActions,
) : ListAdapter<PostItem, RecyclerView.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        PostViewHolder(ItemPostBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is PostViewHolder -> holder.bind(getItem(position))
        }
    }

    inner class PostViewHolder(private val binding: ItemPostBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PostItem) {
            val context = binding.root.context
            val post = item.post

            val authorName = when {
                item.isAuthorDeleted -> context.getString(R.string.community_label_deleted_user)
                !post.author.displayName.isNullOrBlank() -> post.author.displayName
                else -> context.getString(R.string.community_join_request_default_name)
            }
            binding.authorName.text = authorName
            binding.authorAvatar.contentDescription = context.getString(R.string.community_post_author_avatar_cd, authorName)
            val avatarUrl = if (item.isAuthorDeleted) {
                null
            } else {
                post.author.avatarPath?.takeIf { it.isNotBlank() }?.let { StorageUrls.publicUrl(AVATAR_BUCKET, it) }
            }
            binding.authorAvatar.load(avatarUrl) {
                placeholder(R.drawable.ic_person_outline_24)
                error(R.drawable.ic_person_outline_24)
            }

            val timeText = post.createdAt?.let {
                DateUtils.getRelativeTimeSpanString(it.toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
            } ?: context.getString(R.string.community_post_just_now)
            binding.postMeta.text = if (post.isEdited) {
                context.getString(R.string.community_author_time, timeText, context.getString(R.string.community_label_edited))
            } else {
                timeText
            }

            binding.hiddenBadge.isVisible = item.showHiddenBadge
            if (item.showHiddenBadge) {
                binding.hiddenBadge.setText(
                    if (post.hiddenBy == HiddenBy.SYSTEM) R.string.community_label_hidden_by_system else R.string.community_label_hidden,
                )
            }

            binding.postContent.isVisible = post.content.isNotBlank()
            binding.postContent.text = post.content

            bindPhotos(item)

            binding.moreButton.isVisible = item.hasMenuActions
            binding.moreButton.setOnClickListener { showMenu(it, item) }
        }

        private fun bindPhotos(item: PostItem) {
            val paths = item.post.photoPaths
            val context = binding.root.context
            if (paths.isEmpty()) {
                binding.photoContainer.isVisible = false
                return
            }
            binding.photoContainer.isVisible = true
            binding.photoRow2.isVisible = paths.size > 2

            val slots: List<ShapeableImageView> = listOf(binding.photo0, binding.photo1, binding.photo2, binding.photo3)
            val resolvedList = paths.mapNotNull { item.photoUrls[it] }

            slots.forEachIndexed { index, view ->
                val path = paths.getOrNull(index)
                view.isVisible = path != null
                if (path != null) {
                    val url = item.photoUrls[path]
                    view.contentDescription = context.getString(R.string.community_post_photo_cd, index + 1, paths.size)
                    view.load(url) {
                        placeholder(R.drawable.ic_photo_library_24)
                        error(R.drawable.ic_photo_library_24)
                    }
                    view.setOnClickListener {
                        if (resolvedList.isEmpty()) return@setOnClickListener
                        val startIndex = url?.let { resolvedList.indexOf(it).coerceAtLeast(0) } ?: 0
                        actions.onPhotoClick(resolvedList, startIndex)
                    }
                } else {
                    view.setOnClickListener(null)
                }
            }
        }
    }

    private fun showMenu(anchor: View, item: PostItem) {
        val popup = PopupMenu(anchor.context, anchor)
        if (item.canEdit) popup.menu.add(0, MENU_EDIT, 0, R.string.community_action_edit)
        if (item.canDelete) popup.menu.add(0, MENU_DELETE, 1, R.string.community_action_delete)
        if (item.canHide) popup.menu.add(0, MENU_HIDE, 2, R.string.community_action_hide)
        if (item.canUnhide) popup.menu.add(0, MENU_UNHIDE, 3, R.string.community_action_unhide)
        if (item.canReport) popup.menu.add(0, MENU_REPORT, 4, R.string.community_action_report)
        popup.setOnMenuItemClickListener { menuItem ->
            val id = item.post.id
            when (menuItem.itemId) {
                MENU_EDIT -> actions.onEditPost(id)
                MENU_DELETE -> actions.onDeletePost(id)
                MENU_HIDE -> actions.onHidePost(id)
                MENU_UNHIDE -> actions.onUnhidePost(id)
                MENU_REPORT -> actions.onReportPost(id)
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popup.show()
    }

    private object Diff : DiffUtil.ItemCallback<PostItem>() {
        override fun areItemsTheSame(oldItem: PostItem, newItem: PostItem) = oldItem.post.id == newItem.post.id
        override fun areContentsTheSame(oldItem: PostItem, newItem: PostItem) = oldItem == newItem
    }

    private companion object {
        const val AVATAR_BUCKET = "avatars"
        const val MENU_EDIT = 1
        const val MENU_DELETE = 2
        const val MENU_HIDE = 3
        const val MENU_UNHIDE = 4
        const val MENU_REPORT = 5
    }
}

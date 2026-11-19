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
import com.example.booking_app.core.format.TimeFormat
import com.example.booking_app.core.media.StorageUrls
import com.example.booking_app.databinding.ItemEventBinding
import com.example.booking_app.databinding.ItemPostBinding
import com.example.booking_app.feature.community.domain.model.HiddenBy
import com.example.booking_app.feature.community.domain.model.PostType
import com.google.android.material.imageview.ShapeableImageView

/** Hành động trên bài đăng / sự kiện (T050 + T056; US6 bổ sung ẩn/hiện/báo cáo). */
interface PostActions {
    fun onPhotoClick(urls: List<String>, startIndex: Int)
    fun onEditPost(postId: String, type: PostType)
    fun onDeletePost(postId: String)
    fun onToggleAttendance(postId: String) {}
    fun onViewAttendees(postId: String) {}
    fun onHidePost(postId: String) {}
    fun onUnhidePost(postId: String) {}
    fun onReportPost(postId: String) {}
}

/** Bảng tin nhóm (T050 + T056): hỗ trợ cả bài thường (`item_post.xml`) và sự kiện (`item_event.xml`). */
class PostAdapter(
    private val actions: PostActions,
) : ListAdapter<PostItem, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int): Int {
        val post = getItem(position).post
        return if (post.type == PostType.EVENT && post.event != null) TYPE_EVENT else TYPE_POST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_EVENT -> EventViewHolder(ItemEventBinding.inflate(inflater, parent, false))
            else -> PostViewHolder(ItemPostBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is PostViewHolder -> holder.bind(item)
            is EventViewHolder -> holder.bind(item)
        }
    }

    inner class PostViewHolder(private val binding: ItemPostBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PostItem) {
            val context = binding.root.context
            val post = item.post

            val authorName = authorDisplayName(context, item)
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

            bindHiddenBadge(binding.hiddenBadge, item)

            binding.postContent.isVisible = post.content.isNotBlank()
            binding.postContent.text = post.content

            bindPhotos(
                item = item,
                container = binding.photoContainer,
                row2 = binding.photoRow2,
                slots = listOf(binding.photo0, binding.photo1, binding.photo2, binding.photo3),
            )

            binding.moreButton.isVisible = item.hasMenuActions
            binding.moreButton.setOnClickListener { showMenu(it, item) }
        }
    }

    inner class EventViewHolder(private val binding: ItemEventBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PostItem) {
            val context = binding.root.context
            val post = item.post
            val event = post.event ?: return

            val zoned = event.at.atZone(TimeFormat.ZONE)
            binding.eventDayOfWeek.text = TimeFormat.dayOfWeekShort(zoned.dayOfWeek)
            binding.eventDayOfMonth.text = zoned.dayOfMonth.toString()

            binding.eventTitle.text = event.title
            val timeLabel = "${TimeFormat.dayOfWeekDate(event.at)}, ${TimeFormat.time(event.at)}"
            binding.eventTimePlace.text = context.getString(R.string.community_event_time_place, timeLabel, event.location)

            val authorName = authorDisplayName(context, item)
            binding.authorMeta.text = if (post.isEdited) {
                context.getString(R.string.community_author_time, authorName, context.getString(R.string.community_label_edited))
            } else {
                authorName
            }

            bindHiddenBadge(binding.hiddenBadge, item)

            binding.eventDescription.isVisible = post.content.isNotBlank()
            binding.eventDescription.text = post.content

            bindPhotos(
                item = item,
                container = binding.photoContainer,
                row2 = binding.photoRow2,
                slots = listOf(binding.photo0, binding.photo1, binding.photo2, binding.photo3),
            )

            val progressText = context.resources.getQuantityString(
                R.plurals.community_attendee_progress,
                event.attendeeCount,
                event.attendeeCount,
                event.expectedCount,
            )
            binding.attendeeCountText.text = progressText
            binding.attendeesRow.contentDescription = context.getString(R.string.community_event_attendees_cd, progressText)
            binding.attendeesRow.setOnClickListener { actions.onViewAttendees(post.id) }

            binding.overExpectedBadge.isVisible = event.isOverExpected
            binding.pastBadge.isVisible = item.isEventPast

            val showActions = item.canAttend && !item.isEventPast
            binding.attendanceActions.isVisible = showActions
            if (showActions) {
                binding.attendingLabel.isVisible = event.iAmAttending
                binding.unattendButton.isVisible = event.iAmAttending
                binding.attendButton.isVisible = !event.iAmAttending
                binding.attendButton.isEnabled = !item.attendanceBusy
                binding.unattendButton.isEnabled = !item.attendanceBusy
                binding.attendButton.setOnClickListener { actions.onToggleAttendance(post.id) }
                binding.unattendButton.setOnClickListener { actions.onToggleAttendance(post.id) }
            }

            binding.moreButton.isVisible = item.hasMenuActions
            binding.moreButton.setOnClickListener { showMenu(it, item) }
        }
    }

    private fun authorDisplayName(context: android.content.Context, item: PostItem): String = when {
        item.isAuthorDeleted -> context.getString(R.string.community_label_deleted_user)
        !item.post.author.displayName.isNullOrBlank() -> item.post.author.displayName
        else -> context.getString(R.string.community_join_request_default_name)
    }

    private fun bindHiddenBadge(view: android.widget.TextView, item: PostItem) {
        view.isVisible = item.showHiddenBadge
        if (item.showHiddenBadge) {
            view.setText(
                if (item.post.hiddenBy == HiddenBy.SYSTEM) R.string.community_label_hidden_by_system else R.string.community_label_hidden,
            )
        }
    }

    private fun bindPhotos(
        item: PostItem,
        container: View,
        row2: View,
        slots: List<ShapeableImageView>,
    ) {
        val paths = item.post.photoPaths
        val context = container.context
        if (paths.isEmpty()) {
            container.isVisible = false
            return
        }
        container.isVisible = true
        row2.isVisible = paths.size > 2
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
                MENU_EDIT -> actions.onEditPost(id, item.post.type)
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
        const val TYPE_POST = 0
        const val TYPE_EVENT = 1
        const val AVATAR_BUCKET = "avatars"
        const val MENU_EDIT = 1
        const val MENU_DELETE = 2
        const val MENU_HIDE = 3
        const val MENU_UNHIDE = 4
        const val MENU_REPORT = 5
    }
}

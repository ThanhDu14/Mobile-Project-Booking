package com.example.booking_app.feature.community.ui.common

import android.content.Context
import androidx.annotation.StringRes
import com.example.booking_app.R
import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.model.GroupVisibility
import com.example.booking_app.feature.community.domain.toCommunityError

/** Chuỗi hiển thị dùng chung cho các màn hình cộng đồng (chuỗi ở `strings_community.xml`). */
object CommunityUiText {

    fun error(context: Context, error: Throwable?): String = context.getString(errorRes(error?.toCommunityError()))

    @StringRes
    fun errorRes(error: CommunityError?): Int = when (error) {
        is CommunityError.InvalidArgument -> R.string.community_error_invalid_argument
        is CommunityError.ConfirmNameMismatch -> R.string.community_error_confirm_name_mismatch
        is CommunityError.PhotoMissing -> R.string.community_error_photo_missing
        is CommunityError.Unauthenticated -> R.string.community_error_unauthenticated
        is CommunityError.AccountLocked -> R.string.community_error_account_locked
        is CommunityError.NotMember -> R.string.community_error_not_member
        is CommunityError.Forbidden -> R.string.community_error_forbidden
        is CommunityError.HiddenBySystem -> R.string.community_error_hidden_by_system
        is CommunityError.OwnPost -> R.string.community_error_own_post
        is CommunityError.GroupNotFound -> R.string.community_error_group_not_found
        is CommunityError.PostNotFound -> R.string.community_error_post_not_found
        is CommunityError.RequestNotFound -> R.string.community_error_request_not_found
        is CommunityError.MemberNotFound -> R.string.community_error_member_not_found
        is CommunityError.OwnerLimitReached -> R.string.community_error_owner_limit_reached
        is CommunityError.AlreadyMember -> R.string.community_error_already_member
        is CommunityError.AlreadyRequested -> R.string.community_error_already_requested
        is CommunityError.OwnerMustTransfer ->
            if (error.soleMember) R.string.community_error_owner_must_transfer_sole else R.string.community_error_owner_must_transfer
        is CommunityError.EventPast -> R.string.community_error_event_past
        is CommunityError.AlreadyReported -> R.string.community_error_already_reported
        is CommunityError.TooManyPending -> R.string.community_error_too_many_pending
        is CommunityError.GroupLocked -> R.string.community_error_group_locked
        is CommunityError.Busy -> R.string.community_error_busy
        is CommunityError.Network -> R.string.community_error_network
        is CommunityError.AvatarUploadFailed -> R.string.community_error_avatar_upload_failed
        is CommunityError.ImageTooLarge -> R.string.community_error_image_too_large
        is CommunityError.ImageUnreadable -> R.string.community_error_image_unreadable
        else -> R.string.community_error_unknown
    }

    @StringRes
    fun visibilityLabel(visibility: GroupVisibility): Int =
        if (visibility == GroupVisibility.PUBLIC) R.string.community_label_public else R.string.community_label_private

    @StringRes
    fun visibilityDescription(visibility: GroupVisibility): Int =
        if (visibility == GroupVisibility.PUBLIC) R.string.community_visibility_public_desc else R.string.community_visibility_private_desc

    /** URL ảnh đại diện trong bucket public `group-avatars`; `null` thì dùng ảnh mặc định. */
    fun avatarUrl(path: String?): String? =
        path?.takeIf { it.isNotBlank() }?.let { com.example.booking_app.core.media.StorageUrls.publicUrl("group-avatars", it) }
}

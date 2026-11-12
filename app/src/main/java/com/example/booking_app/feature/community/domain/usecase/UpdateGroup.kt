package com.example.booking_app.feature.community.domain.usecase

import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.GroupMediaRepository
import com.example.booking_app.feature.community.domain.GroupRepository
import com.example.booking_app.feature.community.domain.model.AvatarAction
import com.example.booking_app.feature.community.domain.model.Group
import com.example.booking_app.feature.community.domain.model.GroupInput
import javax.inject.Inject

/** Thay đổi ảnh đại diện khi sửa nhóm. */
sealed interface AvatarChange {
    data object Keep : AvatarChange
    data object Remove : AvatarChange
    data class Replace(val localUri: String) : AvatarChange
}

/**
 * T026: sửa nhóm. Chọn ảnh mới thì `update(REPLACE)` trả đích tải lên, sau đó tải và `CONFIRM`; lỗi ở bước ảnh trả
 * [CommunityError.AvatarUploadFailed] (các trường chữ đã lưu).
 */
class UpdateGroup @Inject constructor(
    private val groups: GroupRepository,
    private val media: GroupMediaRepository,
) {
    suspend operator fun invoke(groupId: String, input: GroupInput?, avatar: AvatarChange, requestId: String): Result<Group> {
        val action = when (avatar) {
            AvatarChange.Keep -> AvatarAction.KEEP
            AvatarChange.Remove -> AvatarAction.REMOVE
            is AvatarChange.Replace -> AvatarAction.REPLACE
        }
        val updated = groups.update(groupId, input, action, requestId).getOrElse { return Result.failure(it) }
        if (avatar !is AvatarChange.Replace) return Result.success(updated.group)
        val target = updated.avatarUpload ?: return Result.failure(CommunityError.AvatarUploadFailed(groupId))
        return uploadAndConfirm(groups, media, groupId, avatar.localUri, target)
    }
}

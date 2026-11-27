package com.example.booking_app.feature.community.domain.usecase

import com.example.booking_app.feature.community.domain.CommunityError
import com.example.booking_app.feature.community.domain.GroupMediaRepository
import com.example.booking_app.feature.community.domain.GroupRepository
import com.example.booking_app.feature.community.domain.model.AvatarAction
import com.example.booking_app.feature.community.domain.model.Group
import com.example.booking_app.feature.community.domain.model.GroupInput
import com.example.booking_app.feature.community.domain.model.SignedUpload
import java.util.UUID
import javax.inject.Inject

/**
 * T026 (AC 1.1, 1.3): tạo nhóm, rồi tải ảnh đại diện lên đích server cấp và gọi `update(avatar = CONFIRM)`.
 * Nhóm đã được tạo từ bước đầu, nên nếu bước ảnh lỗi thì trả [CommunityError.AvatarUploadFailed] kèm `groupId`
 * để màn hình cho "Thử lại" ([retryAvatar]) hoặc dùng ảnh mặc định.
 */
class CreateGroup @Inject constructor(
    private val groups: GroupRepository,
    private val media: GroupMediaRepository,
) {
    suspend operator fun invoke(input: GroupInput, avatarUri: String?, requestId: String): Result<Group> {
        val created = groups.create(input, hasAvatar = avatarUri != null, requestId = requestId)
            .getOrElse { return Result.failure(it) }
        if (avatarUri == null) return Result.success(created.group)
        val target = created.avatarUpload ?: return Result.failure(CommunityError.AvatarUploadFailed(created.group.id))
        return uploadAndConfirm(groups, media, created.group.id, avatarUri, target)
    }

    /** Thử lại ảnh của nhóm đã có: xin đích mới bằng `update(REPLACE)`, tải lên, rồi `CONFIRM`. */
    suspend fun retryAvatar(groupId: String, avatarUri: String): Result<Group> {
        val replaced = groups.update(groupId, null, AvatarAction.REPLACE, UUID.randomUUID().toString())
            .getOrElse { return Result.failure(CommunityError.AvatarUploadFailed(groupId)) }
        val target = replaced.avatarUpload ?: return Result.failure(CommunityError.AvatarUploadFailed(groupId))
        return uploadAndConfirm(groups, media, groupId, avatarUri, target)
    }
}

/** Dùng chung cho tạo và sửa nhóm: mọi lỗi ở bước ảnh đều thành [CommunityError.AvatarUploadFailed]. */
internal suspend fun uploadAndConfirm(
    groups: GroupRepository,
    media: GroupMediaRepository,
    groupId: String,
    avatarUri: String,
    target: SignedUpload,
): Result<Group> {
    val failed = Result.failure<Group>(CommunityError.AvatarUploadFailed(groupId))
    media.uploadAvatar(avatarUri, target).getOrElse { return failed }
    return groups.update(groupId, null, AvatarAction.CONFIRM, UUID.randomUUID().toString())
        .fold(onSuccess = { Result.success(it.group) }, onFailure = { failed })
}

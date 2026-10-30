package com.example.booking_app.feature.account.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.account.domain.AccountRepository
import com.example.booking_app.feature.account.domain.model.ProfileDraft
import javax.inject.Inject

/** Chỉ ghi trường đổi để khớp `onlyChanges(selfEditableFields())` của Rules (contracts/security-rules.md §1). */
class AccountRepositoryImpl @Inject constructor(
    private val dataSource: AccountDataSource,
    private val sessionManager: SessionManager,
) : AccountRepository {

    override suspend fun updateProfile(saved: ProfileDraft, draft: ProfileDraft): Boolean {
        val changes = buildMap<String, Any?> {
            val name = draft.displayName.trim()
            if (name != saved.displayName.trim()) put(DISPLAY_NAME, name)
            if (draft.level != saved.level) put(LEVEL, draft.level?.name)
            if (draft.districtIds != saved.districtIds) put(FAVORITE_DISTRICT_IDS, draft.districtIds)
        }
        if (changes.isEmpty()) return false
        write(changes)
        return true
    }

    override suspend fun setAvatarPath(path: String?) = write(mapOf(AVATAR_PATH to path))

    private suspend fun write(changes: Map<String, Any?>) {
        val uid = sessionManager.currentUid() ?: throw AppError.NotSignedIn
        accountCall { dataSource.update(uid, changes + (UPDATED_AT to ServerTimestamp)) }
    }

    private companion object {
        const val DISPLAY_NAME = "displayName"
        const val LEVEL = "level"
        const val FAVORITE_DISTRICT_IDS = "favoriteDistrictIds"
        const val AVATAR_PATH = "avatarPath"
        const val UPDATED_AT = "updatedAt"
    }
}

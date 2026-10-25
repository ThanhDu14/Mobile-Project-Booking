package com.example.booking_app.feature.account

import com.example.booking_app.feature.account.domain.AccountRepository
import com.example.booking_app.feature.account.domain.AvatarImageProcessor
import com.example.booking_app.feature.account.domain.AvatarRepository
import com.example.booking_app.feature.account.domain.DistrictRepository
import com.example.booking_app.feature.account.domain.model.District
import com.example.booking_app.feature.account.domain.model.ProfileDraft
import kotlinx.coroutines.CompletableDeferred

/** Ghi lại thứ tự lời gọi của mọi fake để kiểm tra trình tự R3. */
class CallLog : ArrayList<String>()

class FakeAvatarRepository(private val log: CallLog = CallLog()) : AvatarRepository {
    val objects = mutableSetOf<String>()
    var uploadError: Throwable? = null
    var deleteError: Throwable? = null

    /** Đặt để treo lần tải lên tới khi test cho chạy tiếp. */
    var uploadGate: CompletableDeferred<Unit>? = null

    override suspend fun upload(path: String, jpeg: ByteArray) {
        log += "upload:$path"
        uploadGate?.await()
        uploadError?.let { throw it }
        objects += path
    }

    override suspend fun delete(path: String) {
        log += "delete:$path"
        deleteError?.let { throw it }
        objects -= path
    }
}

class FakeAccountRepository(private val log: CallLog = CallLog()) : AccountRepository {
    var avatarPath: String? = null
    var setAvatarError: Throwable? = null
    var updateError: Throwable? = null
    val updates = mutableListOf<Pair<ProfileDraft, ProfileDraft>>()

    /** Đặt để treo lần lưu tới khi test cho chạy tiếp. */
    var updateGate: CompletableDeferred<Unit>? = null

    override suspend fun updateProfile(saved: ProfileDraft, draft: ProfileDraft): Boolean {
        log += "updateProfile"
        updates += saved to draft
        updateGate?.await()
        updateError?.let { throw it }
        return saved != draft
    }

    override suspend fun setAvatarPath(path: String?) {
        log += "setAvatarPath:$path"
        setAvatarError?.let { throw it }
        avatarPath = path
    }
}

class FakeDistrictRepository(
    var active: List<District> = listOf(
        District("q1", "Quận 1", 1),
        District("q3", "Quận 3", 3),
        District("q10", "Quận 10", 10),
    ),
    var inactive: List<District> = listOf(District("q-cu", "Quận cũ", 99, isActive = false)),
) : DistrictRepository {
    var error: Throwable? = null
    var activeCalls = 0

    override suspend fun activeDistricts(): List<District> {
        activeCalls++
        error?.let { throw it }
        return active
    }

    override suspend fun districtsByIds(ids: Collection<String>): Map<String, District> {
        error?.let { throw it }
        return (active + inactive).filter { it.id in ids }.associateBy { it.id }
    }
}

class FakeAvatarImageProcessor : AvatarImageProcessor {
    var result: ByteArray = ByteArray(10)
    var error: Throwable? = null
    val processed = mutableListOf<String>()

    override suspend fun process(uri: String): ByteArray {
        processed += uri
        error?.let { throw it }
        return result
    }
}

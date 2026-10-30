package com.example.booking_app.feature.account.domain

import com.example.booking_app.core.session.AppRole
import com.example.booking_app.feature.account.domain.model.OwnerApplicationSummary
import com.example.booking_app.feature.account.domain.model.OwnerSection

/**
 * Mục chủ sân theo bảng research R6. Claim `appRole` là nguồn quyền; `ownerStatus` chỉ là bản sao để hiển thị nên
 * hai giá trị lệch nhau vài giây (D ghi claim trước rồi ghi document) vẫn cho kết quả hợp lý.
 */
object OwnerSectionResolver {
    fun resolve(
        appRole: AppRole,
        ownerStatus: String,
        ownerRevokedReason: String?,
        latest: OwnerApplicationSummary?,
    ): OwnerSection = when {
        appRole == AppRole.ADMIN -> OwnerSection.Hidden
        appRole == AppRole.OWNER -> OwnerSection.Approved
        ownerStatus == STATUS_REJECTED -> OwnerSection.Rejected(latest?.rejectReason)
        // APPROVED mà claim chưa là OWNER: token chưa làm mới, chưa cho vào "Quản lý sân".
        ownerStatus == STATUS_PENDING || ownerStatus == STATUS_APPROVED -> OwnerSection.Pending(latest?.submittedAt)
        ownerRevokedReason != null -> OwnerSection.Revoked(ownerRevokedReason)
        else -> OwnerSection.CanApply
    }

    /** Chỉ hai trạng thái này cần đọc hồ sơ gần nhất (ngày nộp, lý do từ chối). */
    fun needsLatestApplication(ownerStatus: String): Boolean =
        ownerStatus == STATUS_PENDING || ownerStatus == STATUS_REJECTED

    private const val STATUS_PENDING = "PENDING"
    private const val STATUS_REJECTED = "REJECTED"
    private const val STATUS_APPROVED = "APPROVED"
}

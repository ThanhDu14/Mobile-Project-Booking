package com.example.booking_app.feature.owner.domain.voucher

import java.time.LocalDate
import java.util.Locale

enum class OwnerVoucherType { PERCENT, FIXED }

/** Editable form values only. Venue is supplied by the parent path; server-owned usage fields are absent. */
data class OwnerVoucherDraft(
    val code: String,
    val type: OwnerVoucherType,
    val value: Long,
    val maxDiscountVnd: Long,
    val minOrderTotalVnd: Long,
    val validFrom: LocalDate,
    val validTo: LocalDate,
    val usageLimit: Int?,
    val perUserLimit: Int?,
    val isActive: Boolean,
)

data class OwnerVoucher(
    val code: String,
    val type: OwnerVoucherType,
    val value: Long,
    val maxDiscountVnd: Long,
    val minOrderTotalVnd: Long,
    val validFrom: LocalDate,
    val validTo: LocalDate,
    val usageLimit: Int?,
    val usedCount: Int,
    val perUserLimit: Int?,
    val isActive: Boolean,
)

interface OwnerVoucherRepository {
    fun observeVouchers(venueId: String): kotlinx.coroutines.flow.Flow<List<OwnerVoucher>>
    suspend fun create(venueId: String, draft: OwnerVoucherDraft): Result<Unit>
    suspend fun update(venueId: String, code: String, draft: OwnerVoucherDraft): Result<Unit>
    suspend fun setActive(venueId: String, code: String, isActive: Boolean): Result<Unit>
}

class OwnerVoucherContractUnavailableException :
    IllegalStateException("Contract CRUD voucher và ownership venue chưa được Feature 111/050/database owner xác nhận.")

object OwnerVoucherValidation {
    fun validate(draft: OwnerVoucherDraft): List<String> = buildList {
        if (draft.code.isBlank()) add("Mã voucher không được để trống")
        if (draft.code != draft.code.uppercase(Locale.ROOT)) add("Mã voucher phải viết hoa")
        if (draft.value <= 0L) add("Giá trị voucher phải lớn hơn 0")
        if (draft.type == OwnerVoucherType.PERCENT && draft.value > 100L) add("Voucher phần trăm không được vượt quá 100%")
        if (draft.maxDiscountVnd < 0L) add("Mức giảm tối đa không được âm")
        if (draft.minOrderTotalVnd < 0L) add("Giá trị đơn tối thiểu không được âm")
        if (draft.validFrom.isAfter(draft.validTo)) add("Ngày bắt đầu không được sau ngày kết thúc")
        if (draft.usageLimit != null && draft.usageLimit <= 0) add("Giới hạn sử dụng phải là số nguyên dương")
        if (draft.perUserLimit != null && draft.perUserLimit <= 0) add("Giới hạn mỗi người phải là số nguyên dương")
        if (draft.usageLimit != null && draft.perUserLimit != null && draft.perUserLimit > draft.usageLimit) {
            add("Giới hạn mỗi người không được lớn hơn tổng giới hạn sử dụng")
        }
    }
}

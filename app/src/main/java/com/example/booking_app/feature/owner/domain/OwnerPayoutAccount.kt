package com.example.booking_app.feature.owner.domain

import com.example.booking_app.feature.payment.domain.model.PayoutAccount
import java.text.Normalizer

/** Ngân hàng chủ sân chọn được cho tài khoản nhận tiền; `bin` là mã BIN NAPAS 6 số mà VietQR cần. */
data class PayoutBank(val bin: String, val shortName: String)

/**
 * Tài khoản nhận tiền của cơ sở (`venues.payoutAccount`, spec 050 data-model §1.7). Trong bản thử nghiệm, tiền
 * người chơi chuyển vào chỉ là giả lập (`booking-payment-simulate`), không có giao dịch thật.
 */
object OwnerPayoutAccount {
    val banks = listOf(
        PayoutBank("970436", "Vietcombank"),
        PayoutBank("970415", "VietinBank"),
        PayoutBank("970418", "BIDV"),
        PayoutBank("970405", "Agribank"),
        PayoutBank("970407", "Techcombank"),
        PayoutBank("970422", "MBBank"),
        PayoutBank("970416", "ACB"),
        PayoutBank("970432", "VPBank"),
        PayoutBank("970423", "TPBank"),
        PayoutBank("970403", "Sacombank"),
    )

    private val accountPattern = Regex("^[0-9]{6,19}$")
    private const val MAX_HOLDER_LENGTH = 50

    /** Tên chủ tài khoản theo kiểu ngân hàng: in hoa, bỏ dấu, gộp khoảng trắng ("Nguyễn Văn Ánh" → "NGUYEN VAN ANH"). */
    fun normalizeHolderName(raw: String): String = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace('đ', 'd').replace('Đ', 'D')
        .uppercase()
        .replace(Regex("\\s+"), " ")

    /** Lỗi theo trường; rỗng nghĩa là lưu được (cùng điều kiện với Firestore Rules `isValidPayoutAccount`). */
    fun validate(bankBin: String?, accountNumber: String, holderName: String): List<OwnerFieldError> = buildList {
        if (banks.none { it.bin == bankBin }) add(OwnerFieldError("bank", "Chọn ngân hàng nhận tiền"))
        if (!accountPattern.matches(accountNumber.trim())) add(OwnerFieldError("accountNumber", "Số tài khoản gồm 6–19 chữ số"))
        val holder = normalizeHolderName(holderName)
        if (holder.isEmpty()) add(OwnerFieldError("accountHolderName", "Nhập tên chủ tài khoản"))
        else if (holder.length > MAX_HOLDER_LENGTH) add(OwnerFieldError("accountHolderName", "Tên chủ tài khoản tối đa $MAX_HOLDER_LENGTH ký tự"))
    }

    /** Dựng tài khoản đã chuẩn hóa; `null` nếu còn lỗi. */
    fun build(bankBin: String?, accountNumber: String, holderName: String): PayoutAccount? {
        if (validate(bankBin, accountNumber, holderName).isNotEmpty()) return null
        val bank = banks.first { it.bin == bankBin }
        return PayoutAccount(bank.bin, bank.shortName, accountNumber.trim(), normalizeHolderName(holderName))
    }
}

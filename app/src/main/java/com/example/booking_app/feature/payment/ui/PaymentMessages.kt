package com.example.booking_app.feature.payment.ui

import android.content.Context
import androidx.annotation.StringRes
import com.example.booking_app.R
import com.example.booking_app.core.format.MoneyFormat
import com.example.booking_app.feature.payment.domain.model.VoucherCheck
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Câu hiển thị của màn thanh toán, lấy từ `strings_payment.xml` để ViewModel không giữ `Context`. */
interface PaymentMessages {

    enum class Key(@StringRes val id: Int, val resName: String) {
        VOUCHER_MIN_ORDER(R.string.payment_voucher_min_order, "payment_voucher_min_order"),
        VOUCHER_EXPIRED_OR_OUT(R.string.payment_voucher_expired_or_out, "payment_voucher_expired_or_out"),
        VOUCHER_PER_USER(R.string.payment_voucher_per_user, "payment_voucher_per_user"),
        VOUCHER_NOT_FOUND(R.string.payment_voucher_not_found, "payment_voucher_not_found"),
        VOUCHER_EMPTY_CODE(R.string.payment_voucher_empty_code, "payment_voucher_empty_code"),
        VOUCHER_CHECK_FAILED(R.string.payment_voucher_check_failed, "payment_voucher_check_failed"),
        VOUCHER_LIST_FAILED(R.string.payment_voucher_list_failed, "payment_voucher_list_failed"),
        MISSING_ORDER(R.string.payment_missing_order, "payment_missing_order"),
    }

    fun string(key: Key, vararg args: Any): String

    /** Câu lỗi cho kết quả kiểm voucher không hợp lệ; `null` với `Valid`. */
    fun voucherError(check: VoucherCheck): String? = when (check) {
        is VoucherCheck.Valid -> null
        VoucherCheck.NotFound -> notFound()
        VoucherCheck.Expired, VoucherCheck.OutOfUses -> string(Key.VOUCHER_EXPIRED_OR_OUT)
        is VoucherCheck.MinOrderNotMet -> string(Key.VOUCHER_MIN_ORDER, MoneyFormat.grouped(check.minOrderTotal))
        VoucherCheck.PerUserLimitReached -> string(Key.VOUCHER_PER_USER)
    }

    fun notFound(): String = string(Key.VOUCHER_NOT_FOUND)
    fun emptyCode(): String = string(Key.VOUCHER_EMPTY_CODE)
    fun checkFailed(): String = string(Key.VOUCHER_CHECK_FAILED)
    fun listFailed(): String = string(Key.VOUCHER_LIST_FAILED)
    fun missingOrder(): String = string(Key.MISSING_ORDER)
}

class AndroidPaymentMessages @Inject constructor(
    @ApplicationContext private val context: Context,
) : PaymentMessages {
    override fun string(key: PaymentMessages.Key, vararg args: Any): String = context.getString(key.id, *args)
}

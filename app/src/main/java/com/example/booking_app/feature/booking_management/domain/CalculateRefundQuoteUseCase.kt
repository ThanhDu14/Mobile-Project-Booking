package com.example.booking_app.feature.booking_management.domain

/** Uses a supplied server-time estimate; only the server determines the final refund. */
class CalculateRefundQuoteUseCase {
    operator fun invoke(
        status: RefundPolicy.Status,
        paymentMethod: RefundPolicy.PaymentMethod,
        paidAmount: Long,
        serverNowEstimateMillis: Long,
        startMillis: Long,
        checkedIn: Boolean
    ): RefundPolicyQuote {
        val result = RefundPolicy.calculate(
            status, paymentMethod, paidAmount, serverNowEstimateMillis, startMillis, checkedIn
        )
        // Compute signed whole minutes without overflowing the millisecond subtraction.
        val remainder = startMillis % MINUTE_MILLIS - serverNowEstimateMillis % MINUTE_MILLIS
        val quotientDifference = startMillis / MINUTE_MILLIS - serverNowEstimateMillis / MINUTE_MILLIS + remainder / MINUTE_MILLIS
        val remainderDifference = remainder % MINUTE_MILLIS
        val minutes = when {
            quotientDifference > 0 && remainderDifference < 0 -> quotientDifference - 1
            quotientDifference < 0 && remainderDifference > 0 -> quotientDifference + 1
            else -> quotientDifference
        }
        val early = serverNowEstimateMillis <= Long.MAX_VALUE - EARLY_MILLIS &&
            startMillis >= serverNowEstimateMillis + EARLY_MILLIS
        val explanation = when {
            result == null -> "Không đủ điều kiện hủy theo giờ máy chủ ước tính. Máy chủ quyết định kết quả cuối cùng."
            status == RefundPolicy.Status.PENDING -> "Đơn chờ duyệt dự kiến được hoàn toàn bộ tiền đã trả. Máy chủ quyết định kết quả cuối cùng."
            early -> "Hủy sớm dự kiến được hoàn toàn bộ tiền đã trả. Máy chủ quyết định kết quả cuối cùng."
            paymentMethod == RefundPolicy.PaymentMethod.DEPOSIT -> "Hủy trễ dự kiến mất tiền cọc. Máy chủ quyết định kết quả cuối cùng."
            paymentMethod == RefundPolicy.PaymentMethod.MOCK_WALLET -> "Hủy trễ dự kiến phạt 30% tiền đã trả, làm tròn xuống bội 1.000 đồng. Máy chủ quyết định kết quả cuối cùng."
            else -> "Thanh toán tại sân không phát sinh tiền hoàn. Máy chủ quyết định kết quả cuối cùng."
        }
        return RefundPolicyQuote(
            isEligibleForCancel = result != null,
            isEarlyCancel = early,
            minutesUntilStart = minutes,
            paidAmount = paidAmount,
            penaltyAmount = result?.penaltyAmount ?: 0L,
            refundAmount = result?.refundAmount ?: 0L,
            policyExplanation = explanation
        )
    }

    private companion object {
        const val MINUTE_MILLIS = 60_000L
        const val EARLY_MILLIS = 240L * MINUTE_MILLIS
    }
}

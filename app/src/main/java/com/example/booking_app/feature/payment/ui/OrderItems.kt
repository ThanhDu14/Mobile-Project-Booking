package com.example.booking_app.feature.payment.ui

import com.example.booking_app.core.format.MoneyFormat
import com.example.booking_app.core.navigation.HoldToPaymentArgs

/** Dòng tóm tắt sân/buổi cho màn thanh toán (FR-003), dựng từ `HoldToPaymentArgs`. */
internal object OrderItems {

    fun from(args: HoldToPaymentArgs): List<String> =
        if (args.courtDetails.isNotEmpty()) args.courtDetails.map(::fromCourtDetail) else fromSlotIds(args.slotIds.toList())

    /**
     * 040 ghi `courtDetails` dạng `badge|tên sân|mặt sân|ngày|giờ|tạm tính`
     * (`BookingSlotGridViewModel.buildCourtDetailsForConfirm`); chuỗi khác dạng thì giữ nguyên.
     */
    fun fromCourtDetail(raw: String): String {
        val parts = raw.split("|")
        if (parts.size < 5) return raw
        val name = listOf(parts[1], parts[2]).filter { it.isNotBlank() }.joinToString(" · ")
        val price = parts.getOrNull(5)?.toLongOrNull()?.let { "${MoneyFormat.grouped(it)}đ" }
        return listOf(name, parts[3], parts[4], price).filter { !it.isNullOrBlank() }.joinToString(" · ")
    }

    /** 041 không gửi `courtDetails`: mỗi buổi (ngày) một dòng `dd/MM/yyyy HH:mm – HH:mm`. */
    fun fromSlotIds(slotIds: List<String>): List<String> = slotIds
        .mapNotNull { id ->
            val parts = id.split("_")
            if (parts.size < 3 || parts[parts.size - 2].length != 8) return@mapNotNull null
            val date = parts[parts.size - 2]
            val time = parts.last().padStart(4, '0')
            val start = (time.take(2).toIntOrNull() ?: return@mapNotNull null) * 60 + (time.drop(2).toIntOrNull() ?: 0)
            date to start
        }
        .groupBy({ it.first }, { it.second })
        .toSortedMap()
        .map { (date, starts) ->
            val from = starts.min()
            val to = starts.max() + 30
            "${date.substring(6, 8)}/${date.substring(4, 6)}/${date.take(4)} · ${hm(from)} – ${hm(to)}"
        }

    private fun hm(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)
}

package com.example.booking_app.feature.courtdetail.domain.model

/** Số điện thoại hiển thị (research R9). */
sealed interface PhoneDisplay {
    /** Bấm để mở trình gọi điện với [e164]. */
    data class Dialable(val e164: String, val display: String) : PhoneDisplay

    /** Không hợp lệ (ví dụ số bị che của dữ liệu demo): chỉ hiện và nhấn giữ để sao chép. */
    data class DisplayOnly(val raw: String) : PhoneDisplay
}

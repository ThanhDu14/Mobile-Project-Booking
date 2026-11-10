package com.example.booking_app.feature.booking_management.domain

import android.os.Parcel
import android.os.Parcelable

/**
 * Hợp đồng điều hướng từ màn biên nhận thanh toán (050) sang màn Lịch đặt (060).
 */
data class ReceiptToMyBookingsArgs(
    val bookingId: String = "",
    val isRecurring: Boolean = false,
    val initialTab: String = "PENDING"
) : Parcelable {
    constructor(parcel: Parcel) : this(
        bookingId = parcel.readString().orEmpty(),
        isRecurring = parcel.readByte() != 0.toByte(),
        initialTab = parcel.readString().orEmpty().ifBlank { "PENDING" }
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(bookingId)
        parcel.writeByte(if (isRecurring) 1 else 0)
        parcel.writeString(initialTab)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ReceiptToMyBookingsArgs> {
        override fun createFromParcel(parcel: Parcel): ReceiptToMyBookingsArgs =
            ReceiptToMyBookingsArgs(parcel)

        override fun newArray(size: Int): Array<ReceiptToMyBookingsArgs?> =
            arrayOfNulls(size)
    }
}

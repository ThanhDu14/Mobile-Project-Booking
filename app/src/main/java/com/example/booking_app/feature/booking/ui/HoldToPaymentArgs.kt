package com.example.booking_app.feature.booking.ui

import android.os.Parcel
import android.os.Parcelable

data class HoldToPaymentArgs(
    val bookingId: String,
    val venueId: String,
    val venueName: String,
    val isRecurring: Boolean = false,
    val totalSessions: Int = 1,
    val subtotalAmount: Long,
    val slotIds: Array<String>,
    val holdExpiresAtMillis: Long
) : Parcelable {
    constructor(parcel: Parcel) : this(
        bookingId = parcel.readString().orEmpty(),
        venueId = parcel.readString().orEmpty(),
        venueName = parcel.readString().orEmpty(),
        isRecurring = parcel.readByte() != 0.toByte(),
        totalSessions = parcel.readInt(),
        subtotalAmount = parcel.readLong(),
        slotIds = parcel.createStringArray() ?: emptyArray(),
        holdExpiresAtMillis = parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(bookingId)
        parcel.writeString(venueId)
        parcel.writeString(venueName)
        parcel.writeByte(if (isRecurring) 1 else 0)
        parcel.writeInt(totalSessions)
        parcel.writeLong(subtotalAmount)
        parcel.writeStringArray(slotIds)
        parcel.writeLong(holdExpiresAtMillis)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<HoldToPaymentArgs> {
        override fun createFromParcel(parcel: Parcel): HoldToPaymentArgs {
            return HoldToPaymentArgs(parcel)
        }

        override fun newArray(size: Int): Array<HoldToPaymentArgs?> {
            return arrayOfNulls(size)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HoldToPaymentArgs

        if (bookingId != other.bookingId) return false
        if (venueId != other.venueId) return false
        if (venueName != other.venueName) return false
        if (isRecurring != other.isRecurring) return false
        if (totalSessions != other.totalSessions) return false
        if (subtotalAmount != other.subtotalAmount) return false
        if (!slotIds.contentEquals(other.slotIds)) return false
        if (holdExpiresAtMillis != other.holdExpiresAtMillis) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bookingId.hashCode()
        result = 31 * result + venueId.hashCode()
        result = 31 * result + venueName.hashCode()
        result = 31 * result + isRecurring.hashCode()
        result = 31 * result + totalSessions
        result = 31 * result + subtotalAmount.hashCode()
        result = 31 * result + slotIds.contentHashCode()
        result = 31 * result + holdExpiresAtMillis.hashCode()
        return result
    }
}

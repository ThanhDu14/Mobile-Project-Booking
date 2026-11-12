package com.example.booking_app.feature.dropin.ui.list

import android.os.Parcel
import android.os.Parcelable
import com.example.booking_app.feature.dropin.domain.model.DropInFilter
import com.example.booking_app.feature.dropin.domain.model.Level
import com.example.booking_app.feature.dropin.domain.model.TimeSlot
import java.time.LocalDate

/**
 * Bộ lọc truyền qua Safe Args (contracts/ui-screens.md mục 1 và 3). Mọi trường nullable để AI Assistant (spec 022)
 * chỉ điền các trường nó hiểu. Dạng chuỗi cho ngày (`yyyy-MM-dd`), khung giờ và trình độ (tên enum); giá trị sai
 * bị bỏ qua khi đổi sang [DropInFilter].
 *
 * Repo chưa bật plugin `kotlin-parcelize` nên viết `Parcelable` bằng tay như `HoldToPaymentArgs`.
 */
data class DropInFilterArgs(
    val districtIds: List<String>? = null,
    val date: String? = null,
    val timeSlot: String? = null,
    val level: String? = null,
    val maxPrice: Long? = null,
) : Parcelable {

    fun toFilter(): DropInFilter = DropInFilter(
        districtIds = districtIds.orEmpty().filter { it.isNotBlank() }.toSet(),
        date = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        timeSlot = timeSlot?.let { name -> TimeSlot.entries.firstOrNull { it.name == name } },
        level = level?.let { name -> Level.entries.firstOrNull { it.name == name } },
        maxPrice = maxPrice?.takeIf { it > 0 },
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeByte(if (districtIds != null) 1 else 0)
        if (districtIds != null) parcel.writeStringList(districtIds)
        parcel.writeString(date)
        parcel.writeString(timeSlot)
        parcel.writeString(level)
        parcel.writeByte(if (maxPrice != null) 1 else 0)
        if (maxPrice != null) parcel.writeLong(maxPrice)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<DropInFilterArgs> {

        fun from(filter: DropInFilter): DropInFilterArgs = DropInFilterArgs(
            districtIds = filter.districtIds.sorted().takeIf { it.isNotEmpty() },
            date = filter.date?.toString(),
            timeSlot = filter.timeSlot?.name,
            level = filter.level?.name,
            maxPrice = filter.maxPrice,
        )

        override fun createFromParcel(parcel: Parcel): DropInFilterArgs {
            val districts = if (parcel.readByte() != 0.toByte()) parcel.createStringArrayList() else null
            val date = parcel.readString()
            val slot = parcel.readString()
            val level = parcel.readString()
            val maxPrice = if (parcel.readByte() != 0.toByte()) parcel.readLong() else null
            return DropInFilterArgs(districts, date, slot, level, maxPrice)
        }

        override fun newArray(size: Int): Array<DropInFilterArgs?> = arrayOfNulls(size)
    }
}

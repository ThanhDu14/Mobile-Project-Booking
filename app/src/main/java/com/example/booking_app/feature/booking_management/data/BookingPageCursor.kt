package com.example.booking_app.feature.booking_management.data

import com.example.booking_app.feature.booking_management.domain.BookingTab
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

internal data class BookingSort(val field: String, val direction: Query.Direction)

internal fun bookingSort(tab: BookingTab): BookingSort = when (tab) {
    BookingTab.HOLD, BookingTab.PENDING -> BookingSort("createdAt", Query.Direction.DESCENDING)
    BookingTab.UPCOMING -> BookingSort("startAt", Query.Direction.ASCENDING)
    BookingTab.COMPLETED -> BookingSort("startAt", Query.Direction.DESCENDING)
    BookingTab.CANCELLED -> BookingSort("updatedAt", Query.Direction.DESCENDING)
}

internal data class BookingPageCursor(val timestamp: Timestamp, val id: String) {
    fun encode(tab: BookingTab, uid: String): String {
        validateId(id)
        require(uid.isNotEmpty() && uid.length <= 128)
        val bytes = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeByte(1)
                out.writeUTF(tab.name)
                out.writeUTF(uid)
                out.writeLong(timestamp.seconds)
                out.writeInt(timestamp.nanoseconds)
                out.writeUTF(id)
            }
        }.toByteArray()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        fun decode(value: String, tab: BookingTab, uid: String): BookingPageCursor {
            require(value.length in 1..4096 && value.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }) { "Invalid cursor" }
            try {
                val bytes = Base64.getUrlDecoder().decode(value)
                require(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) == value)
                val input = DataInputStream(ByteArrayInputStream(bytes))
                require(input.readUnsignedByte() == 1)
                require(input.readUTF() == tab.name && input.readUTF() == uid)
                val timestamp = Timestamp(input.readLong(), input.readInt())
                val id = input.readUTF()
                validateId(id)
                require(input.available() == 0)
                return BookingPageCursor(timestamp, id)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid cursor", e)
            }
        }

        private fun validateId(id: String) {
            require(id.isNotEmpty() && id != "." && id != ".." && '/' !in id && id.toByteArray(Charsets.UTF_8).size <= 1500)
        }
    }
}

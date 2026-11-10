package com.example.booking_app.feature.booking_management

import com.example.booking_app.feature.booking_management.data.BookingPageCursor
import com.example.booking_app.feature.booking_management.data.BookingSort
import com.example.booking_app.feature.booking_management.data.bookingSort
import com.example.booking_app.feature.booking_management.domain.BookingTab
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class BookingPageCursorTest {
    @Test fun tabSortsMatchContract() {
        val descending = Query.Direction.DESCENDING
        assertEquals(BookingSort("createdAt", descending), bookingSort(BookingTab.HOLD))
        assertEquals(BookingSort("createdAt", descending), bookingSort(BookingTab.PENDING))
        assertEquals(BookingSort("startAt", Query.Direction.ASCENDING), bookingSort(BookingTab.UPCOMING))
        assertEquals(BookingSort("startAt", descending), bookingSort(BookingTab.COMPLETED))
        assertEquals(BookingSort("updatedAt", descending), bookingSort(BookingTab.CANCELLED))
    }

    @Test fun roundTripsTimestampWithNanosecondsAndUnicodeId() {
        val cursor = BookingPageCursor(Timestamp(123456789L, 123456789), "sân_2")
        val encoded = cursor.encode(BookingTab.UPCOMING, "user")
        assertEquals(cursor, BookingPageCursor.decode(encoded, BookingTab.UPCOMING, "user"))
        assertNotEquals(encoded, cursor.copy(id = "sân_3").encode(BookingTab.UPCOMING, "user"))
    }

    @Test fun rejectsMalformedOrWrongContext() {
        val encoded = BookingPageCursor(Timestamp(1, 2), "id").encode(BookingTab.HOLD, "user")
        val trailing = Base64.getUrlEncoder().withoutPadding().encodeToString(Base64.getUrlDecoder().decode(encoded) + 0)
        for (bad in listOf("", "!", encoded + "=", encoded.dropLast(3), trailing, "a".repeat(4097))) {
            assertThrows(IllegalArgumentException::class.java) { BookingPageCursor.decode(bad, BookingTab.HOLD, "user") }
        }
        assertThrows(IllegalArgumentException::class.java) { BookingPageCursor.decode(encoded, BookingTab.PENDING, "user") }
        assertThrows(IllegalArgumentException::class.java) { BookingPageCursor.decode(encoded, BookingTab.HOLD, "other") }
        assertThrows(IllegalArgumentException::class.java) { BookingPageCursor(Timestamp(1, 0), "a/b").encode(BookingTab.HOLD, "user") }
    }
}

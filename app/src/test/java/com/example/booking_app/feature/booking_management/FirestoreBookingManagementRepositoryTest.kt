package com.example.booking_app.feature.booking_management

import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.booking_management.data.BookingPageCursor
import com.example.booking_app.feature.booking_management.data.FirestoreBookingManagementRepository
import com.example.booking_app.feature.booking_management.domain.BookingTab
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Date

class FirestoreBookingManagementRepositoryTest {

    private val firestore: FirebaseFirestore = mockk()
    private val session: SessionManager = mockk()
    private val collection: CollectionReference = mockk()
    private val query: Query = mockk()
    private val querySnapshot: QuerySnapshot = mockk()
    private val metadata: SnapshotMetadata = mockk()

    private lateinit var repository: FirestoreBookingManagementRepository

    private val testUid = "user_123"
    private val start = Timestamp(Date(1000000))
    private val end = Timestamp(Date(2800000))

    private fun validBookingData(id: String) = mutableMapOf<String, Any?>(
        "userId" to testUid,
        "venueId" to "venue_01",
        "venueSnapshot" to mapOf("name" to "Court Name", "address" to "123 Street"),
        "items" to listOf(
            mapOf("slotId" to "slot_1", "courtId" to "court_1", "courtName" to "Sân 1", "startAt" to start, "endAt" to end, "price" to 100_000L)
        ),
        "startAt" to start,
        "endAt" to end,
        "status" to "PENDING",
        "subtotal" to 100_000L,
        "discount" to 0L,
        "total" to 100_000L,
        "depositAmount" to 30_000L,
        "paidAmount" to 30_000L,
        "remainingAmount" to 70_000L,
        "paymentMethod" to "DEPOSIT",
        "paymentStatus" to "DEPOSITED",
        "createdAt" to start,
        "updatedAt" to start
    )

    @Before
    fun setUp() {
        every { session.currentUid() } returns testUid
        every { firestore.collection("bookings") } returns collection
        every { collection.whereEqualTo("userId", testUid) } returns query
        every { query.whereIn("status", any()) } returns query
        every { query.orderBy(any<String>(), any()) } returns query
        every { query.orderBy(any<FieldPath>(), any()) } returns query
        every { query.limit(any()) } returns query
        every { query.startAfter(any(), any()) } returns query

        every { metadata.isFromCache } returns false
        every { metadata.hasPendingWrites() } returns false
        every { querySnapshot.metadata } returns metadata
        every { query.get(Source.SERVER) } returns Tasks.forResult(querySnapshot)

        repository = FirestoreBookingManagementRepository(firestore, session)
    }

    @Test
    fun getPage_queriesPendingCorrectlyAndMapsResults(): Unit = runBlocking {
        val doc1: DocumentSnapshot = mockk {
            every { id } returns "b1"
            every { data } returns validBookingData("b1")
            every { getTimestamp("createdAt") } returns start
        }
        every { querySnapshot.documents } returns listOf(doc1)

        val page = repository.getPage(BookingTab.PENDING, null, 10)

        assertEquals(1, page.bookings.size)
        assertEquals("b1", page.bookings[0].bookingId)
        assertEquals(testUid, page.bookings[0].userId)
        assertNull(page.nextCursor)
    }

    @Test
    fun getPage_withCorruptedDocumentInPage_skipsCorruptedAndReturnsValidDocs(): Unit = runBlocking {
        val validDoc: DocumentSnapshot = mockk {
            every { id } returns "valid_b"
            every { data } returns validBookingData("valid_b")
            every { getTimestamp("createdAt") } returns start
        }
        val corruptedDoc: DocumentSnapshot = mockk {
            every { id } returns "corrupted_b"
            every { data } returns mapOf("userId" to testUid, "status" to "PENDING") // missing items, amounts, timestamps
            every { getTimestamp("createdAt") } returns null
        }
        every { querySnapshot.documents } returns listOf(validDoc, corruptedDoc)

        val page = repository.getPage(BookingTab.PENDING, null, 10)

        // Valid document is preserved, corrupted document is skipped rather than crashing the page
        assertEquals(1, page.bookings.size)
        assertEquals("valid_b", page.bookings[0].bookingId)
    }

    @Test
    fun getPage_hasNextPage_encodesNextCursor(): Unit = runBlocking {
        val docs = (1..11).map { i ->
            mockk<DocumentSnapshot> {
                every { id } returns "b$i"
                every { data } returns validBookingData("b$i")
                every { getTimestamp("createdAt") } returns start
            }
        }
        every { querySnapshot.documents } returns docs

        val page = repository.getPage(BookingTab.PENDING, null, 10)

        assertEquals(10, page.bookings.size)
        assertNotNull(page.nextCursor)
        val decoded = BookingPageCursor.decode(page.nextCursor!!, BookingTab.PENDING, testUid)
        assertEquals("b10", decoded.id)
        assertEquals(start, decoded.timestamp)
    }

    @Test
    fun getPage_withCursor_appliesStartAfter(): Unit = runBlocking {
        val cursor = BookingPageCursor(start, "prev_id").encode(BookingTab.PENDING, testUid)
        every { querySnapshot.documents } returns emptyList()

        repository.getPage(BookingTab.PENDING, cursor, 10)

        verify { query.startAfter(start, "prev_id") }
    }

    @Test(expected = UnsupportedOperationException::class)
    fun getPage_holdTab_throwsUnsupported(): Unit = runBlocking {
        repository.getPage(BookingTab.HOLD, null, 10)
    }

    @Test(expected = IllegalStateException::class)
    fun getPage_cachedSnapshot_rejected(): Unit = runBlocking {
        every { metadata.isFromCache } returns true
        every { querySnapshot.documents } returns emptyList()

        repository.getPage(BookingTab.PENDING, null, 10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun getPage_notSignedIn_rejected(): Unit = runBlocking {
        every { session.currentUid() } returns null

        repository.getPage(BookingTab.PENDING, null, 10)
    }

    @Test(expected = UnsupportedOperationException::class)
    fun estimateQuote_unsupported(): Unit = runBlocking {
        repository.estimateQuote("b1")
    }

    @Test(expected = UnsupportedOperationException::class)
    fun cancelBooking_unsupported(): Unit = runBlocking {
        repository.cancelBooking(mockk())
    }
}

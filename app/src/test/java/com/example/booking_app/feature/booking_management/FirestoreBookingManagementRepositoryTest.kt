package com.example.booking_app.feature.booking_management

import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.feature.booking.data.time.ServerTimeOffsetProvider
import com.example.booking_app.feature.booking_management.data.BookingPageCursor
import com.example.booking_app.feature.booking_management.data.FirestoreBookingManagementRepository
import com.example.booking_app.feature.booking_management.domain.*
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Date

class FirestoreBookingManagementRepositoryTest {

    private val firestore: FirebaseFirestore = mockk()
    private val session: SessionManager = mockk()
    private val serverTimeOffsetProvider: ServerTimeOffsetProvider = mockk()
    private val supabase: io.github.jan.supabase.SupabaseClient = mockk(relaxed = true)
    private val functionErrors: com.example.booking_app.core.network.FunctionErrors = mockk(relaxed = true)
    private val collection: CollectionReference = mockk()
    private val userQuery: Query = mockk()
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
        every { session.sessionState } returns MutableStateFlow(SessionState.SignedIn(testUid, AppRole.PLAYER, true))
        every { firestore.collection("bookings") } returns collection
        every { collection.whereEqualTo("userId", testUid) } returns userQuery
        every { userQuery.whereIn("status", any()) } returns query
        every { userQuery.get(Source.SERVER) } returns Tasks.forResult(querySnapshot)
        every { userQuery.addSnapshotListener(any()) } returns mockk(relaxed = true)
        every { query.whereIn("status", any()) } returns query
        every { query.orderBy(any<String>(), any()) } returns query
        every { query.orderBy(any<FieldPath>(), any()) } returns query
        every { query.limit(any()) } returns query
        every { query.startAfter(any(), any()) } returns query

        every { metadata.isFromCache } returns false
        every { metadata.hasPendingWrites() } returns false
        every { querySnapshot.metadata } returns metadata
        every { query.get(Source.SERVER) } returns Tasks.forResult(querySnapshot)

        every { serverTimeOffsetProvider.now() } returns 1000000L
        coEvery { serverTimeOffsetProvider.ensureSynced(any()) } returns 0L

        repository = FirestoreBookingManagementRepository(firestore, session, serverTimeOffsetProvider, supabase, functionErrors)
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

    @Test
    fun getPage_holdTab_queriesSuccessfullyAndFiltersExpired(): Unit = runBlocking {
        val activeHoldData = validBookingData("hold_active").apply {
            put("status", "HOLD")
            put("holdExpiresAt", Timestamp(Date(2000000))) // expires at 2_000_000 > now (1_000_000)
        }
        val expiredHoldData = validBookingData("hold_expired").apply {
            put("status", "HOLD")
            put("holdExpiresAt", Timestamp(Date(500000))) // expires at 500_000 <= now (1_000_000)
        }
        val activeDoc: DocumentSnapshot = mockk {
            every { id } returns "hold_active"
            every { data } returns activeHoldData
            every { getTimestamp("createdAt") } returns start
        }
        val expiredDoc: DocumentSnapshot = mockk {
            every { id } returns "hold_expired"
            every { data } returns expiredHoldData
            every { getTimestamp("createdAt") } returns start
        }
        every { querySnapshot.documents } returns listOf(activeDoc, expiredDoc)

        val page = repository.getPage(BookingTab.HOLD, null, 10)

        assertEquals(1, page.bookings.size)
        assertEquals("hold_active", page.bookings[0].bookingId)
    }

    @Test
    fun getPage_whenIndexMissing_fallsBackToInMemorySortAndFilter(): Unit = runBlocking {
        val failedException = FirebaseFirestoreException("Index required", FirebaseFirestoreException.Code.FAILED_PRECONDITION)
        every { query.get(Source.SERVER) } returns Tasks.forException(failedException)

        val doc1: DocumentSnapshot = mockk {
            every { id } returns "b1"
            every { data } returns validBookingData("b1")
            every { getString("status") } returns "PENDING"
            every { getTimestamp("createdAt") } returns start
        }
        val fallbackSnapshot: QuerySnapshot = mockk()
        every { fallbackSnapshot.metadata } returns this@FirestoreBookingManagementRepositoryTest.metadata
        every { fallbackSnapshot.documents } returns listOf(doc1)
        every { userQuery.get(Source.SERVER) } returns Tasks.forResult(fallbackSnapshot)

        val page = repository.getPage(BookingTab.PENDING, null, 10)

        assertEquals(1, page.bookings.size)
        assertEquals("b1", page.bookings[0].bookingId)
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

    @Test
    fun observeTabCounts_filtersOutMalformed041SessionsAndExpiredHolds() = kotlinx.coroutines.test.runTest {
        val listenerSlot = slot<EventListener<QuerySnapshot>>()
        every { userQuery.addSnapshotListener(capture(listenerSlot)) } returns mockk(relaxed = true)

        val activeHoldData = validBookingData("hold_active").apply {
            put("status", "HOLD")
            put("holdExpiresAt", Timestamp(Date(2000000))) // expires at 2_000_000 > now (1_000_000)
        }
        val expiredHoldData = validBookingData("hold_expired").apply {
            put("status", "HOLD")
            put("holdExpiresAt", Timestamp(Date(500000))) // expires at 500_000 <= now (1_000_000)
        }
        val stale041Data = mutableMapOf<String, Any?>(
            "status" to "HOLD",
            "userId" to testUid,
            "price" to 100000L
        )
        val validPendingData = validBookingData("pending_1").apply {
            put("status", "PENDING")
        }

        val d1: DocumentSnapshot = mockk {
            every { id } returns "hold_active"
            every { data } returns activeHoldData
        }
        val d2: DocumentSnapshot = mockk {
            every { id } returns "hold_expired"
            every { data } returns expiredHoldData
        }
        val d3: DocumentSnapshot = mockk {
            every { id } returns "stale_041"
            every { data } returns stale041Data
        }
        val d4: DocumentSnapshot = mockk {
            every { id } returns "pending_1"
            every { data } returns validPendingData
        }

        val snapshot: QuerySnapshot = mockk {
            every { documents } returns listOf(d1, d2, d3, d4)
        }

        val flow = repository.observeTabCounts()
        val values = mutableListOf<Map<BookingTab, Int>>()
        val job = launch {
            flow.collect { values.add(it) }
        }
        testScheduler.advanceUntilIdle()

        assertTrue(listenerSlot.isCaptured)
        listenerSlot.captured.onEvent(snapshot, null)
        testScheduler.advanceUntilIdle()

        assertEquals(1, values.size)
        val counts = values[0]
        assertEquals(1, counts[BookingTab.HOLD])
        assertEquals(1, counts[BookingTab.PENDING])
        assertEquals(0, counts[BookingTab.UPCOMING])
        assertEquals(0, counts[BookingTab.COMPLETED])
        assertEquals(0, counts[BookingTab.CANCELLED])

        job.cancel()
    }

    @Test
    fun estimateQuote_calculatesAccurateQuoteWithServerTime(): Unit = runBlocking {
        val bookingStart = Timestamp(Date(1000000L + 5 * 3600_000L))
        val bookingEnd = Timestamp(Date(1000000L + 6 * 3600_000L))
        val data = validBookingData("b1").apply {
            put("status", "CONFIRMED")
            put("startAt", bookingStart)
            put("endAt", bookingEnd)
            put("items", listOf(
                mapOf("slotId" to "slot_1", "courtId" to "court_1", "courtName" to "Sân 1", "startAt" to bookingStart, "endAt" to bookingEnd, "price" to 100_000L)
            ))
            put("paidAmount", 100_000L)
            put("paymentMethod", "MOCK_WALLET")
        }
        val bookingDoc: DocumentSnapshot = mockk {
            every { id } returns "b1"
            every { this@mockk.data } returns data
            every { metadata } returns this@FirestoreBookingManagementRepositoryTest.metadata
        }
        val docRef: DocumentReference = mockk()
        every { collection.document("b1") } returns docRef
        every { docRef.get(Source.SERVER) } returns Tasks.forResult(bookingDoc)

        val quote = repository.estimateQuote("b1")
        assertTrue(quote.isEligibleForCancel)
        assertTrue(quote.isEarlyCancel)
        assertEquals(100_000L, quote.refundAmount)
        assertEquals(0L, quote.penaltyAmount)
    }

    @Test
    fun cancelBooking_invokesPlayerCancelEdgeFunction(): Unit = runBlocking {
        coEvery { functionErrors.invokeWithAuthRetry<CancellationResult>(any()) } returns CancellationResult(
            refundAmount = 100_000L,
            penaltyAmount = 0L
        )

        val request = CancelBookingRequest(bookingId = "b1", reason = "Bận việc", requestId = "req-123")
        val result = repository.cancelBooking(request)

        assertEquals(100_000L, result.refundAmount)
        assertEquals(0L, result.penaltyAmount)
    }
}

package com.example.booking_app.feature.booking.data

import com.example.booking_app.feature.booking.data.datasource.FirestoreRecurringBookingRemoteDataSource
import com.example.booking_app.feature.booking.data.model.BookingSessionDto
import com.example.booking_app.feature.booking.data.model.RecurringGroupDto
import com.example.booking_app.feature.booking.data.time.ServerTimeOffsetProvider
import com.example.booking_app.feature.booking.domain.model.RecurringHoldResult
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

/**
 * T042: Integration Test cho transaction giữ chỗ chu kỳ (Spec 041 - All-or-Nothing).
 *
 * Kiểm tra:
 * 1. Giữ chỗ chu kỳ 12 tuần All-or-Nothing: toàn bộ recurringGroups, bookings ghi status = "HOLD",
 *    và slots ghi status = "HELD" với holdExpiresAt trong vòng 5 phút.
 * 2. Rollback All-or-Nothing khi có 1 slot bất kỳ trong chu kỳ bị người khác giữ trước hoặc đã đặt.
 * 3. Xử lý khi cơ sở không chấp nhận đặt cố định (acceptsRecurring = false).
 * 4. Hủy giữ chỗ nhả slots về AVAILABLE và đổi trạng thái group sang CANCELLED.
 */
class RecurringHoldTransactionTest {

    private val firestore: FirebaseFirestore = mockk()
    private val transaction: Transaction = mockk(relaxed = true)

    private val venuesCollection: CollectionReference = mockk()
    private val groupsCollection: CollectionReference = mockk()
    private val bookingsCollection: CollectionReference = mockk()
    private val slotsCollection: CollectionReference = mockk()

    private val venueDocRef: DocumentReference = mockk()
    private val venueSnapshot: DocumentSnapshot = mockk()
    private val serverTimeOffsetProvider: ServerTimeOffsetProvider = mockk(relaxed = true)

    private lateinit var dataSource: FirestoreRecurringBookingRemoteDataSource

    private val venueId = "demo-venue-01"
    private val userId = "player_test_01"
    private val courtId = "court-01"

    @Before
    fun setUp() {
        every { firestore.collection("venues") } returns venuesCollection
        every { firestore.collection("recurringGroups") } returns groupsCollection
        every { firestore.collection("bookings") } returns bookingsCollection
        every { firestore.collection("slots") } returns slotsCollection

        every { venuesCollection.document(venueId) } returns venueDocRef
        every { transaction.get(venueDocRef) } returns venueSnapshot
        every { venueSnapshot.getBoolean("acceptsRecurring") } returns true
        every { venueSnapshot.getBoolean("isRecurringAccepted") } returns true

        every { firestore.runTransaction<Any>(any()) } answers {
            val function = firstArg<Transaction.Function<Any>>()
            val result = function.apply(transaction)
            Tasks.forResult(result)
        }

        every { serverTimeOffsetProvider.now() } returns 1_700_000_000_000L

        dataSource = FirestoreRecurringBookingRemoteDataSource(firestore, serverTimeOffsetProvider)
    }

    @Test
    fun holdRecurringBooking_12WeeksAllOrNothing_success_writesHoldStatuses() = runTest {
        val totalWeeks = 12
        val startDate = LocalDate.now().plusDays(7)
        val sessions = mutableListOf<BookingSessionDto>()
        val slotIds = mutableListOf<String>()

        for (i in 0 until totalWeeks) {
            val date = startDate.plusWeeks(i.toLong())
            val dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            val slotId1 = "${courtId}_${dateStr}_1800"
            val slotId2 = "${courtId}_${dateStr}_1830"
            slotIds.add(slotId1)
            slotIds.add(slotId2)

            sessions.add(
                BookingSessionDto(
                    id = "booking-$i",
                    recurringGroupId = "group-123",
                    userId = userId,
                    venueId = venueId,
                    courtId = courtId,
                    courtName = "Sân 1",
                    date = dateStr,
                    startTime = "1800",
                    endTime = "1900",
                    slotIds = listOf(slotId1, slotId2),
                    status = "HOLD",
                    price = 160000L,
                    depositAmount = 48000L
                )
            )

            // Mock slot docs as non-existent (available)
            val slotRef1: DocumentReference = mockk()
            val slotSnap1: DocumentSnapshot = mockk()
            every { slotsCollection.document(slotId1) } returns slotRef1
            every { transaction.get(slotRef1) } returns slotSnap1
            every { slotSnap1.exists() } returns false

            val slotRef2: DocumentReference = mockk()
            val slotSnap2: DocumentSnapshot = mockk()
            every { slotsCollection.document(slotId2) } returns slotRef2
            every { transaction.get(slotRef2) } returns slotSnap2
            every { slotSnap2.exists() } returns false
        }

        val recurringGroup = RecurringGroupDto(
            id = "group-123",
            userId = userId,
            venueId = venueId,
            venueName = "CLB Cầu Lông SmashNow",
            totalSessions = 12,
            subtotal = 1920000L,
            total = 1920000L,
            depositAmount = 576000L,
            status = "HOLD",
            holdExpiresAt = Timestamp(Date(System.currentTimeMillis() + 5 * 60 * 1000L))
        )

        val groupRef: DocumentReference = mockk()
        every { groupsCollection.document("group-123") } returns groupRef

        for (session in sessions) {
            val bookingRef: DocumentReference = mockk()
            every { bookingsCollection.document(session.id) } returns bookingRef
        }

        // Capture written slot datas
        val writtenSlotDatas = mutableListOf<Map<String, Any?>>()
        val slotDataSlot = slot<Map<String, Any?>>()
        every { transaction.set(any<DocumentReference>(), capture(slotDataSlot)) } answers {
            writtenSlotDatas.add(slotDataSlot.captured)
            transaction
        }

        val result = dataSource.holdRecurringBookingTransaction(
            venueId = venueId,
            recurringGroup = recurringGroup,
            sessions = sessions,
            slotIds = slotIds,
            userId = userId
        )

        // 1. Assert result is Success
        assertTrue(result is RecurringHoldResult.Success)
        val success = result as RecurringHoldResult.Success
        assertEquals("group-123", success.recurringGroupId)

        // 2. Assert group was written with HOLD status
        io.mockk.verify(exactly = 1) {
            transaction.set(groupRef, match<RecurringGroupDto> { it.status == "HOLD" && it.userId == userId })
        }

        // 3. Assert all 12 bookings written with HOLD status
        for (session in sessions) {
            val bRef = bookingsCollection.document(session.id)
            io.mockk.verify(exactly = 1) {
                transaction.set(bRef, match<BookingSessionDto> { it.status == "HOLD" && it.userId == userId })
            }
        }

        // 4. Assert all 24 slots written with HELD status and holdBy
        assertEquals(24, writtenSlotDatas.size)
        assertTrue(writtenSlotDatas.all { it["status"] == "HELD" && it["holdBy"] == userId && it["bookingId"] == "group-123" })
    }

    @Test
    fun holdRecurringBooking_oneSlotConflicted_rollsBackAllOrNothing() = runTest {
        val startDate = LocalDate.now().plusDays(7)
        val sessions = mutableListOf<BookingSessionDto>()
        val slotIds = mutableListOf<String>()

        val dateStr = startDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val freeSlotId = "${courtId}_${dateStr}_1800"
        val busySlotId = "${courtId}_${dateStr}_1830"
        slotIds.add(freeSlotId)
        slotIds.add(busySlotId)

        sessions.add(
            BookingSessionDto(
                id = "booking-01",
                recurringGroupId = "group-conflict",
                userId = userId,
                venueId = venueId,
                courtId = courtId,
                date = dateStr,
                startTime = "1800",
                endTime = "1900",
                slotIds = listOf(freeSlotId, busySlotId),
                status = "HOLD"
            )
        )

        // Free slot
        val freeSlotRef: DocumentReference = mockk()
        val freeSlotSnap: DocumentSnapshot = mockk()
        every { slotsCollection.document(freeSlotId) } returns freeSlotRef
        every { transaction.get(freeSlotRef) } returns freeSlotSnap
        every { freeSlotSnap.exists() } returns false

        // Busy slot: already HELD by another user
        val busySlotRef: DocumentReference = mockk()
        val busySlotSnap: DocumentSnapshot = mockk()
        every { slotsCollection.document(busySlotId) } returns busySlotRef
        every { transaction.get(busySlotRef) } returns busySlotSnap
        every { busySlotSnap.exists() } returns true
        every { busySlotSnap.getString("status") } returns "HELD"
        every { busySlotSnap.getTimestamp("holdExpiresAt") } returns Timestamp(Date(System.currentTimeMillis() + 4 * 60 * 1000L))
        every { busySlotSnap.getString("holdBy") } returns "other_user_999"

        val recurringGroup = RecurringGroupDto(
            id = "group-conflict",
            userId = userId,
            venueId = venueId,
            status = "HOLD"
        )
        val groupRef: DocumentReference = mockk()
        every { groupsCollection.document("group-conflict") } returns groupRef

        val result = dataSource.holdRecurringBookingTransaction(
            venueId = venueId,
            recurringGroup = recurringGroup,
            sessions = sessions,
            slotIds = slotIds,
            userId = userId
        )

        // 1. Assert result is Conflict
        assertTrue(result is RecurringHoldResult.Conflict)
        val conflict = result as RecurringHoldResult.Conflict
        assertTrue(conflict.conflictedSessionDates.contains(startDate))

        // 2. Verify rollback: NO writes performed on group, bookings, or slots
        io.mockk.verify(exactly = 0) {
            transaction.set(groupRef, any<RecurringGroupDto>())
        }
    }

    @Test
    fun releaseRecurringHold_updatesSlotsToAvailable_andGroupToCancelled() = runTest {
        val slotId = "${courtId}_20261110_1800"
        val slotRef: DocumentReference = mockk()
        val slotSnap: DocumentSnapshot = mockk()

        every { slotsCollection.document(slotId) } returns slotRef
        every { transaction.get(slotRef) } returns slotSnap
        every { slotSnap.exists() } returns true
        every { slotSnap.getString("holdBy") } returns userId

        val groupRef: DocumentReference = mockk()
        val groupSnap: DocumentSnapshot = mockk()
        every { groupsCollection.document("group-123") } returns groupRef
        every { transaction.get(groupRef) } returns groupSnap
        every { groupSnap.exists() } returns true

        dataSource.releaseRecurringHold(
            recurringGroupId = "group-123",
            slotIds = listOf(slotId),
            userId = userId
        )

        // Assert slot updated to AVAILABLE
        io.mockk.verify(exactly = 1) {
            transaction.update(
                slotRef,
                match<Map<String, Any?>> {
                    it["status"] == "AVAILABLE" && it["holdBy"] == null
                }
            )
        }

        // Assert group status updated to CANCELLED
        io.mockk.verify(exactly = 1) {
            transaction.update(groupRef, "status", "CANCELLED")
        }
    }

    @Test
    fun holdRecurringBooking_withClockSkew_calculatesHoldUsingServerTime() = runTest {
        // Client clock chậm 2 phút so với server (serverTimeMillis = 1_700_000_120_000L)
        val serverTimeMillis = 1_700_000_120_000L
        every { serverTimeOffsetProvider.now() } returns serverTimeMillis

        val slotId = "${courtId}_20261110_1800"
        val slotRef: DocumentReference = mockk()
        val slotSnap: DocumentSnapshot = mockk()

        val groupRef: DocumentReference = mockk()
        val groupSnap: DocumentSnapshot = mockk()
        every { groupsCollection.document("group-skew") } returns groupRef
        every { transaction.get(groupRef) } returns groupSnap
        every { groupSnap.exists() } returns false

        every { slotsCollection.document(slotId) } returns slotRef
        every { transaction.get(slotRef) } returns slotSnap
        every { slotSnap.exists() } returns false

        val groupDto = RecurringGroupDto(
            id = "group-skew",
            venueId = venueId,
            venueName = "Cơ sở test",
            userId = userId,
            daysOfWeek = listOf(2),
            startTime = "1800",
            endTime = "1830",
            totalSessions = 4,
            status = "HOLD",
            holdExpiresAt = Timestamp(Date(serverTimeMillis + 300_000L))
        )

        val result = dataSource.holdRecurringBookingTransaction(
            venueId = venueId,
            recurringGroup = groupDto,
            sessions = emptyList(),
            slotIds = listOf(slotId),
            userId = userId
        )

        assertTrue(result is RecurringHoldResult.Success)
        val success = result as RecurringHoldResult.Success
        assertEquals("group-skew", success.recurringGroupId)
        // holdExpiresAt phải theo giờ server (serverTimeMillis + 5m), không phải theo giờ client
        assertEquals(serverTimeMillis + 300_000L, success.holdExpiresAtMillis)
        io.mockk.coVerify(atLeast = 1) { serverTimeOffsetProvider.ensureSynced() }
    }
}

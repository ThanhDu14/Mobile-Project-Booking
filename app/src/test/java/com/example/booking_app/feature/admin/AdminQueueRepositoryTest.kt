package com.example.booking_app.feature.admin

import com.example.booking_app.feature.admin.data.AdminApplicationRepositoryImpl
import com.example.booking_app.feature.admin.data.AdminReportRepositoryImpl
import com.google.firebase.firestore.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminQueueRepositoryTest {
    @Test fun allApplicationStatuses_useBoundedOrderedQueriesAndRemoveListeners() = runTest {
        for (status in listOf("PENDING", "APPROVED", "REJECTED")) {
            val firestore = mockk<FirebaseFirestore>()
            val collection = mockk<CollectionReference>()
            val query = mockk<Query>()
            val registration = mockk<ListenerRegistration>(relaxed = true)
            val snapshot = mockk<QuerySnapshot>()
            val callback = slot<EventListener<QuerySnapshot>>()
            every { firestore.collection("ownerApplications") } returns collection
            every { collection.whereEqualTo("status", status) } returns query
            every { query.orderBy(any<String>(), any<Query.Direction>()) } returns query
            every { query.orderBy(any<FieldPath>()) } returns query
            every { query.limit(60) } returns query
            every { query.addSnapshotListener(capture(callback)) } returns registration
            every { snapshot.documents } returns emptyList()
            val repository = AdminApplicationRepositoryImpl(firestore, mockk(), mockk())
            val result = async { repository.observeApplications(status, 60).first() }
            runCurrent()
            callback.captured.onEvent(snapshot, null)
            assertTrue(result.await().isEmpty())
            verify { query.orderBy("submittedAt", if (status == "PENDING") Query.Direction.ASCENDING else Query.Direction.DESCENDING) }
            verify { query.limit(60); registration.remove() }
        }
    }

    @Test fun closedReports_queryBothClosedStatusesWithPagination() = runTest {
        val firestore = mockk<FirebaseFirestore>()
        val collection = mockk<CollectionReference>()
        val query = mockk<Query>()
        val registration = mockk<ListenerRegistration>(relaxed = true)
        val snapshot = mockk<QuerySnapshot>()
        val callback = slot<EventListener<QuerySnapshot>>()
        every { firestore.collection("reports") } returns collection
        every { collection.whereIn("status", listOf("RESOLVED", "DISMISSED")) } returns query
        every { query.orderBy(any<String>(), any<Query.Direction>()) } returns query
        every { query.orderBy(any<FieldPath>()) } returns query
        every { query.limit(30) } returns query
        every { query.addSnapshotListener(capture(callback)) } returns registration
        every { snapshot.documents } returns emptyList()
        val repository = AdminReportRepositoryImpl(firestore, mockk(), mockk())
        val result = async { repository.observeReports(true, 30).first() }
        runCurrent()
        callback.captured.onEvent(snapshot, null)
        assertTrue(result.await().isEmpty())
        verify { query.limit(30); registration.remove() }
    }
}

package com.example.booking_app.feature.admin

import com.example.booking_app.feature.admin.data.AdminToolsRepositoryImpl
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AdminStatsRepositoryTest {
    @Test fun dateSourceAndMissingMetrics_arePreserved() = runTest {
        val firestore = mockk<FirebaseFirestore>()
        val collection = mockk<CollectionReference>()
        val reference = mockk<DocumentReference>()
        val doc = mockk<DocumentSnapshot>()
        val values = mapOf("totalUsers" to 5L)
        every { firestore.collection("systemStats") } returns collection
        every { collection.document("20261112") } returns reference
        every { reference.get(Source.SERVER) } returns Tasks.forResult(doc)
        every { doc.exists() } returns true
        every { doc.id } returns "20261112"
        every { doc.getLong(any()) } answers { values[firstArg()] }
        every { doc.get("updatedAt") } returns "2026-11-12T00:15:00+07:00"
        every { doc.data } returns values + mapOf("updatedAt" to "2026-11-12T00:15:00+07:00")
        val stats = AdminToolsRepositoryImpl(firestore, mockk(), mockk()).getSystemStats("20261112").getOrThrow()
        assertEquals("systemStats/20261112", stats.source)
        assertEquals("20261112", stats.date)
        assertEquals(5L, stats.metrics["totalUsers"])
        assertFalse(stats.metrics.containsKey("totalVenues"))
        assertEquals("2026-11-12T00:15:00+07:00", stats.updatedAt)
    }
}

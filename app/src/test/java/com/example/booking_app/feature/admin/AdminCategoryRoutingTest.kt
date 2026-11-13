package com.example.booking_app.feature.admin

import com.example.booking_app.feature.admin.data.AdminToolsRepositoryImpl
import com.example.booking_app.feature.admin.domain.model.AdminCategoryKind
import com.example.booking_app.feature.admin.domain.model.AdminDistrict
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.QuerySnapshot
import com.google.android.gms.tasks.Tasks
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminCategoryRoutingTest {
    @Test fun amenities_readTheirOwnCollection() = runTest {
        val firestore = mockk<FirebaseFirestore>()
        val collection = mockk<CollectionReference>()
        val snapshot = mockk<QuerySnapshot>()
        every { firestore.collection(any()) } returns collection
        every { collection.get() } returns Tasks.forResult(snapshot)
        every { snapshot.documents } returns emptyList()
        val repository = AdminToolsRepositoryImpl(firestore, mockk(), mockk())
        assertTrue(repository.getCategories(AdminCategoryKind.AMENITIES).isSuccess)
        verify(exactly = 1) { firestore.collection("amenities") }
        verify(exactly = 0) { firestore.collection("districts") }
    }
}

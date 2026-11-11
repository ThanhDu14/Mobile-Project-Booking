package com.example.booking_app.feature.courtdetail.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.booking_app.BuildConfig
import com.example.booking_app.core.media.PhotoUrlResolver
import com.example.booking_app.feature.courtdetail.domain.model.VenueAvailability
import com.example.booking_app.feature.courtdetail.domain.model.VenueResult
import com.example.booking_app.testing.EmulatorAdmin
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.memoryCacheSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T076: `FirestoreVenueDetailRepository` trên Firestore Emulator (constitution IV). Dữ liệu ghi bằng REST của Emulator
 * (bỏ qua Rules); repository đọc bằng client **chưa đăng nhập** (khách) nên chịu Rules hiện tại.
 * Tự bỏ qua khi `USE_EMULATOR != "true"`. Chạy: `./gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreVenueDetailRepositoryTest {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var admin: EmulatorAdmin
    private lateinit var repository: FirestoreVenueDetailRepository

    @Before
    fun setUp() {
        assumeTrue("Chỉ chạy với Firebase Emulator (USE_EMULATOR=true)", BuildConfig.USE_EMULATOR == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val default = FirebaseApp.initializeApp(context) ?: FirebaseApp.getInstance()
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == APP_NAME }
            ?: FirebaseApp.initializeApp(context, default.options, APP_NAME)
        firestore = FirebaseFirestore.getInstance(app).apply {
            runCatching { useEmulator("10.0.2.2", 8080) }
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(memoryCacheSettings {})
                .build()
        }
        admin = EmulatorAdmin(default.options.projectId!!)
        repository = FirestoreVenueDetailRepository(firestore, PhotoUrlResolver("http://10.0.2.2:54321"))
        seed()
    }

    @After
    fun tearDown() {
        if (!::admin.isInitialized) return
        listOf(ACTIVE, HIDDEN).forEach { id ->
            admin.delete("venues/$id/courts/c1")
            admin.delete("venues/$id/courts/c2")
            admin.delete("venues/$id/priceRules/r1")
            admin.delete("venues/$id")
        }
        runBlocking { firestore.enableNetwork().await() }
    }

    private fun seed() {
        venue(ACTIVE, "ACTIVE")
        venue(HIDDEN, "HIDDEN")
        admin.put("venues/$ACTIVE/courts/c1", mapOf("name" to "Sân 1", "surfaceType" to "WOOD", "isActive" to true))
        admin.put("venues/$ACTIVE/courts/c2", mapOf("name" to "Sân 2", "surfaceType" to "MAT", "isActive" to false))
        admin.put(
            "venues/$ACTIVE/priceRules/r1",
            mapOf(
                "daysOfWeek" to listOf(6L, 7L),
                "startTime" to "0500",
                "endTime" to "2400",
                "pricePerHour" to 100000L,
                "label" to "WEEKEND",
            ),
        )
    }

    private fun venue(id: String, status: String) = admin.put(
        "venues/$id",
        mapOf(
            "name" to "Sân T076 $status",
            "address" to "1 Đường Thử",
            "districtId" to "q1",
            "location" to EmulatorAdmin.Geo(10.8, 106.7),
            "openTime" to "0500",
            "closeTime" to "2400",
            "ownerId" to "t076-owner",
            "amenityIds" to emptyList<String>(),
            "photoPaths" to listOf("t076/cover.jpg"),
            "ratingAvg" to 0.0,
            "ratingCount" to 0L,
            "status" to status,
        ),
    )

    @Test
    fun observeVenue_phatFound_dungTruong() = runBlocking {
        val result = withTimeout(TIMEOUT) { repository.observeVenue(ACTIVE, null).first() }
        val venue = (result as VenueResult.Found).venue
        assertEquals("Sân T076 ACTIVE", venue.name)
        assertEquals(VenueAvailability.ACTIVE, venue.availability)
        assertEquals("0500", venue.openTime)
        assertEquals("2400", venue.closeTime)
        assertEquals(
            listOf("http://10.0.2.2:54321/storage/v1/object/public/venue-photos/t076/cover.jpg"),
            venue.photoUrls,
        )
    }

    /**
     * [BLOCKED: D] Theo R10 người chơi đã đăng nhập vẫn đọc được cơ sở `LOCKED` nên listener phát `Found(SUSPENDED)`.
     * Rules hiện tại chỉ cho đọc `ACTIVE`, client khách nhận `PERMISSION_DENIED`: test chấp nhận cả hai cho tới khi
     * D sửa Rules, rồi đổi thành chỉ `SUSPENDED`.
     */
    @Test
    fun observeVenue_doiSangLocked_phatLai_BLOCKED_D() = runBlocking {
        val results = withTimeout(TIMEOUT) {
            val flow = repository.observeVenue(ACTIVE, null)
            val first = flow.first()
            assertTrue(first is VenueResult.Found)
            admin.put("venues/$ACTIVE", mapOf("name" to "Sân T076 ACTIVE", "status" to "LOCKED", "ownerId" to "t076-owner"))
            flow.take(2).toList()
        }
        val second = results.last()
        assertTrue(
            "Nhận $second",
            second == VenueResult.PermissionDenied ||
                (second as? VenueResult.Found)?.venue?.availability == VenueAvailability.SUSPENDED,
        )
    }

    /**
     * [BLOCKED: D] R10 cho `get` khi `resource == null` để app biết "không tồn tại". Rules hiện tại chỉ cho đọc
     * `ACTIVE` nên listener nhận `PERMISSION_DENIED` (kết quả chạy 2026-11-04): chấp nhận cả hai cho tới khi D sửa.
     */
    @Test
    fun observeVenue_xoaDocument_phatGone_BLOCKED_D() = runBlocking {
        val results = withTimeout(TIMEOUT) {
            val flow = repository.observeVenue(ACTIVE, null)
            flow.first()
            admin.delete("venues/$ACTIVE")
            flow.take(2).toList()
        }
        val last = results.last()
        assertTrue("Nhận $last", last == VenueResult.Gone || last == VenueResult.PermissionDenied)
    }

    @Test
    fun observeVenue_huyNguoiThuThap_goListener() = runBlocking {
        // first() hủy người thu thập: awaitClose gỡ listener; thu thập lại vẫn chạy bình thường (không rò listener).
        repeat(3) { withTimeout(TIMEOUT) { repository.observeVenue(ACTIVE, null).first() } }
    }

    @Test
    fun loadCourts_loadPriceRules_docDu_giu2400() = runBlocking {
        val courts = repository.loadCourts(ACTIVE)
        assertEquals(2, courts.size)
        assertEquals(1, courts.count { it.isActive })
        val rules = repository.loadPriceRules(ACTIVE)
        assertEquals(1, rules.size)
        assertEquals("2400", rules.single().endTime)
        assertEquals(listOf(6, 7), rules.single().daysOfWeek)
    }

    @Test
    fun fetchStatusFromServer_dungTrangThai_matMangTraNull() = runBlocking {
        assertEquals(VenueAvailability.ACTIVE, repository.fetchStatusFromServer(ACTIVE))
        firestore.disableNetwork().await()
        assertNull(repository.fetchStatusFromServer(ACTIVE))
    }

    @Test
    fun khachDocCoSoHidden_permissionDenied() = runBlocking {
        val result = withTimeout(TIMEOUT) { repository.observeVenue(HIDDEN, null).first() }
        assertEquals(VenueResult.PermissionDenied, result)
    }

    private companion object {
        const val APP_NAME = "t076"
        const val ACTIVE = "t076-active"
        const val HIDDEN = "t076-hidden"
        const val TIMEOUT = 20_000L
    }
}

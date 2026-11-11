package com.example.booking_app.feature.dropin.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.booking_app.BuildConfig
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.dropin.domain.model.DropInFilter
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import com.example.booking_app.feature.dropin.domain.model.Level
import com.example.booking_app.feature.dropin.domain.model.Page
import com.example.booking_app.feature.dropin.domain.model.SessionStatus
import com.example.booking_app.feature.dropin.domain.model.TimeSlot
import com.example.booking_app.testing.EmulatorAdmin
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.memoryCacheSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * T020: `FirestoreDropInSessionRepository` trên Firestore Emulator (constitution IV). Dữ liệu ghi bằng REST
 * của Emulator (bỏ qua Rules); repository đọc bằng client **chưa đăng nhập** (khách) nên chịu Rules hiện tại.
 * Tự bỏ qua khi `USE_EMULATOR != "true"`. Chạy: `./gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreDropInSessionRepositoryTest {

    private class FixedClock(private val nowMs: Long) : AppClock {
        override fun now(): Long = nowMs
        override fun elapsedRealtime(): Long = nowMs
    }

    private lateinit var firestore: FirebaseFirestore
    private lateinit var admin: EmulatorAdmin
    private lateinit var repository: FirestoreDropInSessionRepository
    private val seededSessions = mutableListOf<String>()

    /** Mốc cố định ở đầu ngày mai (giờ Việt Nam) để giờ bắt đầu và khung giờ không phụ thuộc lúc chạy test. */
    private val tomorrow: LocalDate = LocalDate.now(ZONE).plusDays(1)
    private val now: Instant = tomorrow.atStartOfDay(ZONE).toInstant()

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
        val clock = FixedClock(now.toEpochMilli())
        repository = FirestoreDropInSessionRepository(
            firestore,
            VenueStatusCache(FirestoreVenueStatusSource(firestore), clock),
            clock,
        )
        seed()
    }

    @After
    fun tearDown() {
        if (!::admin.isInitialized) return
        seededSessions.forEach { admin.delete("dropInSessions/$it") }
        listOf(VENUE_ACTIVE, VENUE_HIDDEN).forEach { id ->
            admin.delete("venues/$id/courts/c1")
            admin.delete("venues/$id")
        }
    }

    // ---------- Dữ liệu ----------

    private fun seed() {
        admin.put("venues/$VENUE_ACTIVE", mapOf("name" to "Sân A", "status" to "ACTIVE", "ownerId" to "owner1", "districtId" to "q1"))
        admin.put("venues/$VENUE_ACTIVE/courts/c1", mapOf("name" to "Sân 1", "isActive" to true))
        admin.put("venues/$VENUE_HIDDEN", mapOf("name" to "Sân B", "status" to "HIDDEN", "ownerId" to "owner1", "districtId" to "q3"))

        // Ngày mai: 06:00 (Sáng, q1, BEGINNER, 40k), 11:30 (Trưa, q3, INTERMEDIATE, 60k), 14:00 (Chiều, q1, ADVANCED, 80k),
        // 19:00 (Tối, q1, INTERMEDIATE, 120k, FULL).
        session("t_morning", hour = 6, slot = TimeSlot.MORNING, district = "q1", level = "BEGINNER", price = 40_000)
        session("t_noon", hour = 11, minute = 30, slot = TimeSlot.NOON, district = "q3", level = "INTERMEDIATE", price = 60_000)
        session("t_afternoon", hour = 14, slot = TimeSlot.AFTERNOON, district = "q1", level = "ADVANCED", price = 80_000)
        session("t_evening", hour = 19, slot = TimeSlot.EVENING, district = "q1", level = "INTERMEDIATE", price = 120_000, status = "FULL")
        // Ngày kia, mọi trình độ.
        session("t_day2", day = tomorrow.plusDays(1), hour = 18, slot = TimeSlot.EVENING, district = "q3", level = "ALL", price = 50_000)
        // Không được trả: đã hủy, đã qua, cơ sở HIDDEN, ngoài 30 ngày.
        session("x_cancelled", hour = 8, slot = TimeSlot.MORNING, district = "q1", level = "BEGINNER", price = 40_000, status = "CANCELLED")
        session("x_past", day = tomorrow.minusDays(2), hour = 8, slot = TimeSlot.MORNING, district = "q1", level = "BEGINNER", price = 40_000)
        session("x_hidden", hour = 9, slot = TimeSlot.MORNING, district = "q1", level = "BEGINNER", price = 40_000, venueId = VENUE_HIDDEN)
        session("x_far", day = tomorrow.plusDays(31), hour = 8, slot = TimeSlot.MORNING, district = "q1", level = "BEGINNER", price = 40_000)
    }

    private fun session(
        id: String,
        day: LocalDate = tomorrow,
        hour: Int,
        minute: Int = 0,
        slot: TimeSlot,
        district: String,
        level: String,
        price: Long,
        status: String = "OPEN",
        venueId: String = VENUE_ACTIVE,
    ) {
        val start = day.atTime(hour, minute).atZone(ZONE).toInstant()
        val docId = "${PREFIX}_$id"
        seededSessions += docId
        admin.put(
            "dropInSessions/$docId",
            mapOf(
                "venueId" to venueId,
                "ownerId" to "owner1",
                "districtId" to district,
                "courtIds" to listOf("c1"),
                "date" to DATE_KEY.format(day),
                "startAt" to start,
                "endAt" to start.plus(2, ChronoUnit.HOURS),
                "timeSlot" to slot.name,
                "venueSnapshot" to mapOf("name" to "Sân", "address" to "1 Lê Lợi", "districtName" to district),
                "level" to level,
                "capacity" to 12L,
                "registeredCount" to if (status == "FULL") 12L else 3L,
                "pricePerPerson" to price,
                "status" to status,
            ),
        )
    }

    private fun page(filter: DropInFilter = DropInFilter()): Page<DropInSession> =
        runBlocking { repository.page(filter, null).getOrThrow() }

    /** Chỉ xét buổi do test này ghi (Emulator có thể còn dữ liệu seed khác). */
    private fun Page<DropInSession>.ids(): List<String> =
        items.map { it.id }.filter { it.startsWith(PREFIX) }.map { it.removePrefix("${PREFIX}_") }

    // ---------- Test ----------

    @Test
    fun khongLoc_traBuoiOpenFull_sapTheoStartAt_boHuyDaQuaHiddenNgoai30Ngay() {
        assertEquals(listOf("t_morning", "t_noon", "t_afternoon", "t_evening", "t_day2"), page().ids())
    }

    @Test
    fun locKhuVuc() {
        assertEquals(listOf("t_noon", "t_day2"), page(DropInFilter(districtIds = setOf("q3"))).ids())
        assertEquals(
            listOf("t_morning", "t_noon", "t_afternoon", "t_evening", "t_day2"),
            page(DropInFilter(districtIds = setOf("q1", "q3"))).ids(),
        )
    }

    @Test
    fun locNgay() {
        assertEquals(listOf("t_day2"), page(DropInFilter(date = tomorrow.plusDays(1))).ids())
    }

    @Test
    fun locKhungGio() {
        assertEquals(listOf("t_morning"), page(DropInFilter(timeSlot = TimeSlot.MORNING)).ids())
        assertEquals(listOf("t_noon"), page(DropInFilter(timeSlot = TimeSlot.NOON)).ids())
        assertEquals(listOf("t_afternoon"), page(DropInFilter(timeSlot = TimeSlot.AFTERNOON)).ids())
        assertEquals(listOf("t_evening", "t_day2"), page(DropInFilter(timeSlot = TimeSlot.EVENING)).ids())
    }

    @Test
    fun locTrinhDo() {
        assertEquals(listOf("t_noon", "t_evening"), page(DropInFilter(level = Level.INTERMEDIATE)).ids())
    }

    @Test
    fun locGiaToiDa() {
        assertEquals(listOf("t_morning", "t_noon", "t_day2"), page(DropInFilter(maxPrice = 60_000)).ids())
    }

    @Test
    fun ketHopNhieuBoLoc() {
        val filter = DropInFilter(districtIds = setOf("q1"), date = tomorrow, level = Level.INTERMEDIATE)
        assertEquals(listOf("t_evening"), page(filter).ids())
    }

    @Test
    fun buoiFull_vanHien_voiTrangThaiFull() {
        val evening = page().items.first { it.id == "${PREFIX}_t_evening" }
        assertEquals(SessionStatus.FULL, evening.status)
        assertEquals(0, evening.remaining)
    }

    @Test
    fun phanTrang_startAfter_khongTrungKhongSot() = runBlocking {
        // Thêm đủ buổi để có hơn một trang.
        (0 until Page.PAGE_SIZE + 5).forEach { i ->
            session("p_%02d".format(i), day = tomorrow.plusDays(2), hour = 6 + i / 2, minute = (i % 2) * 30,
                slot = TimeSlot.MORNING, district = "q9", level = "BEGINNER", price = 40_000)
        }
        val filter = DropInFilter(districtIds = setOf("q9"))
        val first = repository.page(filter, null).getOrThrow()
        assertEquals(Page.PAGE_SIZE, first.items.size)
        assertTrue(first.canLoadMore)
        val second = repository.page(filter, first.next).getOrThrow()
        assertEquals(5, second.items.size)
        assertFalse(second.canLoadMore)
        val all = (first.items + second.items).map { it.id }
        assertEquals(all.distinct(), all)
        assertEquals((0 until Page.PAGE_SIZE + 5).map { "${PREFIX}_p_%02d".format(it) }, all)
    }

    @Test
    fun observe_docTenSanCon_vaCapNhatRealtime() = runBlocking {
        val id = "${PREFIX}_t_morning"
        val initial = withTimeout(10_000) { repository.observe(id).filterNotNull().first() }
        assertEquals(listOf("Sân 1"), initial.courtNames)
        assertTrue(initial.venueActive)
        assertEquals(9, initial.remaining)

        admin.put(
            "dropInSessions/$id",
            mapOf(
                "venueId" to VENUE_ACTIVE, "ownerId" to "owner1", "districtId" to "q1", "courtIds" to listOf("c1"),
                "date" to DATE_KEY.format(tomorrow), "startAt" to initial.startAt, "endAt" to initial.endAt,
                "timeSlot" to "MORNING", "level" to "BEGINNER", "capacity" to 12L, "registeredCount" to 11L,
                "pricePerPerson" to 40_000L, "status" to "OPEN",
            ),
        )
        val updated = withTimeout(10_000) { repository.observe(id).filterNotNull().first { it.registeredCount == 11 } }
        assertEquals(1, updated.remaining)
    }

    @Test
    fun observe_coSoHidden_venueActiveFalse_buoiKhongTonTai_null() = runBlocking {
        val hidden = withTimeout(10_000) { repository.observe("${PREFIX}_x_hidden").filterNotNull().first() }
        assertFalse(hidden.venueActive)
        assertNull(withTimeout(10_000) { repository.observe("${PREFIX}_khong_co").first() })
    }

    private companion object {
        const val APP_NAME = "dropin-session-test"
        const val PREFIX = "t020"
        const val VENUE_ACTIVE = "t020_venue_active"
        const val VENUE_HIDDEN = "t020_venue_hidden"
        val ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
        val DATE_KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}

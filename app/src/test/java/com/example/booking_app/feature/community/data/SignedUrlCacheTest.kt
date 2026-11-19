package com.example.booking_app.feature.community.data

import com.example.booking_app.core.media.SignedUrlBatch
import com.example.booking_app.core.media.SignedUrlCache
import com.example.booking_app.core.testing.FakeClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** T044: cache signed URL: trả URL còn hạn (trừ 5 phút an toàn), gom ≤ 40 path, hết hạn xin lại. */
class SignedUrlCacheTest {

    @Test
    fun `tra URL da cache khi con han tru 5 phut an toan`() = runTest {
        var now = 1_000_000L
        val exp = Instant.ofEpochMilli(now + 60L * 60 * 1000)
        var calls = 0
        val cache = SignedUrlCache(
            fetch = { paths ->
                calls++
                Result.success(SignedUrlBatch(paths.associateWith { "https://cdn/$it?v=$calls" }, exp))
            },
            clock = FakeClock { now },
        )

        val first = cache.resolve(listOf("g1/p1/0.jpg"))
        assertEquals("https://cdn/g1/p1/0.jpg?v=1", first["g1/p1/0.jpg"])
        assertEquals(1, calls)

        // Còn đúng 5 phút + 1 ms trước khi hết hạn → vẫn dùng cache.
        now = exp.toEpochMilli() - SignedUrlCache.SAFETY_MARGIN_MS - 1L
        val second = cache.resolve(listOf("g1/p1/0.jpg"))
        assertEquals("https://cdn/g1/p1/0.jpg?v=1", second["g1/p1/0.jpg"])
        assertEquals(1, calls)
    }

    @Test
    fun `het han hoac trong khoang 5 phut an toan thi xin lai`() = runTest {
        var now = 1_000_000L
        val exp1 = Instant.ofEpochMilli(now + 60L * 60 * 1000)
        val exp2 = Instant.ofEpochMilli(now + 2 * 60L * 60 * 1000)
        var calls = 0
        val cache = SignedUrlCache(
            fetch = { paths ->
                calls++
                val exp = if (calls == 1) exp1 else exp2
                Result.success(SignedUrlBatch(paths.associateWith { "https://cdn/$it?v=$calls" }, exp))
            },
            clock = FakeClock { now },
        )

        cache.resolve(listOf("g1/p1/0.jpg"))
        assertEquals(1, calls)

        // Đúng mốc trừ 5 phút an toàn → coi như hết hạn, xin lại.
        now = exp1.toEpochMilli() - SignedUrlCache.SAFETY_MARGIN_MS
        val refreshed = cache.resolve(listOf("g1/p1/0.jpg"))
        assertEquals("https://cdn/g1/p1/0.jpg?v=2", refreshed["g1/p1/0.jpg"])
        assertEquals(2, calls)
    }

    @Test
    fun `gom path con thieu thanh mot loi goi toi da 40 path`() = runTest {
        val now = 1_000_000L
        val exp = Instant.ofEpochMilli(now + 60L * 60 * 1000)
        val batches = mutableListOf<List<String>>()
        val cache = SignedUrlCache(
            fetch = { paths ->
                batches += paths
                Result.success(SignedUrlBatch(paths.associateWith { "https://cdn/$it" }, exp))
            },
            clock = FakeClock { now },
        )

        // Nạp trước 2 path vào cache.
        cache.resolve(listOf("p0", "p1"))
        assertEquals(listOf(listOf("p0", "p1")), batches)

        // Yêu cầu 45 path mới + 2 path cũ + trùng lặp → chỉ gọi cho 45 path mới, chia 40 + 5.
        val all = (0 until 47).map { "p$it" } + listOf("p0", "p10")
        val resolved = cache.resolve(all)

        assertEquals(47, resolved.size)
        assertEquals(3, batches.size)
        assertEquals(40, batches[1].size)
        assertEquals(5, batches[2].size)
        assertEquals("p2", batches[1].first())
        assertEquals("p46", batches[2].last())
    }

    @Test
    fun `danh sach rong khong goi mang`() = runTest {
        var calls = 0
        val cache = SignedUrlCache(
            fetch = {
                calls++
                Result.success(SignedUrlBatch(emptyMap(), null))
            },
            clock = FakeClock { 0L },
        )

        assertEquals(emptyMap<String, String>(), cache.resolve(emptyList()))
        assertEquals(0, calls)
    }

    @Test
    fun `invalidate xoa dung path khoi cache de lan resolve sau xin URL moi`() = runTest {
        val now = 1_000_000L
        val exp = Instant.ofEpochMilli(now + 60L * 60 * 1000)
        var calls = 0
        val cache = SignedUrlCache(
            fetch = { paths ->
                calls++
                Result.success(SignedUrlBatch(paths.associateWith { "https://cdn/$it?v=$calls" }, exp))
            },
            clock = FakeClock { now },
        )

        cache.resolve(listOf("g1/p1/0.jpg", "g1/p1/1.jpg"))
        assertEquals(1, calls)

        cache.invalidate(listOf("g1/p1/0.jpg"))
        val after = cache.resolve(listOf("g1/p1/0.jpg", "g1/p1/1.jpg"))

        assertEquals(2, calls)
        assertEquals("https://cdn/g1/p1/0.jpg?v=2", after["g1/p1/0.jpg"])
        assertEquals("https://cdn/g1/p1/1.jpg?v=1", after["g1/p1/1.jpg"])
    }
}

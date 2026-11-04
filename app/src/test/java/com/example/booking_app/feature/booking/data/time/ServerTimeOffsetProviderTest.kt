package com.example.booking_app.feature.booking.data.time

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ServerTimeOffsetProviderTest {

    private val auth: FirebaseAuth = mockk(relaxed = true)
    private val user: FirebaseUser = mockk(relaxed = true)
    private val tokenResult: GetTokenResult = mockk(relaxed = true)
    private lateinit var provider: ServerTimeOffsetProvider

    @Before
    fun setUp() {
        provider = ServerTimeOffsetProvider(auth)
    }

    @Test
    fun defaultOffset_isZero_nowReturnsApproxLocalTime() {
        assertEquals(0L, provider.getOffsetMillis())
        val before = System.currentTimeMillis()
        val now = provider.now()
        val after = System.currentTimeMillis()
        assertTrue(now in before..after)
    }

    @Test
    fun setOffsetMillis_adjustsNow() {
        val offset = -60_000L // Server chậm hơn 1 phút so với máy client
        provider.setOffsetMillis(offset)

        assertEquals(offset, provider.getOffsetMillis())
        val before = System.currentTimeMillis() + offset
        val now = provider.now()
        val after = System.currentTimeMillis() + offset
        assertTrue(now in before..after)
    }

    @Test
    fun syncOffset_whenUserSignedIn_computesOffsetFromTokenIat() = runTest {
        val serverTimeSeconds = 1_700_000_000L
        every { auth.currentUser } returns user
        every { user.getIdToken(false) } returns Tasks.forResult(tokenResult)
        every { tokenResult.claims } returns mapOf("iat" to serverTimeSeconds)
        every { tokenResult.issuedAtTimestamp } returns serverTimeSeconds

        val offset = provider.syncOffset()
        val localTime = System.currentTimeMillis()
        val expectedApproxOffset = (serverTimeSeconds * 1000L) - localTime

        // Offset tính toán phải xấp xỉ expectedOffset (trong khoảng dung sai mạng/thực thi)
        assertTrue(Math.abs(offset - expectedApproxOffset) < 1000L)
        assertTrue(provider.getLastSyncedAtMillis() > 0L)
    }

    @Test
    fun syncOffset_whenNoUser_keepsExistingOffset() = runTest {
        every { auth.currentUser } returns null
        provider.setOffsetMillis(5000L)

        val offset = provider.syncOffset()
        assertEquals(5000L, offset)
    }

    @Test
    fun ensureSynced_whenNeverSynced_callsSyncOffset() = runTest {
        val serverTimeSeconds = 1_700_000_000L
        every { auth.currentUser } returns user
        every { user.getIdToken(false) } returns Tasks.forResult(tokenResult)
        every { tokenResult.claims } returns mapOf("iat" to serverTimeSeconds)
        every { tokenResult.issuedAtTimestamp } returns serverTimeSeconds

        assertEquals(0L, provider.getLastSyncedAtMillis())
        val offset = provider.ensureSynced()

        verify(exactly = 1) { user.getIdToken(false) }
        assertTrue(provider.getLastSyncedAtMillis() > 0L)
    }

    @Test
    fun ensureSynced_whenCalledMultipleTimesConsecutively_onlySyncsOnce() = runTest {
        val serverTimeSeconds = 1_700_000_000L
        every { auth.currentUser } returns user
        every { user.getIdToken(false) } returns Tasks.forResult(tokenResult)
        every { tokenResult.claims } returns mapOf("iat" to serverTimeSeconds)
        every { tokenResult.issuedAtTimestamp } returns serverTimeSeconds

        provider.ensureSynced()
        provider.ensureSynced()
        provider.ensureSynced()

        // Chỉ gọi getIdToken đúng 1 lần mặc dù gọi ensureSynced 3 lần liên tiếp
        verify(exactly = 1) { user.getIdToken(false) }
    }

    @Test
    fun ensureSynced_whenLastSyncOlderThanOneHour_resyncs() = runTest {
        val serverTimeSeconds = 1_700_000_000L
        every { auth.currentUser } returns user
        every { user.getIdToken(false) } returns Tasks.forResult(tokenResult)
        every { tokenResult.claims } returns mapOf("iat" to serverTimeSeconds)
        every { tokenResult.issuedAtTimestamp } returns serverTimeSeconds

        // Giả lập lần sync trước cách đây 2 tiếng (> 1 giờ)
        provider.setLastSyncedAtMillis(System.currentTimeMillis() - 2 * 60 * 60 * 1000L)

        provider.ensureSynced()

        verify(exactly = 1) { user.getIdToken(false) }
    }

    @Test
    fun ensureSynced_whenSyncThrowsException_keepsExistingOffsetAndDoesNotThrow() = runTest {
        every { auth.currentUser } returns user
        every { user.getIdToken(false) } returns Tasks.forException(RuntimeException("Network failure"))
        provider.setOffsetMillis(12_345L)

        val resultOffset = provider.ensureSynced()

        assertEquals(12_345L, resultOffset)
    }

    @Test
    fun ensureSynced_withForceRefresh_resyncsEvenIfRecent() = runTest {
        val serverTimeSeconds = 1_700_000_000L
        every { auth.currentUser } returns user
        every { user.getIdToken(true) } returns Tasks.forResult(tokenResult)
        every { tokenResult.claims } returns mapOf("iat" to serverTimeSeconds)
        every { tokenResult.issuedAtTimestamp } returns serverTimeSeconds

        // Vừa mới sync cách đây 1 phút
        provider.setLastSyncedAtMillis(System.currentTimeMillis() - 60 * 1000L)

        provider.ensureSynced(forceRefresh = true)

        verify(exactly = 1) { user.getIdToken(true) }
    }
}

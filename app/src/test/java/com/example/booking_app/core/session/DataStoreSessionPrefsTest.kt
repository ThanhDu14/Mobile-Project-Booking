package com.example.booking_app.core.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSessionPrefsTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Before
    fun setUp() {
        // DataStore FileStorage uses File.renameTo, which fails on Windows if the destination file exists.
        // Therefore, multiple consecutive edits on Windows will crash with IOException.
        // We skip this test on Windows.
        val osName = System.getProperty("os.name") ?: ""
        Assume.assumeFalse(osName.lowercase().startsWith("win"))
    }

    private fun createPrefs(scope: CoroutineScope): DataStoreSessionPrefs {
        return DataStoreSessionPrefs(
            PreferenceDataStoreFactory.create(scope = scope) {
                File(folder.root, "session_prefs_${UUID.randomUUID()}.preferences_pb")
            }
        )
    }

    @Test
    fun `luu va doc PendingSignup`() = runTest {
        val prefs = createPrefs(backgroundScope)
        assertNull(prefs.pendingSignup())
        prefs.savePendingSignup(PendingSignup("u1", "Nguyễn An", 42L))
        assertEquals(PendingSignup("u1", "Nguyễn An", 42L), prefs.pendingSignup())
        prefs.clearPendingSignup()
        assertNull(prefs.pendingSignup())
    }

    @Test
    fun `dang xuat xoa du lieu phien nhung giu welcome_seen va moc dat lai mat khau`() = runTest {
        val prefs = createPrefs(backgroundScope)
        prefs.setWelcomeSeen()
        prefs.savePendingSignup(PendingSignup("u1", "Nguyễn An", 42L))
        prefs.setLastVerificationEmailAt(100L)
        prefs.setLastPasswordResetAt(200L)

        prefs.clearOnSignOut()

        assertNull(prefs.pendingSignup())
        assertNull(prefs.lastVerificationEmailAt())
        assertTrue(prefs.welcomeSeen.first())
        // Mốc đặt lại mật khẩu tự hết sau 60 giây, không gắn với phiên (data-model §3).
        assertEquals(200L, prefs.lastPasswordResetAt())
    }
}

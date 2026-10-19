package com.example.booking_app.core.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreSessionPrefsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs by lazy {
        DataStoreSessionPrefs(
            PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "session_prefs.preferences_pb") },
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `luu va doc PendingSignup`() = runTest {
        assertNull(prefs.pendingSignup())
        prefs.savePendingSignup(PendingSignup("u1", "Nguyễn An", 42L))
        assertEquals(PendingSignup("u1", "Nguyễn An", 42L), prefs.pendingSignup())
        prefs.clearPendingSignup()
        assertNull(prefs.pendingSignup())
    }

    @Test
    fun `dang xuat xoa du lieu phien nhung giu welcome_seen va moc dat lai mat khau`() = runTest {
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

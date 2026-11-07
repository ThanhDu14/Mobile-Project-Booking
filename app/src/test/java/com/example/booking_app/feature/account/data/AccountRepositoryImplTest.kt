package com.example.booking_app.feature.account.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.feature.account.domain.model.AccountError
import com.example.booking_app.feature.account.domain.model.PlayerLevel
import com.example.booking_app.feature.account.domain.model.ProfileDraft
import com.example.booking_app.feature.account.domain.model.OwnerApplicationSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AccountRepositoryImplTest {

    private val dataSource = FakeAccountDataSource()
    private val session = FakeSessionManager(SessionState.SignedIn("u1", AppRole.PLAYER, emailVerified = true))
    private val repository = AccountRepositoryImpl(dataSource, session)
    private val saved = ProfileDraft("Lê Hoàng Nam", null, listOf("q1"))

    @Test
    fun `chi gui truong da doi, luon kem updatedAt`() = runTest {
        val wrote = repository.updateProfile(saved, saved.copy(level = PlayerLevel.INTERMEDIATE))
        assertTrue(wrote)
        assertEquals(
            listOf("u1" to mapOf("level" to "INTERMEDIATE", "updatedAt" to ServerTimestamp)),
            dataSource.writes,
        )
    }

    @Test
    fun `doi ca ba truong, ten duoc trim`() = runTest {
        repository.updateProfile(saved, ProfileDraft("  Trần Thu Hà ", PlayerLevel.ADVANCED, listOf("q3", "q10")))
        assertEquals(
            mapOf(
                "displayName" to "Trần Thu Hà",
                "level" to "ADVANCED",
                "favoriteDistrictIds" to listOf("q3", "q10"),
                "updatedAt" to ServerTimestamp,
            ),
            dataSource.writes.single().second,
        )
    }

    @Test
    fun `bo chon trinh do thi ghi null`() = runTest {
        repository.updateProfile(saved.copy(level = PlayerLevel.BEGINNER), saved)
        assertEquals(mapOf("level" to null, "updatedAt" to ServerTimestamp), dataSource.writes.single().second)
    }

    @Test
    fun `khong co truong nao doi thi khong ghi`() = runTest {
        assertFalse(repository.updateProfile(saved, saved.copy(displayName = " Lê Hoàng Nam ")))
        assertTrue(dataSource.writes.isEmpty())
    }

    @Test
    fun `setAvatarPath ghi duong dan kem updatedAt`() = runTest {
        repository.setAvatarPath("u1/avatar_1.jpg")
        repository.setAvatarPath(null)
        assertEquals(
            listOf(
                "u1" to mapOf("avatarPath" to "u1/avatar_1.jpg", "updatedAt" to ServerTimestamp),
                "u1" to mapOf("avatarPath" to null, "updatedAt" to ServerTimestamp),
            ),
            dataSource.writes,
        )
    }

    @Test
    fun `loi mang hoac het gio thanh AccountError Network`() = runTest {
        for (error in listOf(AppError.Network, AppError.Timeout)) {
            dataSource.error = error
            try {
                repository.updateProfile(saved, saved.copy(level = PlayerLevel.BEGINNER))
                fail("phải ném lỗi")
            } catch (e: AccountError) {
                assertEquals(AccountError.Network, e)
            }
        }
    }

    @Test
    fun `chua dang nhap thi bao NotSignedIn, khong ghi`() = runTest {
        session.sessionState.value = SessionState.SignedOut(null)
        try {
            repository.setAvatarPath(null)
            fail("phải ném lỗi")
        } catch (e: AppError) {
            assertEquals(AppError.NotSignedIn, e)
        }
        assertTrue(dataSource.writes.isEmpty())
    }
}

class FakeAccountDataSource : AccountDataSource {
    val writes = mutableListOf<Pair<String, Map<String, Any?>>>()
    var error: Throwable? = null

    override suspend fun update(uid: String, fields: Map<String, Any?>) {
        error?.let { throw it }
        writes += uid to fields
    }

    override fun observeLatestOwnerApplication(uid: String): Flow<OwnerApplicationSummary?> = flowOf(null)
}

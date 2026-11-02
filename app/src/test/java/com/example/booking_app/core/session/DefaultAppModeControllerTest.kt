package com.example.booking_app.core.session

import com.example.booking_app.core.network.AppError
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAppModeControllerTest {

    private val session = FakeSessionManager()

    private fun signedIn(role: AppRole) = SessionState.SignedIn("u1", role, emailVerified = true)

    private fun TestScope.createController(): DefaultAppModeController {
        val controller = DefaultAppModeController(session, backgroundScope)
        runCurrent()
        return controller
    }

    @Test
    fun `chu san dang nhap thi mac dinh OWNER, vai tro khac luon PLAYER`() = runTest {
        val controller = createController()
        session.sessionState.value = signedIn(AppRole.OWNER)
        runCurrent()
        assertEquals(AppMode.OWNER, controller.mode.value)

        session.sessionState.value = signedIn(AppRole.PLAYER)
        runCurrent()
        assertEquals(AppMode.PLAYER, controller.mode.value)

        session.sessionState.value = signedIn(AppRole.ADMIN)
        runCurrent()
        assertEquals(AppMode.PLAYER, controller.mode.value)
    }

    @Test
    fun `dang xuat roi dang nhap lai chu san thi ve OWNER`() = runTest {
        session.sessionState.value = signedIn(AppRole.OWNER)
        val controller = createController()
        controller.switchTo(AppMode.PLAYER)
        assertEquals(AppMode.PLAYER, controller.mode.value)

        session.sessionState.value = SessionState.SignedOut(null)
        runCurrent()
        session.sessionState.value = signedIn(AppRole.OWNER)
        runCurrent()
        assertEquals(AppMode.OWNER, controller.mode.value)
    }

    @Test
    fun `sang PLAYER doi ngay va khong lam moi token`() = runTest {
        session.sessionState.value = signedIn(AppRole.OWNER)
        val controller = createController()
        val result = controller.switchTo(AppMode.PLAYER)
        assertEquals(Result.success(AppMode.PLAYER), result)
        assertEquals(AppMode.PLAYER, controller.mode.value)
        assertTrue(session.refreshCalls.isEmpty())
    }

    @Test
    fun `sang OWNER lam moi token, claim van OWNER thi chuyen`() = runTest {
        session.sessionState.value = signedIn(AppRole.OWNER)
        val controller = createController()
        controller.switchTo(AppMode.PLAYER)

        val result = controller.switchTo(AppMode.OWNER)

        assertEquals(listOf(true), session.refreshCalls)
        assertEquals(Result.success(AppMode.OWNER), result)
        assertEquals(AppMode.OWNER, controller.mode.value)
    }

    @Test
    fun `sang OWNER khi quyen da bi thu hoi thi o lai PLAYER va bao OwnerRevokedException`() = runTest {
        session.sessionState.value = signedIn(AppRole.OWNER)
        val controller = createController()
        controller.switchTo(AppMode.PLAYER)
        session.onRefresh = { session.sessionState.value = signedIn(AppRole.PLAYER) }

        val result = controller.switchTo(AppMode.OWNER)
        runCurrent()

        assertTrue(result.exceptionOrNull() is OwnerRevokedException)
        assertEquals(AppMode.PLAYER, controller.mode.value)
    }

    @Test
    fun `mat mang khi lam moi token thi dung claim dang co`() = runTest {
        session.sessionState.value = signedIn(AppRole.OWNER)
        val controller = createController()
        controller.switchTo(AppMode.PLAYER)
        session.onRefresh = { throw AppError.Network }

        val result = controller.switchTo(AppMode.OWNER)

        assertEquals(Result.success(AppMode.OWNER), result)
        assertEquals(AppMode.OWNER, controller.mode.value)
    }

    @Test
    fun `lam moi token qua 3 giay thi dung claim dang co`() = runTest {
        session.sessionState.value = signedIn(AppRole.OWNER)
        val controller = createController()
        controller.switchTo(AppMode.PLAYER)
        session.onRefresh = { awaitCancellation() }

        val start = testScheduler.currentTime
        val result = controller.switchTo(AppMode.OWNER)

        assertEquals(3_000L, testScheduler.currentTime - start)
        assertEquals(Result.success(AppMode.OWNER), result)
    }

    @Test
    fun `nguoi khong phai chu san xin sang OWNER thi that bai va che do khong doi`() = runTest {
        session.sessionState.value = signedIn(AppRole.PLAYER)
        val controller = createController()

        val result = controller.switchTo(AppMode.OWNER)

        assertTrue(result.isFailure)
        assertEquals(AppMode.PLAYER, controller.mode.value)
    }

    @Test
    fun `modeFor tinh dong bo, chu san vua dang nhap la OWNER du mode chua cap nhat`() = runTest {
        val controller = createController()
        assertEquals(AppMode.OWNER, controller.modeFor(signedIn(AppRole.OWNER)))
        assertEquals(AppMode.PLAYER, controller.modeFor(signedIn(AppRole.PLAYER)))
        assertEquals(AppMode.PLAYER, controller.modeFor(SessionState.SignedOut(null)))
    }

    @Test
    fun `modeFor giu lua chon Nguoi choi cua chu san trong phien`() = runTest {
        session.sessionState.value = signedIn(AppRole.OWNER)
        val controller = createController()
        controller.switchTo(AppMode.PLAYER)
        assertEquals(AppMode.PLAYER, controller.modeFor(session.sessionState.value))
    }
}

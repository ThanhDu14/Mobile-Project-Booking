package com.example.booking_app.feature.account.domain.usecase

import com.example.booking_app.core.session.AppMode
import com.example.booking_app.core.session.OwnerRevokedException
import com.example.booking_app.feature.account.FakeAppModeController
import com.example.booking_app.feature.account.domain.model.AccountError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SwitchModeUseCaseTest {

    private val controller = FakeAppModeController()
    private val useCase = SwitchModeUseCase(controller)

    @Test
    fun `thanh cong thi tra che do moi`() = runTest {
        assertEquals(Result.success(AppMode.OWNER), useCase(AppMode.OWNER, revokedReason = null))
    }

    @Test
    fun `OwnerRevokedException thanh AccountError OwnerRevoked kem ly do`() = runTest {
        controller.result = { Result.failure(OwnerRevokedException()) }
        val error = useCase(AppMode.OWNER, revokedReason = "Sai sự thật").exceptionOrNull()
        assertEquals(AccountError.OwnerRevoked("Sai sự thật"), error)
    }
}

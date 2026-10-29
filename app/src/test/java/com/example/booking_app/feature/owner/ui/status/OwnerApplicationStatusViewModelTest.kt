package com.example.booking_app.feature.owner.ui.status

import com.example.booking_app.core.testing.MainDispatcherRule
import com.example.booking_app.feature.owner.domain.OwnerApplicationStatusMapping
import com.example.booking_app.feature.owner.domain.OwnerApplicationStatusPresentation
import com.example.booking_app.feature.owner.domain.OwnerStatus
import com.example.booking_app.feature.owner.domain.usecase.GetOwnerApplicationStatusUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OwnerApplicationStatusViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun `refresh exposes loading then server error`() = runTest {
        val useCase = mockk<GetOwnerApplicationStatusUseCase>()
        coEvery { useCase() } throws IllegalStateException("private details omitted")
        val viewModel = OwnerApplicationStatusViewModel(useCase)

        viewModel.beginRefresh()
        advanceUntilIdle()
        assertEquals(
            OwnerApplicationStatusUiState.Error("Không thể tải trạng thái. Vui lòng thử lại."),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `content state exposes authoritative presentation`() = runTest {
        val presentation = OwnerApplicationStatusPresentation(OwnerStatus.REJECTED, null)
        val useCase = mockk<GetOwnerApplicationStatusUseCase>()
        coEvery { useCase() } returns OwnerApplicationStatusMapping.Content(presentation)
        val viewModel = OwnerApplicationStatusViewModel(useCase)

        viewModel.beginRefresh()
        advanceUntilIdle()

        assertEquals(OwnerApplicationStatusUiState.Content(presentation), viewModel.uiState.value)
    }

    @Test
    fun `a slower earlier refresh cannot overwrite a newer authoritative result`() = runTest {
        val first = CompletableDeferred<OwnerApplicationStatusMapping>()
        val latest = OwnerApplicationStatusMapping.Content(OwnerApplicationStatusPresentation(OwnerStatus.PENDING, null))
        val useCase = mockk<GetOwnerApplicationStatusUseCase>()
        var callCount = 0
        coEvery { useCase() } coAnswers {
            if (callCount++ == 0) first.await() else latest
        }
        val viewModel = OwnerApplicationStatusViewModel(useCase)

        viewModel.beginRefresh()
        runCurrent()
        viewModel.beginRefresh()
        runCurrent()
        first.complete(OwnerApplicationStatusMapping.Content(OwnerApplicationStatusPresentation(OwnerStatus.REJECTED, null)))
        advanceUntilIdle()

        assertEquals(OwnerApplicationStatusUiState.Content(latest.value), viewModel.uiState.value)
    }

    @Test
    fun `failed refresh replaces previous content and never leaves stale status authoritative`() = runTest {
        val useCase = mockk<GetOwnerApplicationStatusUseCase>()
        coEvery { useCase() } returns OwnerApplicationStatusMapping.Content(
            OwnerApplicationStatusPresentation(OwnerStatus.APPROVED, null),
        ) andThenThrows IllegalStateException("offline")
        val viewModel = OwnerApplicationStatusViewModel(useCase)

        viewModel.beginRefresh()
        advanceUntilIdle()
        assertEquals(OwnerStatus.APPROVED, (viewModel.uiState.value as OwnerApplicationStatusUiState.Content).value.ownerStatus)

        viewModel.beginRefresh()
        advanceUntilIdle()
        assertEquals(
            OwnerApplicationStatusUiState.Error("Không thể tải trạng thái. Vui lòng thử lại."),
            viewModel.uiState.value,
        )
    }
}

package com.example.booking_app.feature.courtdetail.ui

import androidx.lifecycle.SavedStateHandle
import com.example.booking_app.core.session.AppRole
import com.example.booking_app.core.session.FakeSessionManager
import com.example.booking_app.core.session.SessionState
import com.example.booking_app.core.testing.FakeClock
import com.example.booking_app.feature.courtdetail.FakeCatalogRepository
import com.example.booking_app.feature.courtdetail.FakeLatestReviewsSource
import com.example.booking_app.feature.courtdetail.FakeLocationProvider
import com.example.booking_app.feature.courtdetail.FakeVenueDetailRepository
import com.example.booking_app.feature.courtdetail.domain.usecase.CheckVenueBookableUseCase
import com.example.booking_app.feature.courtdetail.domain.usecase.LoadCourtSummaryUseCase
import com.example.booking_app.feature.courtdetail.domain.usecase.LoadLatestReviewsUseCase
import com.example.booking_app.feature.courtdetail.domain.usecase.LoadPriceTableUseCase
import com.example.booking_app.feature.courtdetail.domain.usecase.ObserveVenueDetailUseCase
import com.example.booking_app.feature.courtdetail.vnMillis
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope

/** Dựng `CourtDetailViewModel` với các bản giả; các test US1…US6 dùng chung để constructor đổi chỉ sửa một chỗ. */
class CourtDetailHarness(
    private val scope: TestScope,
    venueId: String = "demo-truong-chinh-909",
    date: String? = null,
    start: String? = null,
    end: String? = null,
    /** Thứ hai 26/10/2026 20:00 giờ Việt Nam. */
    startMillis: Long = vnMillis(2026, 10, 26, 20, 0),
    session: SessionState = SessionState.SignedIn("player1", AppRole.PLAYER, true),
) {
    val repository = FakeVenueDetailRepository()
    val catalog = FakeCatalogRepository()
    val location = FakeLocationProvider()
    val reviews = FakeLatestReviewsSource()
    val session = FakeSessionManager(session)
    val clock = FakeClock { startMillis + scope.testScheduler.currentTime }
    val handle = SavedStateHandle(
        mapOf("venueId" to venueId, "date" to date, "startTime" to start, "endTime" to end),
    )

    fun viewModel(savedState: SavedStateHandle = handle) = CourtDetailViewModel(
        savedStateHandle = savedState,
        observeVenueDetail = ObserveVenueDetailUseCase(repository, catalog, session),
        loadCourtSummary = LoadCourtSummaryUseCase(repository),
        loadPriceTable = LoadPriceTableUseCase(repository),
        checkVenueBookable = CheckVenueBookableUseCase(repository),
        loadLatestReviews = LoadLatestReviewsUseCase(reviews),
        sessionManager = session,
        locationProvider = location,
        clock = clock,
    )

    /** Giữ một người thu thập `uiState` như Fragment ở `STARTED`. */
    fun CourtDetailViewModel.collectIn(): Job = scope.backgroundScope.launch { uiState.collect {} }
}

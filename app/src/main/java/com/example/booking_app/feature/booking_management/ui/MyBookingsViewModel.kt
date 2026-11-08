package com.example.booking_app.feature.booking_management.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.booking_management.domain.BookingDetail
import com.example.booking_app.feature.booking_management.domain.BookingManagementRepository
import com.example.booking_app.feature.booking_management.domain.BookingTab
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MyBookingsState {
    data object Loading : MyBookingsState
    data class Content(val bookings: List<BookingDetail>, val nextCursor: String?) : MyBookingsState
    data object Empty : MyBookingsState
    data class Error(val message: String, val unsupported: Boolean = false) : MyBookingsState
}

@HiltViewModel
class MyBookingsViewModel @Inject constructor(
    private val repository: BookingManagementRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<MyBookingsState>(MyBookingsState.Loading)
    val state: StateFlow<MyBookingsState> = _state.asStateFlow()
    private var currentTab = BookingTab.HOLD
    private var cursor: String? = null
    private var loaded = emptyList<BookingDetail>()
    private var loadJob: Job? = null

    fun selectTab(tab: BookingTab) {
        currentTab = tab
        loadFirstPage()
        if (tab == BookingTab.HOLD) return
        viewModelScope.launch {
            try {
                repository.observeTabInvalidations(tab).collect {
                    if (currentTab == tab) loadFirstPage()
                }
            } catch (_: UnsupportedOperationException) {
                // Loading reports the unsupported repository contract to the user.
            } catch (_: Exception) {
                if (currentTab == tab) _state.value = MyBookingsState.Error("", unsupported = false)
            }
        }
    }

    fun loadFirstPage() {
        loadJob?.cancel()
        cursor = null
        loaded = emptyList()
        loadPage(replace = true)
    }

    fun loadNextPage() {
        if (cursor != null && _state.value !is MyBookingsState.Loading) loadPage(replace = false)
    }

    private fun loadPage(replace: Boolean) {
        val tab = currentTab
        if (replace) _state.value = MyBookingsState.Loading
        loadJob = viewModelScope.launch {
            try {
                val page = repository.getPage(tab, if (replace) null else cursor, 10)
                if (currentTab != tab) return@launch
                loaded = if (replace) page.bookings else loaded + page.bookings
                cursor = page.nextCursor
                _state.value = if (loaded.isEmpty()) MyBookingsState.Empty else MyBookingsState.Content(loaded, cursor)
            } catch (error: UnsupportedOperationException) {
                _state.value = MyBookingsState.Error("", true)
            } catch (_: Exception) {
                _state.value = MyBookingsState.Error("Không thể tải lịch đặt. Kiểm tra kết nối rồi thử lại.")
            }
        }
    }
}

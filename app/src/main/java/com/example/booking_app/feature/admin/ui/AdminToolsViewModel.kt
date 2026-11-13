package com.example.booking_app.feature.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.admin.domain.model.AdminDistrict
import com.example.booking_app.feature.admin.domain.model.AdminCategoryKind
import kotlinx.coroutines.Job
import com.example.booking_app.feature.admin.domain.model.AdminSystemStats
import com.example.booking_app.feature.admin.domain.model.AdminSystemVoucher
import com.example.booking_app.feature.admin.domain.model.BroadcastNotificationRequest
import com.example.booking_app.feature.admin.domain.repository.AdminToolsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SystemStatsUiState {
    data object Loading : SystemStatsUiState
    data class Content(val stats: AdminSystemStats) : SystemStatsUiState
    data object Empty : SystemStatsUiState
    data class Error(val message: String) : SystemStatsUiState
}

sealed interface SystemVouchersUiState {
    data object Loading : SystemVouchersUiState
    data class Content(val vouchers: List<AdminSystemVoucher>) : SystemVouchersUiState
    data object Empty : SystemVouchersUiState
    data class Error(val message: String) : SystemVouchersUiState
}

sealed interface DistrictsUiState {
    data object Loading : DistrictsUiState
    data class Content(val districts: List<AdminDistrict>) : DistrictsUiState
    data object Empty : DistrictsUiState
    data class Error(val message: String) : DistrictsUiState
}

sealed interface ToolsActionUiState {
    data object Idle : ToolsActionUiState
    data object Loading : ToolsActionUiState
    data class Success(val message: String) : ToolsActionUiState
    data class Error(val message: String) : ToolsActionUiState
}

@HiltViewModel
class AdminToolsViewModel @Inject constructor(
    private val repository: AdminToolsRepository,
) : ViewModel() {

    private val _statsState = MutableStateFlow<SystemStatsUiState>(SystemStatsUiState.Loading)
    val statsState: StateFlow<SystemStatsUiState> = _statsState.asStateFlow()

    private val _vouchersState = MutableStateFlow<SystemVouchersUiState>(SystemVouchersUiState.Loading)
    val vouchersState: StateFlow<SystemVouchersUiState> = _vouchersState.asStateFlow()

    private val _districtsState = MutableStateFlow<DistrictsUiState>(DistrictsUiState.Loading)
    val districtsState: StateFlow<DistrictsUiState> = _districtsState.asStateFlow()

    private val _broadcastActionState = MutableStateFlow<ToolsActionUiState>(ToolsActionUiState.Idle)
    val broadcastActionState: StateFlow<ToolsActionUiState> = _broadcastActionState.asStateFlow()

    private val _voucherActionState = MutableStateFlow<ToolsActionUiState>(ToolsActionUiState.Idle)
    val voucherActionState: StateFlow<ToolsActionUiState> = _voucherActionState.asStateFlow()

    private val _districtActionState = MutableStateFlow<ToolsActionUiState>(ToolsActionUiState.Idle)
    val districtActionState: StateFlow<ToolsActionUiState> = _districtActionState.asStateFlow()



    var statsDate: String = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE); private set
    private var statsJob: Job? = null

    fun loadStats(date: String = statsDate) {
        statsDate = date
        statsJob?.cancel()
        _statsState.value = SystemStatsUiState.Loading
        statsJob = viewModelScope.launch {
            repository.getSystemStats(date)
                .onSuccess { stats ->
                    _statsState.value = if (stats.hasData) {
                        SystemStatsUiState.Content(stats)
                    } else {
                        SystemStatsUiState.Empty
                    }
                }
                .onFailure { error ->
                    _statsState.value = SystemStatsUiState.Error(
                        error.localizedMessage ?: "Không thể tải thống kê hệ thống"
                    )
                }
        }
    }

    fun loadVouchers() {
        _vouchersState.value = SystemVouchersUiState.Loading
        viewModelScope.launch {
            repository.getSystemVouchers()
                .onSuccess { list ->
                    _vouchersState.value = if (list.isEmpty()) {
                        SystemVouchersUiState.Empty
                    } else {
                        SystemVouchersUiState.Content(list)
                    }
                }
                .onFailure { error ->
                    _vouchersState.value = SystemVouchersUiState.Error(
                        error.localizedMessage ?: "Không thể tải danh sách voucher hệ thống"
                    )
                }
        }
    }

    fun createVoucher(voucher: AdminSystemVoucher) {
        if (_voucherActionState.value == ToolsActionUiState.Loading) return
        val err = voucher.validate()
        if (err != null) {
            _voucherActionState.value = ToolsActionUiState.Error(err)
            return
        }

        _voucherActionState.value = ToolsActionUiState.Loading
        viewModelScope.launch {
            repository.createSystemVoucher(voucher)
                .onSuccess {
                    _voucherActionState.value = ToolsActionUiState.Success("Tạo voucher hệ thống thành công")
                    loadVouchers()
                }
                .onFailure { error ->
                    _voucherActionState.value = ToolsActionUiState.Error(
                        error.localizedMessage ?: "Không thể tạo voucher hệ thống"
                    )
                }
        }
    }

    fun toggleVoucher(code: String, isActive: Boolean) {
        if (_voucherActionState.value == ToolsActionUiState.Loading) return
        _voucherActionState.value = ToolsActionUiState.Loading
        viewModelScope.launch {
            repository.toggleVoucherActive(code, isActive)
                .onSuccess {
                    val statusText = if (isActive) "kích hoạt" else "tạm ngừng"
                    _voucherActionState.value = ToolsActionUiState.Success("Đã $statusText voucher $code")
                    loadVouchers()
                }
                .onFailure { error ->
                    _voucherActionState.value = ToolsActionUiState.Error(
                        error.localizedMessage ?: "Không thể cập nhật trạng thái voucher"
                    )
                    loadVouchers()
                }
        }
    }

    fun updateVoucher(voucher: AdminSystemVoucher) {
        if (_voucherActionState.value == ToolsActionUiState.Loading) return
        val error = voucher.validate()
        if (error != null) { _voucherActionState.value = ToolsActionUiState.Error(error); return }
        _voucherActionState.value = ToolsActionUiState.Loading
        viewModelScope.launch {
            repository.updateSystemVoucher(voucher)
                .onSuccess { _voucherActionState.value = ToolsActionUiState.Success("Đã lưu voucher"); loadVouchers() }
                .onFailure { _voucherActionState.value = ToolsActionUiState.Error(it.localizedMessage ?: "Không thể lưu voucher") }
        }
    }

    var categoryKind = AdminCategoryKind.DISTRICTS; private set
    private var categoriesJob: Job? = null

    fun selectCategory(kind: AdminCategoryKind) { categoryKind = kind; loadDistricts() }

    fun loadDistricts() {
        categoriesJob?.cancel()
        val kind = categoryKind
        _districtsState.value = DistrictsUiState.Loading
        categoriesJob = viewModelScope.launch {
            repository.getCategories(kind)
                .onSuccess { list ->
                    _districtsState.value = if (list.isEmpty()) {
                        DistrictsUiState.Empty
                    } else {
                        DistrictsUiState.Content(list)
                    }
                }
                .onFailure { error ->
                    _districtsState.value = DistrictsUiState.Error(
                        error.localizedMessage ?: "Không thể tải danh mục khu vực"
                    )
                }
        }
    }

    fun saveDistrict(district: AdminDistrict) {
        if (_districtActionState.value == ToolsActionUiState.Loading) return
        _districtActionState.value = ToolsActionUiState.Loading
        viewModelScope.launch {
            repository.saveDistrict(district)
                .onSuccess {
                    _districtActionState.value = ToolsActionUiState.Success("Lưu khu vực thành công")
                    loadDistricts()
                }
                .onFailure { error ->
                    _districtActionState.value = ToolsActionUiState.Error(
                        error.localizedMessage ?: "Không thể lưu khu vực"
                    )
                }
        }
    }

    private var pendingBroadcast: BroadcastNotificationRequest? = null

    fun broadcastNotification(title: String, body: String) {
        if (_broadcastActionState.value == ToolsActionUiState.Loading) return
        val req = pendingBroadcast?.takeIf { it.title == title && it.body == body }
            ?: BroadcastNotificationRequest(title = title, body = body).also { pendingBroadcast = it }
        val err = req.validate()
        if (err != null) {
            _broadcastActionState.value = ToolsActionUiState.Error(err)
            return
        }

        _broadcastActionState.value = ToolsActionUiState.Loading
        viewModelScope.launch {
            repository.broadcastSystemNotification(req)
                .onSuccess { count ->
                    pendingBroadcast = null
                    _broadcastActionState.value = ToolsActionUiState.Success(
                        "Đã phát thông báo hệ thống tới $count tài khoản đang hoạt động"
                    )
                }
                .onFailure { error ->
                    _broadcastActionState.value = ToolsActionUiState.Error(
                        error.localizedMessage ?: "Không thể phát thông báo hệ thống"
                    )
                }
        }
    }

    fun clearBroadcastActionState() {
        _broadcastActionState.value = ToolsActionUiState.Idle
    }

    fun clearVoucherActionState() {
        _voucherActionState.value = ToolsActionUiState.Idle
    }

    fun clearDistrictActionState() {
        _districtActionState.value = ToolsActionUiState.Idle
    }
    init {
        loadStats()
        loadVouchers()
        loadDistricts()
    }
}

package com.example.booking_app.feature.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.feature.admin.domain.model.AdminUserLookup
import com.example.booking_app.feature.admin.domain.model.AdminVenueItem
import com.example.booking_app.feature.admin.domain.model.OwnerRevocationDecision
import com.example.booking_app.feature.admin.domain.model.UserAccountStatus
import com.example.booking_app.feature.admin.domain.repository.AdminUserVenueRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

sealed interface UserLookupUiState {
    data object Idle : UserLookupUiState
    data object Loading : UserLookupUiState
    data class Success(val user: AdminUserLookup) : UserLookupUiState
    data object NotFound : UserLookupUiState
    data class Error(val message: String) : UserLookupUiState
}

sealed interface VenueLookupUiState {
    data object Idle : VenueLookupUiState
    data object Loading : VenueLookupUiState
    data class Success(val venue: AdminVenueItem) : VenueLookupUiState
    data object NotFound : VenueLookupUiState
    data class Error(val message: String) : VenueLookupUiState
}

sealed interface AdminActionUiState {
    data object Idle : AdminActionUiState
    data object Loading : AdminActionUiState
    data class Success(val message: String) : AdminActionUiState
    data class Error(val message: String) : AdminActionUiState
}

@HiltViewModel
class AdminUserVenueViewModel @Inject constructor(
    private val repository: AdminUserVenueRepository,
) : ViewModel() {

    private val _userLookupState = MutableStateFlow<UserLookupUiState>(UserLookupUiState.Idle)
    val userLookupState: StateFlow<UserLookupUiState> = _userLookupState.asStateFlow()

    private val _venueLookupState = MutableStateFlow<VenueLookupUiState>(VenueLookupUiState.Idle)
    val venueLookupState: StateFlow<VenueLookupUiState> = _venueLookupState.asStateFlow()

    private val _actionState = MutableStateFlow<AdminActionUiState>(AdminActionUiState.Idle)
    val actionState: StateFlow<AdminActionUiState> = _actionState.asStateFlow()

    private var lastUserQuery: String = ""
    private var lastVenueQuery: String = ""

    fun searchUser(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _userLookupState.value = UserLookupUiState.Error("Vui lòng nhập số điện thoại hoặc mã người dùng")
            return
        }
        lastUserQuery = trimmed
        _userLookupState.value = UserLookupUiState.Loading
        viewModelScope.launch {
            repository.lookupUser(trimmed)
                .onSuccess { user ->
                    _userLookupState.value = if (user != null) {
                        UserLookupUiState.Success(user)
                    } else {
                        UserLookupUiState.NotFound
                    }
                }
                .onFailure { error ->
                    _userLookupState.value = UserLookupUiState.Error(
                        error.localizedMessage ?: "Lỗi khi tra cứu người dùng"
                    )
                }
        }
    }

    fun toggleAccountLock(user: AdminUserLookup) {
        val targetStatus = if (user.isLocked) UserAccountStatus.ACTIVE else UserAccountStatus.LOCKED
        _actionState.value = AdminActionUiState.Loading
        viewModelScope.launch {
            repository.setAccountStatus(user.uid, targetStatus)
                .onSuccess {
                    val msg = if (targetStatus == UserAccountStatus.LOCKED) {
                        "Đã khóa tài khoản thành công"
                    } else {
                        "Đã mở khóa tài khoản thành công"
                    }
                    _actionState.value = AdminActionUiState.Success(msg)
                    if (lastUserQuery.isNotEmpty()) {
                        searchUser(lastUserQuery)
                    }
                }
                .onFailure { error ->
                    _actionState.value = AdminActionUiState.Error(
                        error.localizedMessage ?: "Không thể thay đổi trạng thái tài khoản"
                    )
                }
        }
    }

    fun revokeOwner(uid: String, reason: String) {
        val decision = OwnerRevocationDecision(uid = uid, reason = reason)
        val validationErr = decision.validate()
        if (validationErr != null) {
            _actionState.value = AdminActionUiState.Error(validationErr)
            return
        }

        _actionState.value = AdminActionUiState.Loading
        viewModelScope.launch {
            repository.revokeOwner(decision)
                .onSuccess {
                    _actionState.value = AdminActionUiState.Success("Đã thu hồi quyền chủ sân thành công")
                    if (lastUserQuery.isNotEmpty()) {
                        searchUser(lastUserQuery)
                    }
                }
                .onFailure { error ->
                    _actionState.value = AdminActionUiState.Error(
                        error.localizedMessage ?: "Không thể thu hồi quyền chủ sân"
                    )
                }
        }
    }

    fun searchVenue(venueId: String) {
        val trimmed = venueId.trim()
        if (trimmed.isEmpty()) {
            _venueLookupState.value = VenueLookupUiState.Error("Vui lòng nhập mã cơ sở")
            return
        }
        lastVenueQuery = trimmed
        _venueLookupState.value = VenueLookupUiState.Loading
        viewModelScope.launch {
            repository.getVenue(trimmed)
                .onSuccess { venue ->
                    _venueLookupState.value = if (venue != null) {
                        VenueLookupUiState.Success(venue)
                    } else {
                        VenueLookupUiState.NotFound
                    }
                }
                .onFailure { error ->
                    _venueLookupState.value = VenueLookupUiState.Error(
                        error.localizedMessage ?: "Lỗi khi tra cứu cơ sở"
                    )
                }
        }
    }

    fun setVenueStatus(venueId: String, status: String) {
        _actionState.value = AdminActionUiState.Loading
        viewModelScope.launch {
            repository.setVenueStatus(venueId, status)
                .onSuccess {
                    val msg = when (status.uppercase(Locale.ROOT)) {
                        "LOCKED" -> "Đã khóa cơ sở thành công"
                        "ACTIVE" -> "Đã mở khóa cơ sở (ACTIVE) thành công"
                        "HIDDEN" -> "Đã mở khóa cơ sở (HIDDEN) thành công"
                        else -> "Đã cập nhật trạng thái cơ sở thành công"
                    }
                    _actionState.value = AdminActionUiState.Success(msg)
                    if (lastVenueQuery.isNotEmpty()) {
                        searchVenue(lastVenueQuery)
                    }
                }
                .onFailure { error ->
                    _actionState.value = AdminActionUiState.Error(
                        error.localizedMessage ?: "Không thể cập nhật trạng thái cơ sở"
                    )
                }
        }
    }

    fun clearActionState() {
        _actionState.value = AdminActionUiState.Idle
    }
}

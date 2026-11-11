package com.example.booking_app.feature.dropin.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.booking_app.core.catalog.CatalogRepository
import com.example.booking_app.core.catalog.District
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Danh mục quận dùng chung (`core/catalog`, spec 020) cho nhãn chip ở danh sách và lựa chọn ở bottom sheet bộ lọc.
 * [CatalogRepository] giữ danh mục trong bộ nhớ nên mỗi màn hình tạo một ViewModel riêng cũng không đọc lại.
 */
@HiltViewModel
class DropInDistrictsViewModel @Inject constructor(
    private val catalog: CatalogRepository,
) : ViewModel() {

    sealed interface State {
        data object Loading : State
        /** Gồm cả quận đã tắt (để hiện tên chip); bottom sheet chỉ cho chọn quận đang bật. */
        data class Loaded(val districts: List<District>) : State
        data object Failed : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = State.Loading
        viewModelScope.launch {
            _state.value = try {
                State.Loaded(catalog.districts().sortedBy { it.sortOrder })
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                State.Failed
            }
        }
    }

    /** Tên quận theo ID; chưa tải xong thì rỗng (chip tạm hiện ID). */
    fun namesById(): Map<String, String> =
        (state.value as? State.Loaded)?.districts?.associate { it.id to it.name }.orEmpty()
}

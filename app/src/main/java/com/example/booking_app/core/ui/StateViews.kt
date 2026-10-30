package com.example.booking_app.core.ui

import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.example.booking_app.R
import com.example.booking_app.databinding.IncludeStateViewsBinding

/** Nội dung của trạng thái rỗng: chữ, biểu tượng và nút hành động (không bắt buộc). */
data class EmptyStateSpec(
    @StringRes val message: Int,
    @DrawableRes val icon: Int? = null,
    @StringRes val actionText: Int? = null,
    val onAction: (() -> Unit)? = null,
)

/**
 * Hiển thị [state] lên layout `include_state_views.xml`, còn [content] (thường là RecyclerView)
 * chỉ hiện khi có dữ liệu.
 */
fun IncludeStateViewsBinding.render(state: UiState<*>, content: View, empty: EmptyStateSpec) {
    stateLoading.isVisible = state is UiState.Loading
    content.isVisible = state is UiState.Content<*>
    stateMessage.isVisible = state is UiState.Empty || state is UiState.Error

    when (state) {
        is UiState.Empty -> {
            stateText.setText(empty.message)
            stateIcon.isVisible = empty.icon != null
            empty.icon?.let(stateIcon::setImageResource)
            bindAction(empty.actionText, empty.onAction)
        }
        is UiState.Error -> {
            stateText.text = state.message
            stateIcon.isVisible = true
            stateIcon.setImageResource(R.drawable.ic_error_24)
            bindAction(R.string.common_retry, state.retry)
        }
        else -> Unit
    }
}

private fun IncludeStateViewsBinding.bindAction(@StringRes text: Int?, onClick: (() -> Unit)?) {
    stateAction.isVisible = text != null && onClick != null
    if (text != null && onClick != null) {
        stateAction.setText(text)
        stateAction.setOnClickListener { onClick() }
    } else {
        stateAction.setOnClickListener(null)
    }
}

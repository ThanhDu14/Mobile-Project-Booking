package com.example.booking_app.core.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.example.booking_app.R
import com.example.booking_app.databinding.ViewVerifyEmailBannerBinding
import com.google.android.material.card.MaterialCardView

/**
 * Banner "Hãy xác minh email" (FR-009). View chỉ hiển thị; màn hình chứa nó lấy
 * `VerifyEmailBannerViewModel`, gọi [render] và nối [onResendClick].
 */
class VerifyEmailBannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialCardViewStyle,
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val binding = ViewVerifyEmailBannerBinding.inflate(LayoutInflater.from(context), this)

    var onResendClick: (() -> Unit)? = null

    init {
        setCardBackgroundColor(ContextCompat.getColor(context, R.color.sn_lamp_container))
        strokeWidth = 0
        cardElevation = 0f
        binding.resendButton.setOnClickListener { onResendClick?.invoke() }
    }

    fun render(state: VerifyEmailBannerUiState) {
        isVisible = state.visible
        val waiting = state.resendInSeconds > 0
        binding.resendButton.isEnabled = !waiting && !state.isSending
        binding.resendButton.text = when {
            state.isSending -> ""
            waiting -> context.getString(R.string.auth_verify_banner_resend_in, formatSeconds(state.resendInSeconds))
            else -> context.getString(R.string.auth_verify_banner_resend)
        }
        binding.resendProgress.isInvisible = !state.isSending
    }

    private fun formatSeconds(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)
}

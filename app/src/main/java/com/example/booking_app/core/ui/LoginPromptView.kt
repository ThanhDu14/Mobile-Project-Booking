package com.example.booking_app.core.ui

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.content.withStyledAttributes
import androidx.navigation.findNavController
import com.example.booking_app.R
import com.example.booking_app.core.session.AuthNavigator
import com.example.booking_app.databinding.ViewLoginPromptBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Lời mời đăng nhập cho màn hình cần tài khoản khi đang ở chế độ khách (research R15).
 * Đăng nhập xong `nav_auth` đóng lại và màn hình hiện tại còn nguyên; chỉ đặt [returnTo]
 * khi cần mở thêm một màn hình khác sau đó.
 */
@AndroidEntryPoint
class LoginPromptView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    @Inject
    lateinit var authNavigator: AuthNavigator

    var returnTo: Uri? = null

    private val binding = ViewLoginPromptBinding.inflate(LayoutInflater.from(context), this)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        var message: String? = null
        context.withStyledAttributes(attrs, R.styleable.LoginPromptView) {
            message = getString(R.styleable.LoginPromptView_promptMessage)
        }
        setMessage(message ?: context.getString(R.string.auth_guest_prompt_default))
        binding.loginButton.setOnClickListener { authNavigator.openLogin(findNavController(), returnTo) }
        binding.registerButton.setOnClickListener { authNavigator.openRegister(findNavController(), returnTo) }
    }

    fun setMessage(message: CharSequence) {
        binding.promptMessage.text = message
    }
}

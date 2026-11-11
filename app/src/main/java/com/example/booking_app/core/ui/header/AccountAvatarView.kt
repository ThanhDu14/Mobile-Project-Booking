package com.example.booking_app.core.ui.header

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.content.withStyledAttributes
import androidx.core.view.isVisible
import coil3.load
import com.example.booking_app.R
import com.example.booking_app.core.media.StorageUrls
import com.example.booking_app.core.session.AccountAvatarState
import com.example.booking_app.databinding.ViewAccountAvatarBinding

/**
 * Avatar tròn 48dp ở header dùng chung (spec 011 FR-028, FR-031): ảnh đại diện → chữ cái đầu → icon người.
 * Ảnh tải lỗi thì để lộ chữ cái đầu bên dưới.
 */
class AccountAvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val binding = ViewAccountAvatarBinding.inflate(LayoutInflater.from(context), this)

    /** Đường dẫn ảnh đang hiện, để không tải lại khi trạng thái phát lại cùng ảnh. */
    private var shownPath: String? = null

    init {
        isClickable = true
        isFocusable = true
        minimumWidth = resources.getDimensionPixelSize(R.dimen.account_header_avatar_size)
        minimumHeight = resources.getDimensionPixelSize(R.dimen.account_header_avatar_size)
        context.withStyledAttributes(null, intArrayOf(android.R.attr.selectableItemBackgroundBorderless)) {
            foreground = getDrawable(0)
        }
        render(AccountAvatarState.Pending)
    }

    fun render(state: AccountAvatarState) {
        val initials = (state as? AccountAvatarState.SignedIn)?.initials.orEmpty()
        binding.avatarInitials.text = initials
        binding.avatarIcon.isVisible = state == AccountAvatarState.Guest ||
            (state is AccountAvatarState.SignedIn && initials.isEmpty())
        showPhoto((state as? AccountAvatarState.SignedIn)?.avatarPath)
        contentDescription = context.getString(
            if (state == AccountAvatarState.Guest) R.string.account_avatar_cd_guest else R.string.account_avatar_cd_signed_in,
        )
    }

    private fun showPhoto(path: String?) {
        if (path == shownPath) return
        shownPath = path
        if (path == null) {
            binding.avatarPhoto.isVisible = false
            binding.avatarPhoto.setImageDrawable(null)
            return
        }
        binding.avatarPhoto.isVisible = true
        binding.avatarPhoto.load(StorageUrls.publicUrl(AVATAR_BUCKET, path)) {
            listener(onError = { _, _ ->
                binding.avatarPhoto.isVisible = false
                // Cho lần phát sau (ví dụ có mạng lại) thử tải lại.
                shownPath = null
            })
        }
    }

    private companion object {
        const val AVATAR_BUCKET = "avatars"
    }
}

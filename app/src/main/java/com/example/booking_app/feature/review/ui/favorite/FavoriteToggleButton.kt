package com.example.booking_app.feature.review.ui.favorite

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatImageButton
import com.example.booking_app.R
import com.google.android.material.color.MaterialColors

/**
 * Nút trái tim yêu thích, vùng chạm 48dp, đọc được bằng TalkBack ("Thêm/Bỏ yêu thích").
 * Màn hình chứa nút dùng [FavoriteToggleViewModel] để lấy trạng thái và xử lý bấm (xem REV/README.md).
 */
class FavoriteToggleButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatImageButton(context, attrs) {

    var isFavorite: Boolean = false
        set(value) {
            field = value
            render()
        }

    init {
        val touch = resources.getDimensionPixelSize(R.dimen.touch_min)
        minimumWidth = touch
        minimumHeight = touch
        scaleType = ScaleType.CENTER
        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
        setBackgroundResource(outValue.resourceId)
        render()
    }

    private fun render() {
        setImageResource(if (isFavorite) R.drawable.ic_favorite_24 else R.drawable.ic_favorite_border_24)
        val tint = MaterialColors.getColor(
            this,
            if (isFavorite) androidx.appcompat.R.attr.colorPrimary else com.google.android.material.R.attr.colorOnSurfaceVariant,
        )
        imageTintList = ColorStateList.valueOf(tint)
        contentDescription = context.getString(
            if (isFavorite) R.string.favorite_remove_description else R.string.favorite_add_description,
        )
    }
}

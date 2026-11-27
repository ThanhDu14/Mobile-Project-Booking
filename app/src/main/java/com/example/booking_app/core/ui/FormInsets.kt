package com.example.booking_app.core.ui

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnNextLayout
import androidx.core.view.updateLayoutParams

internal fun View.keepFormAboveKeyboard() {
    val originalBottom = (layoutParams as? MarginLayoutParams)?.bottomMargin ?: return
    ViewCompat.setOnApplyWindowInsetsListener(this) { form, insets ->
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        // MainActivity already reserves the system bars.
        val bottom = originalBottom + (ime - systemBars).coerceAtLeast(0)
        if ((form.layoutParams as MarginLayoutParams).bottomMargin != bottom) {
            // Resize the ScrollView's viewport so its focus scrolling uses the space above the IME.
            form.updateLayoutParams<MarginLayoutParams> { bottomMargin = bottom }
            if (ime > systemBars) form.doOnNextLayout {
                val focused = form.findFocus() ?: return@doOnNextLayout
                focused.requestRectangleOnScreen(Rect(0, 0, focused.width, focused.height))
            }
        }
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

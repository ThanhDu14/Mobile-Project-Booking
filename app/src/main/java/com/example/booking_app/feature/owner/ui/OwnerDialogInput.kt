package com.example.booking_app.feature.owner.ui

import android.widget.EditText
import android.widget.FrameLayout
import com.example.booking_app.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

internal fun MaterialAlertDialogBuilder.setOwnerDialogInput(input: EditText): MaterialAlertDialogBuilder {
    val inset = input.resources.getDimensionPixelSize(R.dimen.space_6)
    input.minimumHeight = input.resources.getDimensionPixelSize(R.dimen.touch_min)
    val container = FrameLayout(input.context).apply {
        setPaddingRelative(inset, 0, inset, 0)
        addView(input, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
        ))
    }
    return setView(container)
}

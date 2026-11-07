package com.example.booking_app.feature.auth.ui.legal

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.View
import androidx.annotation.StringRes
import com.example.booking_app.R

/**
 * Câu đồng ý có hai link "Điều khoản sử dụng" và "Chính sách quyền riêng tư". [template] có `%1$s`, `%2$s`.
 * TextView cần `movementMethod = LinkMovementMethod.getInstance()`.
 */
fun legalConsentText(
    context: Context,
    @StringRes template: Int,
    onOpen: (LegalDocumentFragment.Doc) -> Unit,
): CharSequence {
    val terms = context.getString(R.string.auth_terms_link)
    val privacy = context.getString(R.string.auth_privacy_link)
    val text = SpannableString(context.getString(template, terms, privacy))
    listOf(terms to LegalDocumentFragment.Doc.TERMS, privacy to LegalDocumentFragment.Doc.PRIVACY).forEach { (label, doc) ->
        val start = text.indexOf(label)
        if (start < 0) return@forEach
        text.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) = onOpen(doc)
            },
            start,
            start + label.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
    return text
}

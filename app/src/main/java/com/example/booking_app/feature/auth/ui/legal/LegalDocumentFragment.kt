package com.example.booking_app.feature.auth.ui.legal

import android.os.Bundle
import android.view.View
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentAuthLegalBinding

/** Điều khoản sử dụng / Chính sách quyền riêng tư, nội dung tĩnh có dòng "Phiên bản học tập" (FR-003). */
class LegalDocumentFragment : Fragment(R.layout.fragment_auth_legal) {

    enum class Doc(@StringRes val title: Int, @RawRes val raw: Int) {
        TERMS(R.string.auth_legal_terms_title, R.raw.terms_vi),
        PRIVACY(R.string.auth_legal_privacy_title, R.raw.privacy_vi),
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentAuthLegalBinding.bind(view)
        val doc = Doc.valueOf(requireArguments().getString(ARG_DOC) ?: Doc.TERMS.name)
        binding.toolbar.setTitle(doc.title)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        val html = resources.openRawResource(doc.raw).bufferedReader().use { it.readText() }
        binding.body.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
    }

    companion object {
        private const val ARG_DOC = "doc"

        fun open(navController: NavController, doc: Doc) {
            navController.navigate(R.id.legalDocumentFragment, bundleOf(ARG_DOC to doc.name))
        }
    }
}

package com.aqua.aqualight.ui.tabs.settings.app

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTermsOfUseBinding
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.web.LegalDocumentWebView

class TermsOfUseFragment : Fragment(R.layout.fragment_terms_of_use) {

    private var _binding: FragmentTermsOfUseBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentTermsOfUseBinding.bind(view)

        binding.appHeader.setupAquaHeader(fragment = this)
        LegalDocumentWebView.load(
            webView = binding.webViewTerms,
            assetFileName = "terms_of_use_en.html"
        )
    }

    override fun onDestroyView() {
        LegalDocumentWebView.destroy(binding.webViewTerms)
        _binding = null
        super.onDestroyView()
    }
}

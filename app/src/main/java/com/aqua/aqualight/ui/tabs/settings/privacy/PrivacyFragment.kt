package com.aqua.aqualight.ui.tabs.settings.privacy

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentPrivacyBinding
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.web.LegalDocumentWebView

class PrivacyFragment : Fragment(R.layout.fragment_privacy) {

    private var _binding: FragmentPrivacyBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentPrivacyBinding.bind(view)

        binding.appHeader.setupAquaHeader(fragment = this)
        LegalDocumentWebView.load(
            webView = binding.webViewPrivacy,
            assetFileName = "privacy_policy_en.html"
        )
    }

    override fun onDestroyView() {
        LegalDocumentWebView.destroy(binding.webViewPrivacy)
        _binding = null
        super.onDestroyView()
    }
}

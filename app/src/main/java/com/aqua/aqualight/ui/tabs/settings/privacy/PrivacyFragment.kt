package com.aqua.aqualight.ui.tabs.settings.privacy

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentPrivacyBinding
import com.aqua.aqualight.i18n.AppLanguageController
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.web.LegalDocument
import com.aqua.aqualight.ui.common.web.destroySecureLocalContent
import com.aqua.aqualight.ui.common.web.loadSecureLocalAsset

class PrivacyFragment : Fragment(R.layout.fragment_privacy) {

    private var _binding: FragmentPrivacyBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentPrivacyBinding.bind(view)

        setupHeader()

        setupPrivacyWebView()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this
        )
    }

    private fun setupPrivacyWebView() {
        binding.webViewPrivacy.loadSecureLocalAsset(
            LegalDocument.PRIVACY.assetFor(AppLanguageController.current())
        )
    }

    override fun onDestroyView() {
        binding.webViewPrivacy.destroySecureLocalContent()

        _binding =
            null

        super.onDestroyView()
    }
}

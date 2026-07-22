package com.aqua.aqualight.ui.tabs.settings.app

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTermsOfUseBinding
import com.aqua.aqualight.i18n.AppLanguageController
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.web.LegalDocument
import com.aqua.aqualight.ui.common.web.destroySecureLocalContent
import com.aqua.aqualight.ui.common.web.loadSecureLocalAsset

class TermsOfUseFragment : Fragment(R.layout.fragment_terms_of_use) {

    private var _binding: FragmentTermsOfUseBinding? = null
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
            FragmentTermsOfUseBinding.bind(view)

        setupHeader()
        setupWebView()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this
        )
    }

    private fun setupWebView() {
        binding.webViewTerms.loadSecureLocalAsset(
            LegalDocument.TERMS.assetFor(AppLanguageController.current())
        )
    }

    override fun onDestroyView() {
        binding.webViewTerms.destroySecureLocalContent()

        _binding =
            null

        super.onDestroyView()
    }
}

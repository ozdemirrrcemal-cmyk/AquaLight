package com.aqua.aqualight.ui.tabs.settings.app

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentOpenSourceLicensesBinding
import com.aqua.aqualight.i18n.AppLanguageController
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.web.LegalDocument
import com.aqua.aqualight.ui.common.web.destroySecureLocalContent
import com.aqua.aqualight.ui.common.web.loadSecureLocalAsset

class OpenSourceLicensesFragment : Fragment(R.layout.fragment_open_source_licenses) {

    private var _binding: FragmentOpenSourceLicensesBinding? = null
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
            FragmentOpenSourceLicensesBinding.bind(view)

        setupHeader()
        setupLicensesWebView()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this
        )
    }

    private fun setupLicensesWebView() {
        binding.webViewLicenses.loadSecureLocalAsset(
            LegalDocument.OPEN_SOURCE_LICENSES.assetFor(AppLanguageController.current())
        )
    }

    override fun onDestroyView() {
        binding.webViewLicenses.destroySecureLocalContent()

        _binding =
            null

        super.onDestroyView()
    }
}

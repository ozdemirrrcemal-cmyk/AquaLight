package com.aqua.aqualight.ui.common.web

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentLegalDocumentBinding
import com.aqua.aqualight.i18n.AppLanguageController
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

abstract class LegalDocumentFragment : Fragment(R.layout.fragment_legal_document) {

    private var _binding: FragmentLegalDocumentBinding? = null
    private val binding get() = _binding!!
    protected abstract val document: LegalDocument

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentLegalDocumentBinding.bind(view)

        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(document.titleRes)
            )
        )
        binding.legalWebView.loadSecureLocalAsset(
            document.assetFor(AppLanguageController.current())
        )
    }

    override fun onDestroyView() {
        binding.legalWebView.destroySecureLocalContent()
        _binding = null
        super.onDestroyView()
    }

}

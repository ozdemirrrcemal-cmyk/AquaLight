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
        val languageTag = AppLanguageController.current()

        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                title = getString(document.titleRes)
            )
        )
        binding.legalWebView.loadSecureLocalAsset(
            assetName = document.assetFor(languageTag),
            onLocalAssetNavigation = { linkedAsset ->
                val linkedDocument = LegalDocument.entries.firstOrNull { candidate ->
                    candidate.assetFor(languageTag) == linkedAsset
                } ?: return@loadSecureLocalAsset

                _binding?.appHeader?.tvTitle?.text = getString(linkedDocument.titleRes)
            }
        )
    }

    override fun onDestroyView() {
        binding.legalWebView.destroySecureLocalContent()
        _binding = null
        super.onDestroyView()
    }

}

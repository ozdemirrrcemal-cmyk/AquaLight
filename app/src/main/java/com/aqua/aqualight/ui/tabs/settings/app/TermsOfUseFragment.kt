package com.aqua.aqualight.ui.tabs.settings.app

import android.os.Bundle
import android.view.View
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTermsOfUseBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

class TermsOfUseFragment : Fragment(R.layout.fragment_terms_of_use) {

    private var _binding: FragmentTermsOfUseBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentTermsOfUseBinding.bind(view)

        binding.appHeader.setupAquaHeader(
    AquaHeaderConfig(
        title = getString(R.string.settings_about_title),
        showBackButton = true,
        onBackClick = {
            findNavController().popBackStack()
        }
    )
)

        binding.webViewTerms.apply {

            webViewClient = WebViewClient()

            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false

            settings.allowFileAccess = false
            settings.allowContentAccess = false

            loadUrl("file:///android_asset/terms_of_use_en.html")
        }
    }

    override fun onDestroyView() {

        binding.webViewTerms.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }

        _binding = null

        super.onDestroyView()
    }
}
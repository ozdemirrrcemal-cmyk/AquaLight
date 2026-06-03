package com.aqua.aqualight.ui.tabs.settings.privacy

import android.os.Bundle
import android.view.View
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentPrivacyBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

class PrivacyFragment : Fragment(R.layout.fragment_privacy) {

    private var _binding: FragmentPrivacyBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentPrivacyBinding.bind(view)

        binding.appHeader.setupAquaHeader(
    AquaHeaderConfig(
        title = getString(R.string.settings_about_title),
        showBackButton = true,
        onBackClick = {
            findNavController().popBackStack()
        }
    )
)

        setupPrivacyWebView()
    }

    private fun setupPrivacyWebView() {
        binding.webViewPrivacy.apply {
            webViewClient = WebViewClient()

            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            settings.builtInZoomControls = false
            settings.displayZoomControls = false

            loadUrl("file:///android_asset/privacy_policy_en.html")
        }
    }

    override fun onDestroyView() {
        binding.webViewPrivacy.apply {
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
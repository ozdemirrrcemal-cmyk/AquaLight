package com.aqua.aqualight.ui.tabs.settings.app

import android.os.Bundle
import android.view.View
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentOpenSourceLicensesBinding

class OpenSourceLicensesFragment : Fragment(R.layout.fragment_open_source_licenses) {

    private var _binding: FragmentOpenSourceLicensesBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentOpenSourceLicensesBinding.bind(view)

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        setupLicensesWebView()
    }

    private fun setupLicensesWebView() {
        binding.webViewLicenses.apply {
            webViewClient = WebViewClient()

            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            settings.builtInZoomControls = false
            settings.displayZoomControls = false

            loadUrl("file:///android_asset/open_source_licenses_en.html")
        }
    }

    override fun onDestroyView() {
        binding.webViewLicenses.apply {
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
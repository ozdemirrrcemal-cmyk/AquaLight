package com.aqua.aqualight.ui.tabs.settings.privacy

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentPrivacyBinding

class PrivacyFragment : Fragment(R.layout.fragment_privacy) {

    private var _binding: FragmentPrivacyBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPrivacyBinding.bind(view)

        // 🔙 Geri butonu
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // 🔹 WebView: local HTML (assets/privacy_policy_en.html) yükle
        binding.webViewPrivacy.apply {
            webViewClient = android.webkit.WebViewClient()

            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false

            loadUrl("file:///android_asset/privacy_policy_en.html")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // İstersen webView'i de temizleyebilirsin:
        // binding.webViewPrivacy.loadUrl("about:blank")
        // binding.webViewPrivacy.stopLoading()
        _binding = null
    }
}
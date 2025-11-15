package com.aqua.aqualight.ui.tabs.settings.privacy

import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R

class PrivacyFragment : Fragment(R.layout.fragment_privacy) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 🔙 Geri butonu
        val btnBack = view.findViewById<ImageView>(R.id.btnBack)
        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // 🔹 WebView: local HTML (assets/privacy_policy_en.html) yükle
        val webView = view.findViewById<WebView>(R.id.webViewPrivacy)

        webView.apply {
            // Sayfa içi navigation vs. için client set edelim
            webViewClient = WebViewClient()

            settings.javaScriptEnabled = false   // JS'e ihtiyacın yoksa kapalı kalsın
            settings.domStorageEnabled = false   // Gerek yoksa kapalı

            // assets klasörüne koyduğun dosya:
            // app/src/main/assets/privacy_policy_en.html
            loadUrl("file:///android_asset/privacy_policy_en.html")
        }
    }
}
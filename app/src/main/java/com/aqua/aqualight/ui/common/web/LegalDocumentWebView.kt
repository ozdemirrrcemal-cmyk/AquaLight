package com.aqua.aqualight.ui.common.web

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.aqua.aqualight.i18n.SupportedLocaleRegistry
import java.io.ByteArrayInputStream
import java.net.URI

internal enum class LegalDocumentAsset(
    private val fileStem: String
) {
    PRIVACY_POLICY("privacy_policy"),
    TERMS_OF_USE("terms_of_use");

    fun fileName(runtimeLanguageTag: String?): String {
        val language = SupportedLocaleRegistry.resolve(runtimeLanguageTag)
        return "${fileStem}_${language}.html"
    }
}

internal object LegalDocumentUriPolicy {
    private const val ASSET_HOST = "appassets.androidplatform.net"
    private const val ASSET_PREFIX = "/assets/"
    private val assetFileNamePattern = Regex("^[a-z0-9_\\-]+\\.html$")

    fun assetUrl(assetFileName: String): String {
        require(assetFileNamePattern.matches(assetFileName)) {
            "Invalid legal document asset name."
        }
        return "https://$ASSET_HOST$ASSET_PREFIX$assetFileName"
    }

    fun isBundledAsset(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(ASSET_HOST, ignoreCase = true) &&
            uri.path.orEmpty().startsWith(ASSET_PREFIX)
    }

    fun mayOpenExternally(url: String): Boolean {
        val scheme = runCatching { URI(url).scheme.orEmpty() }.getOrDefault("")
        return scheme.equals("https", ignoreCase = true) ||
            scheme.equals("mailto", ignoreCase = true)
    }
}

internal object LegalDocumentWebView {

    fun load(
        webView: WebView,
        document: LegalDocumentAsset
    ) {
        val context = webView.context
        val runtimeLanguageTag = context.resources.configuration.locales
            .takeIf { !it.isEmpty }
            ?.get(0)
            ?.toLanguageTag()
        val assetFileName = document.fileName(runtimeLanguageTag)
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler(
                "/assets/",
                WebViewAssetLoader.AssetsPathHandler(context)
            )
            .build()

        webView.settings.apply {
            javaScriptEnabled = false
            domStorageEnabled = false
            databaseEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true

            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
        }

        webView.webViewClient = RestrictedAssetWebViewClient(
            context = context,
            assetLoader = assetLoader
        )
        webView.loadUrl(LegalDocumentUriPolicy.assetUrl(assetFileName))
    }

    fun destroy(webView: WebView) {
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.clearHistory()
        webView.removeAllViews()
        webView.destroy()
    }

    private class RestrictedAssetWebViewClient(
        private val context: Context,
        private val assetLoader: WebViewAssetLoader
    ) : WebViewClientCompat() {

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? = intercept(request.url)

        @Suppress("DEPRECATION")
        override fun shouldInterceptRequest(
            view: WebView,
            url: String
        ): WebResourceResponse? = intercept(Uri.parse(url))

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean = handleNavigation(request.url)

        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(
            view: WebView,
            url: String
        ): Boolean = handleNavigation(Uri.parse(url))

        private fun intercept(uri: Uri): WebResourceResponse? {
            return if (LegalDocumentUriPolicy.isBundledAsset(uri.toString())) {
                assetLoader.shouldInterceptRequest(uri)
            } else {
                blockedResponse()
            }
        }

        private fun handleNavigation(uri: Uri): Boolean {
            val value = uri.toString()
            if (LegalDocumentUriPolicy.isBundledAsset(value)) return false

            if (LegalDocumentUriPolicy.mayOpenExternally(value)) {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
                runCatching { context.startActivity(intent) }
            }
            return true
        }

        private fun blockedResponse(): WebResourceResponse {
            return WebResourceResponse(
                "text/plain",
                "UTF-8",
                403,
                "Blocked",
                emptyMap(),
                ByteArrayInputStream(ByteArray(0))
            )
        }
    }
}

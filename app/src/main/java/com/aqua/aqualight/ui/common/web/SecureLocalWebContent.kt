package com.aqua.aqualight.ui.common.web

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader

private const val LOCAL_ASSET_HOST = "appassets.androidplatform.net"
private const val LOCAL_ASSET_PREFIX = "/assets/"
private const val PUBLICATION_SUPPORT_EMAIL = ""

/** Loads packaged legal content without granting WebView file, content, script or network access. */
fun WebView.loadSecureLocalAsset(assetName: String) {
    require(assetName.matches(Regex("^[a-z0-9_\\-.]+$"))) {
        "Local WebView asset name is invalid."
    }

    val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler(
            LOCAL_ASSET_PREFIX,
            WebViewAssetLoader.AssetsPathHandler(context)
        )
        .build()

    CookieManager.getInstance().apply {
        setAcceptCookie(false)
        setAcceptThirdPartyCookies(this@loadSecureLocalAsset, false)
    }

    isVerticalScrollBarEnabled = false
    isHorizontalScrollBarEnabled = false

    settings.apply {
        javaScriptEnabled = false
        javaScriptCanOpenWindowsAutomatically = false
        domStorageEnabled = false
        databaseEnabled = false
        allowFileAccess = false
        allowContentAccess = false
        blockNetworkLoads = true
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        safeBrowsingEnabled = true
        setSupportMultipleWindows(false)
        mediaPlaybackRequiresUserGesture = true
        builtInZoomControls = false
        displayZoomControls = false
    }

    webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            return assetLoader.shouldInterceptRequest(request.url)
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            val uri = request.url
            if (uri.isApprovedLocalAsset()) {
                return false
            }
            if (uri.isApprovedSupportEmail()) {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_SENDTO, uri)
                    )
                }.onFailure { error ->
                    if (error !is ActivityNotFoundException) throw error
                }
            }
            return true
        }
    }

    loadUrl("https://$LOCAL_ASSET_HOST$LOCAL_ASSET_PREFIX$assetName")
}

fun WebView.destroySecureLocalContent() {
    stopLoading()
    webViewClient = WebViewClient()
    loadUrl("about:blank")
    clearHistory()
    removeAllViews()
    destroy()
}

private fun Uri.isApprovedLocalAsset(): Boolean {
    return scheme == "https" &&
        host == LOCAL_ASSET_HOST &&
        path.orEmpty().startsWith(LOCAL_ASSET_PREFIX)
}

private fun Uri.isApprovedSupportEmail(): Boolean {
    return PUBLICATION_SUPPORT_EMAIL.isNotBlank() &&
        scheme == "mailto" &&
        schemeSpecificPart.substringBefore('?').equals(
            PUBLICATION_SUPPORT_EMAIL,
            ignoreCase = true
        )
}

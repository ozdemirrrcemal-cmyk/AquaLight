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
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import com.aqua.aqualight.R

private const val LOCAL_ASSET_HOST = "appassets.androidplatform.net"
private const val LOCAL_ASSET_PREFIX = "/assets/"
private const val PUBLICATION_SUPPORT_EMAIL = ""
private val LOCAL_ASSET_NAME_PATTERN = Regex("^[a-z0-9_\\-.]+$")

/** Loads packaged legal content without granting WebView file, content, script or network access. */
fun WebView.loadSecureLocalAsset(
    assetName: String,
    onLocalAssetNavigation: (String) -> Unit = {}
) {
    require(assetName.matches(LOCAL_ASSET_NAME_PATTERN)) {
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
    setBackgroundColor(ContextCompat.getColor(context, R.color.background_color))

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
            val localAssetName = uri.approvedLocalAssetNameOrNull()
            if (localAssetName != null) {
                onLocalAssetNavigation(localAssetName)
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

private fun Uri.approvedLocalAssetNameOrNull(): String? {
    if (scheme != "https" || host != LOCAL_ASSET_HOST) return null

    val assetName = path.orEmpty()
        .takeIf { it.startsWith(LOCAL_ASSET_PREFIX) }
        ?.removePrefix(LOCAL_ASSET_PREFIX)
        ?: return null

    return assetName.takeIf { it.matches(LOCAL_ASSET_NAME_PATTERN) }
}

private fun Uri.isApprovedSupportEmail(): Boolean {
    return PUBLICATION_SUPPORT_EMAIL.isNotBlank() &&
        scheme == "mailto" &&
        schemeSpecificPart.substringBefore('?').equals(
            PUBLICATION_SUPPORT_EMAIL,
            ignoreCase = true
        )
}

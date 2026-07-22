package com.aqua.aqualight.ui.common.web

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.webkit.WebView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.aqua.aqualight.R
import com.aqua.aqualight.i18n.AppLanguageController
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class LegalDocumentDialogFragment : DialogFragment() {

    private var legalWebView: WebView? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val document = requireNotNull(
            arguments?.getString(ARG_DOCUMENT)
                ?.let { value -> runCatching { LegalDocument.valueOf(value) }.getOrNull() }
        ) {
            "Legal document argument is missing or invalid."
        }
        val content = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_legal_document, null, false)
        val webView = content.findViewById<WebView>(R.id.legalWebView)
        legalWebView = webView
        webView.loadSecureLocalAsset(
            document.assetFor(AppLanguageController.current())
        )

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(document.titleRes)
            .setView(content)
            .setPositiveButton(R.string.legal_close, null)
            .create()
    }

    override fun onDestroy() {
        legalWebView?.destroySecureLocalContent()
        legalWebView = null
        super.onDestroy()
    }

    companion object {
        private const val ARG_DOCUMENT = "legal_document"
        private const val TAG = "legal_document_dialog"

        fun show(
            fragmentManager: FragmentManager,
            document: LegalDocument
        ) {
            if (fragmentManager.findFragmentByTag(TAG) != null) return

            LegalDocumentDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DOCUMENT, document.name)
                }
            }.show(fragmentManager, TAG)
        }
    }
}

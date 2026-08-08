package com.aqua.aqualight.ui.common.web

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.i18n.SupportedLocaleRegistry

enum class LegalDocument(
    @StringRes val titleRes: Int,
    private val englishAsset: String,
    private val turkishAsset: String
) {
    PRIVACY(
        titleRes = R.string.legal_privacy_title,
        englishAsset = "privacy_policy_en.html",
        turkishAsset = "privacy_policy_tr.html"
    ),
    TERMS(
        titleRes = R.string.legal_terms_title,
        englishAsset = "terms_of_use_en.html",
        turkishAsset = "terms_of_use_tr.html"
    ),
    THIRD_PARTY_LICENSES(
        titleRes = R.string.legal_third_party_licenses_title,
        englishAsset = "open_source_licenses_en.html",
        turkishAsset = "open_source_licenses_tr.html"
    );

    fun assetFor(languageTag: String): String {
        return if (
            SupportedLocaleRegistry.resolve(languageTag) ==
            SupportedLocaleRegistry.TURKISH_LANGUAGE_TAG
        ) {
            turkishAsset
        } else {
            englishAsset
        }
    }
}

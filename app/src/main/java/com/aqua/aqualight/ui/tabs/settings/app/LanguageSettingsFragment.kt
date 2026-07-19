package com.aqua.aqualight.ui.tabs.settings.app

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentLanguageSettingsBinding
import com.aqua.aqualight.localization.SupportedLocaleRegistry
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.google.android.material.card.MaterialCardView
import com.google.android.material.radiobutton.MaterialRadioButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LanguageSettingsFragment : Fragment(R.layout.fragment_language_settings) {

    private var _binding: FragmentLanguageSettingsBinding? = null
    private val binding get() = _binding!!

    private val settingsOperations by lazy {
        requireContext().requireAppContainer().userSettingsOperations
    }

    private var languageRows: List<LanguageRow> = emptyList()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLanguageSettingsBinding.bind(view)

        setupHeader()
        setupLanguageRows()
        observeSelectedLanguage()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(fragment = this)
    }

    private fun setupLanguageRows() = with(binding) {
        languageRows = listOf(
            LanguageRow("tr", cardTurkish, radioTurkish),
            LanguageRow("en", cardEnglish, radioEnglish),
            LanguageRow("de", cardGerman, radioGerman),
            LanguageRow("fr", cardFrench, radioFrench)
        )

        // Russian and Chinese are not part of the Stage 11 product locale contract.
        // The existing XML geometry is intentionally untouched to preserve the approved visual style.
        cardRussian.isVisible = false
        cardChinese.isVisible = false

        languageRows.forEach { row ->
            val locale = requireNotNull(SupportedLocaleRegistry.find(row.languageTag)) {
                "Language row ${row.languageTag} is missing from SupportedLocaleRegistry."
            }
            val published = locale.isPublished
            val languageName = getString(locale.displayNameRes)

            row.card.isVisible = published
            row.card.isClickable = published
            row.card.isFocusable = published
            row.card.contentDescription = getString(
                R.string.language_option_accessibility,
                languageName
            )
            row.card.suppressDescendantAccessibility()

            row.radio.isClickable = false
            row.radio.isFocusable = false

            if (published) {
                row.card.setOnClickListener {
                    selectLanguage(locale.languageTag)
                }
            } else {
                row.card.setOnClickListener(null)
            }
        }
    }

    private fun observeSelectedLanguage() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            settingsOperations.languageCode.collectLatest(::updateLanguageSelection)
        }
    }

    private fun selectLanguage(languageTag: String) {
        val publishedLocale = requireNotNull(
            SupportedLocaleRegistry.findPublished(languageTag)
        ) {
            "Only published application locales may be selected."
        }

        viewLifecycleOwner.lifecycleScope.launch {
            settingsOperations.updateLanguage(publishedLocale.languageTag)
            applyLanguage(publishedLocale.languageTag)
            findNavController().popBackStack()
        }
    }

    private fun updateLanguageSelection(languageTag: String) {
        val selectedTag = SupportedLocaleRegistry.normalizePublishedTag(languageTag)

        languageRows.forEach { row ->
            val selected = row.languageTag == selectedTag
            row.radio.isChecked = selected
            ViewCompat.setStateDescription(
                row.card,
                getString(
                    if (selected) {
                        R.string.language_option_selected_state
                    } else {
                        R.string.language_option_not_selected_state
                    }
                )
            )
        }
    }

    private fun applyLanguage(languageTag: String) {
        val publishedTag = SupportedLocaleRegistry.normalizePublishedTag(languageTag)
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(publishedTag)
        )
    }

    private fun ViewGroup.suppressDescendantAccessibility() {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            child.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            if (child is ViewGroup) {
                child.suppressDescendantAccessibility()
            }
        }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onDestroyView() {
        super.onDestroyView()
        languageRows = emptyList()
        _binding = null
    }

    private data class LanguageRow(
        val languageTag: String,
        val card: MaterialCardView,
        val radio: MaterialRadioButton
    )
}

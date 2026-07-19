package com.aqua.aqualight.ui.tabs.settings.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentLanguageSettingsBinding
import com.aqua.aqualight.localization.SupportedLocaleRegistry
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LanguageSettingsFragment : Fragment(R.layout.fragment_language_settings) {

    private var _binding: FragmentLanguageSettingsBinding? = null
    private val binding get() = _binding!!

    private val settingsOperations by lazy {
        requireContext().requireAppContainer().userSettingsOperations
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLanguageSettingsBinding.bind(view)

        setupHeader()
        removeUnsupportedLanguageRows()
        setupSupportedLanguageClick()
        observeSelectedLanguage()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(fragment = this)
    }

    private fun removeUnsupportedLanguageRows() = with(binding) {
        listOf(
            cardTurkish,
            cardGerman,
            cardFrench,
            cardRussian,
            cardChinese
        ).forEach { card ->
            card.setOnClickListener(null)
            card.isEnabled = false
            card.visibility = View.GONE
        }
    }

    private fun setupSupportedLanguageClick() = with(binding) {
        val selectEnglish = View.OnClickListener {
            selectLanguage(SupportedLocaleRegistry.DEFAULT_LANGUAGE_TAG)
        }
        cardEnglish.setOnClickListener(selectEnglish)
        radioEnglish.setOnClickListener(selectEnglish)
    }

    private fun observeSelectedLanguage() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            settingsOperations.languageCode.collectLatest { code ->
                binding.radioEnglish.isChecked =
                    SupportedLocaleRegistry.normalize(code) ==
                    SupportedLocaleRegistry.DEFAULT_LANGUAGE_TAG
            }
        }
    }

    private fun selectLanguage(code: String) {
        val normalizedCode = SupportedLocaleRegistry.normalize(code)
        viewLifecycleOwner.lifecycleScope.launch {
            settingsOperations.updateLanguage(normalizedCode)
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(normalizedCode)
            )
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

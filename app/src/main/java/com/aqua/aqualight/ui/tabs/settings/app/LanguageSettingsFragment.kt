package com.aqua.aqualight.ui.tabs.settings.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentLanguageSettingsBinding
import com.aqua.aqualight.i18n.SupportedLocaleRegistry
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
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentLanguageSettingsBinding.bind(view)

        setupHeader()
        setupLanguageOptions()
        observeSelectedLanguage()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this
        )
    }

    private fun observeSelectedLanguage() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            settingsOperations.languageCode.collectLatest { code ->
                updateLanguageSelection(
                    code
                )
            }
        }
    }

    private fun setupLanguageOptions() =
        with(binding) {
            bindLanguageOption(
                code = "tr",
                card = cardTurkish,
                radio = radioTurkish
            )
            bindLanguageOption(
                code = "en",
                card = cardEnglish,
                radio = radioEnglish
            )
            bindLanguageOption(
                code = "de",
                card = cardGerman,
                radio = radioGerman
            )
            bindLanguageOption(
                code = "fr",
                card = cardFrench,
                radio = radioFrench
            )
            bindLanguageOption(
                code = "ru",
                card = cardRussian,
                radio = radioRussian
            )
            bindLanguageOption(
                code = "zh",
                card = cardChinese,
                radio = radioChinese
            )
        }

    private fun bindLanguageOption(
        code: String,
        card: View,
        radio: View
    ) {
        val supported =
            SupportedLocaleRegistry.isSupported(code)

        card.isVisible = supported
        radio.isVisible = supported

        if (!supported) {
            card.setOnClickListener(null)
            radio.setOnClickListener(null)
            return
        }

        val listener = View.OnClickListener {
            selectLanguage(code)
        }
        card.setOnClickListener(listener)
        radio.setOnClickListener(listener)
    }

    private fun selectLanguage(code: String) {
        val supportedCode =
            SupportedLocaleRegistry.supportedCanonicalOrNull(code)
                ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            settingsOperations.updateLanguage(
                supportedCode
            )

            applyLanguage(
                supportedCode
            )

            findNavController()
                .popBackStack()
        }
    }

    private fun updateLanguageSelection(
        code: String
    ) = with(binding) {

        radioTurkish.isChecked =
            false

        radioEnglish.isChecked =
            false

        radioGerman.isChecked =
            false

        radioFrench.isChecked =
            false

        radioRussian.isChecked =
            false

        radioChinese.isChecked =
            false

        when (SupportedLocaleRegistry.resolve(code)) {
            "en" -> radioEnglish.isChecked = true
        }
    }

    private fun applyLanguage(
        code: String
    ) {
        val localeList =
            LocaleListCompat.forLanguageTags(
                SupportedLocaleRegistry.resolve(code)
            )

        AppCompatDelegate.setApplicationLocales(
            localeList
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding =
            null
    }
}

package com.aqua.aqualight.ui.tabs.settings.app

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentLanguageSettingsBinding
import com.aqua.aqualight.i18n.SupportedLocaleRegistry
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
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
        setupLanguageOptions()
        observeSelectedLanguage()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                title = getString(R.string.screen_title_language)
            )
        )
    }

    private fun observeSelectedLanguage() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            settingsOperations.languageCode.collectLatest(::updateLanguageSelection)
        }
    }

    private fun setupLanguageOptions() = with(binding) {
        bindLanguageOption(
            code = SupportedLocaleRegistry.TURKISH_LANGUAGE_TAG,
            card = cardTurkish,
            radio = radioTurkish
        )
        bindLanguageOption(
            code = SupportedLocaleRegistry.ENGLISH_LANGUAGE_TAG,
            card = cardEnglish,
            radio = radioEnglish
        )
    }

    private fun bindLanguageOption(
        code: String,
        card: View,
        radio: View
    ) {
        val listener = View.OnClickListener {
            selectLanguage(code)
        }
        card.setOnClickListener(listener)
        radio.setOnClickListener(listener)
    }

    private fun selectLanguage(code: String) {
        val supportedCode = SupportedLocaleRegistry.supportedCanonicalOrNull(code) ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            settingsOperations.updateLanguage(supportedCode)
            findNavController().popBackStack()
        }
    }

    private fun updateLanguageSelection(code: String) = with(binding) {
        val selectedCode = SupportedLocaleRegistry.resolve(code)
        radioTurkish.isChecked =
            selectedCode == SupportedLocaleRegistry.TURKISH_LANGUAGE_TAG
        radioEnglish.isChecked =
            selectedCode == SupportedLocaleRegistry.ENGLISH_LANGUAGE_TAG
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

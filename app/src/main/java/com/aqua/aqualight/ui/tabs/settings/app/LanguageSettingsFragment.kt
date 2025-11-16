package com.aqua.aqualight.ui.tabs.settings.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.FragmentLanguageSettingsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LanguageSettingsFragment : Fragment(R.layout.fragment_language_settings) {

    private var _binding: FragmentLanguageSettingsBinding? = null
    private val binding get() = _binding!!

    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLanguageSettingsBinding.bind(view)

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        observeSelectedLanguage()
        setupLanguageClicks()
    }

    private fun observeSelectedLanguage() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            userPrefs.languageCode.collectLatest { code ->
                updateLanguageSelection(code)
            }
        }
    }

    private fun setupLanguageClicks() = with(binding) {

        fun select(code: String) {
            viewLifecycleOwner.lifecycleScope.launch {
                // 1) DataStore’a kaydet
                userPrefs.updateLanguage(code)

                // 2) Locale’i anında uygula
                applyLanguage(code)

                // 3) Bir önceki menüye dön
                findNavController().popBackStack()
            }
        }

        // Kart + radio’yu aynı davranışa bağla
        cardTurkish.setOnClickListener { select("tr") }
        radioTurkish.setOnClickListener { select("tr") }

        cardEnglish.setOnClickListener { select("en") }
        radioEnglish.setOnClickListener { select("en") }

        cardGerman.setOnClickListener { select("de") }
        radioGerman.setOnClickListener { select("de") }

        cardFrench.setOnClickListener { select("fr") }
        radioFrench.setOnClickListener { select("fr") }

        cardRussian.setOnClickListener { select("ru") }
        radioRussian.setOnClickListener { select("ru") }

        cardChinese.setOnClickListener { select("zh") }
        radioChinese.setOnClickListener { select("zh") }
    }

    private fun updateLanguageSelection(code: String) = with(binding) {
        // Hepsini sıfırla
        radioTurkish.isChecked = false
        radioEnglish.isChecked = false
        radioGerman.isChecked = false
        radioFrench.isChecked = false
        radioRussian.isChecked = false
        radioChinese.isChecked = false

        // Seçileni işaretle
        when (code) {
            "tr" -> radioTurkish.isChecked = true
            "en" -> radioEnglish.isChecked = true
            "de" -> radioGerman.isChecked = true
            "fr" -> radioFrench.isChecked = true
            "ru" -> radioRussian.isChecked = true
            "zh" -> radioChinese.isChecked = true
        }
    }

    private fun applyLanguage(code: String) {
        val safeCode = code.ifBlank { "en" }  // Default dil istersen "tr" de yapabilirsin
        val localeList = LocaleListCompat.forLanguageTags(safeCode)
        AppCompatDelegate.setApplicationLocales(localeList)
        // AppCompatDelegate değişiklik sonrası activity’yi recreate ediyor,
        // bu yüzden stringler hemen güncelleniyor.
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
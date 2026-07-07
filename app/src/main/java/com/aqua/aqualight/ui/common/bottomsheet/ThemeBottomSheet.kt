package com.aqua.aqualight.ui.common.bottomsheet

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.databinding.DialogThemeSelectionBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ThemeBottomSheet : BottomSheetDialogFragment(R.layout.dialog_theme_selection) {

    private var _binding: DialogThemeSelectionBinding? = null
    private val binding get() = _binding!!

    // DataStore tabanlı UserPreferences
    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }

    // Sheet kapandığında AppSettingsFragment isterse ekstra bir iş yapsın diye callback
    var onThemeChanged: (() -> Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = DialogThemeSelectionBinding.bind(view)

        // İlk açıldığında seçili modu radio'lara yansıt
        refreshRadios()

        with(binding) {
            layoutLight.setOnClickListener { selectTheme("light") }
            layoutDark.setOnClickListener { selectTheme("dark") }
            layoutSystem.setOnClickListener { selectTheme("system") }
        }
    }

    private fun selectTheme(mode: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            // 1) DataStore’a yaz
            userPrefs.updateThemeMode(mode)

            // 2) Radio’yu güncelle (sheet kapanmadan da doğru görünsün)
            updateRadios(mode)

            // 3) Küçük fade animasyonu ile temayı uygula
            val root = requireActivity()
                .window
                .decorView
                .findViewById<View>(android.R.id.content)

            root.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction {
                    applyTheme(mode)
                    root.animate()
                        .alpha(1f)
                        .setDuration(150)
                        .start()
                }
                .start()

            // 4) Sheet’i kapat
            dismiss()
        }
    }

    private fun refreshRadios() {
        viewLifecycleOwner.lifecycleScope.launch {
            // DataStore’daki themeMode değerini oku ("light" / "dark" / "system")
            val mode = userPrefs.themeMode.first()
            updateRadios(mode)
        }
    }

    private fun updateRadios(mode: String) = with(binding) {
        val lightSelected = mode == "light"
        val darkSelected = mode == "dark"
        val systemSelected = mode == "system"

        layoutLight.isSelected = lightSelected
        layoutDark.isSelected = darkSelected
        layoutSystem.isSelected = systemSelected

        radioLight.isChecked = lightSelected
        radioDark.isChecked = darkSelected
        radioSystem.isChecked = systemSelected
    }

    private fun applyTheme(mode: String) {
        when (mode) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onThemeChanged?.invoke()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
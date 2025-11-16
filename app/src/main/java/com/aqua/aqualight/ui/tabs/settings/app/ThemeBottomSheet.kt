package com.aqua.aqualight.ui.tabs.settings.app

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.data.UserPreferencesManager
import com.aqua.aqualight.databinding.DialogThemeSelectionBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ThemeBottomSheet : BottomSheetDialogFragment(R.layout.dialog_theme_selection) {

    private var _binding: DialogThemeSelectionBinding? = null
    private val binding get() = _binding!!

    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }

    var onThemeChanged: (() -> Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = DialogThemeSelectionBinding.bind(view)

        // Mevcut modu okuyup tikleri ayarla
        refreshChecks()

        with(binding) {
            layoutLight.setOnClickListener { selectTheme("light") }
            layoutDark.setOnClickListener { selectTheme("dark") }
            layoutSystem.setOnClickListener { selectTheme("system") }
        }
    }

    private fun selectTheme(mode: String) {
        // 1) DataStore’a kaydet
        viewLifecycleOwner.lifecycleScope.launch {
            userPrefs.updateThemeMode(mode)
        }

        // 2) Fade animasyonu + temayı uygula
        val activity = requireActivity() as FragmentActivity
        val root = activity.window
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

        // 3) Sheet’i kapat
        dismiss()
    }

    private fun refreshChecks() {
        viewLifecycleOwner.lifecycleScope.launch {
            val mode = userPrefs.themeMode.first() // Flow<String> -> "light"/"dark"/"system"

            with(binding) {
                checkLight.visibility = if (mode == "light") View.VISIBLE else View.GONE
                checkDark.visibility = if (mode == "dark") View.VISIBLE else View.GONE
                checkSystem.visibility = if (mode == "system") View.VISIBLE else View.GONE
            }
        }
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
        // AppSettingsFragment özet text’i yenilesin
        onThemeChanged?.invoke()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
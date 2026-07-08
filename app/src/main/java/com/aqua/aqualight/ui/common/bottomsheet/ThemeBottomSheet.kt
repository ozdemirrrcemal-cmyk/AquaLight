package com.aqua.aqualight.ui.common.bottomsheet

import android.app.UiModeManager
import android.content.DialogInterface
import android.os.Build
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

    private val userPrefs by lazy { UserPreferencesManager.create(requireContext()) }

    private var pendingThemeMode: String? = null

    var onThemeChanged: (() -> Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = DialogThemeSelectionBinding.bind(view)

        refreshRadios()

        with(binding) {
            layoutLight.setOnClickListener { selectTheme("light") }
            layoutDark.setOnClickListener { selectTheme("dark") }
            layoutSystem.setOnClickListener { selectTheme("system") }
        }
    }

    private fun selectTheme(mode: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            userPrefs.updateThemeMode(mode)
            updateRadios(mode)

            pendingThemeMode = mode
            dismissAllowingStateLoss()
        }
    }

    private fun refreshRadios() {
        viewLifecycleOwner.lifecycleScope.launch {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val uiModeManager = requireContext().getSystemService(UiModeManager::class.java)
            uiModeManager?.setApplicationNightMode(
                when (mode) {
                    "dark" -> UiModeManager.MODE_NIGHT_YES
                    "system" -> UiModeManager.MODE_NIGHT_AUTO
                    else -> UiModeManager.MODE_NIGHT_NO
                }
            )
            return
        }

        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                else -> AppCompatDelegate.MODE_NIGHT_NO
            }
        )
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        val selectedMode = pendingThemeMode
        pendingThemeMode = null

        if (selectedMode != null) {
            applyTheme(selectedMode)
        }

        onThemeChanged?.invoke()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

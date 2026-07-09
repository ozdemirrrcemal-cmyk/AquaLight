package com.aqua.aqualight.ui.common.bottomsheet

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import com.aqua.aqualight.R
import com.aqua.aqualight.base.theme.AppThemeController
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.databinding.DialogThemeSelectionBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ThemeBottomSheet : BottomSheetDialogFragment(R.layout.dialog_theme_selection) {

    private var _binding: DialogThemeSelectionBinding? = null
    private val binding get() = _binding!!

    private val userPrefs by lazy {
        UserPreferencesManager.create(requireContext())
    }

    var onThemeChanged: (() -> Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = DialogThemeSelectionBinding.bind(view)

        refreshRadios()

        with(binding) {
            layoutLight.setOnClickListener {
                selectTheme(AppThemeController.MODE_LIGHT)
            }

            layoutDark.setOnClickListener {
                selectTheme(AppThemeController.MODE_DARK)
            }

            layoutSystem.setOnClickListener {
                selectTheme(AppThemeController.MODE_SYSTEM)
            }
        }
    }

    private fun selectTheme(mode: String) {
        val normalizedMode = AppThemeController.normalize(
            mode
        )

        viewLifecycleOwner.lifecycleScope.launch {
            userPrefs.updateThemeMode(
                normalizedMode
            )

            updateRadios(
                normalizedMode
            )

            val hostActivity =
                activity

            dismiss()

            hostActivity?.window?.decorView?.post {
                runCatching {
                    Navigation.findNavController(
                        hostActivity,
                        R.id.nav_host
                    ).popBackStack(
                        R.id.settingsFragment,
                        false
                    )
                }

                AppThemeController.apply(
                    context = hostActivity.applicationContext,
                    mode = normalizedMode
                )

                onThemeChanged?.invoke()
            }
        }
    }

    private fun refreshRadios() {
        viewLifecycleOwner.lifecycleScope.launch {
            val mode = userPrefs.themeMode.first()

            updateRadios(
                mode
            )
        }
    }

    private fun updateRadios(mode: String) = with(binding) {
        val normalizedMode = AppThemeController.normalize(
            mode
        )

        val lightSelected = normalizedMode == AppThemeController.MODE_LIGHT
        val darkSelected = normalizedMode == AppThemeController.MODE_DARK
        val systemSelected = normalizedMode == AppThemeController.MODE_SYSTEM

        layoutLight.isSelected = lightSelected
        layoutDark.isSelected = darkSelected
        layoutSystem.isSelected = systemSelected

        radioLight.isChecked = lightSelected
        radioDark.isChecked = darkSelected
        radioSystem.isChecked = systemSelected
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

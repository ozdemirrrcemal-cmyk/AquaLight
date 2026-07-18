package com.aqua.aqualight.ui.common.bottomsheet

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.base.theme.AppThemeController
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.DialogThemeSelectionBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ThemeBottomSheet : BottomSheetDialogFragment(R.layout.dialog_theme_selection) {

    private var _binding: DialogThemeSelectionBinding? = null
    private val binding get() = _binding!!

    private val settingsOperations by lazy {
        requireContext().requireAppContainer().userSettingsOperations
    }

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
        val normalizedMode = AppThemeController.normalize(mode)

        viewLifecycleOwner.lifecycleScope.launch {
            settingsOperations.updateThemeMode(normalizedMode)
            updateRadios(normalizedMode)

            val appContext = context?.applicationContext
            val hostView = activity?.window?.decorView

            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf(RESULT_THEME_MODE to normalizedMode)
            )
            dismiss()

            hostView?.post {
                if (appContext != null) {
                    AppThemeController.apply(
                        context = appContext,
                        mode = normalizedMode
                    )
                }
            }
        }
    }

    private fun refreshRadios() {
        viewLifecycleOwner.lifecycleScope.launch {
            updateRadios(settingsOperations.themeMode.first())
        }
    }

    private fun updateRadios(mode: String) = with(binding) {
        val normalizedMode = AppThemeController.normalize(mode)
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
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val REQUEST_KEY = "theme_bottom_sheet_result"
        const val RESULT_THEME_MODE = "theme_mode"

        private const val TAG = "ThemeBottomSheet"

        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.findFragmentByTag(TAG) != null) return
            ThemeBottomSheet().show(fragmentManager, TAG)
        }
    }
}

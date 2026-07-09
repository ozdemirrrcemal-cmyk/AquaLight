package com.aqua.aqualight.ui.common.bottomsheet

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.base.theme.AppThemeController
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.databinding.DialogThemeSelectionBinding
import com.aqua.aqualight.ui.main.MainActivity
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
                AppThemeController.apply(
                    context = hostActivity.applicationContext,
                    mode = normalizedMode
                )

                restartMainActivityForThemeChange(
                    hostActivity
                )
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

    private fun restartMainActivityForThemeChange(
        hostActivity: FragmentActivity
    ) {
        val restartIntent = Intent(
            hostActivity,
            MainActivity::class.java
        ).apply {
            putExtra(
                MainActivity.EXTRA_START_IN_APP,
                true
            )
            putExtra(
                MainActivity.EXTRA_OPEN_SETTINGS_TAB,
                true
            )
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
        }

        hostActivity.startActivity(
            restartIntent
        )
        hostActivity.overridePendingTransition(
            0,
            0
        )
        hostActivity.finish()
        hostActivity.overridePendingTransition(
            0,
            0
        )

        onThemeChanged?.invoke()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

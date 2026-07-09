package com.aqua.aqualight.base.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate

object AppThemeController {

    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"
    const val MODE_SYSTEM = "system"

    fun normalize(
        mode: String
    ): String {
        return when (mode.trim().lowercase()) {
            MODE_DARK -> MODE_DARK
            MODE_SYSTEM -> MODE_SYSTEM
            else -> MODE_LIGHT
        }
    }

    fun apply(
        context: Context,
        mode: String
    ) {
        val normalizedMode = normalize(
            mode
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val uiModeManager =
                context.applicationContext.getSystemService(
                    UiModeManager::class.java
                )

            uiModeManager?.setApplicationNightMode(
                normalizedMode.toPlatformNightMode()
            )

            return
        }

        AppCompatDelegate.setDefaultNightMode(
            normalizedMode.toAppCompatNightMode()
        )
    }

    private fun String.toPlatformNightMode(): Int {
        return when (this) {
            MODE_DARK -> UiModeManager.MODE_NIGHT_YES
            MODE_SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            else -> UiModeManager.MODE_NIGHT_NO
        }
    }

    private fun String.toAppCompatNightMode(): Int {
        return when (this) {
            MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            MODE_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_NO
        }
    }
}

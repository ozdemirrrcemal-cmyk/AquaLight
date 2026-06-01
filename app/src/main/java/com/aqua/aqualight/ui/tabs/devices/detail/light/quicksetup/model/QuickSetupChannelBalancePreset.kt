package com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model

import androidx.annotation.StringRes
import com.aqua.aqualight.R

enum class QuickSetupChannelBalancePreset(
    @StringRes val labelRes: Int,
    val red: Int,
    val green: Int,
    val blue: Int,
    val white: Int
) {
    NATURAL(
        labelRes = R.string.light_quick_setup_balance_natural,
        red = 80,
        green = 84,
        blue = 79,
        white = 65
    ),

    PLANT(
        labelRes = R.string.light_quick_setup_balance_plant,
        red = 85,
        green = 92,
        blue = 76,
        white = 70
    ),

    WARM(
        labelRes = R.string.light_quick_setup_balance_warm,
        red = 90,
        green = 76,
        blue = 55,
        white = 70
    ),

    BLUE(
        labelRes = R.string.light_quick_setup_balance_blue,
        red = 55,
        green = 68,
        blue = 95,
        white = 50
    )
}
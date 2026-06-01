package com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import java.io.Serializable

enum class QuickSetupChannelBalancePreset(
    @StringRes val labelRes: Int,
    val balance: WrgbChannelBalance
) : Serializable {

    NATURAL(
        labelRes = R.string.light_quick_setup_balance_natural,
        balance =
            WrgbChannelBalance(
                red = 80,
                green = 84,
                blue = 79,
                white = 65
            )
    ),

    PLANT(
        labelRes = R.string.light_quick_setup_balance_plant,
        balance =
            WrgbChannelBalance(
                red = 85,
                green = 92,
                blue = 76,
                white = 70
            )
    ),

    WARM(
        labelRes = R.string.light_quick_setup_balance_warm,
        balance =
            WrgbChannelBalance(
                red = 90,
                green = 76,
                blue = 55,
                white = 70
            )
    ),

    BLUE(
        labelRes = R.string.light_quick_setup_balance_blue,
        balance =
            WrgbChannelBalance(
                red = 55,
                green = 68,
                blue = 95,
                white = 50
            )
    )
}
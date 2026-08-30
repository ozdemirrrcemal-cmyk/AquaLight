package com.aqua.aqualight.ui.common.header

import androidx.annotation.DrawableRes

data class AquaHeaderAction(
    @DrawableRes val iconRes: Int,
    val contentDescription: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true
)

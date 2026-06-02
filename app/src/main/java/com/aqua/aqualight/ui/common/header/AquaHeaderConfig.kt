package com.aqua.aqualight.ui.common.header

import androidx.annotation.DrawableRes

data class AquaHeaderConfig(
    val title: String,
    val showBackButton: Boolean = true,
    val onBackClick: (() -> Unit)? = null,
    @DrawableRes val statusIconRes: Int? = null,
    val actions: List<AquaHeaderAction> = emptyList()
)
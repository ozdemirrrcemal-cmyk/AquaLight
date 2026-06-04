package com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model

data class QuickSetupUiState(
    val isLoading: Boolean = false,
    val hasLinkedTank: Boolean = false,

    val tankProfile: QuickSetupTankProfile? = null,
    val recommendation: QuickSetupRecommendation? = null,

    val errorMessage: String? = null
)
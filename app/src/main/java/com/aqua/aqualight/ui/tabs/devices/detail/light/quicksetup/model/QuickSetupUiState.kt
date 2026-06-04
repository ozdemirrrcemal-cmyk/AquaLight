package com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model

data class QuickSetupUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val hasLinkedTank: Boolean = false,

    val tankProfile: QuickSetupTankProfile? = null,
    val recommendation: QuickSetupRecommendation? = null,

    val savedProgramId: String? = null,
    val isProgramSaved: Boolean = false,
    val isProgramLoaded: Boolean = false,

    val errorMessage: String? = null
)
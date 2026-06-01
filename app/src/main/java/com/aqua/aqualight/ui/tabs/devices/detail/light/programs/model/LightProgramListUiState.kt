package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model

data class LightProgramListUiState(
    val isLoading: Boolean = false,
    val activeProgram: LightProgramListItem? = null,
    val programs: List<LightProgramListItem> = emptyList()
)
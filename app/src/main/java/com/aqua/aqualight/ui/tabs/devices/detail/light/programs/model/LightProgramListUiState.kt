package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model

data class LightProgramListUiState(
    val selectedFilter: ProgramFilter = ProgramFilter.ALL,
    val allPrograms: List<LightProgramListItem> = emptyList(),
    val visiblePrograms: List<LightProgramListItem> = emptyList()
) {

    val showEmptyState: Boolean
        get() = allPrograms.isEmpty()

    val showFilterBar: Boolean
        get() = allPrograms.isNotEmpty()

    val showProgramList: Boolean
        get() = visiblePrograms.isNotEmpty()
}
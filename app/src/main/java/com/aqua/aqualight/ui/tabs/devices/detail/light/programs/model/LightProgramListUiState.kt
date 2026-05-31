package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model

data class LightProgramListUiState(
    val isLoading: Boolean = false,
    val programs: List<LightProgramListItem> = emptyList(),
    val selectedFilter: ProgramFilter = ProgramFilter.ALL,
    val activeProgramId: String? = null
) {

    val activeProgram: LightProgramListItem?
        get() {
            return activeProgramId
                ?.let { id ->
                    programs.firstOrNull { program ->
                        program.id == id
                    }
                }
                ?: programs.firstOrNull { program ->
                    program.isEnabled
                }
        }

    val filteredPrograms: List<LightProgramListItem>
        get() {
            return when (selectedFilter) {
                ProgramFilter.ALL -> {
                    programs
                }

                ProgramFilter.ACTIVE -> {
                    programs.filter { program ->
                        program.isEnabled
                    }
                }

                ProgramFilter.DISABLED -> {
                    programs.filter { program ->
                        !program.isEnabled
                    }
                }
            }
        }

    val shouldShowSummary: Boolean
        get() = activeProgram != null

    val shouldShowFilters: Boolean
        get() = programs.isNotEmpty()

    val shouldShowList: Boolean
        get() = filteredPrograms.isNotEmpty()

    val shouldShowEmptyState: Boolean
        get() = filteredPrograms.isEmpty()
}
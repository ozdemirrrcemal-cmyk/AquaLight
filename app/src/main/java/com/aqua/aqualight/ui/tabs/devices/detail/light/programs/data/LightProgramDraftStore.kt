package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.data

import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgram

object LightProgramDraftStore {

    private val programsByDeviceId: MutableMap<Long, MutableList<SavedLightProgram>> =
        mutableMapOf()

    fun getPrograms(
        deviceId: Long
    ): List<SavedLightProgram> {
        return programsByDeviceId[deviceId]
            ?.sortedByDescending { program ->
                program.updatedAtMillis
            }
            .orEmpty()
    }

    fun getActiveProgram(
        deviceId: Long
    ): SavedLightProgram? {
        return programsByDeviceId[deviceId]
            ?.firstOrNull { program ->
                program.isActive && program.isEnabled
            }
    }
	
	fun getProgram(
    deviceId: Long,
    programId: String
): SavedLightProgram? {
    return programsByDeviceId[deviceId]
        ?.firstOrNull { program ->
            program.id == programId
        }
}

    fun upsertProgram(
        program: SavedLightProgram
    ) {
        val currentPrograms =
            programsByDeviceId
                .getOrPut(program.deviceId) {
                    mutableListOf()
                }

        val shouldBeActive =
            program.isActive ||
                currentPrograms.none { item ->
                    item.isActive && item.isEnabled
                }

        val normalizedProgram =
            program.copy(
                isActive = shouldBeActive,
                updatedAtMillis = System.currentTimeMillis()
            )

        val updatedPrograms =
            currentPrograms
                .filterNot { item ->
                    item.id == normalizedProgram.id
                }
                .map { item ->
                    if (shouldBeActive) {
                        item.copy(
                            isActive = false
                        )
                    } else {
                        item
                    }
                }
                .toMutableList()

        updatedPrograms.add(
            0,
            normalizedProgram
        )

        programsByDeviceId[program.deviceId] = updatedPrograms
    }

    fun setActiveProgram(
        deviceId: Long,
        programId: String
    ) {
        val currentPrograms =
            programsByDeviceId[deviceId] ?: return

        programsByDeviceId[deviceId] =
            currentPrograms
                .map { program ->
                    program.copy(
                        isActive = program.id == programId,
                        isEnabled =
                            if (program.id == programId) {
                                true
                            } else {
                                program.isEnabled
                            },
                        updatedAtMillis =
                            if (program.id == programId) {
                                System.currentTimeMillis()
                            } else {
                                program.updatedAtMillis
                            }
                    )
                }
                .toMutableList()
    }

    fun deleteProgram(
        deviceId: Long,
        programId: String
    ) {
        val currentPrograms =
            programsByDeviceId[deviceId] ?: return

        val filteredPrograms =
            currentPrograms
                .filterNot { program ->
                    program.id == programId
                }
                .toMutableList()

        if (
            filteredPrograms.none { program ->
                program.isActive && program.isEnabled
            }
        ) {
            val firstEnabledProgram =
                filteredPrograms.firstOrNull { program ->
                    program.isEnabled
                }

            if (firstEnabledProgram != null) {
                val index =
                    filteredPrograms.indexOfFirst { program ->
                        program.id == firstEnabledProgram.id
                    }

                filteredPrograms[index] =
                    firstEnabledProgram.copy(
                        isActive = true
                    )
            }
        }

        programsByDeviceId[deviceId] = filteredPrograms
    }
	
	fun setProgramEnabled(
    deviceId: Long,
    programId: String,
    isEnabled: Boolean
) {
    val currentPrograms =
        programsByDeviceId[deviceId] ?: return

    val updatedPrograms =
        currentPrograms
            .map { program ->
                if (program.id == programId) {
                    program.copy(
                        isEnabled = isEnabled,
                        isActive =
                            if (isEnabled) {
                                program.isActive
                            } else {
                                false
                            },
                        updatedAtMillis = System.currentTimeMillis()
                    )
                } else {
                    program
                }
            }
            .toMutableList()

    if (
        updatedPrograms.none { program ->
            program.isActive && program.isEnabled
        }
    ) {
        val firstEnabledIndex =
            updatedPrograms.indexOfFirst { program ->
                program.isEnabled
            }

        if (firstEnabledIndex >= 0) {
            val firstEnabledProgram = updatedPrograms[firstEnabledIndex]

            updatedPrograms[firstEnabledIndex] =
                firstEnabledProgram.copy(
                    isActive = true,
                    updatedAtMillis = System.currentTimeMillis()
                )
        }
    }

    programsByDeviceId[deviceId] = updatedPrograms
}

fun duplicateProgram(
    deviceId: Long,
    programId: String
) {
    val currentPrograms =
        programsByDeviceId[deviceId] ?: return

    val sourceProgram =
        currentPrograms.firstOrNull { program ->
            program.id == programId
        } ?: return

    val duplicatedProgram =
        sourceProgram.copy(
            id = "light_program_${System.currentTimeMillis()}",
            title = "${sourceProgram.title} Copy",
            isActive = false,
            createdAtMillis = System.currentTimeMillis(),
            updatedAtMillis = System.currentTimeMillis()
        )

    upsertProgram(
        program = duplicatedProgram
    )
}

    fun clearDevicePrograms(
        deviceId: Long
    ) {
        programsByDeviceId.remove(deviceId)
    }
}
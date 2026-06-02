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

    fun getProgram(
        deviceId: Long,
        programId: String
    ): SavedLightProgram? {
        return programsByDeviceId[deviceId]
            ?.firstOrNull { program ->
                program.id == programId
            }
    }

    fun getActiveProgram(
        deviceId: Long
    ): SavedLightProgram? {
        return programsByDeviceId[deviceId]
            ?.firstOrNull { program ->
                program.isActive && program.isEnabled
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

        val normalizedProgram =
            program.copy(
                updatedAtMillis = System.currentTimeMillis()
            )

        val updatedPrograms =
            currentPrograms
                .filterNot { item ->
                    item.id == normalizedProgram.id
                }
                .map { item ->
                    if (normalizedProgram.isActive) {
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

        val targetExists =
            currentPrograms.any { program ->
                program.id == programId
            }

        if (!targetExists) {
            return
        }

        programsByDeviceId[deviceId] =
            currentPrograms
                .map { program ->
                    if (program.id == programId) {
                        program.copy(
                            isEnabled = true,
                            isActive = true,
                            updatedAtMillis = System.currentTimeMillis()
                        )
                    } else {
                        program.copy(
                            isActive = false
                        )
                    }
                }
                .toMutableList()
    }
	
	fun clearActiveProgram(
    deviceId: Long
) {
    val currentPrograms =
        programsByDeviceId[deviceId] ?: return

    programsByDeviceId[deviceId] =
        currentPrograms
            .map { program ->
                if (program.isActive) {
                    program.copy(
                        isActive = false,
                        updatedAtMillis = System.currentTimeMillis()
                    )
                } else {
                    program
                }
            }
            .toMutableList()
}

    fun setProgramEnabled(
        deviceId: Long,
        programId: String,
        isEnabled: Boolean
    ) {
        val currentPrograms =
            programsByDeviceId[deviceId] ?: return

        programsByDeviceId[deviceId] =
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

        val now = System.currentTimeMillis()

        val duplicatedProgram =
            sourceProgram.copy(
                id = "light_program_$now",
                title = "${sourceProgram.title} Copy",
                isActive = false,
                createdAtMillis = now,
                updatedAtMillis = now
            )

        upsertProgram(
            program = duplicatedProgram
        )
    }

    fun deleteProgram(
        deviceId: Long,
        programId: String
    ) {
        val currentPrograms =
            programsByDeviceId[deviceId] ?: return

        programsByDeviceId[deviceId] =
            currentPrograms
                .filterNot { program ->
                    program.id == programId
                }
                .toMutableList()
    }

    fun clearDevicePrograms(
        deviceId: Long
    ) {
        programsByDeviceId.remove(deviceId)
    }
}
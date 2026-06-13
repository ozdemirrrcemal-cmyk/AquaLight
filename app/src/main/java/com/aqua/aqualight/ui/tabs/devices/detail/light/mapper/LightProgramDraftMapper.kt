package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.mapper

import com.aqua.aqualight.ui.tabs.devices.detail.light.core.programs.model.LightProgramDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.programs.model.SavedLightProgram
import java.util.UUID

object LightProgramDraftMapper {

    fun toSavedProgram(
        draft: LightProgramDraft,
        name: String,
        deviceId: Long,
        isActive: Boolean
    ): SavedLightProgram {
        val now = System.currentTimeMillis()

        return SavedLightProgram(
            id = UUID.randomUUID().toString(),
            deviceId = deviceId,
            name = name,
            draft = draft,
            isActive = isActive,
            createdAt = now,
            updatedAt = now
        )
    }
}
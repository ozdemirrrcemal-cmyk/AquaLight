package com.aqua.aqualight.data.devices.light.programs

import android.content.Context
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramDraft
import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram
import kotlinx.coroutines.flow.Flow

/**
 * Repository boundary for saved light programs.
 *
 * UI talks to this class, not to Proto/DataStore or device APIs directly.
 * Device upload will later be added behind use-cases without changing the
 * editor/list screen contracts.
 */
class LightProgramRepository private constructor(
    private val store: LightProgramDataStoreManager
) {

    companion object {
        @Volatile
        private var INSTANCE: LightProgramRepository? = null

        fun get(
            context: Context
        ): LightProgramRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LightProgramRepository(
                    store = LightProgramDataStoreManager.create(context.applicationContext)
                ).also { repository ->
                    INSTANCE = repository
                }
            }
        }
    }

    fun observePrograms(
        deviceId: Long
    ): Flow<List<SavedLightProgram>> {
        return store.programsForDeviceFlow(deviceId)
    }

    suspend fun getProgram(
        deviceId: Long,
        programId: String
    ): SavedLightProgram? {
        return store.getProgram(
            deviceId = deviceId,
            programId = programId
        )
    }

    suspend fun saveProgram(
        deviceId: Long,
        programId: String?,
        name: String,
        draft: LightProgramDraft,
        makeActiveLocally: Boolean
    ): SavedLightProgram {
        val normalizedProgramId = programId
            ?.takeIf { id -> id.isNotBlank() }

        val saved = if (normalizedProgramId == null) {
            store.createProgram(
                deviceId = deviceId,
                name = name,
                draft = draft,
                isActive = makeActiveLocally
            )
        } else {
            store.updateProgram(
                deviceId = deviceId,
                programId = normalizedProgramId,
                name = name,
                draft = draft
            ) ?: error("Program not found.")
        }

        return if (makeActiveLocally && !saved.isActive) {
            store.setProgramActive(
                deviceId = deviceId,
                programId = saved.id,
                isActive = true
            ) ?: saved.copy(isActive = true)
        } else {
            saved
        }
    }

    suspend fun renameProgram(
        deviceId: Long,
        programId: String,
        name: String
    ): SavedLightProgram {
        return store.renameProgram(
            deviceId = deviceId,
            programId = programId,
            name = name
        ) ?: error("Program not found.")
    }

    suspend fun duplicateProgram(
        deviceId: Long,
        programId: String
    ): SavedLightProgram {
        return store.duplicateProgram(
            deviceId = deviceId,
            programId = programId
        ) ?: error("Program not found.")
    }

    suspend fun deleteProgram(
        deviceId: Long,
        programId: String
    ) {
        val deleted = store.deleteProgram(
            deviceId = deviceId,
            programId = programId
        )

        if (!deleted) {
            error("Program not found.")
        }
    }

    suspend fun setProgramActive(
        deviceId: Long,
        programId: String,
        isActive: Boolean
    ): SavedLightProgram {
        return store.setProgramActive(
            deviceId = deviceId,
            programId = programId,
            isActive = isActive
        ) ?: error("Program not found.")
    }
}

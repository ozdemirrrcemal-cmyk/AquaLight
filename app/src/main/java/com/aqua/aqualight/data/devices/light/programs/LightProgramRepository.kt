package com.aqua.aqualight.data.devices.light.programs

import android.content.Context
import com.aqua.aqualight.data.devices.light.programs.activation.ActivateLightProgramUseCase
import com.aqua.aqualight.data.devices.light.programs.activation.LightProgramActivationResult
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramDraft
import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeRepository
import kotlinx.coroutines.flow.Flow

/**
 * Repository boundary for saved light programs.
 *
 * UI talks to this class, not to Proto/DataStore or device APIs directly.
 * Device upload is hidden behind use-cases so editor/list screens never talk
 * directly to firmware APIs or protocol payloads.
 */
class LightProgramRepository private constructor(
    private val store: LightProgramDataStoreManager,
    private val activateProgramUseCase: ActivateLightProgramUseCase
) {

    companion object {
        @Volatile
        private var INSTANCE: LightProgramRepository? = null

        fun get(
            context: Context
        ): LightProgramRepository {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                val store = LightProgramDataStoreManager.create(appContext)
                INSTANCE ?: LightProgramRepository(
                    store = store,
                    activateProgramUseCase = ActivateLightProgramUseCase(
                        store = store,
                        runtimeRepository = LightRuntimeRepository.get(appContext)
                    )
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

    suspend fun activateProgram(
        deviceId: Long,
        programId: String
    ): LightProgramActivationResult {
        return activateProgramUseCase.activate(
            deviceId = deviceId,
            programId = programId
        )
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

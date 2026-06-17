package com.aqua.aqualight.data.devices.light.programs

import android.content.Context
import com.aqua.aqualight.data.devices.light.programs.activation.ActivateLightProgramUseCase
import com.aqua.aqualight.data.devices.light.programs.activation.LightProgramActivationResult
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramDraft
import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram
import com.aqua.aqualight.data.devices.light.programs.recovery.AutoRecoverActiveLightProgramUseCase
import com.aqua.aqualight.data.devices.light.programs.sync.LightProgramDeviceProgramsSnapshot
import com.aqua.aqualight.data.devices.light.programs.sync.LightProgramDeviceSyncMatcher
import com.aqua.aqualight.data.devices.light.programs.sync.LightProgramDeviceSyncState
import com.aqua.aqualight.data.devices.light.programs.sync.LightProgramDeviceSyncStatus
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Repository boundary for saved light programs.
 *
 * UI talks to this class, not to Proto/DataStore or device APIs directly.
 * Device upload is hidden behind use-cases so editor/list screens never talk
 * directly to firmware APIs or protocol payloads.
 */
class LightProgramRepository private constructor(
    private val store: LightProgramDataStoreManager,
    private val runtimeRepository: LightRuntimeRepository,
    private val activateProgramUseCase: ActivateLightProgramUseCase,
    private val autoRecoverActiveLightProgramUseCase: AutoRecoverActiveLightProgramUseCase
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
                val runtimeRepository = LightRuntimeRepository.get(appContext)
                INSTANCE ?: LightProgramRepository(
                    store = store,
                    runtimeRepository = runtimeRepository,
                    activateProgramUseCase = ActivateLightProgramUseCase(
                        store = store,
                        runtimeRepository = runtimeRepository
                    ),
                    autoRecoverActiveLightProgramUseCase = AutoRecoverActiveLightProgramUseCase(
                        store = store
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

    fun observeDeviceProgramSyncState(
        deviceId: Long
    ): Flow<LightProgramDeviceSyncState> {
        val session = runtimeRepository.session(deviceId)
        return combine(
            store.programsForDeviceFlow(deviceId),
            session.state
        ) { programs, runtimeState ->
            LightProgramDeviceSyncMatcher.match(
                programs = programs,
                runtimeState = runtimeState
            )
        }
    }

    fun observeProgramsWithDeviceSync(
        deviceId: Long
    ): Flow<LightProgramDeviceProgramsSnapshot> {
        val session = runtimeRepository.session(deviceId)
        return combine(
            store.programsForDeviceFlow(deviceId),
            session.state
        ) { programs, runtimeState ->
            LightProgramDeviceProgramsSnapshot(
                programs = programs,
                syncState = LightProgramDeviceSyncMatcher.match(
                    programs = programs,
                    runtimeState = runtimeState
                )
            )
        }
    }

    fun acquireDeviceRuntimeSync(
        deviceId: Long,
        consumerKey: String
    ) {
        if (deviceId <= 0L || consumerKey.isBlank()) return
        runtimeRepository.session(deviceId).acquire(consumerKey)
    }

    fun releaseDeviceRuntimeSync(
        deviceId: Long,
        consumerKey: String
    ) {
        if (deviceId <= 0L || consumerKey.isBlank()) return
        runtimeRepository.session(deviceId).release(consumerKey)
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
        val program = store.getProgram(
            deviceId = deviceId,
            programId = programId
        ) ?: error("Program not found.")

        if (shouldClearDeviceScheduleOnDelete(deviceId, program)) {
            activateProgramUseCase.clearDeviceSchedule(deviceId)
        }

        val deleted = store.deleteProgram(
            deviceId = deviceId,
            programId = programId
        )

        if (!deleted) {
            error("Program not found.")
        }
    }

    private fun shouldClearDeviceScheduleOnDelete(
        deviceId: Long,
        program: SavedLightProgram
    ): Boolean {
        val syncState = LightProgramDeviceSyncMatcher.match(
            programs = listOf(program),
            runtimeState = runtimeRepository.session(deviceId).state.value
        )

        if (syncState.matchedProgramId == program.id) {
            return true
        }

        return program.isActive &&
            syncState.status == LightProgramDeviceSyncStatus.NO_RUNTIME
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

    suspend fun autoRecoverActiveDeviceProgram(
        deviceId: Long
    ): SavedLightProgram? {
        if (deviceId <= 0L) return null

        val snapshot = runtimeRepository.session(deviceId)
            .state
            .value
            .snapshot

        return autoRecoverActiveLightProgramUseCase.recoverIfNeeded(
            deviceId = deviceId,
            snapshot = snapshot
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

package com.aqua.aqualight.data.devices.light.programs

import android.content.Context
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.light.programs.compiler.CompiledLightProgramSchedule
import com.aqua.aqualight.data.devices.light.programs.compiler.LightProgramCompileResult
import com.aqua.aqualight.data.devices.light.programs.compiler.LightProgramScheduleCompiler
import com.aqua.aqualight.data.devices.light.programs.device.LightProgramDevicePayloadMapper
import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeRepository
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramDraft
import kotlinx.coroutines.flow.Flow

class LightProgramsRepository private constructor(
    private val store: LightProgramDataStoreManager,
    private val runtimeRepository: LightRuntimeRepository
) {

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

    suspend fun saveDraft(
        deviceId: Long,
        programId: String?,
        name: String,
        draft: LightProgramDraft
    ): SaveLightProgramResult {
        val safeName = name.trim()
        if (safeName.isBlank()) {
            return SaveLightProgramResult.Error("Program name is required")
        }

        val resolvedProgramId = programId.orEmpty().ifBlank {
            "draft"
        }
        val normalizedDraft = draft.normalizedForCurrentFirmware()

        when (val compileResult = LightProgramScheduleCompiler.compileDraft(
            programId = resolvedProgramId,
            programName = safeName,
            draft = normalizedDraft
        )) {
            is LightProgramCompileResult.Invalid -> {
                return SaveLightProgramResult.Error(compileResult.message)
            }

            is LightProgramCompileResult.Success -> Unit
        }

        return runCatching {
            val saved = store.saveDraft(
                deviceId = deviceId,
                programId = programId,
                name = safeName,
                draft = normalizedDraft,
                syncState = null,
                active = null
            )
            SaveLightProgramResult.Success(saved)
        }.getOrElse { exception ->
            SaveLightProgramResult.Error(
                exception.message ?: "Program could not be saved"
            )
        }
    }

    suspend fun loadDraftToDevice(
        deviceId: Long,
        programId: String?,
        name: String,
        draft: LightProgramDraft
    ): LoadLightProgramResult {
        val safeName = name.trim()
        if (safeName.isBlank()) {
            return LoadLightProgramResult.Error("Program name is required")
        }

        val saved = when (val saveResult = saveDraft(
            deviceId = deviceId,
            programId = programId,
            name = safeName,
            draft = draft
        )) {
            is SaveLightProgramResult.Success -> saveResult.program
            is SaveLightProgramResult.Error -> return LoadLightProgramResult.Error(saveResult.message)
        }

        return loadSavedProgramToDevice(saved)
    }

    suspend fun loadProgramToDevice(
        deviceId: Long,
        programId: String
    ): LoadLightProgramResult {
        val program = store.getProgram(
            deviceId = deviceId,
            programId = programId
        ) ?: return LoadLightProgramResult.Error("Program could not be found")

        return loadSavedProgramToDevice(program)
    }

    suspend fun setProgramActive(
        deviceId: Long,
        programId: String,
        isActive: Boolean
    ): LoadLightProgramResult {
        if (isActive) {
            return loadProgramToDevice(
                deviceId = deviceId,
                programId = programId
            )
        }

        val updated = store.setProgramActiveLocal(
            deviceId = deviceId,
            programId = programId,
            isActive = false
        )

        return if (updated) {
            LoadLightProgramResult.LocalOnly("Program disabled locally")
        } else {
            LoadLightProgramResult.Error("Program could not be found")
        }
    }

    suspend fun duplicateProgram(
        deviceId: Long,
        programId: String
    ): SaveLightProgramResult {
        return runCatching {
            val duplicate = store.duplicateProgram(
                deviceId = deviceId,
                programId = programId
            ) ?: return SaveLightProgramResult.Error("Program could not be found")

            SaveLightProgramResult.Success(duplicate)
        }.getOrElse { exception ->
            SaveLightProgramResult.Error(
                exception.message ?: "Program could not be duplicated"
            )
        }
    }

    suspend fun renameProgram(
        deviceId: Long,
        programId: String,
        newName: String
    ): SaveLightProgramResult {
        return runCatching {
            val renamed = store.renameProgram(
                deviceId = deviceId,
                programId = programId,
                newName = newName
            )

            if (renamed) {
                val program = store.getProgram(
                    deviceId = deviceId,
                    programId = programId
                ) ?: return SaveLightProgramResult.Error("Program could not be found")

                SaveLightProgramResult.Success(program)
            } else {
                SaveLightProgramResult.Error("Program could not be renamed")
            }
        }.getOrElse { exception ->
            SaveLightProgramResult.Error(
                exception.message ?: "Program could not be renamed"
            )
        }
    }

    suspend fun deleteProgram(
        deviceId: Long,
        programId: String
    ): Boolean {
        return store.deleteProgram(
            deviceId = deviceId,
            programId = programId
        )
    }

    private suspend fun loadSavedProgramToDevice(
        program: SavedLightProgram
    ): LoadLightProgramResult {
        val compileResult = LightProgramScheduleCompiler.compile(program)
        if (compileResult is LightProgramCompileResult.Invalid) {
            store.markSyncFailed(
                deviceId = program.deviceId,
                programId = program.id
            )
            return LoadLightProgramResult.Error(compileResult.message)
        }

        val schedule = (compileResult as LightProgramCompileResult.Success).schedule
        val apiProgram = LightProgramDevicePayloadMapper.toApiProgram(
            savedProgram = program,
            schedule = schedule
        )

        val runtimeSession = runtimeRepository.session(program.deviceId)
        return when (val result = runtimeSession.loadProgram(apiProgram)) {
            is ApiResult.Success -> {
                val synced = store.replaceAfterSuccessfulLoad(
                    loadedProgram = program,
                    compiledHash = schedule.hash
                )
                LoadLightProgramResult.Loaded(synced, schedule)
            }

            is ApiResult.Error -> {
                store.markSyncFailed(
                    deviceId = program.deviceId,
                    programId = program.id
                )
                LoadLightProgramResult.Error(result.error.message)
            }
        }
    }


    companion object {
        @Volatile
        private var INSTANCE: LightProgramsRepository? = null

        fun get(
            context: Context
        ): LightProgramsRepository {
            val appContext = context.applicationContext
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LightProgramsRepository(
                    store = LightProgramDataStoreManager.create(appContext),
                    runtimeRepository = LightRuntimeRepository.get(appContext)
                ).also { repository ->
                    INSTANCE = repository
                }
            }
        }
    }
}

sealed interface SaveLightProgramResult {
    data class Success(
        val program: SavedLightProgram
    ) : SaveLightProgramResult

    data class Error(
        val message: String
    ) : SaveLightProgramResult
}

sealed interface LoadLightProgramResult {
    data class Loaded(
        val program: SavedLightProgram,
        val schedule: CompiledLightProgramSchedule
    ) : LoadLightProgramResult

    data class LocalOnly(
        val message: String
    ) : LoadLightProgramResult

    data class Error(
        val message: String
    ) : LoadLightProgramResult
}

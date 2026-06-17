package com.aqua.aqualight.data.devices.light.programs.activation

import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.light.programs.LightProgramDataStoreManager
import com.aqua.aqualight.data.devices.light.programs.compiler.LightProgramDevicePointExpander
import com.aqua.aqualight.data.devices.light.programs.compiler.LightProgramDeviceTransitionStrategy
import com.aqua.aqualight.data.devices.light.programs.compiler.LightProgramPointExpansionOptions
import com.aqua.aqualight.data.devices.light.programs.device.LightProgramDevicePayloadMapper
import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram
import com.aqua.aqualight.data.devices.light.programs.validation.LightProgramDraftValidator
import com.aqua.aqualight.data.devices.light.programs.validation.LightProgramValidationResult
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeReadProfile
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeRepository

/**
 * Uploads a locally saved program to the controller as the single active LP schedule.
 *
 * Commercial rule:
 * - The app can store many programs per device.
 * - Current ESP32 firmware runs one concrete active schedule at a time.
 * - Activation compiles local intent into full channel LP payload, uploads it,
 *   refreshes runtime state, then marks the local program active and synced.
 */
class ActivateLightProgramUseCase(
    private val store: LightProgramDataStoreManager,
    private val runtimeRepository: LightRuntimeRepository,
    private val payloadMapper: LightProgramDevicePayloadMapper = LightProgramDevicePayloadMapper
) {


    suspend fun clearDeviceSchedule(
        deviceId: Long
    ) {
        require(deviceId > 0L) {
            "Light device id is missing"
        }

        val payload = payloadMapper.emptySchedulePayload()
        val session = runtimeRepository.session(deviceId)

        when (val clearResult = session.writeProgramSchedule(payload.request)) {
            is ApiResult.Success -> {
                session.refreshNow(
                    readProfile = LightRuntimeReadProfile.STANDARD
                )
            }

            is ApiResult.Error -> {
                error(clearResult.error.message)
            }
        }
    }

    suspend fun activate(
        deviceId: Long,
        programId: String
    ): LightProgramActivationResult {
        require(deviceId > 0L) {
            "Light device id is missing"
        }
        require(programId.isNotBlank()) {
            "Program id is missing"
        }

        val program = store.getProgram(
            deviceId = deviceId,
            programId = programId
        ) ?: error("Program not found.")

        validateProgram(program)

        val schedule = LightProgramDevicePointExpander.expand(
            draft = program.draft,
            options = LightProgramPointExpansionOptions(
                strategy = if (program.firmwareProfile.supportsNativeTransition) {
                    LightProgramDeviceTransitionStrategy.NATIVE_TRANSITION
                } else {
                    LightProgramDeviceTransitionStrategy.EXPANDED_POINTS
                }
            )
        )
        val payload = payloadMapper.toPayload(schedule)
        val session = runtimeRepository.session(deviceId)

        return when (val upload = session.writeProgramSchedule(payload.request)) {
            is ApiResult.Success -> {
                store.setProgramActive(
                    deviceId = deviceId,
                    programId = programId,
                    isActive = true
                )
                store.markProgramSynced(
                    deviceId = deviceId,
                    programId = programId,
                    checksum = payload.checksum
                )
                session.refreshNow(
                    readProfile = LightRuntimeReadProfile.STANDARD
                )

                val syncedProgram = store.getProgram(
                    deviceId = deviceId,
                    programId = programId
                ) ?: program.copy(
                    isActive = true,
                    compiledChecksum = payload.checksum
                )

                LightProgramActivationResult(
                    program = syncedProgram,
                    checksum = payload.checksum,
                    uploadedToDevice = true
                )
            }

            is ApiResult.Error -> {
                store.markProgramSyncFailed(
                    deviceId = deviceId,
                    programId = programId,
                    error = upload.error.message
                )
                error(upload.error.message)
            }
        }
    }

    private fun validateProgram(
        program: SavedLightProgram
    ) {
        when (val validation = LightProgramDraftValidator.validate(program.draft)) {
            LightProgramValidationResult.Valid -> Unit
            is LightProgramValidationResult.Invalid -> error(validation.message)
        }
    }
}

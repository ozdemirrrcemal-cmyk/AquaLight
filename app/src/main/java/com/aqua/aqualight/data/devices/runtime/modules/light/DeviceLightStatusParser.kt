package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONArray
import org.json.JSONObject

/** Exact parser for the complete firmware `light.status.get.data` contract. */
object DeviceLightStatusParser {

    fun parse(data: JSONObject): DeviceLightStatus {
        data.requireLightKeys(STATUS_KEYS, STATUS_LABEL)
        val channels = parseChannels(data.requireLightArray(FIELD_CHANNELS))
        val programs = parsePrograms(data.requireLightArray(FIELD_PROGRAMS))
        val channelCount = data.requireLightInt(FIELD_CHANNEL_COUNT, minimum = LIGHT_MIN_COUNT)
        val programCount = data.requireLightInt(FIELD_PROGRAM_COUNT, minimum = LIGHT_MIN_COUNT)
        require(channelCount == channels.size) {
            "$STATUS_LABEL channelCount differs from channels size."
        }
        require(programCount == programs.size) {
            "$STATUS_LABEL programCount differs from programs size."
        }

        val status = DeviceLightStatus(
            supported = data.requireLightBoolean(FIELD_SUPPORTED),
            manualSupported = data.requireLightBoolean(FIELD_MANUAL_SUPPORTED),
            programSupported = data.requireLightBoolean(FIELD_PROGRAM_SUPPORTED),
            presetsSupported = data.requireLightBoolean(FIELD_PRESETS_SUPPORTED),
            simulationSupported = data.requireLightBoolean(FIELD_SIMULATION_SUPPORTED),
            channelCount = channelCount,
            programCount = programCount,
            liveEditEnabled = data.requireLightBoolean(FIELD_LIVE_EDIT_ENABLED),
            channelEdit = data.requireLightInt(FIELD_CHANNEL_EDIT),
            powerLimitW = data.requireLightDouble(
                FIELD_POWER_LIMIT_W,
                minimum = LIGHT_NON_NEGATIVE_VALUE
            ),
            lockLoop = data.requireLightBoolean(FIELD_LOCK_LOOP),
            temperatureDownStepPercent = data.requireLightDouble(
                FIELD_TEMPERATURE_DOWN_STEP_PERCENT,
                minimum = LIGHT_PERCENT_MIN,
                maximum = LIGHT_PERCENT_MAX
            ),
            temperatureRecoveryMs = data.requireLightLong(
                FIELD_TEMPERATURE_RECOVERY_MS,
                minimum = LIGHT_NON_NEGATIVE_LONG
            ),
            lightCorrectionFactor = data.requireLightDouble(
                FIELD_LIGHT_CORRECTION_FACTOR,
                minimum = LIGHT_NON_NEGATIVE_VALUE
            ),
            uptimeMs = data.requireLightLong(
                FIELD_UPTIME_MS,
                minimum = LIGHT_NON_NEGATIVE_LONG
            ),
            channels = channels,
            programs = programs,
            runtime = parseRuntime(data.requireLightObject(FIELD_RUNTIME))
        )
        validateStatus(status)
        return status
    }

    private fun parseRuntime(data: JSONObject): DeviceLightRuntimeCapabilities {
        data.requireLightKeys(RUNTIME_KEYS, "$STATUS_LABEL.runtime")
        return DeviceLightRuntimeCapabilities(
            module = data.requireLightText(FIELD_MODULE),
            readOnly = data.requireLightBoolean(FIELD_READ_ONLY),
            supportsManualSet = data.requireLightBoolean(FIELD_SUPPORTS_MANUAL_SET),
            supportsChannelRegimeSet = data.requireLightBoolean(
                FIELD_SUPPORTS_CHANNEL_REGIME_SET
            ),
            supportsProgramApply = data.requireLightBoolean(FIELD_SUPPORTS_PROGRAM_APPLY),
            supportsProgramDelete = data.requireLightBoolean(FIELD_SUPPORTS_PROGRAM_DELETE),
            supportsLiveEdit = data.requireLightBoolean(FIELD_SUPPORTS_LIVE_EDIT),
            event = data.requireLightText(FIELD_EVENT)
        )
    }

    private fun parseChannels(data: JSONArray): List<DeviceLightChannelStatus> =
        List(data.length()) { index ->
            DeviceLightChannelParser.parseStatus(data.requireLightObject(index))
        }.also { channels ->
            require(channels.map(DeviceLightChannelStatus::key).toSet().size == channels.size) {
                "$STATUS_LABEL channels contain duplicate keys."
            }
            require(channels.map(DeviceLightChannelStatus::index).toSet().size == channels.size) {
                "$STATUS_LABEL channels contain duplicate indexes."
            }
        }

    private fun parsePrograms(data: JSONArray): List<DeviceLightProgramStatus> =
        List(data.length()) { listIndex ->
            DeviceLightProgramParser.parseStatus(
                data = data.requireLightObject(listIndex),
                listIndex = listIndex
            )
        }.also { programs ->
            require(programs.map(DeviceLightProgramStatus::index).toSet().size == programs.size) {
                "$STATUS_LABEL programs contain duplicate indexes."
            }
        }

    private fun validateStatus(status: DeviceLightStatus) {
        require(status.runtime.module == DeviceLightRuntimeContract.MODULE)
        require(!status.runtime.readOnly)
        require(status.runtime.supportsManualSet == status.manualSupported)
        require(status.runtime.supportsChannelRegimeSet)
        require(status.runtime.supportsProgramApply == status.programSupported)
        require(status.runtime.supportsProgramDelete == status.programSupported)
        require(status.runtime.supportsLiveEdit == status.liveEditEnabled)
        require(status.runtime.event == DeviceLightRuntimeContract.Event.STATUS_CHANGED)
    }

    private const val STATUS_LABEL = "light.status.get.data"
    private const val FIELD_SUPPORTED = "supported"
    private const val FIELD_MANUAL_SUPPORTED = "manualSupported"
    private const val FIELD_PROGRAM_SUPPORTED = "programSupported"
    private const val FIELD_PRESETS_SUPPORTED = "presetsSupported"
    private const val FIELD_SIMULATION_SUPPORTED = "simulationSupported"
    private const val FIELD_CHANNEL_COUNT = "channelCount"
    private const val FIELD_PROGRAM_COUNT = "programCount"
    private const val FIELD_LIVE_EDIT_ENABLED = "liveEditEnabled"
    private const val FIELD_CHANNEL_EDIT = "channelEdit"
    private const val FIELD_POWER_LIMIT_W = "powerLimitW"
    private const val FIELD_LOCK_LOOP = "lockLoop"
    private const val FIELD_TEMPERATURE_DOWN_STEP_PERCENT = "temperatureDownStepPercent"
    private const val FIELD_TEMPERATURE_RECOVERY_MS = "temperatureRecoveryMs"
    private const val FIELD_LIGHT_CORRECTION_FACTOR = "lightCorrectionFactor"
    private const val FIELD_UPTIME_MS = "uptimeMs"
    private const val FIELD_CHANNELS = "channels"
    private const val FIELD_PROGRAMS = "programs"
    private const val FIELD_RUNTIME = "runtime"
    private const val FIELD_MODULE = "module"
    private const val FIELD_READ_ONLY = "readOnly"
    private const val FIELD_SUPPORTS_MANUAL_SET = "supportsManualSet"
    private const val FIELD_SUPPORTS_CHANNEL_REGIME_SET = "supportsChannelRegimeSet"
    private const val FIELD_SUPPORTS_PROGRAM_APPLY = "supportsProgramApply"
    private const val FIELD_SUPPORTS_PROGRAM_DELETE = "supportsProgramDelete"
    private const val FIELD_SUPPORTS_LIVE_EDIT = "supportsLiveEdit"
    private const val FIELD_EVENT = "event"

    private val STATUS_KEYS = setOf(
        FIELD_SUPPORTED,
        FIELD_MANUAL_SUPPORTED,
        FIELD_PROGRAM_SUPPORTED,
        FIELD_PRESETS_SUPPORTED,
        FIELD_SIMULATION_SUPPORTED,
        FIELD_CHANNEL_COUNT,
        FIELD_PROGRAM_COUNT,
        FIELD_LIVE_EDIT_ENABLED,
        FIELD_CHANNEL_EDIT,
        FIELD_POWER_LIMIT_W,
        FIELD_LOCK_LOOP,
        FIELD_TEMPERATURE_DOWN_STEP_PERCENT,
        FIELD_TEMPERATURE_RECOVERY_MS,
        FIELD_LIGHT_CORRECTION_FACTOR,
        FIELD_UPTIME_MS,
        FIELD_CHANNELS,
        FIELD_PROGRAMS,
        FIELD_RUNTIME
    )
    private val RUNTIME_KEYS = setOf(
        FIELD_MODULE,
        FIELD_READ_ONLY,
        FIELD_SUPPORTS_MANUAL_SET,
        FIELD_SUPPORTS_CHANNEL_REGIME_SET,
        FIELD_SUPPORTS_PROGRAM_APPLY,
        FIELD_SUPPORTS_PROGRAM_DELETE,
        FIELD_SUPPORTS_LIVE_EDIT,
        FIELD_EVENT
    )
}

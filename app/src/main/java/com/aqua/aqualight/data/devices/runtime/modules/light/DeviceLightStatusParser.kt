package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONArray
import org.json.JSONObject

/** Exact parser for the complete firmware `light.status.get.data` contract. */
object DeviceLightStatusParser {

    fun parse(data: JSONObject): DeviceLightStatus {
        data.requireLightKeys(STATUS_KEYS, STATUS_LABEL)
        val channels = parseChannels(data.requireLightArray(FIELD_CHANNELS))
        val programs = parsePrograms(data.requireLightArray(FIELD_PROGRAMS))
        val channelCount = data.requireLightInt(FIELD_CHANNEL_COUNT, minimum = 0)
        val programCount = data.requireLightInt(FIELD_PROGRAM_COUNT, minimum = 0)
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
            powerLimitW = data.requireLightDouble(FIELD_POWER_LIMIT_W, minimum = 0.0),
            lockLoop = data.requireLightBoolean(FIELD_LOCK_LOOP),
            temperatureDownStepPercent = data.requireLightDouble(
                FIELD_TEMPERATURE_DOWN_STEP_PERCENT,
                minimum = 0.0,
                maximum = 100.0
            ),
            temperatureRecoveryMs = data.requireLightLong(
                FIELD_TEMPERATURE_RECOVERY_MS,
                minimum = 0L
            ),
            lightCorrectionFactor = data.requireLightDouble(
                FIELD_LIGHT_CORRECTION_FACTOR,
                minimum = 0.0
            ),
            uptimeMs = data.requireLightLong(FIELD_UPTIME_MS, minimum = 0L),
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

internal object DeviceLightChannelParser {
    fun parseStatus(data: JSONObject): DeviceLightChannelStatus =
        parse(data, STATUS_CHANNEL_KEYS, "light status channel")

    fun parseMutation(data: JSONObject): DeviceLightChannelMutationSnapshot {
        data.requireLightKeys(MUTATION_CHANNEL_KEYS, "light mutation channel")
        return DeviceLightChannelMutationSnapshot(
            listIndex = data.requireLightInt(FIELD_LIST_INDEX, minimum = 0),
            channel = parse(data, MUTATION_CHANNEL_KEYS, "light mutation channel")
        )
    }

    private fun parse(
        data: JSONObject,
        keys: Set<String>,
        label: String
    ): DeviceLightChannelStatus {
        data.requireLightKeys(keys, label)
        val status = DeviceLightChannelStatus(
            index = data.requireLightInt(FIELD_INDEX, minimum = 0),
            key = data.requireLightText(FIELD_KEY),
            name = data.requireLightText(FIELD_NAME),
            displayName = data.requireLightText(FIELD_DISPLAY_NAME),
            profileManaged = data.requireLightBoolean(FIELD_PROFILE_MANAGED),
            regime = requireNotNull(
                DeviceLightRegime.values().singleOrNull { regime ->
                    regime.wireValue == data.requireLightText(FIELD_REGIME)
                }
            ) { "Unknown firmware light regime." },
            channelKind = data.requireLightText(FIELD_CHANNEL_KIND),
            gpio = data.requireLightInt(FIELD_GPIO),
            ledcChannel = data.requireLightInt(FIELD_LEDC_CHANNEL),
            group = data.requireLightInt(FIELD_GROUP),
            valueNow = data.requireLightDouble(FIELD_VALUE_NOW, 0.0, 1.0),
            valueAuto = data.requireLightDouble(FIELD_VALUE_AUTO, 0.0, 1.0),
            valueManual = data.requireLightDouble(FIELD_VALUE_MANUAL, -1.0, 1.0),
            manualTimeoutMs = data.requireLightLong(FIELD_MANUAL_TIMEOUT_MS, minimum = 0L),
            percentNow = data.requireLightDouble(FIELD_PERCENT_NOW, 0.0, 100.0),
            percentAuto = data.requireLightDouble(FIELD_PERCENT_AUTO, 0.0, 100.0),
            percentManual = data.requireLightDouble(FIELD_PERCENT_MANUAL, -100.0, 100.0),
            invert = data.requireLightBoolean(FIELD_INVERT),
            pwmResolutionBits = data.requireLightInt(FIELD_PWM_RESOLUTION_BITS, minimum = 0),
            pwmFrequencyHz = data.requireLightInt(FIELD_PWM_FREQUENCY_HZ, minimum = 0),
            color = data.requireLightInt(FIELD_COLOR),
            lumen = data.requireLightDouble(FIELD_LUMEN, minimum = 0.0),
            lux = data.requireLightDouble(FIELD_LUX, minimum = 0.0),
            watt = data.requireLightDouble(FIELD_WATT, minimum = 0.0),
            editable = parseEditable(data.requireLightObject(FIELD_EDITABLE))
        )
        validateChannel(status)
        return status
    }

    private fun parseEditable(data: JSONObject): DeviceLightChannelEditable {
        data.requireLightKeys(EDITABLE_KEYS, "light channel editable")
        return DeviceLightChannelEditable(
            hardware = data.requireLightBoolean(FIELD_HARDWARE),
            displayName = data.requireLightBoolean(FIELD_EDITABLE_DISPLAY_NAME),
            color = data.requireLightBoolean(FIELD_EDITABLE_COLOR),
            hardwareCalibration = data.requireLightBoolean(FIELD_HARDWARE_CALIBRATION)
        )
    }

    private fun validateChannel(status: DeviceLightChannelStatus) {
        require(status.channelKind in CHANNEL_KINDS)
        require(closeEnough(status.percentNow, status.valueNow * 100.0))
        require(closeEnough(status.percentAuto, status.valueAuto * 100.0))
        require(closeEnough(status.percentManual, status.valueManual * 100.0))
    }

    private const val FIELD_LIST_INDEX = "listIndex"
    private const val FIELD_INDEX = "index"
    private const val FIELD_KEY = "key"
    private const val FIELD_NAME = "name"
    private const val FIELD_DISPLAY_NAME = "displayName"
    private const val FIELD_PROFILE_MANAGED = "profileManaged"
    private const val FIELD_REGIME = "regime"
    private const val FIELD_CHANNEL_KIND = "channelKind"
    private const val FIELD_GPIO = "gpio"
    private const val FIELD_LEDC_CHANNEL = "ledcChannel"
    private const val FIELD_GROUP = "group"
    private const val FIELD_VALUE_NOW = "valueNow"
    private const val FIELD_VALUE_AUTO = "valueAuto"
    private const val FIELD_VALUE_MANUAL = "valueManual"
    private const val FIELD_MANUAL_TIMEOUT_MS = "manualTimeoutMs"
    private const val FIELD_PERCENT_NOW = "percentNow"
    private const val FIELD_PERCENT_AUTO = "percentAuto"
    private const val FIELD_PERCENT_MANUAL = "percentManual"
    private const val FIELD_INVERT = "invert"
    private const val FIELD_PWM_RESOLUTION_BITS = "pwmResolutionBits"
    private const val FIELD_PWM_FREQUENCY_HZ = "pwmFrequencyHz"
    private const val FIELD_COLOR = "color"
    private const val FIELD_LUMEN = "lumen"
    private const val FIELD_LUX = "lux"
    private const val FIELD_WATT = "watt"
    private const val FIELD_EDITABLE = "editable"
    private const val FIELD_HARDWARE = "hardware"
    private const val FIELD_EDITABLE_DISPLAY_NAME = "displayName"
    private const val FIELD_EDITABLE_COLOR = "color"
    private const val FIELD_HARDWARE_CALIBRATION = "hardwareCalibration"

    private val STATUS_CHANNEL_KEYS = setOf(
        FIELD_INDEX, FIELD_KEY, FIELD_NAME, FIELD_DISPLAY_NAME, FIELD_PROFILE_MANAGED,
        FIELD_REGIME, FIELD_CHANNEL_KIND, FIELD_GPIO, FIELD_LEDC_CHANNEL, FIELD_GROUP,
        FIELD_VALUE_NOW, FIELD_VALUE_AUTO, FIELD_VALUE_MANUAL, FIELD_MANUAL_TIMEOUT_MS,
        FIELD_PERCENT_NOW, FIELD_PERCENT_AUTO, FIELD_PERCENT_MANUAL, FIELD_INVERT,
        FIELD_PWM_RESOLUTION_BITS, FIELD_PWM_FREQUENCY_HZ, FIELD_COLOR, FIELD_LUMEN,
        FIELD_LUX, FIELD_WATT, FIELD_EDITABLE
    )
    private val MUTATION_CHANNEL_KEYS = STATUS_CHANNEL_KEYS + FIELD_LIST_INDEX
    private val EDITABLE_KEYS = setOf(
        FIELD_HARDWARE,
        FIELD_EDITABLE_DISPLAY_NAME,
        FIELD_EDITABLE_COLOR,
        FIELD_HARDWARE_CALIBRATION
    )
    private val CHANNEL_KINDS = setOf("gpio", "digital", "none")
}

internal object DeviceLightProgramParser {
    fun parseStatus(data: JSONObject, listIndex: Int): DeviceLightProgramStatus =
        parse(
            data = data,
            expectedKeys = STATUS_PROGRAM_KEYS,
            listIndex = listIndex,
            pointsHaveIndexes = false,
            label = "light status program"
        )

    fun parseMutation(data: JSONObject): DeviceLightProgramStatus =
        parse(
            data = data,
            expectedKeys = MUTATION_PROGRAM_KEYS,
            listIndex = data.requireLightInt(FIELD_LIST_INDEX, minimum = 0),
            pointsHaveIndexes = true,
            label = "light mutation program"
        )

    private fun parse(
        data: JSONObject,
        expectedKeys: Set<String>,
        listIndex: Int,
        pointsHaveIndexes: Boolean,
        label: String
    ): DeviceLightProgramStatus {
        data.requireLightKeys(expectedKeys, label)
        val points = parsePoints(
            data.requireLightArray(FIELD_POINTS),
            pointsHaveIndexes
        )
        val pointCount = data.requireLightInt(FIELD_POINT_COUNT, minimum = 0)
        require(pointCount == points.size) { "$label pointCount differs from points size." }
        return DeviceLightProgramStatus(
            listIndex = listIndex,
            index = data.requireLightInt(FIELD_INDEX, minimum = 0),
            channelKey = data.requireLightText(FIELD_CHANNEL_KEY),
            bound = data.requireLightBoolean(FIELD_BOUND),
            pointCount = pointCount,
            points = points
        )
    }

    private fun parsePoints(data: JSONArray, haveIndexes: Boolean): List<DeviceLightProgramPointStatus> =
        List(data.length()) { position ->
            parsePoint(data.requireLightObject(position), position, haveIndexes)
        }

    private fun parsePoint(
        data: JSONObject,
        position: Int,
        haveIndex: Boolean
    ): DeviceLightProgramPointStatus {
        data.requireLightKeys(
            if (haveIndex) MUTATION_POINT_KEYS else STATUS_POINT_KEYS,
            "light program point"
        )
        val point = DeviceLightProgramPointStatus(
            index = if (haveIndex) data.requireLightInt(FIELD_INDEX, minimum = 0) else position,
            timeMs = data.requireLightLong(
                FIELD_TIME_MS,
                minimum = 0L,
                maximum = DeviceLightRuntimeContract.Limit.MILLIS_IN_DAY - 1L
            ),
            time = data.requireLightText(FIELD_TIME),
            value = data.requireLightDouble(FIELD_VALUE, 0.0, 1.0),
            percent = data.requireLightDouble(FIELD_PERCENT, 0.0, 100.0)
        )
        require(closeEnough(point.percent, point.value * 100.0))
        if (haveIndex) require(point.index == position)
        return point
    }

    private const val FIELD_LIST_INDEX = "listIndex"
    private const val FIELD_INDEX = "index"
    private const val FIELD_CHANNEL_KEY = "channelKey"
    private const val FIELD_BOUND = "bound"
    private const val FIELD_POINT_COUNT = "pointCount"
    private const val FIELD_POINTS = "points"
    private const val FIELD_TIME_MS = "timeMs"
    private const val FIELD_TIME = "time"
    private const val FIELD_VALUE = "value"
    private const val FIELD_PERCENT = "percent"

    private val STATUS_PROGRAM_KEYS = setOf(
        FIELD_INDEX, FIELD_CHANNEL_KEY, FIELD_BOUND, FIELD_POINT_COUNT, FIELD_POINTS
    )
    private val MUTATION_PROGRAM_KEYS = STATUS_PROGRAM_KEYS + FIELD_LIST_INDEX
    private val STATUS_POINT_KEYS = setOf(FIELD_TIME_MS, FIELD_TIME, FIELD_VALUE, FIELD_PERCENT)
    private val MUTATION_POINT_KEYS = STATUS_POINT_KEYS + FIELD_INDEX
}

internal fun JSONObject.requireLightKeys(expected: Set<String>, label: String) {
    val actual = keys().asSequence().toSet()
    require(actual == expected) {
        "$label keys differ from the firmware contract; expected=$expected actual=$actual"
    }
}

internal fun JSONObject.requireLightObject(key: String): JSONObject =
    get(key) as? JSONObject ?: error("$key must be a JSON object.")

internal fun JSONObject.requireLightArray(key: String): JSONArray =
    get(key) as? JSONArray ?: error("$key must be a JSON array.")

internal fun JSONArray.requireLightObject(index: Int): JSONObject =
    get(index) as? JSONObject ?: error("[$index] must be a JSON object.")

internal fun JSONObject.requireLightText(key: String): String {
    val value = get(key) as? String ?: error("$key must be a string.")
    require(value.isNotEmpty()) { "$key must not be empty." }
    require(!value.first().isWhitespace() && !value.last().isWhitespace()) {
        "$key must not contain surrounding whitespace."
    }
    require(value.none(Char::isISOControl)) { "$key must not contain control characters." }
    return value
}

internal fun JSONObject.requireLightBoolean(key: String): Boolean =
    get(key) as? Boolean ?: error("$key must be a boolean.")

internal fun JSONObject.requireLightInt(
    key: String,
    minimum: Int = Int.MIN_VALUE,
    maximum: Int = Int.MAX_VALUE
): Int = requireLightIntegral(key).also { value ->
    require(value in minimum.toLong()..maximum.toLong()) { "$key is outside its supported range." }
}.toInt()

internal fun JSONObject.requireLightLong(
    key: String,
    minimum: Long = Long.MIN_VALUE,
    maximum: Long = Long.MAX_VALUE
): Long = requireLightIntegral(key).also { value ->
    require(value in minimum..maximum) { "$key is outside its supported range." }
}

internal fun JSONObject.requireLightDouble(
    key: String,
    minimum: Double = -Double.MAX_VALUE,
    maximum: Double = Double.MAX_VALUE
): Double {
    val value = get(key) as? Number ?: error("$key must be numeric.")
    return value.toDouble().also { number ->
        require(number.isFinite()) { "$key must be finite." }
        require(number in minimum..maximum) { "$key is outside its supported range." }
    }
}

private fun JSONObject.requireLightIntegral(key: String): Long {
    val value = get(key) as? Number ?: error("$key must be an integer.")
    val asDouble = value.toDouble()
    val asLong = value.toLong()
    require(asDouble.isFinite() && asDouble == asLong.toDouble()) {
        "$key must be an integer."
    }
    return asLong
}

private fun closeEnough(left: Double, right: Double): Boolean =
    kotlin.math.abs(left - right) <= VALUE_TOLERANCE

private const val VALUE_TOLERANCE = 0.001

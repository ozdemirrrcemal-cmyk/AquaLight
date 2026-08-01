package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONArray
import org.json.JSONObject

internal object DeviceLightChannelParser {
    fun parseStatus(data: JSONObject): DeviceLightChannelStatus =
        parse(data, STATUS_CHANNEL_KEYS, "light status channel")

    fun parseMutation(data: JSONObject): DeviceLightChannelMutationSnapshot {
        data.requireLightKeys(MUTATION_CHANNEL_KEYS, "light mutation channel")
        return DeviceLightChannelMutationSnapshot(
            listIndex = data.requireLightInt(FIELD_LIST_INDEX, minimum = LIGHT_MIN_COUNT),
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
            index = data.requireLightInt(FIELD_INDEX, minimum = LIGHT_MIN_COUNT),
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
            valueNow = data.requireLightDouble(
                FIELD_VALUE_NOW,
                LIGHT_NORMALIZED_MIN,
                LIGHT_NORMALIZED_MAX
            ),
            valueAuto = data.requireLightDouble(
                FIELD_VALUE_AUTO,
                LIGHT_NORMALIZED_MIN,
                LIGHT_NORMALIZED_MAX
            ),
            valueManual = data.requireLightDouble(
                FIELD_VALUE_MANUAL,
                LIGHT_MANUAL_INACTIVE_VALUE,
                LIGHT_NORMALIZED_MAX
            ),
            manualTimeoutMs = data.requireLightLong(
                FIELD_MANUAL_TIMEOUT_MS,
                minimum = LIGHT_NON_NEGATIVE_LONG
            ),
            percentNow = data.requireLightDouble(
                FIELD_PERCENT_NOW,
                LIGHT_PERCENT_MIN,
                LIGHT_PERCENT_MAX
            ),
            percentAuto = data.requireLightDouble(
                FIELD_PERCENT_AUTO,
                LIGHT_PERCENT_MIN,
                LIGHT_PERCENT_MAX
            ),
            percentManual = data.requireLightDouble(
                FIELD_PERCENT_MANUAL,
                LIGHT_MANUAL_INACTIVE_PERCENT,
                LIGHT_PERCENT_MAX
            ),
            invert = data.requireLightBoolean(FIELD_INVERT),
            pwmResolutionBits = data.requireLightInt(
                FIELD_PWM_RESOLUTION_BITS,
                minimum = LIGHT_MIN_COUNT
            ),
            pwmFrequencyHz = data.requireLightInt(
                FIELD_PWM_FREQUENCY_HZ,
                minimum = LIGHT_MIN_COUNT
            ),
            color = data.requireLightInt(FIELD_COLOR),
            lumen = data.requireLightDouble(FIELD_LUMEN, minimum = LIGHT_NON_NEGATIVE_VALUE),
            lux = data.requireLightDouble(FIELD_LUX, minimum = LIGHT_NON_NEGATIVE_VALUE),
            watt = data.requireLightDouble(FIELD_WATT, minimum = LIGHT_NON_NEGATIVE_VALUE),
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
        require(lightValuesEquivalent(status.percentNow, status.valueNow * LIGHT_PERCENT_SCALE))
        require(lightValuesEquivalent(status.percentAuto, status.valueAuto * LIGHT_PERCENT_SCALE))
        require(
            lightValuesEquivalent(
                status.percentManual,
                status.valueManual * LIGHT_PERCENT_SCALE
            )
        )
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
            listIndex = data.requireLightInt(FIELD_LIST_INDEX, minimum = LIGHT_MIN_COUNT),
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
        val points = parsePoints(data.requireLightArray(FIELD_POINTS), pointsHaveIndexes)
        val pointCount = data.requireLightInt(FIELD_POINT_COUNT, minimum = LIGHT_MIN_COUNT)
        require(pointCount == points.size) { "$label pointCount differs from points size." }
        return DeviceLightProgramStatus(
            listIndex = listIndex,
            index = data.requireLightInt(FIELD_INDEX, minimum = LIGHT_MIN_COUNT),
            channelKey = data.requireLightText(FIELD_CHANNEL_KEY),
            bound = data.requireLightBoolean(FIELD_BOUND),
            pointCount = pointCount,
            points = points
        )
    }

    private fun parsePoints(
        data: JSONArray,
        haveIndexes: Boolean
    ): List<DeviceLightProgramPointStatus> = List(data.length()) { position ->
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
            index = if (haveIndex) {
                data.requireLightInt(FIELD_INDEX, minimum = LIGHT_MIN_COUNT)
            } else {
                position
            },
            timeMs = data.requireLightLong(
                FIELD_TIME_MS,
                minimum = LIGHT_NON_NEGATIVE_LONG,
                maximum = LIGHT_LAST_DAY_MILLISECOND
            ),
            time = data.requireLightText(FIELD_TIME),
            value = data.requireLightDouble(
                FIELD_VALUE,
                LIGHT_NORMALIZED_MIN,
                LIGHT_NORMALIZED_MAX
            ),
            percent = data.requireLightDouble(
                FIELD_PERCENT,
                LIGHT_PERCENT_MIN,
                LIGHT_PERCENT_MAX
            )
        )
        require(lightValuesEquivalent(point.percent, point.value * LIGHT_PERCENT_SCALE))
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

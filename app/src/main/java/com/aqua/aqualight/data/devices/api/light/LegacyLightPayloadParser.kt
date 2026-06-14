package com.aqua.aqualight.data.devices.api.light

import com.aqua.aqualight.data.devices.api.model.ApiErrorCode
import com.aqua.aqualight.data.devices.api.model.ApiResult
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

class LegacyLightPayloadParser {

    fun parseDeviceState(
        payload: String
    ): ApiResult<LightDeviceState> {
        if (payload.isBlank()) {
            return ApiResult.failure(
                code = ApiErrorCode.INVALID_RESPONSE,
                message = "Empty legacy light payload"
            )
        }

        return runCatching {
            val root = JSONObject(payload)
            val ledChannels = parsePwmChannels(
                root = root,
                rootKey = KEY_LED_PWM,
                fallbackRole = null
            )
            val fanChannels = parsePwmChannels(
                root = root,
                rootKey = KEY_FAN_PWM,
                fallbackRole = LightChannelRole.FAN
            )
            val temperatures = parseTemperatureSensors(root)
            val coolingControllers = parseCoolingControllers(
                root = root,
                sensors = temperatures,
                fanChannels = fanChannels
            )
            val scheduleChannels = parseScheduleChannels(
                root = root,
                ledChannels = ledChannels
            )
            val time = parseTimeState(root)
            val thermal = parseThermalProtection(
                root = root,
                sensors = temperatures
            )
            val channels = buildChannelValues(ledChannels)
            val maxWatt = LightApiMath.calculateMaxWatt(
                configuredMaxWatt = root.optJSONObject(KEY_LED_PWM).optNullableDouble("WMax"),
                channels = ledChannels
            )
            val currentWatt = LightApiMath.calculateCurrentWatt(ledChannels)
            val fanOutputPercent = fanChannels.mapNotNull { channel ->
                channel.currentPercent
            }.maxOrNull()
            val nextEvent = buildNextEvent(
                scheduleChannels = scheduleChannels,
                currentMinuteOfDay = time.currentMinuteOfDay
            )
            val temperature = temperatures.mapNotNull { sensor ->
                sensor.temperatureCelsius
            }.maxOrNull()
            val mode = resolveMode(
                channels = channels,
                ledChannels = ledChannels,
                scheduleChannels = scheduleChannels
            )

            val status = LightStatus(
                mode = mode,
                isPowerOn = !channels.isOff,
                outputPercent = channels.maxPercent,
                redPercent = channels.red,
                greenPercent = channels.green,
                bluePercent = channels.blue,
                whitePercent = channels.white,
                deviceTimeText = time.currentText,
                temperatureCelsius = temperature,
                currentWatt = currentWatt,
                maxWatt = maxWatt,
                powerLoadPercent = LightApiMath.powerLoadPercent(
                    currentWatt = currentWatt,
                    maxWatt = maxWatt
                ),
                thermalReductionPercent = thermal.reductionPercent,
                fanOutputPercent = fanOutputPercent
            )

            ApiResult.success(
                LightDeviceState(
                    status = status,
                    channels = channels,
                    ledChannels = ledChannels,
                    fanChannels = fanChannels,
                    scheduleChannels = scheduleChannels,
                    temperatureSensors = temperatures,
                    coolingControllers = coolingControllers,
                    time = time,
                    thermalProtection = thermal,
                    nextEvent = nextEvent,
                    legacyIp = root.optString("IP"),
                    legacyRawPayload = payload
                )
            )
        }.getOrElse { exception ->
            ApiResult.failure(
                code = ApiErrorCode.PARSE,
                message = exception.message ?: "Cannot parse legacy light payload",
                cause = exception
            )
        }
    }

    private fun parsePwmChannels(
        root: JSONObject,
        rootKey: String,
        fallbackRole: LightChannelRole?
    ): List<LightPwmChannelState> {
        val listObject = root.optJSONObject(rootKey) ?: return emptyList()
        val data = listObject.optJSONObject("Data") ?: return emptyList()
        val pca9685Status = listObject.optString("PCA9685")
        val frequency = listObject.optNullableInt("Frequency")

        return data.sortedObjects().map { (index, item) ->
            val currentValue = item.optNullableDouble("VNow")
                ?.takeIf { value -> value >= 0.0 }
            val role = fallbackRole ?: inferLightChannelRole(
                index = index,
                name = item.optString("Name"),
                color = item.optNullableLong("Color")
            )

            LightPwmChannelState(
                index = index,
                name = item.optString("Name"),
                role = role,
                regime = item.optString("Regime").toPwmRegime(),
                gpioPwm = item.optString("GPIO_PWM"),
                color = item.optNullableLong("Color"),
                lumen = item.optNullableDouble("Lm"),
                lux = item.optNullableDouble("Lux"),
                maxWatt = item.optNullableDouble("W"),
                group = item.optNullableInt("Gr"),
                currentValue = currentValue,
                currentPercent = LightApiMath.valueToPercent(currentValue),
                minValue = item.optNullableDouble("VMin"),
                maxValue = item.optNullableDouble("VMax"),
                isInverted = item.optBooleanFlexible("Invert"),
                pca9685Status = pca9685Status,
                frequency = frequency
            )
        }
    }

    private fun parseTemperatureSensors(
        root: JSONObject
    ): List<LightTemperatureSensorState> {
        val data = root.optJSONObject(KEY_TEMPERATURE)
            ?.optJSONObject("Data")
            ?: return emptyList()

        return data.sortedObjects().map { (index, item) ->
            LightTemperatureSensorState(
                index = index,
                name = item.optString("Name"),
                color = item.optNullableLong("Color"),
                temperatureCelsius = item.optNullableDouble("Temperature"),
                lightLimitCelsius = item.optNullableDouble("TempLightErr"),
                historyRaw = item.optString("LT")
            )
        }
    }

    private fun parseCoolingControllers(
        root: JSONObject,
        sensors: List<LightTemperatureSensorState>,
        fanChannels: List<LightPwmChannelState>
    ): List<LightCoolingControllerState> {
        val data = root.optJSONObject(KEY_COOLING)
            ?.optJSONObject("Data")
            ?: return emptyList()

        return data.sortedObjects().map { (index, item) ->
            val sensorIndexes = item.optJSONArray("LbT")
                .toBooleanIndexList()
            val currentTemperature = sensorIndexes.mapNotNull { sensorIndex ->
                sensors.firstOrNull { sensor -> sensor.index == sensorIndex }
                    ?.temperatureCelsius
            }.maxOrNull()
            val gpioPwm = item.optString("GPIO_PWM")
            val linkedFan = fanChannels.firstOrNull { channel ->
                channel.gpioPwm == gpioPwm && gpioPwm.isNotBlank() && gpioPwm != "-"
            }

            LightCoolingControllerState(
                index = index,
                enabled = item.optBooleanFlexible("Enabled"),
                name = item.optString("Name"),
                gpioPwm = gpioPwm,
                sensorIndexes = sensorIndexes,
                startCelsius = item.optNullableDouble("TMin"),
                fullSpeedCelsius = item.optNullableDouble("TMax"),
                currentTemperatureCelsius = currentTemperature,
                linkedFanChannel = linkedFan
            )
        }
    }

    private fun parseScheduleChannels(
        root: JSONObject,
        ledChannels: List<LightPwmChannelState>
    ): List<LightScheduleChannelState> {
        val data = root.optJSONObject(KEY_LIGHT_PROGRAM)
            ?.optJSONObject("Data")
            ?: return emptyList()

        return data.sortedObjects().map { (index, item) ->
            val gpioPwm = item.optString("GPIO_PWM")
            val linkedChannel = ledChannels.firstOrNull { channel ->
                channel.gpioPwm == gpioPwm && gpioPwm.isNotBlank() && gpioPwm != "-"
            }
            val role = linkedChannel?.role ?: inferLightChannelRole(
                index = index,
                name = linkedChannel?.name.orEmpty(),
                color = linkedChannel?.color
            )

            LightScheduleChannelState(
                index = index,
                role = role,
                gpioPwm = gpioPwm,
                points = item.optJSONArray("LP").toSchedulePoints()
            )
        }
    }

    private fun parseTimeState(
        root: JSONObject
    ): LightTimeState {
        val time = root.optJSONObject(KEY_TIME)
        val currentText = time.optString("TimeCurrent")

        return LightTimeState(
            currentText = currentText,
            currentMinuteOfDay = parseMinuteOfDay(currentText),
            uptimeText = time.optString("Uptime"),
            timeZoneMinutes = time.optNullableInt("TimeZone"),
            autoSyncNtpEnabled = time.optNullableBoolean("EnabledAutoSyncNTP"),
            autoSyncGadgetEnabled = time.optNullableBoolean("EnabledAutoSyncGadget"),
            ds1307Status = time.optString("DS1307"),
            pcf8563Status = time.optString("PCF8563")
        )
    }

    private fun parseThermalProtection(
        root: JSONObject,
        sensors: List<LightTemperatureSensorState>
    ): LightThermalProtectionState {
        val light = root.optJSONObject(KEY_LIGHT_PROGRAM)
        val reductionFactor = light.optNullableDouble("kLightErr")
        val lightDownErrPercent = light.optNullableInt("LightDownErr")
        val recoveryIntervalSeconds = light.optNullableInt("TimeDownErr")
        val limit = sensors.mapNotNull { sensor ->
            sensor.lightLimitCelsius
        }.minOrNull()

        return LightThermalProtectionState(
            reductionFactor = reductionFactor,
            reductionPercent = reductionFactor?.let { factor ->
                (factor * 100.0).roundToInt().coerceIn(0, 100)
            },
            lightDownErrPercent = lightDownErrPercent,
            recoveryIntervalSeconds = recoveryIntervalSeconds,
            limitCelsius = limit
        )
    }

    private fun buildChannelValues(
        ledChannels: List<LightPwmChannelState>
    ): LightChannelValues {
        fun valueFor(role: LightChannelRole): Int {
            return ledChannels.firstOrNull { channel ->
                channel.role == role
            }?.currentPercent ?: 0
        }

        return LightChannelValues(
            red = valueFor(LightChannelRole.RED),
            green = valueFor(LightChannelRole.GREEN),
            blue = valueFor(LightChannelRole.BLUE),
            white = valueFor(LightChannelRole.WHITE)
        ).normalized()
    }

    private fun resolveMode(
        channels: LightChannelValues,
        ledChannels: List<LightPwmChannelState>,
        scheduleChannels: List<LightScheduleChannelState>
    ): LightMode {
        if (channels.isOff) {
            return LightMode.IDLE
        }

        val regimes = ledChannels.map { channel -> channel.regime }.toSet()
        return when {
            regimes.contains(LightPwmRegime.ON) || regimes.contains(LightPwmRegime.OFF) -> LightMode.MANUAL
            regimes.contains(LightPwmRegime.AUTO) && scheduleChannels.any { it.points.isNotEmpty() } -> LightMode.AUTO
            regimes.contains(LightPwmRegime.AUTO) -> LightMode.AUTO
            else -> LightMode.UNKNOWN
        }
    }

    private fun buildNextEvent(
        scheduleChannels: List<LightScheduleChannelState>,
        currentMinuteOfDay: Int?
    ): LightNextEvent? {
        if (currentMinuteOfDay == null) {
            return null
        }

        val points = scheduleChannels.flatMap { channel ->
            channel.points.map { point -> channel to point }
        }

        if (points.isEmpty()) {
            return null
        }

        val next = points
            .sortedWith(
                compareBy<Pair<LightScheduleChannelState, LightSchedulePoint>> { (_, point) ->
                    val delta = point.minuteOfDay - currentMinuteOfDay
                    if (delta > 0) delta else delta + MINUTES_PER_DAY
                }.thenBy { (_, point) -> point.minuteOfDay }
            )
            .firstOrNull()
            ?: return null

        val roleText = next.first.role.displayName
        val point = next.second

        return LightNextEvent(
            minuteOfDay = point.minuteOfDay,
            timeText = point.timeText,
            label = if (roleText.isBlank()) {
                point.timeText
            } else {
                "${point.timeText} $roleText"
            }
        )
    }

    private fun inferLightChannelRole(
        index: Int,
        name: String,
        color: Long?
    ): LightChannelRole {
        val normalizedName = name.trim().lowercase()
        when {
            normalizedName.contains("red") || normalizedName == "r" -> return LightChannelRole.RED
            normalizedName.contains("green") || normalizedName == "g" -> return LightChannelRole.GREEN
            normalizedName.contains("blue") || normalizedName == "b" -> return LightChannelRole.BLUE
            normalizedName.contains("white") || normalizedName == "w" -> return LightChannelRole.WHITE
        }

        val fromColor = color?.let(::roleFromColor)
        if (fromColor != null) {
            return fromColor
        }

        return when (index) {
            0 -> LightChannelRole.WHITE
            1 -> LightChannelRole.RED
            2 -> LightChannelRole.GREEN
            3 -> LightChannelRole.BLUE
            else -> LightChannelRole.UNKNOWN
        }
    }

    private fun roleFromColor(
        color: Long
    ): LightChannelRole? {
        val red = ((color shr 16) and 0xFF).toInt()
        val green = ((color shr 8) and 0xFF).toInt()
        val blue = (color and 0xFF).toInt()

        return when {
            red > 220 && green > 220 && blue > 220 -> LightChannelRole.WHITE
            red > green && red > blue -> LightChannelRole.RED
            green > red && green > blue -> LightChannelRole.GREEN
            blue > red && blue > green -> LightChannelRole.BLUE
            else -> null
        }
    }

    private fun parseMinuteOfDay(
        value: String
    ): Int? {
        val match = TIME_PATTERN.find(value.trim()) ?: return null
        val hour = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null

        if (hour !in 0..23 || minute !in 0..59) {
            return null
        }

        return hour * 60 + minute
    }

    private fun JSONObject?.optString(
        name: String
    ): String {
        if (this == null || isNull(name)) {
            return ""
        }

        return optString(name, "")
    }

    private fun JSONObject?.optNullableDouble(
        name: String
    ): Double? {
        if (this == null || isNull(name)) {
            return null
        }

        return runCatching { getDouble(name) }.getOrNull()
    }

    private fun JSONObject?.optNullableLong(
        name: String
    ): Long? {
        if (this == null || isNull(name)) {
            return null
        }

        return runCatching { getLong(name) }.getOrNull()
    }

    private fun JSONObject?.optNullableInt(
        name: String
    ): Int? {
        if (this == null || isNull(name)) {
            return null
        }

        return runCatching { getInt(name) }.getOrNull()
    }

    private fun JSONObject?.optNullableBoolean(
        name: String
    ): Boolean? {
        if (this == null || isNull(name)) {
            return null
        }

        return optBooleanFlexible(name)
    }

    private fun JSONObject.optBooleanFlexible(
        name: String
    ): Boolean {
        if (isNull(name)) {
            return false
        }

        val value = opt(name) ?: return false
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.equals("true", ignoreCase = true) || value == "1"
            else -> false
        }
    }

    private fun JSONObject.sortedObjects(): List<Pair<Int, JSONObject>> {
        val keys = keys().asSequence().toList()
        return keys.mapNotNull { key ->
            val index = key.toIntOrNull() ?: return@mapNotNull null
            val value = optJSONObject(key) ?: return@mapNotNull null
            index to value
        }.sortedBy { (index, _) -> index }
    }

    private fun JSONArray?.toBooleanIndexList(): List<Int> {
        if (this == null) {
            return emptyList()
        }

        return buildList {
            for (index in 0 until length()) {
                val value = opt(index)
                val enabled = when (value) {
                    is Boolean -> value
                    is Number -> value.toInt() != 0
                    is String -> value.equals("true", ignoreCase = true) || value == "1"
                    else -> false
                }

                if (enabled) {
                    add(index)
                }
            }
        }
    }

    private fun JSONArray?.toSchedulePoints(): List<LightSchedulePoint> {
        if (this == null) {
            return emptyList()
        }

        return buildList {
            for (index in 0 until length()) {
                val point = optJSONArray(index) ?: continue
                val timeText = point.optString(0, "")
                val value = point.optDouble(1, 0.0)
                val minute = parseMinuteOfDay(timeText) ?: continue
                add(
                    LightSchedulePoint(
                        minuteOfDay = minute,
                        timeText = timeText,
                        value = value,
                        percent = LightApiMath.valueToPercent(value) ?: 0
                    )
                )
            }
        }.sortedBy { point -> point.minuteOfDay }
    }

    private val LightChannelRole.displayName: String
        get() = when (this) {
            LightChannelRole.RED -> "Red"
            LightChannelRole.GREEN -> "Green"
            LightChannelRole.BLUE -> "Blue"
            LightChannelRole.WHITE -> "White"
            LightChannelRole.FAN -> "Fan"
            LightChannelRole.UNKNOWN -> ""
        }

    private fun String.toPwmRegime(): LightPwmRegime {
        return when (trim().lowercase()) {
            "auto" -> LightPwmRegime.AUTO
            "on" -> LightPwmRegime.ON
            "off" -> LightPwmRegime.OFF
            else -> LightPwmRegime.UNKNOWN
        }
    }

    private companion object {
        const val KEY_TIME = "Time"
        const val KEY_TEMPERATURE = "LTemperature"
        const val KEY_LED_PWM = "LPWMChanelLED"
        const val KEY_FAN_PWM = "LPWMChanelFan"
        const val KEY_LIGHT_PROGRAM = "LLight"
        const val KEY_COOLING = "LCool"
        const val MINUTES_PER_DAY = 24 * 60
        val TIME_PATTERN = Regex("""^(\d{1,2}):(\d{2})(?::\d{2})?.*""")
    }
}

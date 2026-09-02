package com.aqua.aqualight.data.devices.runtime.modules.cooling.v1

import org.json.JSONArray
import org.json.JSONObject

data class DeviceCoolingV1ConfigSnapshot(
    val configRevision: Long,
    val controlMode: DeviceCoolingV1ControlMode,
    val manualTargetPercent: Double,
    val startTemperatureC: Double,
    val fullSpeedTemperatureC: Double,
    val silentModeEnabled: Boolean
)

data class DeviceCoolingV1ProgramSlot(
    val startMinute: Int,
    val endMinute: Int,
    val fanOnTemperatureC: Double,
    val fanPercent: Double
)

data class DeviceCoolingV1FanPolicy(
    val minimumPercent: Double,
    val maximumPercent: Double,
    val stepPercent: Double
)

data class DeviceCoolingV1TemperaturePolicy(
    val minimumC: Double,
    val maximumC: Double,
    val stepC: Double,
    val defaultC: Double
)

data class DeviceCoolingV1ProgramPolicy(
    val maximumSlotCount: Int,
    val timeStepMinutes: Int,
    val minimumDurationMinutes: Int,
    val crossMidnightSlotsSupported: Boolean,
    val requiresTrustedDeviceClock: Boolean,
    val scheduleBasis: String,
    val programActivation: String,
    val timeAuthority: String,
    val currentMinuteOfDaySource: String,
    val activeSlotAuthority: String,
    val startBoundary: String,
    val endBoundary: String,
    val endMinuteMaximum: Int,
    val fan: DeviceCoolingV1FanPolicy,
    val fanOnTemperature: DeviceCoolingV1TemperaturePolicy
)

data class DeviceCoolingV1ProgramSnapshot(
    val programRevision: Long,
    val clockReady: Boolean,
    val currentMinuteOfDay: Int?,
    val activeSlotIndex: Int?,
    val policy: DeviceCoolingV1ProgramPolicy,
    val slots: List<DeviceCoolingV1ProgramSlot>
)

data class DeviceCoolingV1ConfigApplyResult(
    val command: String,
    val operation: String,
    val saved: Boolean,
    val event: String,
    val config: DeviceCoolingV1ConfigSnapshot
)

data class DeviceCoolingV1StatusDocument(
    val schema: String,
    val schemaVersion: Int,
    val uptimeMs: Long,
    val catalogVersion: Int,
    val catalogSha256: String,
    val productKey: String,
    val configRevision: Long,
    val programRevision: Long,
    val data: JSONObject
)

data class DeviceCoolingV1ManualApplyResult(
    val command: String,
    val operation: String,
    val saved: Boolean,
    val configRevision: Long,
    val fanKey: String,
    val manualActive: Boolean,
    val manualTargetPercent: Double,
    val event: String
)

data class DeviceCoolingV1ProgramApplyResult(
    val operation: String,
    val saved: Boolean,
    val event: String,
    val program: DeviceCoolingV1ProgramSnapshot
)

data class DeviceCoolingV1SensorTelemetry(
    val sensorKey: String,
    val present: Boolean?,
    val health: String,
    val readingValid: Boolean,
    val temperatureC: Double?,
    val humidityPercent: Double?,
    val sampledAtMs: Long
)

data class DeviceCoolingV1FanTelemetry(
    val fanKey: String,
    val targetPercent: Double,
    val outputPercent: Double?,
    val rpm: Double?,
    val pwmOutputHealth: String,
    val health: String
)

data class DeviceCoolingV1PowerTelemetry(
    val source: String,
    val available: Boolean,
    val ratedPowerWatts: Double?,
    val powerWatts: Double?,
    val estimatedKwhPerDay: Double?,
    val reason: String?
)

data class DeviceCoolingV1Alarm(
    val code: String,
    val severity: String,
    val active: Boolean,
    val latched: Boolean,
    val affectedKey: String,
    val reason: String
)

data class DeviceCoolingV1HealthSummary(
    val fanHealth: String,
    val sensorHealth: String,
    val activeAlarmCount: Int,
    val highestAlarmSeverity: String
)

data class DeviceCoolingV1Telemetry(
    val schema: String,
    val schemaVersion: Int,
    val catalogSha256: String,
    val configRevision: Long,
    val programRevision: Long,
    val uptimeMs: Long,
    val decisionSequence: Long,
    val evaluatedAtMs: Long,
    val inputSampleSequence: Long,
    val timeGeneration: Long,
    val controlMode: DeviceCoolingV1ControlMode,
    val operatingState: DeviceCoolingV1OperatingState,
    val controlReason: String,
    val manualActive: Boolean,
    val manualTargetPercent: Double,
    val clockReady: Boolean,
    val currentMinuteOfDay: Int?,
    val activeProgramSlotIndex: Int?,
    val sensors: List<DeviceCoolingV1SensorTelemetry>,
    val fan: DeviceCoolingV1FanTelemetry,
    val power: DeviceCoolingV1PowerTelemetry,
    val alarms: List<DeviceCoolingV1Alarm>,
    val healthSummary: DeviceCoolingV1HealthSummary
)

data class DeviceCoolingV1HistorySummary(
    val minimumTemperatureC: Double?,
    val averageTemperatureC: Double?,
    val maximumTemperatureC: Double?
)

data class DeviceCoolingV1HistorySample(
    val sampledAtMs: Long,
    val temperatureC: Double
)

data class DeviceCoolingV1HistoryDay(
    val dayStartAtMs: Long,
    val minimumTemperatureC: Double,
    val averageTemperatureC: Double,
    val maximumTemperatureC: Double
)

data class DeviceCoolingV1History(
    val range: DeviceCoolingV1HistoryRange,
    val chartSource: DeviceCoolingV1ChartSource,
    val generatedAtMs: Long,
    val summary: DeviceCoolingV1HistorySummary,
    val samples: List<DeviceCoolingV1HistorySample>,
    val days: List<DeviceCoolingV1HistoryDay>
)

object DeviceCoolingV1ResponseParser {
    fun parseStatus(data: JSONObject): DeviceCoolingV1StatusDocument {
        data.requireExactKeys(STATUS_KEYS, "cooling.status.get")
        val contract = data.requireObject("contract")
        contract.requireExactKeys(CONTRACT_KEYS, "cooling contract")
        data.requireObject("topology").requireExactKeys(TOPOLOGY_KEYS, "cooling topology")
        parseConfig(data.requireObject("config"))
        data.requireObject("program").requireExactKeys(PROGRAM_STATUS_KEYS, "cooling program status")
        data.requireObject("control").requireExactKeys(CONTROL_KEYS, "cooling control")
        data.requireObject("policy").requireExactKeys(POLICY_KEYS, "cooling policy")
        data.requireObject("telemetry").requireExactKeys(STATUS_TELEMETRY_KEYS, "cooling telemetry")
        data.requireArray("alarms").objects().forEach(::parseAlarm)
        parseHealthSummary(data.requireObject("healthSummary"))
        data.requireObject("history").requireExactKeys(STATUS_HISTORY_KEYS, "cooling history")
        return DeviceCoolingV1StatusDocument(
            schema = data.requireText("schema"),
            schemaVersion = data.requireInt("schemaVersion", 1, 1),
            uptimeMs = data.requireNonNegativeLong("uptimeMs"),
            catalogVersion = contract.requireInt("catalogVersion", 1, 1),
            catalogSha256 = contract.requireText("catalogSha256"),
            productKey = contract.requireText("productKey"),
            configRevision = contract.requireRevision("configRevision"),
            programRevision = contract.requireRevision("programRevision"),
            data = JSONObject(data.toString())
        ).also { status ->
            require(status.schema == DeviceCoolingV1Contract.SCHEMA)
            require(status.catalogSha256 == DeviceCoolingV1Contract.CATALOG_SHA256)
            require(status.productKey == DeviceCoolingV1Contract.PRODUCT_KEY)
        }
    }

    fun parseConfigApply(data: JSONObject): DeviceCoolingV1ConfigApplyResult {
        data.requireExactKeys(CONFIG_APPLY_KEYS, "cooling.config.apply")
        return DeviceCoolingV1ConfigApplyResult(
            command = data.requireText("command"),
            operation = data.requireText("operation"),
            saved = data.requireBoolean("saved"),
            event = data.requireText("event"),
            config = parseConfig(data.requireObject("config"))
        ).also { result ->
            require(result.command == "cooling.config.apply")
            require(result.operation == "configApply")
            require(result.saved)
            require(result.event == DeviceCoolingV1Contract.Event.STATUS_CHANGED)
        }
    }

    fun parseManualApply(data: JSONObject): DeviceCoolingV1ManualApplyResult {
        data.requireExactKeys(MANUAL_APPLY_KEYS, "cooling.manual.apply")
        return DeviceCoolingV1ManualApplyResult(
            command = data.requireText("command"),
            operation = data.requireText("operation"),
            saved = data.requireBoolean("saved"),
            configRevision = data.requireRevision("configRevision"),
            fanKey = data.requireText("fanKey"),
            manualActive = data.requireBoolean("manualActive"),
            manualTargetPercent = data.requirePercent("manualTargetPercent"),
            event = data.requireText("event")
        ).also { result ->
            require(result.command == "cooling.manual.apply")
            require(result.operation == "manualApply")
            require(result.saved)
            require(result.fanKey == DeviceCoolingV1Contract.FAN_KEY)
            require(result.manualActive == (result.manualTargetPercent > 0.0))
            require(result.event == DeviceCoolingV1Contract.Event.STATUS_CHANGED)
        }
    }

    fun parseProgram(data: JSONObject): DeviceCoolingV1ProgramSnapshot {
        data.requireExactKeys(PROGRAM_KEYS, "cooling program")
        val slots = data.requireArray("slots").objects().map(::parseProgramSlot)
        return DeviceCoolingV1ProgramSnapshot(
            programRevision = data.requireRevision("programRevision"),
            clockReady = data.requireBoolean("clockReady"),
            currentMinuteOfDay = data.requireNullableInt(
                "currentMinuteOfDay",
                0,
                DeviceCoolingV1Contract.Limit.MINUTES_PER_DAY - 1
            ),
            activeSlotIndex = data.requireNullableInt(
                "activeSlotIndex",
                0,
                DeviceCoolingV1Contract.Limit.PROGRAM_SLOT_CAPACITY - 1
            ),
            policy = parseProgramPolicy(data.requireObject("policy")),
            slots = slots
        ).also { program ->
            require(program.clockReady == (program.currentMinuteOfDay != null))
            require(program.slots.size <= program.policy.maximumSlotCount)
            program.activeSlotIndex?.let { require(it in program.slots.indices) }
        }
    }

    fun parseProgramApply(data: JSONObject): DeviceCoolingV1ProgramApplyResult {
        data.requireExactKeys(PROGRAM_APPLY_KEYS, "cooling.program.apply")
        return DeviceCoolingV1ProgramApplyResult(
            operation = data.requireText("operation"),
            saved = data.requireBoolean("saved"),
            event = data.requireText("event"),
            program = parseProgram(data.requireObject("program"))
        ).also { result ->
            require(result.operation == "programApply")
            require(result.saved)
            require(result.event == DeviceCoolingV1Contract.Event.STATUS_CHANGED)
        }
    }

    fun parseHistory(data: JSONObject): DeviceCoolingV1History {
        data.requireExactKeys(HISTORY_KEYS, "cooling.history.get")
        val range = enumValue<DeviceCoolingV1HistoryRange>(
            data.requireText("range")
        ) { it.wireValue }
        val chartSource = enumValue<DeviceCoolingV1ChartSource>(
            data.requireText("chartSource")
        ) { it.name }
        val history = DeviceCoolingV1History(
            range = range,
            chartSource = chartSource,
            generatedAtMs = data.requireNonNegativeLong("generatedAtMs"),
            summary = parseHistorySummary(data.requireObject("summary")),
            samples = data.requireArray("samples").objects().map(::parseHistorySample),
            days = data.requireArray("days").objects().map(::parseHistoryDay)
        )
        require(
            history.chartSource == if (range == DeviceCoolingV1HistoryRange.DAYS_30) {
                DeviceCoolingV1ChartSource.DAILY_AVERAGE
            } else {
                DeviceCoolingV1ChartSource.SAMPLES
            }
        )
        return history
    }

    fun parseTelemetry(data: JSONObject): DeviceCoolingV1Telemetry {
        data.requireExactKeys(TELEMETRY_KEYS, "cooling.telemetry.changed")
        val telemetry = DeviceCoolingV1Telemetry(
            schema = data.requireText("schema"),
            schemaVersion = data.requireInt("schemaVersion", 1, 1),
            catalogSha256 = data.requireText("catalogSha256"),
            configRevision = data.requireRevision("configRevision"),
            programRevision = data.requireRevision("programRevision"),
            uptimeMs = data.requireNonNegativeLong("uptimeMs"),
            decisionSequence = data.requireNonNegativeLong("decisionSequence"),
            evaluatedAtMs = data.requireNonNegativeLong("evaluatedAtMs"),
            inputSampleSequence = data.requireNonNegativeLong("inputSampleSequence"),
            timeGeneration = data.requireNonNegativeLong("timeGeneration"),
            controlMode = enumValue(
                data.requireText("controlMode"),
                DeviceCoolingV1ControlMode::wireValue
            ),
            operatingState = enumValue(
                data.requireText("operatingState"),
                DeviceCoolingV1OperatingState::name
            ),
            controlReason = data.requireText("controlReason"),
            manualActive = data.requireBoolean("manualActive"),
            manualTargetPercent = data.requirePercent("manualTargetPercent"),
            clockReady = data.requireBoolean("clockReady"),
            currentMinuteOfDay = data.requireNullableInt(
                "currentMinuteOfDay",
                0,
                DeviceCoolingV1Contract.Limit.MINUTES_PER_DAY - 1
            ),
            activeProgramSlotIndex = data.requireNullableInt(
                "activeProgramSlotIndex",
                0,
                DeviceCoolingV1Contract.Limit.PROGRAM_SLOT_CAPACITY - 1
            ),
            sensors = data.requireArray("sensors").objects().map { parseSensor(it, false) },
            fan = parseFan(data.requireObject("fan")),
            power = parsePower(data.requireObject("power")),
            alarms = data.requireArray("alarms").objects().map(::parseAlarm),
            healthSummary = parseHealthSummary(data.requireObject("healthSummary"))
        )
        require(telemetry.schema == DeviceCoolingV1Contract.SCHEMA)
        require(telemetry.catalogSha256 == DeviceCoolingV1Contract.CATALOG_SHA256)
        require(telemetry.clockReady == (telemetry.currentMinuteOfDay != null))
        require(
            telemetry.sensors.map { it.sensorKey } == listOf(
                DeviceCoolingV1Contract.WATER_SENSOR_KEY,
                DeviceCoolingV1Contract.AMBIENT_SENSOR_KEY
            )
        )
        require(telemetry.healthSummary.activeAlarmCount == telemetry.alarms.count { it.active })
        return telemetry
    }

    private fun parseConfig(data: JSONObject): DeviceCoolingV1ConfigSnapshot {
        data.requireExactKeys(CONFIG_KEYS, "cooling config")
        return DeviceCoolingV1ConfigSnapshot(
            configRevision = data.requireRevision("configRevision"),
            controlMode = enumValue(
                data.requireText("controlMode"),
                DeviceCoolingV1ControlMode::wireValue
            ),
            manualTargetPercent = data.requirePercent("manualTargetPercent"),
            startTemperatureC = data.requireTemperature("startTemperatureC"),
            fullSpeedTemperatureC = data.requireTemperature("fullSpeedTemperatureC"),
            silentModeEnabled = data.requireBoolean("silentModeEnabled")
        ).also { config ->
            require(
                config.fullSpeedTemperatureC - config.startTemperatureC >=
                    DeviceCoolingV1Contract.Limit.MINIMUM_AUTOMATIC_GAP_C
            )
            if (config.controlMode != DeviceCoolingV1ControlMode.MANUAL) {
                require(config.manualTargetPercent == 0.0)
            }
        }
    }

    private fun parseProgramSlot(data: JSONObject): DeviceCoolingV1ProgramSlot {
        data.requireExactKeys(PROGRAM_SLOT_KEYS, "cooling program slot")
        val payload = DeviceCoolingV1ProgramSlotPayload(
            startMinute = data.requireInt(
                "startMinute",
                0,
                DeviceCoolingV1Contract.Limit.MINUTES_PER_DAY - 1
            ),
            endMinute = data.requireInt(
                "endMinute",
                1,
                DeviceCoolingV1Contract.Limit.MINUTES_PER_DAY
            ),
            fanOnTemperatureC = data.requireTemperature("fanOnTemperatureC"),
            fanPercent = data.requirePercent("fanPercent")
        )
        return DeviceCoolingV1ProgramSlot(
            payload.startMinute,
            payload.endMinute,
            payload.fanOnTemperatureC,
            payload.fanPercent
        )
    }

    private fun parseProgramPolicy(data: JSONObject): DeviceCoolingV1ProgramPolicy {
        data.requireExactKeys(PROGRAM_POLICY_KEYS, "cooling program policy")
        val policy = DeviceCoolingV1ProgramPolicy(
            maximumSlotCount = data.requireInt("maximumSlotCount", 1, 8),
            timeStepMinutes = data.requireInt("timeStepMinutes", 1, 1_440),
            minimumDurationMinutes = data.requireInt("minimumDurationMinutes", 1, 1_440),
            crossMidnightSlotsSupported = data.requireBoolean("crossMidnightSlotsSupported"),
            requiresTrustedDeviceClock = data.requireBoolean("requiresTrustedDeviceClock"),
            scheduleBasis = data.requireText("scheduleBasis"),
            programActivation = data.requireText("programActivation"),
            timeAuthority = data.requireText("timeAuthority"),
            currentMinuteOfDaySource = data.requireText("currentMinuteOfDaySource"),
            activeSlotAuthority = data.requireText("activeSlotAuthority"),
            startBoundary = data.requireText("startBoundary"),
            endBoundary = data.requireText("endBoundary"),
            endMinuteMaximum = data.requireInt("endMinuteMaximum", 1, 1_440),
            fan = parseFanPolicy(data.requireObject("fan")),
            fanOnTemperature = parseTemperaturePolicy(data.requireObject("fanOnTemperature"))
        )
        require(policy.maximumSlotCount == DeviceCoolingV1Contract.Limit.PROGRAM_SLOT_CAPACITY)
        require(policy.timeStepMinutes == DeviceCoolingV1Contract.Limit.PROGRAM_TIME_STEP_MINUTES)
        require(
            policy.minimumDurationMinutes ==
                DeviceCoolingV1Contract.Limit.PROGRAM_MINIMUM_DURATION_MINUTES
        )
        require(!policy.crossMidnightSlotsSupported)
        require(policy.requiresTrustedDeviceClock)
        require(policy.scheduleBasis == "LOCAL_WALL_CLOCK_DAILY")
        require(policy.programActivation == "CONTROL_MODE_PROGRAM")
        require(policy.timeAuthority == "AqlTimeService")
        require(policy.currentMinuteOfDaySource == "FIRMWARE_TRUSTED_DEVICE_CLOCK")
        require(policy.activeSlotAuthority == "FIRMWARE")
        require(policy.startBoundary == "INCLUSIVE")
        require(policy.endBoundary == "EXCLUSIVE")
        require(policy.endMinuteMaximum == DeviceCoolingV1Contract.Limit.MINUTES_PER_DAY)
        return policy
    }

    private fun parseFanPolicy(data: JSONObject): DeviceCoolingV1FanPolicy {
        data.requireExactKeys(FAN_POLICY_KEYS, "cooling fan policy")
        return DeviceCoolingV1FanPolicy(
            minimumPercent = data.requirePercent("minimumPercent"),
            maximumPercent = data.requirePercent("maximumPercent"),
            stepPercent = data.requireDouble("stepPercent", 0.0, 100.0)
        )
    }

    private fun parseTemperaturePolicy(data: JSONObject): DeviceCoolingV1TemperaturePolicy {
        data.requireExactKeys(TEMPERATURE_POLICY_KEYS, "cooling temperature policy")
        return DeviceCoolingV1TemperaturePolicy(
            minimumC = data.requireTemperature("minimumC"),
            maximumC = data.requireTemperature("maximumC"),
            stepC = data.requireDouble("stepC", 0.0, 40.0),
            defaultC = data.requireTemperature("defaultC")
        )
    }

    private fun parseSensor(
        data: JSONObject,
        includesPresent: Boolean
    ): DeviceCoolingV1SensorTelemetry {
        val sensorKey = data.requireText("sensorKey")
        val expectedKeys = when {
            sensorKey == DeviceCoolingV1Contract.AMBIENT_SENSOR_KEY ->
                (if (includesPresent) STATUS_SENSOR_KEYS else EVENT_SENSOR_KEYS) +
                    "humidityPercent"
            else -> if (includesPresent) STATUS_SENSOR_KEYS else EVENT_SENSOR_KEYS
        }
        data.requireExactKeys(expectedKeys, "cooling sensor telemetry")
        val readingValid = data.requireBoolean("readingValid")
        val temperature = data.requireNullableDouble("temperatureC", -40.0, 125.0)
        val humidity = if (data.has("humidityPercent")) {
            data.requireNullableDouble("humidityPercent", 0.0, 100.0)
        } else {
            null
        }
        require(readingValid == (temperature != null))
        if (sensorKey == DeviceCoolingV1Contract.AMBIENT_SENSOR_KEY) {
            require(readingValid == (humidity != null))
        }
        return DeviceCoolingV1SensorTelemetry(
            sensorKey = sensorKey,
            present = if (includesPresent) data.requireBoolean("present") else null,
            health = data.requireText("health"),
            readingValid = readingValid,
            temperatureC = temperature,
            humidityPercent = humidity,
            sampledAtMs = data.requireNonNegativeLong("sampledAtMs")
        )
    }

    private fun parseFan(data: JSONObject): DeviceCoolingV1FanTelemetry {
        data.requireExactKeys(FAN_KEYS, "cooling fan telemetry")
        return DeviceCoolingV1FanTelemetry(
            fanKey = data.requireText("fanKey"),
            targetPercent = data.requirePercent("targetPercent"),
            outputPercent = data.requireNullableDouble("outputPercent", 0.0, 100.0),
            rpm = data.requireNullableDouble("rpm", 0.0),
            pwmOutputHealth = data.requireText("pwmOutputHealth"),
            health = data.requireText("health")
        ).also { fan ->
            require(fan.fanKey == DeviceCoolingV1Contract.FAN_KEY)
            require(fan.rpm == null)
            require(fan.pwmOutputHealth in setOf("OK", "FAULT"))
            require(fan.health in setOf("UNVERIFIED", "HARDWARE_FAULT"))
            require((fan.outputPercent == null) == (fan.pwmOutputHealth == "FAULT"))
        }
    }

    private fun parsePower(data: JSONObject): DeviceCoolingV1PowerTelemetry {
        val available = data.requireBoolean("available")
        data.requireExactKeys(
            if (available) POWER_AVAILABLE_KEYS else POWER_UNAVAILABLE_KEYS,
            "cooling power telemetry"
        )
        return DeviceCoolingV1PowerTelemetry(
            source = data.requireText("source"),
            available = available,
            ratedPowerWatts = data.requireNullableDouble("ratedPowerWatts", 0.0),
            powerWatts = data.requireNullableDouble("powerWatts", 0.0),
            estimatedKwhPerDay = data.requireNullableDouble("estimatedKwhPerDay", 0.0),
            reason = if (available) null else data.requireText("reason")
        ).also { power ->
            require(power.source == "ESTIMATED")
            require(
                power.available == (
                    power.ratedPowerWatts != null &&
                        power.powerWatts != null &&
                        power.estimatedKwhPerDay != null
                    )
            )
        }
    }

    private fun parseAlarm(data: JSONObject): DeviceCoolingV1Alarm {
        data.requireExactKeys(ALARM_KEYS, "cooling alarm")
        return DeviceCoolingV1Alarm(
            code = data.requireText("code"),
            severity = data.requireText("severity"),
            active = data.requireBoolean("active"),
            latched = data.requireBoolean("latched"),
            affectedKey = data.requireText("affectedKey"),
            reason = data.requireText("reason")
        )
    }

    private fun parseHealthSummary(data: JSONObject): DeviceCoolingV1HealthSummary {
        data.requireExactKeys(HEALTH_SUMMARY_KEYS, "cooling health summary")
        return DeviceCoolingV1HealthSummary(
            fanHealth = data.requireText("fanHealth"),
            sensorHealth = data.requireText("sensorHealth"),
            activeAlarmCount = data.requireInt("activeAlarmCount", 0, 6),
            highestAlarmSeverity = data.requireText("highestAlarmSeverity")
        )
    }

    private fun parseHistorySummary(data: JSONObject): DeviceCoolingV1HistorySummary {
        data.requireExactKeys(HISTORY_SUMMARY_KEYS, "cooling history summary")
        return DeviceCoolingV1HistorySummary(
            minimumTemperatureC = data.requireNullableDouble("minTemperatureC", -40.0, 125.0),
            averageTemperatureC = data.requireNullableDouble("avgTemperatureC", -40.0, 125.0),
            maximumTemperatureC = data.requireNullableDouble("maxTemperatureC", -40.0, 125.0)
        )
    }

    private fun parseHistorySample(data: JSONObject): DeviceCoolingV1HistorySample {
        data.requireExactKeys(HISTORY_SAMPLE_KEYS, "cooling history sample")
        return DeviceCoolingV1HistorySample(
            sampledAtMs = data.requireNonNegativeLong("sampledAtMs"),
            temperatureC = data.requireDouble("temperatureC", -40.0, 125.0)
        )
    }

    private fun parseHistoryDay(data: JSONObject): DeviceCoolingV1HistoryDay {
        data.requireExactKeys(HISTORY_DAY_KEYS, "cooling history day")
        return DeviceCoolingV1HistoryDay(
            dayStartAtMs = data.requireNonNegativeLong("dayStartAtMs"),
            minimumTemperatureC = data.requireDouble("minTemperatureC", -40.0, 125.0),
            averageTemperatureC = data.requireDouble("avgTemperatureC", -40.0, 125.0),
            maximumTemperatureC = data.requireDouble("maxTemperatureC", -40.0, 125.0)
        )
    }

    private val CONFIG_APPLY_KEYS = setOf("command", "operation", "saved", "event", "config")
    private val MANUAL_APPLY_KEYS = setOf(
        "command", "operation", "saved", "configRevision", "fanKey",
        "manualActive", "manualTargetPercent", "event"
    )
    private val PROGRAM_APPLY_KEYS = setOf("operation", "saved", "event", "program")
    private val CONFIG_KEYS = setOf(
        "configRevision", "controlMode", "manualTargetPercent", "startTemperatureC",
        "fullSpeedTemperatureC", "silentModeEnabled"
    )
    private val PROGRAM_KEYS = setOf(
        "programRevision", "clockReady", "currentMinuteOfDay", "activeSlotIndex",
        "policy", "slots"
    )
    private val PROGRAM_SLOT_KEYS = setOf(
        "startMinute", "endMinute", "fanOnTemperatureC", "fanPercent"
    )
    private val PROGRAM_POLICY_KEYS = setOf(
        "maximumSlotCount", "timeStepMinutes", "minimumDurationMinutes",
        "crossMidnightSlotsSupported", "requiresTrustedDeviceClock", "scheduleBasis",
        "programActivation", "timeAuthority", "currentMinuteOfDaySource",
        "activeSlotAuthority", "startBoundary", "endBoundary", "endMinuteMaximum",
        "fan", "fanOnTemperature"
    )
    private val FAN_POLICY_KEYS = setOf("minimumPercent", "maximumPercent", "stepPercent")
    private val TEMPERATURE_POLICY_KEYS = setOf("minimumC", "maximumC", "stepC", "defaultC")
    private val HISTORY_KEYS = setOf(
        "range", "chartSource", "generatedAtMs", "summary", "samples", "days"
    )
    private val HISTORY_SUMMARY_KEYS = setOf(
        "minTemperatureC", "avgTemperatureC", "maxTemperatureC"
    )
    private val HISTORY_SAMPLE_KEYS = setOf("sampledAtMs", "temperatureC")
    private val HISTORY_DAY_KEYS = setOf(
        "dayStartAtMs", "minTemperatureC", "avgTemperatureC", "maxTemperatureC"
    )
    private val EVENT_SENSOR_KEYS = setOf(
        "sensorKey", "health", "readingValid", "temperatureC", "sampledAtMs"
    )
    private val STATUS_SENSOR_KEYS = EVENT_SENSOR_KEYS + "present"
    private val FAN_KEYS = setOf(
        "fanKey", "targetPercent", "outputPercent", "rpm", "pwmOutputHealth", "health"
    )
    private val POWER_AVAILABLE_KEYS = setOf(
        "source", "available", "ratedPowerWatts", "powerWatts", "estimatedKwhPerDay"
    )
    private val POWER_UNAVAILABLE_KEYS = POWER_AVAILABLE_KEYS + "reason"
    private val ALARM_KEYS = setOf(
        "code", "severity", "active", "latched", "affectedKey", "reason"
    )
    private val HEALTH_SUMMARY_KEYS = setOf(
        "fanHealth", "sensorHealth", "activeAlarmCount", "highestAlarmSeverity"
    )
    private val TELEMETRY_KEYS = setOf(
        "schema", "schemaVersion", "catalogSha256", "configRevision", "programRevision",
        "uptimeMs", "decisionSequence", "evaluatedAtMs", "inputSampleSequence",
        "timeGeneration", "controlMode", "operatingState", "controlReason",
        "manualActive", "manualTargetPercent", "clockReady", "currentMinuteOfDay",
        "activeProgramSlotIndex", "sensors", "fan", "power", "alarms", "healthSummary"
    )
    private val STATUS_KEYS = setOf(
        "schema", "schemaVersion", "uptimeMs", "contract", "topology", "config",
        "program", "control", "policy", "telemetry", "alarms", "healthSummary", "history"
    )
    private val CONTRACT_KEYS = setOf(
        "catalogVersion", "catalogSha256", "productKey", "configRevision", "programRevision"
    )
    private val TOPOLOGY_KEYS = setOf(
        "fanOutputCapacity", "sensorSlotCapacity", "programSlotCapacity",
        "fanOutputs", "sensorSlots"
    )
    private val PROGRAM_STATUS_KEYS = setOf(
        "programRevision", "evaluatedProgramRevision", "slotCount", "clockReady",
        "currentMinuteOfDay", "activeSlotIndex"
    )
    private val CONTROL_KEYS = setOf(
        "decisionSequence", "evaluatedAtMs", "inputSampleSequence", "timeGeneration",
        "controlMode", "operatingState", "controlReason", "targetPercent", "manualActive"
    )
    private val POLICY_KEYS = setOf(
        "controlModes", "temperature", "fanPercent", "silentMode", "manual", "program",
        "failSafe"
    )
    private val STATUS_TELEMETRY_KEYS = setOf("sensors", "fan", "power")
    private val STATUS_HISTORY_KEYS = setOf(
        "sensorKey", "ranges", "captureIntervalMinutes", "persistent", "storageHealthy",
        "chartSources"
    )
}

private fun JSONObject.requireExactKeys(expected: Set<String>, label: String) {
    val actual = keys().asSequence().toSet()
    require(actual == expected) {
        "$label keys differ from firmware; expected=$expected actual=$actual"
    }
}

private fun JSONObject.requireObject(key: String): JSONObject =
    get(key) as? JSONObject ?: error("$key must be an object.")

private fun JSONObject.requireArray(key: String): JSONArray =
    get(key) as? JSONArray ?: error("$key must be an array.")

private fun JSONObject.requireText(key: String): String =
    (get(key) as? String)?.also { value ->
        require(value.isNotEmpty() && value.none(Char::isISOControl))
        require(value == value.trim())
    } ?: error("$key must be a canonical string.")

private fun JSONObject.requireBoolean(key: String): Boolean =
    get(key) as? Boolean ?: error("$key must be boolean.")

private fun JSONObject.requireInt(key: String, minimum: Int, maximum: Int): Int {
    val value = get(key) as? Number ?: error("$key must be integer.")
    val long = value.toLong()
    require(value.toDouble().isFinite() && value.toDouble() == long.toDouble())
    require(long in minimum.toLong()..maximum.toLong())
    return long.toInt()
}

private fun JSONObject.requireNullableInt(key: String, minimum: Int, maximum: Int): Int? =
    if (get(key) === JSONObject.NULL) null else requireInt(key, minimum, maximum)

private fun JSONObject.requireNonNegativeLong(key: String): Long {
    val value = get(key) as? Number ?: error("$key must be integer.")
    val long = value.toLong()
    require(value.toDouble().isFinite() && value.toDouble() == long.toDouble())
    require(long >= 0L)
    return long
}

private fun JSONObject.requireRevision(key: String): Long =
    requireNonNegativeLong(key).also { requireRevision(it, key) }

private fun JSONObject.requireDouble(
    key: String,
    minimum: Double,
    maximum: Double = Double.MAX_VALUE
): Double = (get(key) as? Number)?.toDouble()?.also { value ->
    require(value.isFinite() && value in minimum..maximum)
} ?: error("$key must be numeric.")

private fun JSONObject.requireNullableDouble(
    key: String,
    minimum: Double,
    maximum: Double = Double.MAX_VALUE
): Double? = if (get(key) === JSONObject.NULL) {
    null
} else {
    requireDouble(key, minimum, maximum)
}

private fun JSONObject.requireTemperature(key: String): Double =
    requireDouble(
        key,
        DeviceCoolingV1Contract.Limit.TEMPERATURE_MINIMUM_C,
        DeviceCoolingV1Contract.Limit.TEMPERATURE_MAXIMUM_C
    ).also(::requireCoolingTemperature)

private fun JSONObject.requirePercent(key: String): Double =
    requireDouble(
        key,
        DeviceCoolingV1Contract.Limit.FAN_PERCENT_MINIMUM,
        DeviceCoolingV1Contract.Limit.FAN_PERCENT_MAXIMUM
    ).also(::requireCoolingFanPercent)

private fun JSONArray.objects(): List<JSONObject> = List(length()) { index ->
    get(index) as? JSONObject ?: error("[$index] must be an object.")
}

private inline fun <reified T : Enum<T>> enumValue(
    value: String,
    wireValue: (T) -> String
): T = enumValues<T>().singleOrNull { wireValue(it) == value }
    ?: error("Unknown enum value: $value")

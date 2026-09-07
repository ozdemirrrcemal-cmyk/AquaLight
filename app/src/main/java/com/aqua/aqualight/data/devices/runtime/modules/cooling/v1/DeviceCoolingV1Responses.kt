package com.aqua.aqualight.data.devices.runtime.modules.cooling.v1

import org.json.JSONObject

data class DeviceCoolingV1ConfigSnapshot(
    val configRevision: Long,
    val controlMode: DeviceCoolingV1ControlMode,
    val manualTargetPercent: Double,
    val startTemperatureC: Double,
    val fullSpeedTemperatureC: Double,
    val silentModeEnabled: Boolean
)

data class DeviceCoolingV1FanTopology(
    val fanKey: String,
    val outputKind: String,
    val rpmAvailable: Boolean,
    val hardwareEditable: Boolean
)

data class DeviceCoolingV1SensorTopology(
    val sensorKey: String,
    val role: String,
    val driver: String,
    val bus: String,
    val address: Int?,
    val temperature: Boolean,
    val relativeHumidity: Boolean,
    val requiredForProduct: Boolean,
    val requiredForControl: Boolean
)

data class DeviceCoolingV1Topology(
    val fanOutputCapacity: Int,
    val sensorSlotCapacity: Int,
    val programSlotCapacity: Int,
    val fanOutputs: List<DeviceCoolingV1FanTopology>,
    val sensorSlots: List<DeviceCoolingV1SensorTopology>
)

data class DeviceCoolingV1ProgramStatus(
    val programRevision: Long,
    val evaluatedProgramRevision: Long,
    val slotCount: Int,
    val clockReady: Boolean,
    val currentMinuteOfDay: Int?,
    val activeSlotIndex: Int?
)

data class DeviceCoolingV1ControlStatus(
    val decisionSequence: Long,
    val evaluatedAtMs: Long,
    val inputSampleSequence: Long,
    val timeGeneration: Long,
    val controlMode: DeviceCoolingV1ControlMode,
    val operatingState: DeviceCoolingV1OperatingState,
    val controlReason: String,
    val targetPercent: Double,
    val manualActive: Boolean
)

data class DeviceCoolingV1AutomaticTemperaturePolicy(
    val minimumC: Double,
    val maximumC: Double,
    val stepC: Double,
    val minimumGapC: Double,
    val hysteresisC: Double
)

data class DeviceCoolingV1SilentModePolicy(
    val supported: Boolean,
    val maximumPercent: Double
)

data class DeviceCoolingV1ManualPolicy(
    val persistent: Boolean,
    val survivesRestart: Boolean,
    val zeroPercentStopsImmediately: Boolean,
    val clearedOnControlModeExit: Boolean,
    val disconnectDoesNotStop: Boolean,
    val allowedWhenWaterSensorFaulted: Boolean
)

data class DeviceCoolingV1StatusProgramPolicy(
    val maximumSlotCount: Int,
    val timeStepMinutes: Int,
    val minimumDurationMinutes: Int,
    val requiresTrustedDeviceClock: Boolean,
    val programActivation: String,
    val startBoundary: String,
    val endBoundary: String,
    val endMinuteMaximum: Int
)

data class DeviceCoolingV1FailSafePolicy(
    val waterSensorFaultPercent: Double
)

data class DeviceCoolingV1StatusPolicy(
    val controlModes: List<DeviceCoolingV1ControlMode>,
    val temperature: DeviceCoolingV1AutomaticTemperaturePolicy,
    val fanPercent: DeviceCoolingV1FanPolicy,
    val silentMode: DeviceCoolingV1SilentModePolicy,
    val manual: DeviceCoolingV1ManualPolicy,
    val program: DeviceCoolingV1StatusProgramPolicy,
    val failSafe: DeviceCoolingV1FailSafePolicy
)

data class DeviceCoolingV1HistoryCapabilities(
    val sensorKey: String,
    val ranges: List<DeviceCoolingV1HistoryRange>,
    val captureIntervalMinutes: Int,
    val persistent: Boolean,
    val storageHealthy: Boolean,
    val chartSources: Map<DeviceCoolingV1HistoryRange, DeviceCoolingV1ChartSource>
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
    val topology: DeviceCoolingV1Topology,
    val config: DeviceCoolingV1ConfigSnapshot,
    val program: DeviceCoolingV1ProgramStatus,
    val control: DeviceCoolingV1ControlStatus,
    val policy: DeviceCoolingV1StatusPolicy,
    val telemetry: DeviceCoolingV1Telemetry,
    val history: DeviceCoolingV1HistoryCapabilities
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
    fun parseStatus(data: JSONObject): DeviceCoolingV1StatusDocument =
        StatusDocumentParser.parse(data)

    fun parseConfigApply(data: JSONObject): DeviceCoolingV1ConfigApplyResult {
        data.requireExactKeys(CONFIG_APPLY_KEYS, "cooling.config.apply")
        return DeviceCoolingV1ConfigApplyResult(
            command = data.requireText("command"),
            operation = data.requireText("operation"),
            saved = data.requireBoolean("saved"),
            event = data.requireText("event"),
            config = ConfigParser.parseConfig(data.requireObject("config"))
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
        val slots = data.requireArray("slots").objects().map(ConfigParser::parseProgramSlot)
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
            policy = ConfigParser.parseProgramPolicy(data.requireObject("policy")),
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
            summary = HistoryParser.parseSummary(data.requireObject("summary")),
            samples = data.requireArray("samples").objects().map(HistoryParser::parseSample),
            days = data.requireArray("days").objects().map(HistoryParser::parseDay)
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
            sensors = data.requireArray("sensors").objects().map {
                TelemetryParser.parseSensor(it, includesPresent = false)
            },
            fan = TelemetryParser.parseFan(data.requireObject("fan")),
            power = TelemetryParser.parsePower(data.requireObject("power")),
            alarms = data.requireArray("alarms").objects().map(TelemetryParser::parseAlarm),
            healthSummary = TelemetryParser.parseHealthSummary(
                data.requireObject("healthSummary")
            )
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

    private object StatusDocumentParser {
        fun parse(data: JSONObject): DeviceCoolingV1StatusDocument {
            data.requireExactKeys(STATUS_KEYS, "cooling.status.get")
            val identity = parseIdentity(data)
            val components = parseComponents(data, identity)
            return createDocument(identity, components).also(::validate)
        }

        private fun parseIdentity(data: JSONObject): StatusIdentity {
            val contract = data.requireObject("contract")
            contract.requireExactKeys(CONTRACT_KEYS, "cooling contract")
            return StatusIdentity(
                schema = data.requireText("schema"),
                schemaVersion = data.requireInt("schemaVersion", 1, 1),
                uptimeMs = data.requireNonNegativeLong("uptimeMs"),
                catalogVersion = contract.requireInt("catalogVersion", 1, 1),
                catalogSha256 = contract.requireText("catalogSha256"),
                productKey = contract.requireText("productKey"),
                configRevision = contract.requireRevision("configRevision"),
                programRevision = contract.requireRevision("programRevision")
            )
        }

        private fun parseComponents(
            data: JSONObject,
            identity: StatusIdentity
        ): StatusComponents {
            val config = ConfigParser.parseConfig(data.requireObject("config"))
            val program = StatusParser.parseProgram(data.requireObject("program"))
            val control = StatusParser.parseControl(data.requireObject("control"))
            return StatusComponents(
                topology = StatusParser.parseTopology(data.requireObject("topology")),
                config = config,
                program = program,
                control = control,
                policy = StatusPolicyParser.parsePolicy(data.requireObject("policy")),
                telemetry = parseTelemetry(data, identity, config, program, control),
                history = StatusParser.parseHistory(data.requireObject("history"))
            )
        }

        private fun parseTelemetry(
            data: JSONObject,
            identity: StatusIdentity,
            config: DeviceCoolingV1ConfigSnapshot,
            program: DeviceCoolingV1ProgramStatus,
            control: DeviceCoolingV1ControlStatus
        ): DeviceCoolingV1Telemetry {
            val telemetryData = data.requireObject("telemetry")
            telemetryData.requireExactKeys(STATUS_TELEMETRY_KEYS, "cooling status telemetry")
            return DeviceCoolingV1Telemetry(
                schema = identity.schema,
                schemaVersion = identity.schemaVersion,
                catalogSha256 = identity.catalogSha256,
                configRevision = identity.configRevision,
                programRevision = program.evaluatedProgramRevision,
                uptimeMs = identity.uptimeMs,
                decisionSequence = control.decisionSequence,
                evaluatedAtMs = control.evaluatedAtMs,
                inputSampleSequence = control.inputSampleSequence,
                timeGeneration = control.timeGeneration,
                controlMode = control.controlMode,
                operatingState = control.operatingState,
                controlReason = control.controlReason,
                manualActive = control.manualActive,
                manualTargetPercent = config.manualTargetPercent,
                clockReady = program.clockReady,
                currentMinuteOfDay = program.currentMinuteOfDay,
                activeProgramSlotIndex = program.activeSlotIndex,
                sensors = telemetryData.requireArray("sensors").objects().map {
                    TelemetryParser.parseSensor(it, includesPresent = true)
                },
                fan = TelemetryParser.parseFan(telemetryData.requireObject("fan")),
                power = TelemetryParser.parsePower(telemetryData.requireObject("power")),
                alarms = data.requireArray("alarms").objects().map(TelemetryParser::parseAlarm),
                healthSummary = TelemetryParser.parseHealthSummary(
                    data.requireObject("healthSummary")
                )
            )
        }

        private fun createDocument(
            identity: StatusIdentity,
            components: StatusComponents
        ): DeviceCoolingV1StatusDocument = DeviceCoolingV1StatusDocument(
            schema = identity.schema,
            schemaVersion = identity.schemaVersion,
            uptimeMs = identity.uptimeMs,
            catalogVersion = identity.catalogVersion,
            catalogSha256 = identity.catalogSha256,
            productKey = identity.productKey,
            configRevision = identity.configRevision,
            programRevision = identity.programRevision,
            topology = components.topology,
            config = components.config,
            program = components.program,
            control = components.control,
            policy = components.policy,
            telemetry = components.telemetry,
            history = components.history
        )

        private fun validate(status: DeviceCoolingV1StatusDocument) {
            require(status.schema == DeviceCoolingV1Contract.SCHEMA)
            require(status.catalogSha256 == DeviceCoolingV1Contract.CATALOG_SHA256)
            require(status.productKey == DeviceCoolingV1Contract.PRODUCT_KEY)
            require(status.config.configRevision == status.configRevision)
            require(status.program.programRevision == status.programRevision)
            require(status.control.controlMode == status.config.controlMode)
            require(status.config.controlMode in status.policy.controlModes)
            require(status.program.slotCount <= status.topology.programSlotCapacity)
            require(status.program.slotCount <= status.policy.program.maximumSlotCount)
            require(
                status.config.startTemperatureC in
                    status.policy.temperature.minimumC..status.policy.temperature.maximumC
            )
            require(
                status.config.fullSpeedTemperatureC in
                    status.policy.temperature.minimumC..status.policy.temperature.maximumC
            )
            require(
                status.config.fullSpeedTemperatureC - status.config.startTemperatureC >=
                    status.policy.temperature.minimumGapC
            )
            require(status.telemetry.fan.targetPercent == status.control.targetPercent)
            require(
                status.control.targetPercent in
                    status.policy.fanPercent.minimumPercent..status.policy.fanPercent.maximumPercent
            )
            require(
                status.telemetry.sensors.map(DeviceCoolingV1SensorTelemetry::sensorKey) ==
                    status.topology.sensorSlots.map(DeviceCoolingV1SensorTopology::sensorKey)
            )
            require(
                status.telemetry.healthSummary.activeAlarmCount ==
                    status.telemetry.alarms.count { alarm -> alarm.active }
            )
        }

        private data class StatusIdentity(
            val schema: String,
            val schemaVersion: Int,
            val uptimeMs: Long,
            val catalogVersion: Int,
            val catalogSha256: String,
            val productKey: String,
            val configRevision: Long,
            val programRevision: Long
        )

        private data class StatusComponents(
            val topology: DeviceCoolingV1Topology,
            val config: DeviceCoolingV1ConfigSnapshot,
            val program: DeviceCoolingV1ProgramStatus,
            val control: DeviceCoolingV1ControlStatus,
            val policy: DeviceCoolingV1StatusPolicy,
            val telemetry: DeviceCoolingV1Telemetry,
            val history: DeviceCoolingV1HistoryCapabilities
        )
    }

    private object StatusParser {
        fun parseTopology(data: JSONObject): DeviceCoolingV1Topology {
            data.requireExactKeys(TOPOLOGY_KEYS, "cooling topology")
            val fanOutputs = data.requireArray("fanOutputs").objects().map(::parseFanTopology)
            val sensorSlots = data.requireArray("sensorSlots").objects().map(::parseSensorTopology)
            return DeviceCoolingV1Topology(
                fanOutputCapacity = data.requireInt(
                    "fanOutputCapacity",
                    1,
                    DeviceCoolingV1Contract.Limit.FAN_OUTPUT_CAPACITY
                ),
                sensorSlotCapacity = data.requireInt(
                    "sensorSlotCapacity",
                    1,
                    DeviceCoolingV1Contract.Limit.SENSOR_SLOT_CAPACITY
                ),
                programSlotCapacity = data.requireInt(
                    "programSlotCapacity",
                    DeviceCoolingV1Contract.Limit.PROGRAM_SLOT_COUNT_MINIMUM,
                    DeviceCoolingV1Contract.Limit.PROGRAM_SLOT_CAPACITY
                ),
                fanOutputs = fanOutputs,
                sensorSlots = sensorSlots
            ).also { topology ->
                require(topology.fanOutputs.size == topology.fanOutputCapacity)
                require(topology.sensorSlots.size == topology.sensorSlotCapacity)
                require(topology.fanOutputs.map(DeviceCoolingV1FanTopology::fanKey).distinct().size ==
                    topology.fanOutputs.size)
                require(topology.sensorSlots.map(DeviceCoolingV1SensorTopology::sensorKey).distinct().size ==
                    topology.sensorSlots.size)
            }
        }

        fun parseProgram(data: JSONObject): DeviceCoolingV1ProgramStatus {
            data.requireExactKeys(PROGRAM_STATUS_KEYS, "cooling program status")
            return DeviceCoolingV1ProgramStatus(
                programRevision = data.requireRevision("programRevision"),
                evaluatedProgramRevision = data.requireRevision("evaluatedProgramRevision"),
                slotCount = data.requireInt(
                    "slotCount",
                    0,
                    DeviceCoolingV1Contract.Limit.PROGRAM_SLOT_CAPACITY
                ),
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
                )
            ).also { program ->
                require(program.clockReady == (program.currentMinuteOfDay != null))
                if (program.programRevision == program.evaluatedProgramRevision) {
                    program.activeSlotIndex?.let { require(it < program.slotCount) }
                }
            }
        }

        fun parseControl(data: JSONObject): DeviceCoolingV1ControlStatus {
            data.requireExactKeys(CONTROL_KEYS, "cooling control")
            return DeviceCoolingV1ControlStatus(
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
                targetPercent = data.requireRuntimePercent("targetPercent"),
                manualActive = data.requireBoolean("manualActive")
            )
        }

        fun parseHistory(data: JSONObject): DeviceCoolingV1HistoryCapabilities {
            data.requireExactKeys(STATUS_HISTORY_KEYS, "cooling history capabilities")
            val ranges = data.requireArray("ranges")
                .textValues("cooling history ranges")
                .map { value -> enumValue<DeviceCoolingV1HistoryRange>(value) { it.wireValue } }
            require(ranges.isNotEmpty() && ranges.distinct().size == ranges.size)
            val chartSourcesData = data.requireObject("chartSources")
            chartSourcesData.requireExactKeys(
                ranges.mapTo(linkedSetOf()) { range -> range.wireValue },
                "cooling history chartSources"
            )
            val chartSources = ranges.associateWith { range ->
                enumValue<DeviceCoolingV1ChartSource>(
                    chartSourcesData.requireText(range.wireValue),
                    DeviceCoolingV1ChartSource::name
                )
            }
            return DeviceCoolingV1HistoryCapabilities(
                sensorKey = data.requireText("sensorKey"),
                ranges = ranges,
                captureIntervalMinutes = data.requireInt(
                    "captureIntervalMinutes",
                    1,
                    DeviceCoolingV1Contract.Limit.MINUTES_PER_DAY
                ),
                persistent = data.requireBoolean("persistent"),
                storageHealthy = data.requireBoolean("storageHealthy"),
                chartSources = chartSources
            ).also { history ->
                require(history.sensorKey == DeviceCoolingV1Contract.WATER_SENSOR_KEY)
            }
        }

        private fun parseFanTopology(data: JSONObject): DeviceCoolingV1FanTopology {
            data.requireExactKeys(FAN_TOPOLOGY_KEYS, "cooling fan topology")
            return DeviceCoolingV1FanTopology(
                fanKey = data.requireText("fanKey"),
                outputKind = data.requireText("outputKind"),
                rpmAvailable = data.requireBoolean("rpmAvailable"),
                hardwareEditable = data.requireBoolean("hardwareEditable")
            ).also { fan ->
                require(fan.fanKey == DeviceCoolingV1Contract.FAN_KEY)
                require(fan.outputKind == "PWM_DUTY_PERCENT")
            }
        }

        private fun parseSensorTopology(data: JSONObject): DeviceCoolingV1SensorTopology {
            val keys = if (data.has("address")) {
                SENSOR_TOPOLOGY_KEYS + "address"
            } else {
                SENSOR_TOPOLOGY_KEYS
            }
            data.requireExactKeys(keys, "cooling sensor topology")
            return DeviceCoolingV1SensorTopology(
                sensorKey = data.requireText("sensorKey"),
                role = data.requireText("role"),
                driver = data.requireText("driver"),
                bus = data.requireText("bus"),
                address = if (data.has("address")) {
                    data.requireInt("address", SENSOR_ADDRESS_MINIMUM, SENSOR_ADDRESS_MAXIMUM)
                } else {
                    null
                },
                temperature = data.requireBoolean("temperature"),
                relativeHumidity = data.requireBoolean("relativeHumidity"),
                requiredForProduct = data.requireBoolean("requiredForProduct"),
                requiredForControl = data.requireBoolean("requiredForControl")
            ).also { sensor ->
                require(sensor.sensorKey in setOf(
                    DeviceCoolingV1Contract.WATER_SENSOR_KEY,
                    DeviceCoolingV1Contract.AMBIENT_SENSOR_KEY
                ))
                require(sensor.temperature)
                require((sensor.bus == "I2C") == (sensor.address != null))
            }
        }

    }

    private object StatusPolicyParser {
        fun parsePolicy(data: JSONObject): DeviceCoolingV1StatusPolicy {
            data.requireExactKeys(POLICY_KEYS, "cooling policy")
            val controlModes = data.requireArray("controlModes")
                .textValues("cooling controlModes")
                .map { value -> enumValue(value, DeviceCoolingV1ControlMode::wireValue) }
            require(controlModes.isNotEmpty() && controlModes.distinct().size == controlModes.size)
            return DeviceCoolingV1StatusPolicy(
                controlModes = controlModes,
                temperature = parseAutomaticTemperaturePolicy(
                    data.requireObject("temperature")
                ),
                fanPercent = parseStatusFanPolicy(data.requireObject("fanPercent")),
                silentMode = parseSilentModePolicy(data.requireObject("silentMode")),
                manual = parseManualPolicy(data.requireObject("manual")),
                program = parseStatusProgramPolicy(data.requireObject("program")),
                failSafe = parseFailSafePolicy(data.requireObject("failSafe"))
            ).also { policy ->
                require(
                    policy.silentMode.maximumPercent in
                        policy.fanPercent.minimumPercent..policy.fanPercent.maximumPercent
                )
                if (policy.silentMode.supported) {
                    require(policy.silentMode.maximumPercent > policy.fanPercent.minimumPercent)
                }
                require(
                    policy.failSafe.waterSensorFaultPercent in
                        policy.fanPercent.minimumPercent..policy.fanPercent.maximumPercent
                )
            }
        }

        private fun parseAutomaticTemperaturePolicy(
            data: JSONObject
        ): DeviceCoolingV1AutomaticTemperaturePolicy {
            data.requireExactKeys(AUTOMATIC_TEMPERATURE_POLICY_KEYS, "cooling temperature policy")
            return DeviceCoolingV1AutomaticTemperaturePolicy(
                minimumC = data.requireDouble("minimumC", Double.NEGATIVE_INFINITY),
                maximumC = data.requireDouble("maximumC", Double.NEGATIVE_INFINITY),
                stepC = data.requireDouble("stepC", Double.MIN_VALUE),
                minimumGapC = data.requireDouble("minimumGapC", 0.0),
                hysteresisC = data.requireDouble("hysteresisC", 0.0)
            ).also { policy ->
                require(policy.minimumC < policy.maximumC)
                require(policy.minimumGapC >= policy.stepC)
            }
        }

        private fun parseStatusFanPolicy(data: JSONObject): DeviceCoolingV1FanPolicy {
            data.requireExactKeys(STATUS_FAN_POLICY_KEYS, "cooling fan percent policy")
            return DeviceCoolingV1FanPolicy(
                minimumPercent = data.requireDouble(
                    "minimum",
                    DeviceCoolingV1Contract.Limit.FAN_PERCENT_MINIMUM,
                    DeviceCoolingV1Contract.Limit.FAN_PERCENT_MAXIMUM
                ),
                maximumPercent = data.requireDouble(
                    "maximum",
                    DeviceCoolingV1Contract.Limit.FAN_PERCENT_MINIMUM,
                    DeviceCoolingV1Contract.Limit.FAN_PERCENT_MAXIMUM
                ),
                stepPercent = data.requireDouble(
                    "step",
                    Double.MIN_VALUE,
                    DeviceCoolingV1Contract.Limit.FAN_PERCENT_MAXIMUM
                )
            ).also { policy ->
                require(policy.minimumPercent < policy.maximumPercent)
            }
        }

        private fun parseSilentModePolicy(data: JSONObject): DeviceCoolingV1SilentModePolicy {
            data.requireExactKeys(SILENT_MODE_POLICY_KEYS, "cooling silent mode policy")
            return DeviceCoolingV1SilentModePolicy(
                supported = data.requireBoolean("supported"),
                maximumPercent = data.requireDouble(
                    "maximumPercent",
                    DeviceCoolingV1Contract.Limit.FAN_PERCENT_MINIMUM,
                    DeviceCoolingV1Contract.Limit.FAN_PERCENT_MAXIMUM
                )
            )
        }

        private fun parseManualPolicy(data: JSONObject): DeviceCoolingV1ManualPolicy {
            data.requireExactKeys(MANUAL_POLICY_KEYS, "cooling manual policy")
            return DeviceCoolingV1ManualPolicy(
                persistent = data.requireBoolean("persistent"),
                survivesRestart = data.requireBoolean("survivesRestart"),
                zeroPercentStopsImmediately = data.requireBoolean("zeroPercentStopsImmediately"),
                clearedOnControlModeExit = data.requireBoolean("clearedOnControlModeExit"),
                disconnectDoesNotStop = data.requireBoolean("disconnectDoesNotStop"),
                allowedWhenWaterSensorFaulted = data.requireBoolean("allowedWhenWaterSensorFaulted")
            )
        }

        private fun parseStatusProgramPolicy(data: JSONObject): DeviceCoolingV1StatusProgramPolicy {
            data.requireExactKeys(STATUS_PROGRAM_POLICY_KEYS, "cooling status program policy")
            return DeviceCoolingV1StatusProgramPolicy(
                maximumSlotCount = data.requireInt(
                    "maximumSlotCount",
                    1,
                    DeviceCoolingV1Contract.Limit.PROGRAM_SLOT_CAPACITY
                ),
                timeStepMinutes = data.requireInt(
                    "timeStepMinutes",
                    1,
                    DeviceCoolingV1Contract.Limit.MINUTES_PER_DAY
                ),
                minimumDurationMinutes = data.requireInt(
                    "minimumDurationMinutes",
                    1,
                    DeviceCoolingV1Contract.Limit.MINUTES_PER_DAY
                ),
                requiresTrustedDeviceClock = data.requireBoolean("requiresTrustedDeviceClock"),
                programActivation = data.requireText("programActivation"),
                startBoundary = data.requireText("startBoundary"),
                endBoundary = data.requireText("endBoundary"),
                endMinuteMaximum = data.requireInt(
                    "endMinuteMaximum",
                    1,
                    DeviceCoolingV1Contract.Limit.MINUTES_PER_DAY
                )
            )
        }

        private fun parseFailSafePolicy(data: JSONObject): DeviceCoolingV1FailSafePolicy {
            data.requireExactKeys(FAIL_SAFE_POLICY_KEYS, "cooling fail-safe policy")
            return DeviceCoolingV1FailSafePolicy(
                waterSensorFaultPercent = data.requireDouble(
                    "waterSensorFaultPercent",
                    DeviceCoolingV1Contract.Limit.FAN_PERCENT_MINIMUM,
                    DeviceCoolingV1Contract.Limit.FAN_PERCENT_MAXIMUM
                )
            )
        }
    }

    private object ConfigParser {
        fun parseConfig(data: JSONObject): DeviceCoolingV1ConfigSnapshot {
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

        fun parseProgramSlot(data: JSONObject): DeviceCoolingV1ProgramSlot {
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

        fun parseProgramPolicy(data: JSONObject): DeviceCoolingV1ProgramPolicy {
            data.requireExactKeys(PROGRAM_POLICY_KEYS, "cooling program policy")
            val limit = DeviceCoolingV1Contract.Limit
            val policy = DeviceCoolingV1ProgramPolicy(
                maximumSlotCount = data.requireInt(
                    "maximumSlotCount",
                    limit.PROGRAM_SLOT_COUNT_MINIMUM,
                    limit.PROGRAM_SLOT_CAPACITY
                ),
                timeStepMinutes = data.requireInt(
                    "timeStepMinutes",
                    limit.END_MINUTE_MINIMUM,
                    limit.MINUTES_PER_DAY
                ),
                minimumDurationMinutes = data.requireInt(
                    "minimumDurationMinutes",
                    limit.END_MINUTE_MINIMUM,
                    limit.MINUTES_PER_DAY
                ),
                crossMidnightSlotsSupported = data.requireBoolean("crossMidnightSlotsSupported"),
                requiresTrustedDeviceClock = data.requireBoolean("requiresTrustedDeviceClock"),
                scheduleBasis = data.requireText("scheduleBasis"),
                programActivation = data.requireText("programActivation"),
                timeAuthority = data.requireText("timeAuthority"),
                currentMinuteOfDaySource = data.requireText("currentMinuteOfDaySource"),
                activeSlotAuthority = data.requireText("activeSlotAuthority"),
                startBoundary = data.requireText("startBoundary"),
                endBoundary = data.requireText("endBoundary"),
                endMinuteMaximum = data.requireInt(
                    "endMinuteMaximum",
                    limit.END_MINUTE_MINIMUM,
                    limit.MINUTES_PER_DAY
                ),
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
                stepPercent = data.requireDouble(
                    "stepPercent",
                    DeviceCoolingV1Contract.Limit.FAN_PERCENT_MINIMUM,
                    DeviceCoolingV1Contract.Limit.FAN_PERCENT_MAXIMUM
                )
            )
        }

        private fun parseTemperaturePolicy(data: JSONObject): DeviceCoolingV1TemperaturePolicy {
            data.requireExactKeys(TEMPERATURE_POLICY_KEYS, "cooling temperature policy")
            return DeviceCoolingV1TemperaturePolicy(
                minimumC = data.requireTemperature("minimumC"),
                maximumC = data.requireTemperature("maximumC"),
                stepC = data.requireDouble(
                    "stepC",
                    DeviceCoolingV1Contract.Limit.TEMPERATURE_MINIMUM_C,
                    DeviceCoolingV1Contract.Limit.TEMPERATURE_MAXIMUM_C
                ),
                defaultC = data.requireTemperature("defaultC")
            )
        }

    }

    private object TelemetryParser {
        fun parseSensor(
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
            val limit = DeviceCoolingV1Contract.Limit
            val temperature = data.requireNullableDouble(
                "temperatureC",
                limit.SENSOR_READING_MINIMUM_C,
                limit.SENSOR_READING_MAXIMUM_C
            )
            val humidity = if (data.has("humidityPercent")) {
                data.requireNullableDouble(
                    "humidityPercent",
                    limit.HUMIDITY_PERCENT_MINIMUM,
                    limit.HUMIDITY_PERCENT_MAXIMUM
                )
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

        fun parseFan(data: JSONObject): DeviceCoolingV1FanTelemetry {
            data.requireExactKeys(FAN_KEYS, "cooling fan telemetry")
            return DeviceCoolingV1FanTelemetry(
                fanKey = data.requireText("fanKey"),
                targetPercent = data.requireRuntimePercent("targetPercent"),
                outputPercent = data.requireNullableDouble(
                    "outputPercent",
                    DeviceCoolingV1Contract.Limit.FAN_PERCENT_MINIMUM,
                    DeviceCoolingV1Contract.Limit.FAN_PERCENT_MAXIMUM
                ),
                rpm = data.requireNullableDouble(
                    "rpm",
                    DeviceCoolingV1Contract.Limit.FAN_RPM_MINIMUM
                ),
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

        fun parsePower(data: JSONObject): DeviceCoolingV1PowerTelemetry {
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

        fun parseAlarm(data: JSONObject): DeviceCoolingV1Alarm {
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

        fun parseHealthSummary(data: JSONObject): DeviceCoolingV1HealthSummary {
            data.requireExactKeys(HEALTH_SUMMARY_KEYS, "cooling health summary")
            return DeviceCoolingV1HealthSummary(
                fanHealth = data.requireText("fanHealth"),
                sensorHealth = data.requireText("sensorHealth"),
                activeAlarmCount = data.requireInt(
                    "activeAlarmCount",
                    0,
                    DeviceCoolingV1Contract.Limit.ACTIVE_ALARM_COUNT_MAXIMUM
                ),
                highestAlarmSeverity = data.requireText("highestAlarmSeverity")
            )
        }

    }

    private object HistoryParser {
        fun parseSummary(data: JSONObject): DeviceCoolingV1HistorySummary {
            data.requireExactKeys(HISTORY_SUMMARY_KEYS, "cooling history summary")
            val limit = DeviceCoolingV1Contract.Limit
            return DeviceCoolingV1HistorySummary(
                minimumTemperatureC = data.requireNullableDouble(
                    "minTemperatureC",
                    limit.SENSOR_READING_MINIMUM_C,
                    limit.SENSOR_READING_MAXIMUM_C
                ),
                averageTemperatureC = data.requireNullableDouble(
                    "avgTemperatureC",
                    limit.SENSOR_READING_MINIMUM_C,
                    limit.SENSOR_READING_MAXIMUM_C
                ),
                maximumTemperatureC = data.requireNullableDouble(
                    "maxTemperatureC",
                    limit.SENSOR_READING_MINIMUM_C,
                    limit.SENSOR_READING_MAXIMUM_C
                )
            )
        }

        fun parseSample(data: JSONObject): DeviceCoolingV1HistorySample {
            data.requireExactKeys(HISTORY_SAMPLE_KEYS, "cooling history sample")
            return DeviceCoolingV1HistorySample(
                sampledAtMs = data.requireNonNegativeLong("sampledAtMs"),
                temperatureC = data.requireDouble(
                    "temperatureC",
                    DeviceCoolingV1Contract.Limit.SENSOR_READING_MINIMUM_C,
                    DeviceCoolingV1Contract.Limit.SENSOR_READING_MAXIMUM_C
                )
            )
        }

        fun parseDay(data: JSONObject): DeviceCoolingV1HistoryDay {
            data.requireExactKeys(HISTORY_DAY_KEYS, "cooling history day")
            val limit = DeviceCoolingV1Contract.Limit
            return DeviceCoolingV1HistoryDay(
                dayStartAtMs = data.requireNonNegativeLong("dayStartAtMs"),
                minimumTemperatureC = data.requireDouble(
                    "minTemperatureC",
                    limit.SENSOR_READING_MINIMUM_C,
                    limit.SENSOR_READING_MAXIMUM_C
                ),
                averageTemperatureC = data.requireDouble(
                    "avgTemperatureC",
                    limit.SENSOR_READING_MINIMUM_C,
                    limit.SENSOR_READING_MAXIMUM_C
                ),
                maximumTemperatureC = data.requireDouble(
                    "maxTemperatureC",
                    limit.SENSOR_READING_MINIMUM_C,
                    limit.SENSOR_READING_MAXIMUM_C
                )
            )
        }

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

    private val FAN_TOPOLOGY_KEYS = setOf(
        "fanKey", "outputKind", "rpmAvailable", "hardwareEditable"
    )
    private val SENSOR_TOPOLOGY_KEYS = setOf(
        "sensorKey", "role", "driver", "bus", "temperature", "relativeHumidity",
        "requiredForProduct", "requiredForControl"
    )
    private val AUTOMATIC_TEMPERATURE_POLICY_KEYS = setOf(
        "minimumC", "maximumC", "stepC", "minimumGapC", "hysteresisC"
    )
    private val STATUS_FAN_POLICY_KEYS = setOf("minimum", "maximum", "step")
    private val SILENT_MODE_POLICY_KEYS = setOf("supported", "maximumPercent")
    private val MANUAL_POLICY_KEYS = setOf(
        "persistent", "survivesRestart", "zeroPercentStopsImmediately",
        "clearedOnControlModeExit", "disconnectDoesNotStop", "allowedWhenWaterSensorFaulted"
    )
    private val STATUS_PROGRAM_POLICY_KEYS = setOf(
        "maximumSlotCount", "timeStepMinutes", "minimumDurationMinutes",
        "requiresTrustedDeviceClock", "programActivation", "startBoundary", "endBoundary",
        "endMinuteMaximum"
    )
    private val FAIL_SAFE_POLICY_KEYS = setOf("waterSensorFaultPercent")

    private const val SENSOR_ADDRESS_MINIMUM = 0
    private const val SENSOR_ADDRESS_MAXIMUM = 255
}

private fun org.json.JSONArray.textValues(label: String): List<String> = List(length()) { index ->
    (get(index) as? String)?.also { value ->
        require(value.isNotEmpty() && value.none(Char::isISOControl) && value == value.trim())
    } ?: error("$label[$index] must be a canonical string.")
}

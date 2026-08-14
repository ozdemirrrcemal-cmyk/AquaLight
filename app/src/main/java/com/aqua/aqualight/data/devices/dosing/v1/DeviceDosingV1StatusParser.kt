package com.aqua.aqualight.data.devices.dosing.v1

import org.json.JSONArray
import org.json.JSONObject

@Suppress("LargeClass", "LongMethod", "MagicNumber", "TooManyFunctions")
object DeviceDosingV1StatusParser {
    private val ENVELOPE_KEYS = setOf(
        "supported", "schema", "schemaVersion", "unit", "channelCount", "uptimeMs",
        "bootReady", "storageHealthy", "storageIssue"
    )
    private val GLOBAL_KEYS = ENVELOPE_KEYS + setOf(
        "scheduling", "channels", "runtime", "resources"
    )
    private val CHANNEL_KEYS = ENVELOPE_KEYS + "channel"
    private val PROGRESS_KEYS = ENVELOPE_KEYS + setOf(
        "channelKey", "revision", "programEnabled", "programMode", "progress", "occurrences"
    )

    fun parseGlobal(data: JSONObject): DeviceDosingV1GlobalStatus {
        data.requireDosingKeys(GLOBAL_KEYS, "global dosing status")
        val envelope = parseEnvelope(data)
        val channels = parseGlobalChannels(data.requireDosingArray("channels"))
        require(channels.size == envelope.channelCount) {
            "Global channel count differs from the firmware envelope."
        }
        require(channels.map(DeviceDosingV1GlobalChannel::channelKey).distinct().size == channels.size) {
            "Global dosing status contains duplicate channel keys."
        }
        return DeviceDosingV1GlobalStatus(
            envelope = envelope,
            scheduling = parseScheduling(data.requireDosingObject("scheduling")),
            channels = channels,
            runtime = parseRuntime(data.requireDosingObject("runtime")),
            resources = parseResources(data.requireDosingObject("resources"))
        )
    }

    fun parseChannel(data: JSONObject): DeviceDosingV1ChannelStatus {
        data.requireDosingKeys(CHANNEL_KEYS, "channel dosing status")
        val envelope = parseEnvelope(data)
        require(envelope.channelCount >= 1) {
            "A channel-scoped response must report at least one configured channel."
        }
        return DeviceDosingV1ChannelStatus(
            envelope = envelope,
            channel = parseChannelDetail(data.requireDosingObject("channel"))
        )
    }

    fun parseProgress(data: JSONObject): DeviceDosingV1ProgressStatus {
        data.requireDosingKeys(PROGRESS_KEYS, "dosing progress status")
        val occurrences = parseOccurrences(data.requireDosingArray("occurrences"))
        val progress = parseProgressSummary(data.requireDosingObject("progress"))
        require(occurrences.size <= DeviceDosingV1Contract.Limit.MAX_EVENTS_PER_CHANNEL) {
            "Progress exceeds the bounded firmware occurrence capacity."
        }
        require(progress.total == occurrences.size) {
            "Progress total differs from the firmware occurrence array size."
        }
        return DeviceDosingV1ProgressStatus(
            envelope = parseEnvelope(data),
            channelKey = data.requireDosingChannelKey("channelKey"),
            revision = data.requireDosingLong(
                "revision",
                minimum = 0L,
                maximum = DeviceDosingV1Contract.Limit.MAX_UNSIGNED_INT
            ),
            programEnabled = data.requireDosingBoolean("programEnabled"),
            programMode = dosingWireValue(data.requireDosingString("programMode")),
            progress = progress,
            occurrences = occurrences
        )
    }

    internal fun parseChannelDetail(data: JSONObject): DeviceDosingV1ChannelDetail {
        data.requireDosingKeys(CHANNEL_DETAIL_KEYS, "dosing channel detail")
        return DeviceDosingV1ChannelDetail(
            channelKey = data.requireDosingChannelKey("channelKey"),
            revision = data.requireDosingLong(
                "revision",
                minimum = 0L,
                maximum = DeviceDosingV1Contract.Limit.MAX_UNSIGNED_INT
            ),
            runtimeEnabled = data.requireDosingBoolean("runtimeEnabled"),
            runtimeReason = dosingWireValue(data.requireDosingString("runtimeReason")),
            program = parseNullableProgram(data.get("program")),
            usageToday = parseUsage(data.requireDosingObject("usageToday")),
            index = data.requireDosingInt("index", minimum = 0),
            defaultName = data.requireDosingString("defaultName"),
            displayName = data.requireDosingNullableString("displayName"),
            effectiveName = data.requireDosingString("effectiveName"),
            profileManaged = data.requireDosingBoolean("profileManaged"),
            deliveryAccountingCertain = data.requireDosingBoolean("deliveryAccountingCertain"),
            hardware = parseHardware(data.requireDosingObject("hardware")),
            calibration = parseCalibration(data.requireDosingObject("calibration")),
            reservoir = parseReservoir(data.requireDosingObject("reservoir")),
            activeRun = parseActiveRun(data.requireDosingObject("activeRun")),
            lastRuntimeEvent = parseRuntimeEvent(data.requireDosingObject("lastRuntimeEvent")),
            editable = parseEditable(data.requireDosingObject("editable"))
        )
    }

    internal fun parseRuntimeEvent(data: JSONObject): DeviceDosingV1RuntimeEventSnapshot {
        data.requireDosingKeys(RUNTIME_EVENT_KEYS, "dosing runtime event")
        return DeviceDosingV1RuntimeEventSnapshot(
            valid = data.requireDosingBoolean("valid"),
            sequence = data.requireDosingLong(
                "sequence",
                minimum = 0L,
                maximum = DeviceDosingV1Contract.Limit.MAX_UNSIGNED_INT
            ),
            occurredAtMillis = data.requireDosingLong(
                "occurredAtMs",
                minimum = 0L,
                maximum = DeviceDosingV1Contract.Limit.MAX_UNSIGNED_INT
            ),
            kind = dosingWireValue(data.requireDosingString("kind")),
            reason = dosingWireValue(data.requireDosingString("reason")),
            source = dosingWireValue(data.requireDosingString("source"))
        )
    }

    private fun parseEnvelope(data: JSONObject): DeviceDosingV1Envelope =
        DeviceDosingV1Envelope(
            supported = data.requireDosingBoolean("supported"),
            schema = data.requireDosingString("schema"),
            schemaVersion = data.requireDosingLong("schemaVersion", minimum = 1L),
            unit = data.requireDosingString("unit"),
            channelCount = data.requireDosingInt(
                "channelCount",
                minimum = 0,
                maximum = DeviceDosingV1Contract.Limit.MAX_EVENTS_PER_CHANNEL
            ),
            uptimeMillis = data.requireDosingLong(
                "uptimeMs",
                minimum = 0L,
                maximum = DeviceDosingV1Contract.Limit.MAX_UNSIGNED_INT
            ),
            bootReady = data.requireDosingBoolean("bootReady"),
            storageHealthy = data.requireDosingBoolean("storageHealthy"),
            storageIssue = data.requireDosingString("storageIssue", allowEmpty = true)
        ).also { envelope ->
            require(envelope.supported) { "Dosing response must report supported=true." }
            require(envelope.schema == DeviceDosingV1Contract.SCHEMA)
            require(envelope.schemaVersion == DeviceDosingV1Contract.SCHEMA_VERSION)
            require(envelope.unit == DeviceDosingV1Contract.UNIT)
            require(envelope.storageIssue.toByteArray(Charsets.UTF_8).size <= MAX_STORAGE_ISSUE_BYTES)
        }

    private fun parseScheduling(data: JSONObject): DeviceDosingV1SchedulingMetadata {
        data.requireDosingKeys(SCHEDULING_KEYS, "dosing scheduling metadata")
        val modes = parseStringValues(data.requireDosingArray("supportedModes"))
            .map(::dosingWireValue)
        val weekdayOrder = parseStringValues(data.requireDosingArray("weekdayOrder"))
        return DeviceDosingV1SchedulingMetadata(
            contract = data.requireDosingString("contract"),
            schemaVersion = data.requireDosingLong("schemaVersion", minimum = 1L),
            amountResolutionMilliliters = data.requireDosingDouble(
                "amountResolutionMl",
                minimum = 0.0
            ),
            maxEventsPerChannel = data.requireDosingInt(
                "maxEventsPerChannel",
                minimum = 1,
                maximum = DeviceDosingV1Contract.Limit.MAX_EVENTS_PER_CHANNEL
            ),
            maxCustomPeriodsPerChannel = data.requireDosingInt(
                "maxCustomPeriodsPerChannel",
                minimum = 1,
                maximum = DeviceDosingV1Contract.Limit.MAX_CUSTOM_PERIODS_PER_CHANNEL
            ),
            scheduledDispatchGraceMillis = data.requireDosingLong(
                "scheduledDispatchGraceMs",
                minimum = 0L
            ),
            missedDoseRecoveryProgramDay =
                data.requireDosingBoolean("missedDoseRecoveryProgramDay"),
            minimumPumpRunDurationMillis = data.requireDosingLong(
                "minPumpRunDurationMs",
                minimum = 1L
            ),
            maximumPumpRunDurationMillis = data.requireDosingLong(
                "maxPumpRunDurationMs",
                minimum = 1L
            ),
            maximumManualDoseMilliliters = data.requireDosingDouble(
                "maxManualDoseMl",
                minimum = 0.0
            ),
            supportsWeekdayRecurrence = data.requireDosingBoolean("supportsWeekdayRecurrence"),
            supportsMissedDoseRecovery = data.requireDosingBoolean("supportsMissedDoseRecovery"),
            supportsChannelReset = data.requireDosingBoolean("supportsChannelReset"),
            supportsDailyDeliveredUsage =
                data.requireDosingBoolean("supportsDailyDeliveredUsage"),
            supportedModes = modes,
            weekdayOrder = weekdayOrder,
            effectiveScheduledDose = parseEffectiveDose(
                data.requireDosingObject("effectiveScheduledDose")
            )
        ).also { metadata ->
            require(metadata.contract == DeviceDosingV1Contract.SCHEMA)
            require(metadata.schemaVersion == DeviceDosingV1Contract.SCHEMA_VERSION)
            require(
                metadata.amountResolutionMilliliters ==
                    DeviceDosingV1Contract.Limit.AMOUNT_RESOLUTION_ML
            )
            require(metadata.minimumPumpRunDurationMillis <= metadata.maximumPumpRunDurationMillis)
            require(metadata.weekdayOrder == WEEKDAY_ORDER)
            require(metadata.supportedModes.map(DeviceDosingV1WireValue::raw) == SUPPORTED_MODES)
        }
    }

    private fun parseEffectiveDose(data: JSONObject): DeviceDosingV1EffectiveScheduledDose {
        data.requireDosingKeys(EFFECTIVE_DOSE_KEYS, "effective scheduled dose")
        return DeviceDosingV1EffectiveScheduledDose(
            available = data.requireDosingBoolean("available"),
            minimumMilliliters = data.requireDosingNullableDouble("minDoseMl", minimum = 0.0),
            maximumMilliliters = data.requireDosingNullableDouble("maxDoseMl", minimum = 0.0)
        ).also { dose ->
            require(dose.available == (dose.minimumMilliliters != null))
            require(dose.available == (dose.maximumMilliliters != null))
            if (dose.available) {
                require(checkNotNull(dose.minimumMilliliters) <= checkNotNull(dose.maximumMilliliters))
            }
        }
    }

    private fun parseGlobalChannels(data: JSONArray): List<DeviceDosingV1GlobalChannel> =
        List(data.length()) { index -> parseGlobalChannel(data.requireDosingObject(index)) }

    private fun parseGlobalChannel(data: JSONObject): DeviceDosingV1GlobalChannel {
        data.requireDosingKeys(GLOBAL_CHANNEL_KEYS, "global dosing channel")
        return DeviceDosingV1GlobalChannel(
            channelKey = data.requireDosingChannelKey("channelKey"),
            effectiveName = data.requireDosingString("effectiveName"),
            revision = data.requireDosingLong(
                "revision",
                minimum = 0L,
                maximum = DeviceDosingV1Contract.Limit.MAX_UNSIGNED_INT
            ),
            runtimeEnabled = data.requireDosingBoolean("runtimeEnabled"),
            runtimeReason = dosingWireValue(data.requireDosingString("runtimeReason")),
            programEnabled = data.requireDosingBoolean("programEnabled"),
            programMode = dosingWireValue(data.requireDosingString("programMode")),
            deliveryAccountingCertain = data.requireDosingBoolean("deliveryAccountingCertain"),
            usageToday = parseUsage(data.requireDosingObject("usageToday")),
            reservoir = parseGlobalReservoir(data.requireDosingObject("reservoir")),
            active = data.requireDosingBoolean("active")
        )
    }

    private fun parseGlobalReservoir(data: JSONObject): DeviceDosingV1GlobalReservoir {
        data.requireDosingKeys(GLOBAL_RESERVOIR_KEYS, "global dosing reservoir")
        return DeviceDosingV1GlobalReservoir(
            trackingEnabled = data.requireDosingBoolean("trackingEnabled"),
            remainingMilliliters = data.requireDosingDouble("remainingMl", minimum = -1.0),
            accountingCertain = data.requireDosingBoolean("accountingCertain"),
            lowLevelActive = data.requireDosingBoolean("lowLevelActive")
        )
    }

    private fun parseRuntime(data: JSONObject): DeviceDosingV1RuntimeCapabilities {
        data.requireDosingKeys(RUNTIME_KEYS, "dosing runtime capabilities")
        return DeviceDosingV1RuntimeCapabilities(
            module = data.requireDosingString("module"),
            supportsProgramApply = data.requireDosingBoolean("supportsProgramApply"),
            supportsChannelConfig = data.requireDosingBoolean("supportsChannelConfig"),
            supportsChannelReset = data.requireDosingBoolean("supportsChannelReset"),
            supportsPrime = data.requireDosingBoolean("supportsPrime"),
            supportsManualDose = data.requireDosingBoolean("supportsManualDose"),
            supportsCalibrationWorkflow =
                data.requireDosingBoolean("supportsCalibrationWorkflow"),
            supportsReservoirRefill = data.requireDosingBoolean("supportsReservoirRefill"),
            supportsChannelScopedStatus =
                data.requireDosingBoolean("supportsChannelScopedStatus")
        ).also { runtime ->
            require(runtime.module == DeviceDosingV1Contract.MODULE)
        }
    }

    private fun parseResources(data: JSONObject): DeviceDosingV1Resources {
        data.requireDosingKeys(RESOURCE_KEYS, "dosing runtime resources")
        return DeviceDosingV1Resources(
            freeHeapBytes = data.requireDosingLong("freeHeapBytes", minimum = 0L),
            minimumFreeHeapBytes = data.requireDosingLong("minimumFreeHeapBytes", minimum = 0L),
            largestFreeBlockBytes = data.requireDosingLong("largestFreeBlockBytes", minimum = 0L),
            taskStackHighWaterBytes =
                data.requireDosingLong("taskStackHighWaterBytes", minimum = 0L),
            checkpointWritesThisBoot =
                data.requireDosingLong("checkpointWritesThisBoot", minimum = 0L),
            canonicalConfigBytes = data.requireDosingLong("canonicalConfigBytes", minimum = 0L),
            programServiceBytes = data.requireDosingLong("programServiceBytes", minimum = 0L),
            runtimeSnapshotBytes = data.requireDosingLong("runtimeSnapshotBytes", minimum = 0L),
            statusSnapshotBytes = data.requireDosingLong("statusSnapshotBytes", minimum = 0L)
        )
    }

    private fun parseUsage(data: JSONObject): DeviceDosingV1DailyUsage {
        data.requireDosingKeys(USAGE_KEYS, "daily dosing usage")
        return DeviceDosingV1DailyUsage(
            dateValid = data.requireDosingBoolean("dateValid"),
            localDate = data.requireDosingNullableString("localDate"),
            scheduledDeliveredMilliliters =
                data.requireDosingDouble("scheduledDeliveredMl", minimum = 0.0),
            manualDeliveredMilliliters =
                data.requireDosingDouble("manualDeliveredMl", minimum = 0.0),
            totalDeliveredMilliliters =
                data.requireDosingDouble("totalDeliveredMl", minimum = 0.0)
        ).also { usage ->
            require(usage.dateValid == (usage.localDate != null))
        }
    }

    private fun parseNullableProgram(value: Any): DeviceDosingV1ProgramSnapshot? {
        if (value === JSONObject.NULL) return null
        return parseProgram(value as? JSONObject ?: error("program must be an object or null."))
    }

    private fun parseProgram(data: JSONObject): DeviceDosingV1ProgramSnapshot {
        data.requireDosingKeys(PROGRAM_KEYS, "dosing program")
        val mode = dosingWireValue(data.requireDosingString("mode"))
        return DeviceDosingV1ProgramSnapshot(
            enabled = data.requireDosingBoolean("enabled"),
            weekdays = parseWeekdays(data.requireDosingArray("weekdays")),
            mode = mode,
            missedDoseRecoveryEnabled = data.requireDosingBoolean("missedDoseRecoveryEnabled"),
            config = parseProgramConfig(mode, data.requireDosingObject("config"))
        )
    }

    private fun parseProgramConfig(
        mode: DeviceDosingV1WireValue,
        data: JSONObject
    ): DeviceDosingV1ProgramSnapshotConfig = when (mode.raw) {
        "single" -> {
            data.requireDosingKeys(DAILY_CONFIG_KEYS, "single dosing config")
            DeviceDosingV1ProgramSnapshotConfig.Single(
                dailyDoseMilliliters = data.requireDosingDouble("dailyDoseMl", minimum = 0.0),
                startTimeMillis = data.requireDosingTime("startTimeMs")
            )
        }
        "hourly24" -> {
            data.requireDosingKeys(DAILY_CONFIG_KEYS, "hourly24 dosing config")
            DeviceDosingV1ProgramSnapshotConfig.Hourly24(
                dailyDoseMilliliters = data.requireDosingDouble("dailyDoseMl", minimum = 0.0),
                startTimeMillis = data.requireDosingTime("startTimeMs")
            )
        }
        "customPeriods" -> {
            data.requireDosingKeys(CUSTOM_CONFIG_KEYS, "custom-period dosing config")
            val periods = data.requireDosingArray("periods")
            require(periods.length() in 1..DeviceDosingV1Contract.Limit.MAX_CUSTOM_PERIODS_PER_CHANNEL)
            DeviceDosingV1ProgramSnapshotConfig.CustomPeriods(
                dailyDoseMilliliters = data.requireDosingDouble("dailyDoseMl", minimum = 0.0),
                periods = List(periods.length()) { index ->
                    parseCustomPeriod(periods.requireDosingObject(index))
                }
            )
        }
        "timer" -> {
            data.requireDosingKeys(TIMER_CONFIG_KEYS, "timer dosing config")
            val events = data.requireDosingArray("events")
            require(events.length() in 1..DeviceDosingV1Contract.Limit.MAX_EVENTS_PER_CHANNEL)
            DeviceDosingV1ProgramSnapshotConfig.Timer(
                events = List(events.length()) { index ->
                    parseTimerEvent(events.requireDosingObject(index))
                }
            )
        }
        else -> DeviceDosingV1ProgramSnapshotConfig.Unknown(mode, data.toString())
    }

    private fun parseCustomPeriod(data: JSONObject): DeviceDosingV1ProgramSnapshotConfig.CustomPeriod {
        data.requireDosingKeys(CUSTOM_PERIOD_KEYS, "custom dosing period")
        return DeviceDosingV1ProgramSnapshotConfig.CustomPeriod(
            startTimeMillis = data.requireDosingTime("startTimeMs"),
            endTimeMillis = data.requireDosingTime("endTimeMs"),
            doseCount = data.requireDosingInt(
                "doseCount",
                minimum = 1,
                maximum = DeviceDosingV1Contract.Limit.MAX_EVENTS_PER_CHANNEL
            )
        )
    }

    private fun parseTimerEvent(data: JSONObject): DeviceDosingV1ProgramSnapshotConfig.TimerEvent {
        data.requireDosingKeys(TIMER_EVENT_KEYS, "dosing timer event")
        return DeviceDosingV1ProgramSnapshotConfig.TimerEvent(
            timeMillis = data.requireDosingTime("timeMs"),
            amountMilliliters = data.requireDosingDouble("amountMl", minimum = 0.0)
        )
    }

    private fun parseWeekdays(data: JSONArray): List<Boolean> {
        require(data.length() == DeviceDosingV1Contract.Limit.WEEKDAY_COUNT)
        return List(data.length(), data::requireDosingBoolean)
    }

    private fun parseHardware(data: JSONObject): DeviceDosingV1Hardware {
        data.requireDosingKeys(HARDWARE_KEYS, "dosing hardware")
        return DeviceDosingV1Hardware(
            channelType = data.requireDosingString("channelType"),
            gpio = data.requireDosingInt("gpio"),
            ledcChannel = data.requireDosingInt("ledcChannel"),
            resolutionBits = data.requireDosingInt("resolutionBits", minimum = 1),
            frequencyHertz = data.requireDosingInt("frequencyHz", minimum = 1)
        )
    }

    private fun parseCalibration(data: JSONObject): DeviceDosingV1Calibration {
        data.requireDosingKeys(CALIBRATION_KEYS, "dosing calibration")
        return DeviceDosingV1Calibration(
            confirmed = data.requireDosingBoolean("confirmed"),
            doseMillisPerMilliliter = data.requireDosingDouble("doseMsPerMl", minimum = 0.0),
            lastCalibratedAt = data.requireDosingLong("lastCalibratedAt", minimum = 0L),
            state = dosingWireValue(data.requireDosingString("state")),
            durationMillis = data.requireDosingLong("durationMs", minimum = 0L),
            measuredMilliliters = data.requireDosingDouble("measuredMl", minimum = 0.0),
            pendingDoseMillisPerMilliliter =
                data.requireDosingDouble("pendingDoseMsPerMl", minimum = 0.0),
            verificationDoseStarted = data.requireDosingBoolean("verificationDoseStarted"),
            verificationDoseComplete = data.requireDosingBoolean("verificationDoseComplete")
        )
    }

    private fun parseReservoir(data: JSONObject): DeviceDosingV1Reservoir {
        data.requireDosingKeys(RESERVOIR_KEYS, "dosing reservoir")
        return DeviceDosingV1Reservoir(
            trackingEnabled = data.requireDosingBoolean("trackingEnabled"),
            capacityMilliliters = data.requireDosingDouble("capacityMl", minimum = -1.0),
            remainingMilliliters = data.requireDosingDouble("remainingMl", minimum = -1.0),
            accountingCertain = data.requireDosingBoolean("accountingCertain"),
            lowLevelActive = data.requireDosingBoolean("lowLevelActive"),
            remainingPercent = data.requireDosingDouble("remainingPercent", minimum = -1.0)
        )
    }

    private fun parseActiveRun(data: JSONObject): DeviceDosingV1ActiveRun {
        data.requireDosingKeys(ACTIVE_RUN_KEYS, "dosing active run")
        return DeviceDosingV1ActiveRun(
            active = data.requireDosingBoolean("active"),
            source = dosingWireValue(data.requireDosingString("source")),
            targetAmountMilliliters =
                data.requireDosingDouble("targetAmountMl", minimum = 0.0),
            remainingMillis = data.requireDosingLong("remainingMs", minimum = 0L)
        )
    }

    private fun parseEditable(data: JSONObject): DeviceDosingV1Editable {
        data.requireDosingKeys(EDITABLE_KEYS, "dosing editable flags")
        return DeviceDosingV1Editable(
            hardware = data.requireDosingBoolean("hardware"),
            displayName = data.requireDosingBoolean("displayName"),
            dosingCalibration = data.requireDosingBoolean("dosingCalibration"),
            reservoir = data.requireDosingBoolean("reservoir")
        ).also { editable ->
            require(!editable.hardware)
        }
    }

    private fun parseProgressSummary(data: JSONObject): DeviceDosingV1ProgressSummary {
        data.requireDosingKeys(PROGRESS_SUMMARY_KEYS, "dosing progress summary")
        return DeviceDosingV1ProgressSummary(
            scheduleState = dosingWireValue(data.requireDosingString("scheduleState")),
            total = data.requireDosingProgressCount("total"),
            completed = data.requireDosingProgressCount("completed"),
            resolved = data.requireDosingProgressCount("resolved"),
            pending = data.requireDosingProgressCount("pending"),
            running = data.requireDosingProgressCount("running"),
            skipped = data.requireDosingProgressCount("skipped"),
            uncertain = data.requireDosingProgressCount("uncertain"),
            totalAmountMilliliters = data.requireDosingDouble("totalAmountMl", minimum = 0.0),
            completedAmountMilliliters =
                data.requireDosingDouble("completedAmountMl", minimum = 0.0),
            remainingAmountMilliliters =
                data.requireDosingDouble("remainingAmountMl", minimum = 0.0),
            completionPercent = data.requireDosingDouble(
                "completionPercent",
                minimum = 0.0,
                maximum = 100.0
            ),
            executionCurrent = data.requireDosingBoolean("executionCurrent"),
            programDayDate = data.requireDosingNullableString("programDayDate")
        )
    }

    private fun parseOccurrences(data: JSONArray): List<DeviceDosingV1Occurrence> =
        List(data.length()) { index ->
            val item = data.requireDosingObject(index)
            item.requireDosingKeys(OCCURRENCE_KEYS, "dosing occurrence")
            DeviceDosingV1Occurrence(
                index = item.requireDosingInt(
                    "index",
                    minimum = 0,
                    maximum = DeviceDosingV1Contract.Limit.MAX_EVENTS_PER_CHANNEL - 1
                ),
                eventId = item.requireDosingLong(
                    "eventId",
                    minimum = 0L,
                    maximum = DeviceDosingV1Contract.Limit.MAX_UNSIGNED_INT
                ),
                programDayOffset = item.requireDosingInt("programDayOffset", minimum = 0),
                timeMillis = item.requireDosingTime("timeMs"),
                amountMilliliters = item.requireDosingDouble("amountMl", minimum = 0.0),
                status = dosingWireValue(item.requireDosingString("status"))
            )
        }.also { occurrences ->
            require(occurrences.map(DeviceDosingV1Occurrence::index) == occurrences.indices.toList())
            require(occurrences.map(DeviceDosingV1Occurrence::eventId).distinct().size == occurrences.size)
        }

    private fun JSONObject.requireDosingTime(key: String): Long =
        requireDosingLong(
            key,
            minimum = 0L,
            maximum = DeviceDosingV1Contract.Limit.MILLIS_PER_DAY - 1L
        )

    private fun JSONObject.requireDosingProgressCount(key: String): Int =
        requireDosingInt(
            key,
            minimum = 0,
            maximum = DeviceDosingV1Contract.Limit.MAX_EVENTS_PER_CHANNEL
        )

    private fun parseStringValues(data: JSONArray): List<String> =
        List(data.length(), data::requireDosingString)

    private const val MAX_STORAGE_ISSUE_BYTES = 127

    private val WEEKDAY_ORDER = listOf(
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
    )
    private val SUPPORTED_MODES = listOf("single", "hourly24", "customPeriods", "timer")
    private val SCHEDULING_KEYS = setOf(
        "contract", "schemaVersion", "amountResolutionMl", "maxEventsPerChannel",
        "maxCustomPeriodsPerChannel", "scheduledDispatchGraceMs",
        "missedDoseRecoveryProgramDay", "minPumpRunDurationMs", "maxPumpRunDurationMs",
        "maxManualDoseMl", "supportsWeekdayRecurrence", "supportsMissedDoseRecovery",
        "supportsChannelReset", "supportsDailyDeliveredUsage", "supportedModes",
        "weekdayOrder", "effectiveScheduledDose"
    )
    private val EFFECTIVE_DOSE_KEYS = setOf("available", "minDoseMl", "maxDoseMl")
    private val GLOBAL_CHANNEL_KEYS = setOf(
        "channelKey", "effectiveName", "revision", "runtimeEnabled", "runtimeReason",
        "programEnabled", "programMode", "deliveryAccountingCertain", "usageToday",
        "reservoir", "active"
    )
    private val GLOBAL_RESERVOIR_KEYS = setOf(
        "trackingEnabled", "remainingMl", "accountingCertain", "lowLevelActive"
    )
    private val RUNTIME_KEYS = setOf(
        "module", "supportsProgramApply", "supportsChannelConfig", "supportsChannelReset",
        "supportsPrime", "supportsManualDose", "supportsCalibrationWorkflow",
        "supportsReservoirRefill", "supportsChannelScopedStatus"
    )
    private val RESOURCE_KEYS = setOf(
        "freeHeapBytes", "minimumFreeHeapBytes", "largestFreeBlockBytes",
        "taskStackHighWaterBytes", "checkpointWritesThisBoot", "canonicalConfigBytes",
        "programServiceBytes", "runtimeSnapshotBytes", "statusSnapshotBytes"
    )
    private val USAGE_KEYS = setOf(
        "dateValid", "localDate", "scheduledDeliveredMl", "manualDeliveredMl",
        "totalDeliveredMl"
    )
    private val CHANNEL_DETAIL_KEYS = setOf(
        "channelKey", "revision", "runtimeEnabled", "runtimeReason", "program",
        "usageToday", "index", "defaultName", "displayName", "effectiveName",
        "profileManaged", "deliveryAccountingCertain", "hardware", "calibration",
        "reservoir", "activeRun", "lastRuntimeEvent", "editable"
    )
    private val PROGRAM_KEYS = setOf(
        "enabled", "weekdays", "mode", "missedDoseRecoveryEnabled", "config"
    )
    private val DAILY_CONFIG_KEYS = setOf("dailyDoseMl", "startTimeMs")
    private val CUSTOM_CONFIG_KEYS = setOf("dailyDoseMl", "periods")
    private val CUSTOM_PERIOD_KEYS = setOf("startTimeMs", "endTimeMs", "doseCount")
    private val TIMER_CONFIG_KEYS = setOf("events")
    private val TIMER_EVENT_KEYS = setOf("timeMs", "amountMl")
    private val HARDWARE_KEYS = setOf(
        "channelType", "gpio", "ledcChannel", "resolutionBits", "frequencyHz"
    )
    private val CALIBRATION_KEYS = setOf(
        "confirmed", "doseMsPerMl", "lastCalibratedAt", "state", "durationMs",
        "measuredMl", "pendingDoseMsPerMl", "verificationDoseStarted",
        "verificationDoseComplete"
    )
    private val RESERVOIR_KEYS = setOf(
        "trackingEnabled", "capacityMl", "remainingMl", "accountingCertain",
        "lowLevelActive", "remainingPercent"
    )
    private val ACTIVE_RUN_KEYS = setOf("active", "source", "targetAmountMl", "remainingMs")
    private val RUNTIME_EVENT_KEYS = setOf(
        "valid", "sequence", "occurredAtMs", "kind", "reason", "source"
    )
    private val EDITABLE_KEYS = setOf(
        "hardware", "displayName", "dosingCalibration", "reservoir"
    )
    private val PROGRESS_SUMMARY_KEYS = setOf(
        "scheduleState", "total", "completed", "resolved", "pending", "running",
        "skipped", "uncertain", "totalAmountMl", "completedAmountMl",
        "remainingAmountMl", "completionPercent", "executionCurrent", "programDayDate"
    )
    private val OCCURRENCE_KEYS = setOf(
        "index", "eventId", "programDayOffset", "timeMs", "amountMl", "status"
    )
}

package com.aqua.aqualight.data.devices.runtime.modules.dosing.parsers

import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.normalizeDosingChannelKey
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingActiveRunStatus
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationState
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationStatus
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingChannelDetail
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingChannelEditable
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingChannelHardware
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCustomPeriod
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCustomPeriodsProgramConfig
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingDistributedProgramConfig
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingEffectiveScheduledDose
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingGlobalChannelSummary
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingGlobalRuntimeCapabilities
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingProgram
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingProgramMode
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingReservoirStatus
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingReservoirSummary
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingResourceMetrics
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingRunSource
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingRuntimeEventStatus
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingRuntimeReason
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingSchedulingMetadata
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingStatusEnvelope
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingTimerEvent
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingTimerProgramConfig
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingUsageToday
import org.json.JSONArray
import org.json.JSONObject

internal object DeviceDosingComponentParsers {
    fun parseEnvelope(data: JSONObject): DeviceDosingStatusEnvelope = DeviceDosingStatusEnvelope(
        supported = data.requireDosingBoolean("supported").also { require(it) },
        schema = data.requireDosingText("schema").also {
            require(it == DeviceDosingRuntimeContract.SCHEMA)
        },
        schemaVersion = data.requireDosingInt("schemaVersion").also {
            require(it == DeviceDosingRuntimeContract.SCHEMA_VERSION)
        },
        unit = data.requireDosingText("unit").also {
            require(it == DeviceDosingRuntimeContract.UNIT_ML)
        },
        channelCount = data.requireDosingInt(
            "channelCount",
            minimum = 1,
            maximum = DeviceDosingRuntimeContract.Limit.MAX_CHANNELS
        ),
        uptimeMs = data.requireDosingLong(
            "uptimeMs",
            minimum = 0L,
            maximum = DeviceDosingRuntimeContract.Limit.MAX_UINT32
        ),
        bootReady = data.requireDosingBoolean("bootReady"),
        storageHealthy = data.requireDosingBoolean("storageHealthy"),
        storageIssue = data.requireDosingText("storageIssue", allowEmpty = true)
    )

    fun parseScheduling(data: JSONObject): DeviceDosingSchedulingMetadata {
        data.requireDosingKeys(SCHEDULING_KEYS, "Dosing scheduling metadata")
        val effective = data.requireDosingObject("effectiveScheduledDose")
        effective.requireDosingKeys(EFFECTIVE_DOSE_KEYS, "Dosing effective scheduled dose")
        val available = effective.requireDosingBoolean("available")
        val min = nullableDouble(effective, "minDoseMl")
        val max = nullableDouble(effective, "maxDoseMl")
        if (available) {
            require(min != null && max != null && min > 0.0 && max >= min)
        } else {
            require(min == null && max == null)
        }
        val modes = parseTextArray(data.requireDosingArray("supportedModes"))
            .map(DeviceDosingProgramMode::fromWire)
        require(modes.toSet() == DeviceDosingProgramMode.entries.toSet())
        val weekdays = parseTextArray(data.requireDosingArray("weekdayOrder"))
        require(weekdays == WEEKDAY_ORDER)
        return DeviceDosingSchedulingMetadata(
            contract = data.requireDosingText("contract").also {
                require(it == DeviceDosingRuntimeContract.SCHEMA)
            },
            schemaVersion = data.requireDosingInt("schemaVersion").also {
                require(it == DeviceDosingRuntimeContract.SCHEMA_VERSION)
            },
            amountResolutionMl = data.requireDosingDouble("amountResolutionMl", minimum = 0.0),
            maxEventsPerChannel = data.requireDosingInt("maxEventsPerChannel", minimum = 1),
            maxCustomPeriodsPerChannel = data.requireDosingInt(
                "maxCustomPeriodsPerChannel",
                minimum = 1
            ),
            scheduledDispatchGraceMs = data.requireDosingLong(
                "scheduledDispatchGraceMs",
                minimum = 0L
            ),
            missedDoseRecoveryWindowMs = data.requireDosingLong(
                "missedDoseRecoveryWindowMs",
                minimum = 0L
            ),
            minPumpRunDurationMs = data.requireDosingLong("minPumpRunDurationMs", minimum = 1L),
            maxPumpRunDurationMs = data.requireDosingLong("maxPumpRunDurationMs", minimum = 1L),
            maxManualDoseMl = data.requireDosingDouble("maxManualDoseMl", minimum = 0.0),
            supportsWeekdayRecurrence = data.requireDosingBoolean("supportsWeekdayRecurrence"),
            supportsMissedDoseRecovery = data.requireDosingBoolean("supportsMissedDoseRecovery"),
            supportsChannelReset = data.requireDosingBoolean("supportsChannelReset"),
            supportsDailyDeliveredUsage = data.requireDosingBoolean("supportsDailyDeliveredUsage"),
            supportedModes = modes,
            weekdayOrder = weekdays,
            effectiveScheduledDose = DeviceDosingEffectiveScheduledDose(available, min, max)
        ).also { metadata ->
            require(metadata.minPumpRunDurationMs <= metadata.maxPumpRunDurationMs)
        }
    }

    fun parseProgram(data: JSONObject?): DeviceDosingProgram? {
        if (data == null) return null
        data.requireDosingKeys(PROGRAM_KEYS, "Dosing program")
        val mode = DeviceDosingProgramMode.fromWire(data.requireDosingText("mode"))
        val config = data.requireDosingObject("config")
        return DeviceDosingProgram(
            enabled = data.requireDosingBoolean("enabled"),
            weekdays = parseBooleanArray(
                data.requireDosingArray("weekdays"),
                DeviceDosingRuntimeContract.Limit.WEEKDAY_COUNT
            ),
            mode = mode,
            missedDoseRecoveryEnabled = data.requireDosingBoolean("missedDoseRecoveryEnabled"),
            config = when (mode) {
                DeviceDosingProgramMode.SINGLE,
                DeviceDosingProgramMode.HOURLY_24 -> parseDistributedConfig(config)
                DeviceDosingProgramMode.CUSTOM_PERIODS -> parseCustomConfig(config)
                DeviceDosingProgramMode.TIMER -> parseTimerConfig(config)
            }
        )
    }

    fun parseUsageToday(data: JSONObject): DeviceDosingUsageToday {
        data.requireDosingKeys(USAGE_KEYS, "Dosing usageToday")
        val dateValid = data.requireDosingBoolean("dateValid")
        val localDate = nullableText(data, "localDate")
        require(dateValid == (localDate != null))
        if (localDate != null) require(ISO_LOCAL_DATE.matches(localDate))
        val scheduled = data.requireDosingDouble("scheduledDeliveredMl", minimum = 0.0)
        val manual = data.requireDosingDouble("manualDeliveredMl", minimum = 0.0)
        val total = data.requireDosingDouble("totalDeliveredMl", minimum = 0.0)
        require(kotlin.math.abs(total - (scheduled + manual)) <= 0.001_001)
        return DeviceDosingUsageToday(dateValid, localDate, scheduled, manual, total)
    }

    fun parseGlobalSummary(data: JSONObject): DeviceDosingGlobalChannelSummary {
        data.requireDosingKeys(GLOBAL_CHANNEL_KEYS, "Dosing global channel summary")
        val modeText = data.requireDosingText("programMode")
        return DeviceDosingGlobalChannelSummary(
            channelKey = normalizeDosingChannelKey(data.requireDosingText("channelKey")),
            effectiveName = data.requireDosingText("effectiveName"),
            revision = data.requireDosingLong(
                "revision",
                minimum = 0L,
                maximum = DeviceDosingRuntimeContract.Limit.MAX_UINT32
            ),
            runtimeEnabled = data.requireDosingBoolean("runtimeEnabled"),
            runtimeReason = DeviceDosingRuntimeReason.fromWire(data.requireDosingText("runtimeReason")),
            programEnabled = data.requireDosingBoolean("programEnabled"),
            programMode = if (modeText == DeviceDosingRuntimeContract.Literal.PROGRAM_NONE) {
                null
            } else {
                DeviceDosingProgramMode.fromWire(modeText)
            },
            deliveryAccountingCertain = data.requireDosingBoolean("deliveryAccountingCertain"),
            usageToday = parseUsageToday(data.requireDosingObject("usageToday")),
            reservoir = parseReservoirSummary(data.requireDosingObject("reservoir")),
            active = data.requireDosingBoolean("active")
        )
    }

    fun parseRuntimeCapabilities(data: JSONObject): DeviceDosingGlobalRuntimeCapabilities {
        data.requireDosingKeys(RUNTIME_KEYS, "Dosing runtime capabilities")
        return DeviceDosingGlobalRuntimeCapabilities(
            module = data.requireDosingText("module").also {
                require(it == DeviceDosingRuntimeContract.MODULE)
            },
            supportsProgramApply = data.requireDosingBoolean("supportsProgramApply"),
            supportsChannelConfig = data.requireDosingBoolean("supportsChannelConfig"),
            supportsChannelReset = data.requireDosingBoolean("supportsChannelReset"),
            supportsPrime = data.requireDosingBoolean("supportsPrime"),
            supportsManualDose = data.requireDosingBoolean("supportsManualDose"),
            supportsCalibrationWorkflow = data.requireDosingBoolean("supportsCalibrationWorkflow"),
            supportsReservoirRefill = data.requireDosingBoolean("supportsReservoirRefill"),
            supportsChannelScopedStatus = data.requireDosingBoolean("supportsChannelScopedStatus"),
            displayNameEditable = data.requireDosingBoolean("displayNameEditable")
        )
    }

    fun parseResources(data: JSONObject): DeviceDosingResourceMetrics {
        data.requireDosingKeys(RESOURCE_KEYS, "Dosing runtime resources")
        fun value(key: String) = data.requireDosingLong(key, minimum = 0L)
        return DeviceDosingResourceMetrics(
            freeHeapBytes = value("freeHeapBytes"),
            minimumFreeHeapBytes = value("minimumFreeHeapBytes"),
            largestFreeBlockBytes = value("largestFreeBlockBytes"),
            taskStackHighWaterBytes = value("taskStackHighWaterBytes"),
            checkpointWritesThisBoot = value("checkpointWritesThisBoot"),
            canonicalConfigBytes = value("canonicalConfigBytes"),
            programServiceBytes = value("programServiceBytes"),
            runtimeSnapshotBytes = value("runtimeSnapshotBytes"),
            statusSnapshotBytes = value("statusSnapshotBytes")
        )
    }

    fun parseChannelDetail(data: JSONObject): DeviceDosingChannelDetail {
        data.requireDosingKeys(CHANNEL_DETAIL_KEYS, "Dosing channel detail")
        val program = if (data.isNull("program")) null else parseProgram(data.requireDosingObject("program"))
        return DeviceDosingChannelDetail(
            channelKey = normalizeDosingChannelKey(data.requireDosingText("channelKey")),
            revision = data.requireDosingLong(
                "revision",
                minimum = 0L,
                maximum = DeviceDosingRuntimeContract.Limit.MAX_UINT32
            ),
            runtimeEnabled = data.requireDosingBoolean("runtimeEnabled"),
            runtimeReason = DeviceDosingRuntimeReason.fromWire(data.requireDosingText("runtimeReason")),
            program = program,
            usageToday = parseUsageToday(data.requireDosingObject("usageToday")),
            index = data.requireDosingInt("index", minimum = 0),
            defaultName = data.requireDosingText("defaultName"),
            displayName = data.requireDosingText("displayName", allowEmpty = true),
            effectiveName = data.requireDosingText("effectiveName"),
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

    fun parseRuntimeEvent(data: JSONObject): DeviceDosingRuntimeEventStatus {
        data.requireDosingKeys(RUNTIME_EVENT_KEYS, "Dosing runtime event")
        return DeviceDosingRuntimeEventStatus(
            valid = data.requireDosingBoolean("valid"),
            sequence = data.requireDosingLong("sequence", minimum = 0L),
            occurredAtMs = data.requireDosingLong(
                "occurredAtMs",
                minimum = 0L,
                maximum = DeviceDosingRuntimeContract.Limit.MAX_UINT32
            ),
            kind = data.requireDosingText("kind"),
            reason = data.requireDosingText("reason"),
            source = DeviceDosingRunSource.fromWire(data.requireDosingText("source"))
        )
    }

    private fun parseDistributedConfig(data: JSONObject): DeviceDosingDistributedProgramConfig {
        data.requireDosingKeys(DISTRIBUTED_CONFIG_KEYS, "Dosing distributed program config")
        return DeviceDosingDistributedProgramConfig(
            dailyDoseMl = positiveAmount(data, "dailyDoseMl"),
            startTimeMs = localDayTime(data, "startTimeMs")
        )
    }

    private fun parseCustomConfig(data: JSONObject): DeviceDosingCustomPeriodsProgramConfig {
        data.requireDosingKeys(CUSTOM_CONFIG_KEYS, "Dosing customPeriods config")
        val periods = data.requireDosingArray("periods").objects().map { period ->
            period.requireDosingKeys(CUSTOM_PERIOD_KEYS, "Dosing custom period")
            DeviceDosingCustomPeriod(
                startTimeMs = localDayTime(period, "startTimeMs"),
                endTimeMs = localDayTime(period, "endTimeMs"),
                doseCount = period.requireDosingInt("doseCount", minimum = 1)
            ).also { require(it.startTimeMs < it.endTimeMs) }
        }
        require(periods.isNotEmpty())
        return DeviceDosingCustomPeriodsProgramConfig(
            dailyDoseMl = positiveAmount(data, "dailyDoseMl"),
            periods = periods
        )
    }

    private fun parseTimerConfig(data: JSONObject): DeviceDosingTimerProgramConfig {
        data.requireDosingKeys(TIMER_CONFIG_KEYS, "Dosing timer program config")
        val events = data.requireDosingArray("events").objects().map { event ->
            event.requireDosingKeys(TIMER_EVENT_KEYS, "Dosing timer event")
            DeviceDosingTimerEvent(
                timeMs = localDayTime(event, "timeMs"),
                amountMl = positiveAmount(event, "amountMl")
            )
        }
        require(events.isNotEmpty())
        return DeviceDosingTimerProgramConfig(events)
    }

    private fun parseReservoirSummary(data: JSONObject): DeviceDosingReservoirSummary {
        data.requireDosingKeys(RESERVOIR_SUMMARY_KEYS, "Dosing reservoir summary")
        return DeviceDosingReservoirSummary(
            trackingEnabled = data.requireDosingBoolean("trackingEnabled"),
            remainingMl = data.requireDosingDouble("remainingMl", minimum = -1.0),
            accountingCertain = data.requireDosingBoolean("accountingCertain")
        )
    }

    private fun parseHardware(data: JSONObject): DeviceDosingChannelHardware {
        data.requireDosingKeys(HARDWARE_KEYS, "Dosing channel hardware")
        return DeviceDosingChannelHardware(
            channelType = data.requireDosingText("channelType"),
            gpio = data.requireDosingInt("gpio", minimum = 0),
            ledcChannel = data.requireDosingInt("ledcChannel", minimum = 0),
            resolutionBits = data.requireDosingInt("resolutionBits", minimum = 1),
            frequencyHz = data.requireDosingInt("frequencyHz", minimum = 1)
        )
    }

    private fun parseCalibration(data: JSONObject): DeviceDosingCalibrationStatus {
        data.requireDosingKeys(CALIBRATION_KEYS, "Dosing calibration")
        return DeviceDosingCalibrationStatus(
            confirmed = data.requireDosingBoolean("confirmed"),
            doseMsPerMl = data.requireDosingLong("doseMsPerMl", minimum = 0L),
            lastCalibratedAt = data.requireDosingLong("lastCalibratedAt", minimum = 0L),
            state = DeviceDosingCalibrationState.fromWire(data.requireDosingText("state")),
            durationMs = data.requireDosingLong("durationMs", minimum = 0L),
            measuredMl = data.requireDosingDouble("measuredMl", minimum = 0.0),
            pendingDoseMsPerMl = data.requireDosingLong("pendingDoseMsPerMl", minimum = 0L),
            verificationDoseStarted = data.requireDosingBoolean("verificationDoseStarted"),
            verificationDoseComplete = data.requireDosingBoolean("verificationDoseComplete")
        )
    }

    private fun parseReservoir(data: JSONObject): DeviceDosingReservoirStatus {
        data.requireDosingKeys(RESERVOIR_KEYS, "Dosing reservoir")
        return DeviceDosingReservoirStatus(
            trackingEnabled = data.requireDosingBoolean("trackingEnabled"),
            capacityMl = data.requireDosingDouble("capacityMl", minimum = -1.0),
            remainingMl = data.requireDosingDouble("remainingMl", minimum = -1.0),
            accountingCertain = data.requireDosingBoolean("accountingCertain"),
            remainingPercent = data.requireDosingDouble("remainingPercent", minimum = -1.0, maximum = 100.0)
        )
    }

    private fun parseActiveRun(data: JSONObject): DeviceDosingActiveRunStatus {
        data.requireDosingKeys(ACTIVE_RUN_KEYS, "Dosing active run")
        val active = data.requireDosingBoolean("active")
        val source = DeviceDosingRunSource.fromWire(data.requireDosingText("source"))
        require(active || source == DeviceDosingRunSource.NONE)
        return DeviceDosingActiveRunStatus(
            active = active,
            source = source,
            targetAmountMl = data.requireDosingDouble("targetAmountMl", minimum = 0.0),
            remainingMs = data.requireDosingLong("remainingMs", minimum = 0L)
        )
    }

    private fun parseEditable(data: JSONObject): DeviceDosingChannelEditable {
        data.requireDosingKeys(EDITABLE_KEYS, "Dosing editable flags")
        return DeviceDosingChannelEditable(
            hardware = data.requireDosingBoolean("hardware"),
            displayName = data.requireDosingBoolean("displayName"),
            dosingCalibration = data.requireDosingBoolean("dosingCalibration"),
            reservoir = data.requireDosingBoolean("reservoir")
        )
    }

    private fun positiveAmount(data: JSONObject, key: String): Double =
        data.requireDosingDouble(key, minimum = 0.0).also { require(it > 0.0) }

    private fun localDayTime(data: JSONObject, key: String): Long = data.requireDosingLong(
        key,
        minimum = 0L,
        maximum = DeviceDosingRuntimeContract.Limit.LAST_MILLISECOND_OF_DAY
    )

    private fun nullableDouble(data: JSONObject, key: String): Double? =
        if (data.isNull(key)) null else data.requireDosingDouble(key)

    private fun nullableText(data: JSONObject, key: String): String? =
        if (data.isNull(key)) null else data.requireDosingText(key)

    private fun parseBooleanArray(array: JSONArray, size: Int): List<Boolean> {
        require(array.length() == size)
        return List(size) { index ->
            array.get(index) as? Boolean ?: error("Dosing boolean array contains non-boolean value.")
        }
    }

    private fun parseTextArray(array: JSONArray): List<String> = List(array.length()) { index ->
        array.get(index) as? String ?: error("Dosing text array contains non-string value.")
    }

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { index ->
        get(index) as? JSONObject ?: error("Dosing array contains non-object value.")
    }

    private val ISO_LOCAL_DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
    private val WEEKDAY_ORDER = listOf(
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
    )

    private val SCHEDULING_KEYS = setOf(
        "contract", "schemaVersion", "amountResolutionMl", "maxEventsPerChannel",
        "maxCustomPeriodsPerChannel", "scheduledDispatchGraceMs", "missedDoseRecoveryWindowMs",
        "minPumpRunDurationMs", "maxPumpRunDurationMs", "maxManualDoseMl",
        "supportsWeekdayRecurrence", "supportsMissedDoseRecovery", "supportsChannelReset",
        "supportsDailyDeliveredUsage", "supportedModes", "weekdayOrder", "effectiveScheduledDose"
    )
    private val EFFECTIVE_DOSE_KEYS = setOf("available", "minDoseMl", "maxDoseMl")
    private val PROGRAM_KEYS = setOf(
        "enabled", "weekdays", "mode", "missedDoseRecoveryEnabled", "config"
    )
    private val DISTRIBUTED_CONFIG_KEYS = setOf("dailyDoseMl", "startTimeMs")
    private val CUSTOM_CONFIG_KEYS = setOf("dailyDoseMl", "periods")
    private val CUSTOM_PERIOD_KEYS = setOf("startTimeMs", "endTimeMs", "doseCount")
    private val TIMER_CONFIG_KEYS = setOf("events")
    private val TIMER_EVENT_KEYS = setOf("timeMs", "amountMl")
    private val USAGE_KEYS = setOf(
        "dateValid", "localDate", "scheduledDeliveredMl", "manualDeliveredMl", "totalDeliveredMl"
    )
    private val GLOBAL_CHANNEL_KEYS = setOf(
        "channelKey", "effectiveName", "revision", "runtimeEnabled", "runtimeReason",
        "programEnabled", "programMode", "deliveryAccountingCertain", "usageToday", "reservoir",
        "active"
    )
    private val RESERVOIR_SUMMARY_KEYS = setOf(
        "trackingEnabled", "remainingMl", "accountingCertain"
    )
    private val RUNTIME_KEYS = setOf(
        "module", "supportsProgramApply", "supportsChannelConfig", "supportsChannelReset",
        "supportsPrime", "supportsManualDose", "supportsCalibrationWorkflow",
        "supportsReservoirRefill", "supportsChannelScopedStatus", "displayNameEditable"
    )
    private val RESOURCE_KEYS = setOf(
        "freeHeapBytes", "minimumFreeHeapBytes", "largestFreeBlockBytes", "taskStackHighWaterBytes",
        "checkpointWritesThisBoot", "canonicalConfigBytes", "programServiceBytes",
        "runtimeSnapshotBytes", "statusSnapshotBytes"
    )
    private val CHANNEL_DETAIL_KEYS = setOf(
        "channelKey", "revision", "runtimeEnabled", "runtimeReason", "program", "usageToday",
        "index", "defaultName", "displayName", "effectiveName", "profileManaged",
        "deliveryAccountingCertain", "hardware", "calibration", "reservoir", "activeRun",
        "lastRuntimeEvent", "editable"
    )
    private val HARDWARE_KEYS = setOf(
        "channelType", "gpio", "ledcChannel", "resolutionBits", "frequencyHz"
    )
    private val CALIBRATION_KEYS = setOf(
        "confirmed", "doseMsPerMl", "lastCalibratedAt", "state", "durationMs", "measuredMl",
        "pendingDoseMsPerMl", "verificationDoseStarted", "verificationDoseComplete"
    )
    private val RESERVOIR_KEYS = setOf(
        "trackingEnabled", "capacityMl", "remainingMl", "accountingCertain", "remainingPercent"
    )
    private val ACTIVE_RUN_KEYS = setOf("active", "source", "targetAmountMl", "remainingMs")
    private val RUNTIME_EVENT_KEYS = setOf(
        "valid", "sequence", "occurredAtMs", "kind", "reason", "source"
    )
    private val EDITABLE_KEYS = setOf("hardware", "displayName", "dosingCalibration", "reservoir")
}

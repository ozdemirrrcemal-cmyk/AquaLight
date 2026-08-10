package com.aqua.aqualight.data.devices.runtime.modules.dosing

import org.json.JSONArray
import org.json.JSONObject

internal object DeviceDosingRegimeParser {
    fun parse(value: String): DeviceDosingRegime = requireNotNull(
        DeviceDosingRegime.values().singleOrNull { regime -> regime.wireValue == value }
    ) { "Unknown firmware Dosing regime: $value" }
}

internal object DeviceDosingCalibrationStateParser {
    fun parse(value: String): DeviceDosingCalibrationState = requireNotNull(
        DeviceDosingCalibrationState.values().singleOrNull { state ->
            state.wireValue == value
        }
    ) { "Unknown firmware Dosing calibration state: $value" }
}

internal object DeviceDosingManualDoseCompletionReasonParser {
    fun parse(value: String): DeviceDosingManualDoseCompletionReason = requireNotNull(
        DeviceDosingManualDoseCompletionReason.values().singleOrNull { reason ->
            reason.wireValue == value
        }
    ) { "Unknown firmware manual dose completion reason: $value" }
}

internal object DeviceDosingManualDoseDeliveryBasisParser {
    fun parse(value: String): DeviceDosingManualDoseDeliveryBasis = requireNotNull(
        DeviceDosingManualDoseDeliveryBasis.values().singleOrNull { basis ->
            basis.wireValue == value
        }
    ) { "Unknown firmware manual dose delivery basis: $value" }
}

internal object DeviceDosingLastManualDoseParser {
    private val KEYS = setOf(
        "valid", "requestedAmountMl", "deliveredAmountMl", "actualDurationMs",
        "completedAt", "reservoirRemainingMlBefore", "reservoirRemainingMlAfter",
        "completionReason", "deliveryBasis", "persisted"
    )

    fun parse(data: JSONObject): DeviceDosingLastManualDose {
        data.requireDosingKeys(KEYS, "Dosing last manual dose")
        return DeviceDosingLastManualDose(
            valid = data.requireDosingBoolean("valid"),
            requestedAmountMl = data.requireDosingDouble("requestedAmountMl", 0.0),
            deliveredAmountMl = data.requireDosingDouble("deliveredAmountMl", 0.0),
            actualDurationMs = data.requireDosingLong(
                "actualDurationMs",
                DOSING_NON_NEGATIVE_LONG,
                DOSING_DEVICE_UPTIME_MAX_MS
            ),
            completedAt = data.requireDosingLong(
                "completedAt",
                DOSING_NON_NEGATIVE_LONG,
                DOSING_DEVICE_UPTIME_MAX_MS
            ),
            reservoirRemainingMlBefore = data.requireDosingDouble(
                "reservoirRemainingMlBefore",
                DOSING_UNSET_RESERVOIR
            ),
            reservoirRemainingMlAfter = data.requireDosingDouble(
                "reservoirRemainingMlAfter",
                DOSING_UNSET_RESERVOIR
            ),
            completionReason = DeviceDosingManualDoseCompletionReasonParser.parse(
                data.requireDosingText("completionReason")
            ),
            deliveryBasis = DeviceDosingManualDoseDeliveryBasisParser.parse(
                data.requireDosingText("deliveryBasis")
            ),
            persisted = data.requireDosingBoolean("persisted")
        ).also(::validate)
    }

    private fun validate(lastManualDose: DeviceDosingLastManualDose) {
        require(
            lastManualDose.deliveryBasis ==
                DeviceDosingManualDoseDeliveryBasis.CALIBRATED_RUNTIME
        )
        if (!lastManualDose.valid) {
            require(dosingValuesEquivalent(lastManualDose.requestedAmountMl, 0.0))
            require(dosingValuesEquivalent(lastManualDose.deliveredAmountMl, 0.0))
            require(lastManualDose.actualDurationMs == 0L)
            require(lastManualDose.completedAt == 0L)
            require(lastManualDose.reservoirRemainingMlBefore == DOSING_UNSET_RESERVOIR)
            require(lastManualDose.reservoirRemainingMlAfter == DOSING_UNSET_RESERVOIR)
            require(!lastManualDose.persisted)
            require(
                lastManualDose.completionReason ==
                    DeviceDosingManualDoseCompletionReason.NONE
            )
            return
        }

        require(lastManualDose.requestedAmountMl > 0.0)
        require(
            lastManualDose.completionReason !=
                DeviceDosingManualDoseCompletionReason.NONE
        )
        val reservoirUnavailable =
            lastManualDose.reservoirRemainingMlBefore == DOSING_UNSET_RESERVOIR &&
                lastManualDose.reservoirRemainingMlAfter == DOSING_UNSET_RESERVOIR
        if (!reservoirUnavailable) {
            require(lastManualDose.reservoirRemainingMlBefore >= 0.0)
            require(lastManualDose.reservoirRemainingMlAfter >= 0.0)
            require(
                lastManualDose.reservoirRemainingMlAfter <=
                    lastManualDose.reservoirRemainingMlBefore
            )
            require(
                dosingValuesEquivalent(
                    lastManualDose.reservoirRemainingMlBefore -
                        lastManualDose.reservoirRemainingMlAfter,
                    lastManualDose.deliveredAmountMl
                )
            )
        }
    }
}

internal object DeviceDosingRuntimeCapabilitiesParser {
    private val KEYS = setOf(
        "module", "readOnly", "supportsConfigApply", "supportsSchedules", "supportsChannels",
        "supportsPrime", "supportsManualDose", "supportsCalibrationWorkflow",
        "supportsCalibrationSessionState", "supportsReservoirRefill", "event"
    )

    fun parse(data: JSONObject): DeviceDosingRuntimeCapabilities {
        data.requireDosingKeys(KEYS, "Dosing runtime capabilities")
        return DeviceDosingRuntimeCapabilities(
            module = data.requireDosingText("module"),
            readOnly = data.requireDosingBoolean("readOnly"),
            supportsConfigApply = data.requireDosingBoolean("supportsConfigApply"),
            supportsSchedules = data.requireDosingBoolean("supportsSchedules"),
            supportsChannels = data.requireDosingBoolean("supportsChannels"),
            supportsPrime = data.requireDosingBoolean("supportsPrime"),
            supportsManualDose = data.requireDosingBoolean("supportsManualDose"),
            supportsCalibrationWorkflow = data.requireDosingBoolean(
                "supportsCalibrationWorkflow"
            ),
            supportsCalibrationSessionState = data.requireDosingBoolean(
                "supportsCalibrationSessionState"
            ),
            supportsReservoirRefill = data.requireDosingBoolean("supportsReservoirRefill"),
            event = data.requireDosingText("event")
        ).also { runtime ->
            require(runtime.module == DeviceDosingRuntimeContract.MODULE)
            require(!runtime.readOnly)
            require(runtime.supportsConfigApply)
            require(runtime.supportsSchedules)
            require(runtime.supportsChannels)
            require(runtime.supportsPrime)
            require(runtime.supportsManualDose)
            require(runtime.supportsCalibrationWorkflow)
            require(runtime.supportsCalibrationSessionState)
            require(runtime.supportsReservoirRefill)
            require(runtime.event == DeviceDosingRuntimeContract.STATUS_EVENT)
        }
    }
}

internal object DeviceDosingChannelParser {
    private val EDITABLE_KEYS = setOf(
        "hardware", "displayName", "hardwareCalibration", "dosingCalibration", "reservoir"
    )
    private val DOSING_KEYS = setOf(
        "unit", "doseMsPerMl", "lastCalibratedAt", "calibrated",
        "calibration", "lastManualDose",
        "reservoirTrackingEnabled", "reservoirCapacityMl", "reservoirRemainingMl",
        "reservoirRemainingPercent"
    )

    private val CALIBRATION_KEYS = setOf(
        "state", "startedAtUptimeMs", "durationMs", "measuredMl", "pendingDoseMsPerMl",
        "verificationDoseStarted", "verificationDoseComplete", "verificationDoseRemainingMs"
    )
    private val STATUS_KEYS = setOf(
        "index", "key", "name", "displayName", "profileManaged", "regime",
        "channelKind", "gpio", "ledcChannel", "group", "valueNow", "valueAuto",
        "valueManual", "manualTimeoutMs", "invert", "pwmResolutionBits",
        "pwmFrequencyHz", "dosing", "editable"
    )
    private val MUTATION_KEYS = STATUS_KEYS + "listIndex"
    private val CHANNEL_KINDS = setOf(
        DeviceDosingRuntimeContract.Literal.CHANNEL_KIND_GPIO,
        DeviceDosingRuntimeContract.Literal.CHANNEL_KIND_DIGITAL,
        DeviceDosingRuntimeContract.Literal.CHANNEL_KIND_NONE
    )

    fun parseStatus(data: JSONObject): DeviceDosingChannelStatus =
        parse(data, STATUS_KEYS, "Dosing status channel")

    fun parseMutation(data: JSONObject): DeviceDosingChannelStatusSnapshot {
        data.requireDosingKeys(MUTATION_KEYS, "Dosing channel mutation snapshot")
        return DeviceDosingChannelStatusSnapshot(
            listIndex = data.requireDosingInt(
                "listIndex",
                DOSING_MIN_INDEX,
                DeviceDosingRuntimeContract.Limit.MAX_CHANNELS - 1
            ),
            channel = parse(data, MUTATION_KEYS, "Dosing channel mutation snapshot")
        ).also { snapshot ->
            require(snapshot.listIndex == snapshot.channel.index)
        }
    }

    private fun parse(
        data: JSONObject,
        expectedKeys: Set<String>,
        label: String
    ): DeviceDosingChannelStatus {
        data.requireDosingKeys(expectedKeys, label)
        return DeviceDosingChannelStatus(
            index = data.requireDosingInt(
                "index",
                DOSING_MIN_INDEX,
                DeviceDosingRuntimeContract.Limit.MAX_CHANNELS - 1
            ),
            key = data.requireDosingText("key"),
            name = data.requireDosingText("name"),
            displayName = data.requireDosingText("displayName"),
            profileManaged = data.requireDosingBoolean("profileManaged"),
            regime = DeviceDosingRegimeParser.parse(data.requireDosingText("regime")),
            channelKind = data.requireDosingText("channelKind"),
            gpio = data.requireDosingInt(
                "gpio",
                DOSING_UNAVAILABLE_INDEX,
                Byte.MAX_VALUE.toInt()
            ),
            ledcChannel = data.requireDosingInt(
                "ledcChannel",
                DOSING_UNAVAILABLE_INDEX,
                Byte.MAX_VALUE.toInt()
            ),
            group = data.requireDosingInt(
                "group",
                Byte.MIN_VALUE.toInt(),
                Byte.MAX_VALUE.toInt()
            ),
            valueNow = data.requireDosingDouble(
                "valueNow",
                DOSING_INACTIVE_VALUE,
                DOSING_NORMALIZED_MAX
            ),
            valueAuto = data.requireDosingDouble(
                "valueAuto",
                DOSING_NORMALIZED_MIN,
                DOSING_NORMALIZED_MAX
            ),
            valueManual = data.requireDosingDouble(
                "valueManual",
                DOSING_INACTIVE_VALUE,
                DOSING_NORMALIZED_MAX
            ),
            manualTimeoutMs = data.requireDosingLong(
                "manualTimeoutMs",
                DOSING_NON_NEGATIVE_LONG,
                DOSING_DEVICE_UPTIME_MAX_MS
            ),
            invert = data.requireDosingBoolean("invert"),
            pwmResolutionBits = data.requireDosingInt("pwmResolutionBits", minimum = 0),
            pwmFrequencyHz = data.requireDosingInt("pwmFrequencyHz", minimum = 0),
            editable = parseEditable(data.requireDosingObject("editable")),
            dosing = parseDosing(data.requireDosingObject("dosing"))
        ).also(::validate)
    }

    private fun parseEditable(data: JSONObject): DeviceDosingChannelEditable {
        data.requireDosingKeys(EDITABLE_KEYS, "Dosing channel editable")
        return DeviceDosingChannelEditable(
            hardware = data.requireDosingBoolean("hardware"),
            displayName = data.requireDosingBoolean("displayName"),
            hardwareCalibration = data.requireDosingBoolean("hardwareCalibration"),
            dosingCalibration = data.requireDosingBoolean("dosingCalibration"),
            reservoir = data.requireDosingBoolean("reservoir")
        ).also { editable ->
            require(!editable.hardware)
            require(!editable.hardwareCalibration)
        }
    }

    private fun parseDosing(data: JSONObject): DeviceDosingPumpStatus {
        data.requireDosingKeys(DOSING_KEYS, "Dosing pump status")
        return DeviceDosingPumpStatus(
            unit = data.requireDosingText("unit"),
            doseMsPerMl = data.requireDosingLong(
                "doseMsPerMl",
                DOSING_UNSET_CALIBRATION,
                DeviceDosingRuntimeContract.Limit.MAX_DOSE_MS_PER_ML
            ),
            lastCalibratedAt = data.requireDosingLong(
                "lastCalibratedAt",
                DOSING_NON_NEGATIVE_LONG,
                DOSING_DEVICE_UPTIME_MAX_MS
            ),
            calibrated = data.requireDosingBoolean("calibrated"),
            calibration = parseCalibration(data.requireDosingObject("calibration")),
            lastManualDose = DeviceDosingLastManualDoseParser.parse(
                data.requireDosingObject("lastManualDose")
            ),
            reservoirTrackingEnabled = data.requireDosingBoolean(
                "reservoirTrackingEnabled"
            ),
            reservoirCapacityMl = data.requireDosingDouble(
                "reservoirCapacityMl",
                DOSING_UNSET_RESERVOIR
            ),
            reservoirRemainingMl = data.requireDosingDouble(
                "reservoirRemainingMl",
                DOSING_UNSET_RESERVOIR
            ),
            reservoirRemainingPercent = data.requireDosingDouble(
                "reservoirRemainingPercent",
                DOSING_UNSET_RESERVOIR,
                DOSING_PERCENT_MAX + DOSING_VALUE_EPSILON
            )
        ).also(::validateDosing)
    }

    private fun parseCalibration(data: JSONObject): DeviceDosingCalibrationSessionStatus {
        data.requireDosingKeys(CALIBRATION_KEYS, "Dosing calibration session")
        return DeviceDosingCalibrationSessionStatus(
            state = DeviceDosingCalibrationStateParser.parse(
                data.requireDosingText("state")
            ),
            startedAtUptimeMs = data.requireDosingLong(
                "startedAtUptimeMs",
                DOSING_NON_NEGATIVE_LONG,
                DOSING_DEVICE_UPTIME_MAX_MS
            ),
            durationMs = data.requireDosingLong(
                "durationMs",
                DOSING_NON_NEGATIVE_LONG,
                DeviceDosingRuntimeContract.Limit.MAX_CALIBRATION_DURATION_MS
            ),
            measuredMl = data.requireDosingDouble(
                "measuredMl",
                DOSING_NON_NEGATIVE_LONG.toDouble(),
                DeviceDosingRuntimeContract.Limit.MAX_CALIBRATION_MEASURED_ML
            ),
            pendingDoseMsPerMl = data.requireDosingLong(
                "pendingDoseMsPerMl",
                DOSING_UNSET_CALIBRATION,
                DeviceDosingRuntimeContract.Limit.MAX_DOSE_MS_PER_ML
            ),
            verificationDoseStarted = data.requireDosingBoolean("verificationDoseStarted"),
            verificationDoseComplete = data.requireDosingBoolean("verificationDoseComplete"),
            verificationDoseRemainingMs = data.requireDosingLong(
                "verificationDoseRemainingMs",
                DOSING_NON_NEGATIVE_LONG,
                DeviceDosingRuntimeContract.Limit.MAX_MANUAL_DOSE_DURATION_MS
            )
        ).also(::validateCalibration)
    }

    private fun validate(channel: DeviceDosingChannelStatus) {
        require(channel.profileManaged)
        require(channel.channelKind in CHANNEL_KINDS)
        require(channel.editable.displayName)
        require(channel.editable.dosingCalibration)
        require(channel.editable.reservoir)
        if (channel.valueManual < DOSING_NORMALIZED_MIN) {
            require(channel.manualTimeoutMs == DOSING_NON_NEGATIVE_LONG)
        }
    }

    private fun validateDosing(dosing: DeviceDosingPumpStatus) {
        require(dosing.unit == DeviceDosingRuntimeContract.Literal.UNIT_ML)
        require(
            dosing.calibrated ==
                (dosing.doseMsPerMl > 0L && dosing.lastCalibratedAt > 0L)
        )
        if (dosing.reservoirTrackingEnabled) {
            require(dosing.reservoirCapacityMl > 0.0)
            require(dosing.reservoirRemainingMl in 0.0..dosing.reservoirCapacityMl)
            val expectedPercent =
                dosing.reservoirRemainingMl / dosing.reservoirCapacityMl * DOSING_PERCENT_MAX
            require(dosingValuesEquivalent(dosing.reservoirRemainingPercent, expectedPercent))
        } else {
            require(dosingValuesEquivalent(
                dosing.reservoirRemainingPercent,
                DOSING_UNSET_RESERVOIR
            ))
        }
    }

    private fun validateCalibration(calibration: DeviceDosingCalibrationSessionStatus) {
        when (calibration.state) {
            DeviceDosingCalibrationState.IDLE -> {
                require(calibration.startedAtUptimeMs == 0L)
                require(calibration.durationMs == 0L)
                require(dosingValuesEquivalent(calibration.measuredMl, 0.0))
                require(calibration.pendingDoseMsPerMl == DOSING_UNSET_CALIBRATION)
                require(!calibration.verificationDoseStarted)
                require(!calibration.verificationDoseComplete)
                require(calibration.verificationDoseRemainingMs == 0L)
            }
            DeviceDosingCalibrationState.RUNNING -> {
                require(calibration.durationMs in
                    DeviceDosingRuntimeContract.Limit.MIN_CALIBRATION_DURATION_MS..
                        DeviceDosingRuntimeContract.Limit.MAX_CALIBRATION_DURATION_MS)
                require(calibration.pendingDoseMsPerMl == DOSING_UNSET_CALIBRATION)
                require(!calibration.verificationDoseStarted)
            }
            DeviceDosingCalibrationState.PENDING_VERIFICATION -> {
                require(calibration.durationMs in
                    DeviceDosingRuntimeContract.Limit.MIN_CALIBRATION_DURATION_MS..
                        DeviceDosingRuntimeContract.Limit.MAX_CALIBRATION_DURATION_MS)
                require(calibration.measuredMl in
                    DeviceDosingRuntimeContract.Limit.MIN_CALIBRATION_MEASURED_ML..
                        DeviceDosingRuntimeContract.Limit.MAX_CALIBRATION_MEASURED_ML)
                require(calibration.pendingDoseMsPerMl > 0L)
                require(!calibration.verificationDoseComplete || calibration.verificationDoseStarted)
                require(!calibration.verificationDoseComplete ||
                    calibration.verificationDoseRemainingMs == 0L)
            }
        }
    }
}

internal object DeviceDosingScheduleParser {
    private val KEYS = setOf(
        "index", "enabled", "runtimeEnabled", "name", "channelKey", "bound", "group",
        "weekdays", "startTimeMs", "startTime", "intervalOnMs", "intervalOn",
        "intervalOffMs", "intervalOff", "repeatCount", "amountMl", "pulseCountRuntime",
        "pulseOffPending", "pulseRemainingMs"
    )

    fun parse(data: JSONObject): DeviceDosingScheduleStatus {
        data.requireDosingKeys(KEYS, "Dosing status schedule")
        return DeviceDosingScheduleStatus(
            index = data.requireDosingInt(
                "index",
                DOSING_MIN_INDEX,
                DeviceDosingRuntimeContract.Limit.MAX_SCHEDULES - 1
            ),
            enabled = data.requireDosingBoolean("enabled"),
            runtimeEnabled = data.requireDosingBoolean("runtimeEnabled"),
            name = data.requireDosingText("name"),
            channelKey = data.requireDosingText("channelKey"),
            bound = data.requireDosingBoolean("bound"),
            group = data.requireDosingInt(
                "group",
                Byte.MIN_VALUE.toInt(),
                Byte.MAX_VALUE.toInt()
            ),
            weekdays = parseWeekdays(data.requireDosingArray("weekdays")),
            startTimeMs = data.requireDosingLong(
                "startTimeMs",
                DOSING_NON_NEGATIVE_LONG,
                DeviceDosingRuntimeContract.Limit.LAST_MILLISECOND_OF_DAY
            ),
            startTime = data.requireDosingText("startTime"),
            intervalOnMs = data.requireDosingLong(
                "intervalOnMs",
                DOSING_NON_NEGATIVE_LONG,
                DOSING_DEVICE_UPTIME_MAX_MS
            ),
            intervalOn = data.requireDosingText("intervalOn"),
            intervalOffMs = data.requireDosingLong(
                "intervalOffMs",
                DOSING_NON_NEGATIVE_LONG,
                DOSING_DEVICE_UPTIME_MAX_MS
            ),
            intervalOff = data.requireDosingText("intervalOff"),
            repeatCount = data.requireDosingInt("repeatCount", minimum = DOSING_MIN_COUNT),
            amountMl = data.requireDosingDouble("amountMl", minimum = Double.MIN_VALUE),
            pulseCountRuntime = data.requireDosingInt(
                "pulseCountRuntime",
                minimum = DOSING_UNAVAILABLE_INDEX
            ),
            pulseOffPending = data.requireDosingBoolean("pulseOffPending"),
            pulseRemainingMs = data.requireDosingLong(
                "pulseRemainingMs",
                DOSING_NON_NEGATIVE_LONG,
                DOSING_DEVICE_UPTIME_MAX_MS
            )
        ).also(::validate)
    }

    private fun parseWeekdays(data: JSONArray): List<Boolean> {
        require(data.length() == DOSING_WEEKDAY_COUNT) {
            "Dosing weekdays must contain exactly $DOSING_WEEKDAY_COUNT booleans."
        }
        return List(data.length()) { index -> data.requireDosingBoolean(index) }
    }

    private fun validate(schedule: DeviceDosingScheduleStatus) {
        require(schedule.startTime == dosingTimeText(schedule.startTimeMs))
        require(schedule.intervalOn == dosingTimeText(schedule.intervalOnMs))
        require(schedule.intervalOff == dosingTimeText(schedule.intervalOffMs))
        require(schedule.pulseOffPending == (schedule.pulseRemainingMs > 0L))
    }
}

internal object DeviceDosingConfigChannelParser {
    private val REQUIRED_KEYS = setOf("channelKey", "regime", "dosing")
    private val OPTIONAL_KEYS = setOf("displayName")
    private val DOSING_KEYS = setOf(
        "doseMsPerMl", "lastCalibratedAt", "reservoirTrackingEnabled",
        "reservoirCapacityMl"
    )
    private val DOSING_OPTIONAL_KEYS = setOf("lastManualDose")

    fun parse(data: JSONObject, listIndex: Int): DeviceDosingChannelConfigSnapshot {
        data.requireDosingKeys(REQUIRED_KEYS, OPTIONAL_KEYS, "Dosing config channel")
        return DeviceDosingChannelConfigSnapshot(
            listIndex = listIndex,
            channelKey = data.requireDosingText("channelKey"),
            displayNameOverride = data.optionalDosingText("displayName"),
            regime = DeviceDosingRegimeParser.parse(data.requireDosingText("regime")),
            dosing = parseDosing(data.requireDosingObject("dosing"))
        )
    }

    private fun parseDosing(data: JSONObject): DeviceDosingChannelDosingConfigSnapshot {
        data.requireDosingKeys(
            DOSING_KEYS,
            DOSING_OPTIONAL_KEYS,
            "Dosing config channel settings"
        )
        return DeviceDosingChannelDosingConfigSnapshot(
            doseMsPerMl = data.requireDosingLong(
                "doseMsPerMl",
                DOSING_UNSET_CALIBRATION,
                DeviceDosingRuntimeContract.Limit.MAX_DOSE_MS_PER_ML
            ),
            lastCalibratedAt = data.requireDosingLong(
                "lastCalibratedAt",
                DOSING_NON_NEGATIVE_LONG,
                DOSING_DEVICE_UPTIME_MAX_MS
            ),
            reservoirTrackingEnabled = data.requireDosingBoolean(
                "reservoirTrackingEnabled"
            ),
            reservoirCapacityMl = data.requireDosingDouble(
                "reservoirCapacityMl",
                DOSING_UNSET_RESERVOIR
            ),
            lastManualDose = if (data.has("lastManualDose")) {
                DeviceDosingLastManualDoseParser.parse(
                    data.requireDosingObject("lastManualDose")
                ).also { history -> require(history.persisted) }
                    .takeIf(DeviceDosingLastManualDose::valid)
            } else {
                null
            }
        ).also { dosing ->
            if (dosing.reservoirTrackingEnabled) require(dosing.reservoirCapacityMl > 0.0)
        }
    }
}

internal object DeviceDosingConfigScheduleParser {
    private val KEYS = setOf(
        "enabled", "name", "channelKey", "weekdays", "startTimeMs", "intervalOnMs",
        "intervalOffMs", "repeatCount", "amountMl"
    )

    fun parse(data: JSONObject, listIndex: Int): DeviceDosingScheduleConfigSnapshot {
        data.requireDosingKeys(KEYS, "Dosing config schedule")
        val config = DeviceDosingScheduleConfig(
            enabled = data.requireDosingBoolean("enabled"),
            name = data.requireDosingText("name"),
            channelKey = data.requireDosingText("channelKey"),
            weekdays = parseWeekdays(data.requireDosingArray("weekdays")),
            startTimeMs = data.requireDosingLong(
                "startTimeMs",
                DOSING_NON_NEGATIVE_LONG,
                DeviceDosingRuntimeContract.Limit.LAST_MILLISECOND_OF_DAY
            ),
            intervalOnMs = data.requireDosingLong(
                "intervalOnMs",
                DOSING_NON_NEGATIVE_LONG,
                DOSING_DEVICE_UPTIME_MAX_MS
            ),
            intervalOffMs = data.requireDosingLong(
                "intervalOffMs",
                DOSING_NON_NEGATIVE_LONG,
                DOSING_DEVICE_UPTIME_MAX_MS
            ),
            repeatCount = data.requireDosingInt("repeatCount", minimum = DOSING_MIN_COUNT),
            amountMl = data.requireDosingDouble("amountMl", minimum = Double.MIN_VALUE)
        )
        return DeviceDosingScheduleConfigSnapshot(
            listIndex = listIndex,
            enabled = config.enabled,
            name = config.normalizedName,
            channelKey = config.normalizedChannelKey,
            weekdays = config.weekdays.toList(),
            startTimeMs = config.startTimeMs,
            intervalOnMs = config.intervalOnMs,
            intervalOffMs = config.intervalOffMs,
            repeatCount = config.repeatCount,
            amountMl = config.amountMl
        )
    }

    private fun parseWeekdays(data: JSONArray): List<Boolean> {
        require(data.length() == DOSING_WEEKDAY_COUNT) {
            "Dosing weekdays must contain exactly $DOSING_WEEKDAY_COUNT booleans."
        }
        return List(data.length()) { index -> data.requireDosingBoolean(index) }
    }
}

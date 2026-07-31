package com.aqua.aqualight.data.devices.runtime.modules.dosing

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import org.json.JSONArray
import org.json.JSONObject

object DeviceDosingStatusParser {

    fun parse(data: JSONObject): DeviceDosingStatus {
        data.requireExactKeys(STATUS_KEYS, "dosing.status.get.data")

        val channels = parseStatusChannels(data.requiredArray("channels"))
        val schedules = parseStatusSchedules(data.requiredArray("schedules"))
        val channelCount = data.requiredNonNegativeInt("channelCount")
        val scheduleCount = data.requiredNonNegativeInt("scheduleCount")

        require(channelCount == channels.size) {
            "dosing status channelCount differs from channels size."
        }
        require(scheduleCount == schedules.size) {
            "dosing status scheduleCount differs from schedules size."
        }
        require(scheduleCount <= DeviceDosingRuntimeContract.Limit.MAX_SCHEDULES)

        requireUnique(channels.map(DeviceDosingChannelStatus::index), "dosing channel index")
        requireUnique(channels.map(DeviceDosingChannelStatus::key), "dosing channel key")
        requireUnique(schedules.map(DeviceDosingScheduleStatus::index), "dosing schedule index")

        return DeviceDosingStatus(
            supported = data.requiredBoolean("supported"),
            channelCount = channelCount,
            scheduleCount = scheduleCount,
            lockLoop = data.requiredBoolean("lockLoop"),
            schema = data.requiredNonBlankString("schema").also {
                require(it == DOSING_SCHEMA)
            },
            rootName = data.requiredNonBlankString("rootName").also {
                require(it == DeviceDosingRuntimeContract.MODULE)
            },
            unit = data.requiredNonBlankString("unit").also {
                require(it == DOSING_UNIT)
            },
            uptimeMs = data.requiredNonNegativeLong("uptimeMs"),
            channels = channels,
            schedules = schedules,
            runtime = parseRuntime(data.requiredObject("runtime"))
        )
    }

    fun parseConfigApply(data: JSONObject): DeviceDosingConfigApplyResult {
        data.requireExactKeys(CONFIG_APPLY_KEYS, "dosing.config.apply.data")

        val saveRequested = data.requiredBoolean("saveRequested")
        val saved = data.requiredBoolean("saved")
        require(saved == saveRequested)

        val appliedChannels = data.requiredBoolean("appliedChannels")
        val appliedSchedules = data.requiredBoolean("appliedSchedules")
        require(appliedChannels || appliedSchedules)

        return DeviceDosingConfigApplyResult(
            operation = data.requiredExactString("operation", "configApply"),
            changed = data.requiredBoolean("changed"),
            saved = saved,
            saveRequested = saveRequested,
            runtimeTransport = data.requiredRuntimeTransport(),
            command = data.requiredCommand(DOSING_CONFIG_APPLY_COMMAND),
            event = data.requiredStatusEvent(),
            appliedChannels = appliedChannels,
            appliedSchedules = appliedSchedules,
            config = parseConfigSnapshot(data.requiredObject("config"))
        )
    }

    fun parsePrimeStart(data: JSONObject): DeviceDosingManualPumpResult =
        parseManualPumpResult(
            data = data,
            operation = "primeStart",
            command = DOSING_PRIME_START_COMMAND,
            manualActive = true
        )

    fun parsePrimeStop(data: JSONObject): DeviceDosingManualPumpResult =
        parseManualPumpResult(
            data = data,
            operation = "primeStop",
            command = DOSING_PRIME_STOP_COMMAND,
            manualActive = false
        )

    fun parseDoseStop(data: JSONObject): DeviceDosingManualPumpResult =
        parseManualPumpResult(
            data = data,
            operation = "doseStop",
            command = DOSING_DOSE_STOP_COMMAND,
            manualActive = false
        )

    fun parseDoseNow(data: JSONObject): DeviceDosingDoseNowResult {
        data.requireExactKeys(DOSE_NOW_KEYS, "dosing.dose.now.data")

        val channelKey = data.requiredDosingChannelKey("channelKey")
        val amountMl = data.requiredFiniteDouble("amountMl").also {
            require(it > 0.0 && it <= DeviceDosingRuntimeContract.Limit.MAX_MANUAL_DOSE_ML)
        }
        val durationMs = data.requiredLong("durationMs").also {
            require(
                it in MIN_MANUAL_DOSE_DURATION_MS..MAX_MANUAL_DOSE_DURATION_MS
            )
        }
        val doseMsPerMl = data.requiredLong("doseMsPerMl").also(::requireSafeDoseMsPerMl)
        val channel = parseChannelSnapshot(
            data.requiredObject("channel"),
            "dosing.dose.now.data.channel"
        )
        require(channel.channel.key == channelKey)

        return DeviceDosingDoseNowResult(
            operation = data.requiredExactString("operation", "doseNow"),
            channelKey = channelKey,
            amountMl = amountMl,
            durationMs = durationMs,
            doseMsPerMl = doseMsPerMl,
            usePendingCalibration = data.requiredBoolean("usePendingCalibration"),
            manualActive = data.requiredBoolean("manualActive").also(::requireTrue),
            saved = data.requiredBoolean("saved").also(::requireFalse),
            runtimeTransport = data.requiredRuntimeTransport(),
            command = data.requiredCommand(DOSING_DOSE_NOW_COMMAND),
            event = data.requiredStatusEvent(),
            channel = channel
        )
    }

    fun parseCalibrationStart(data: JSONObject): DeviceDosingCalibrationStartResult {
        data.requireExactKeys(CALIBRATION_START_KEYS, "dosing.calibration.start.data")

        return DeviceDosingCalibrationStartResult(
            operation = data.requiredExactString("operation", "calibrationStart"),
            channelKey = data.requiredDosingChannelKey("channelKey"),
            durationMs = data.requiredLong("durationMs").also(::requireCalibrationDuration),
            manualActive = data.requiredBoolean("manualActive").also(::requireTrue),
            saved = data.requiredBoolean("saved").also(::requireFalse),
            runtimeTransport = data.requiredRuntimeTransport(),
            command = data.requiredCommand(DOSING_CALIBRATION_START_COMMAND),
            event = data.requiredStatusEvent()
        )
    }

    fun parseCalibrationFinish(data: JSONObject): DeviceDosingCalibrationFinishResult {
        data.requireExactKeys(CALIBRATION_FINISH_KEYS, "dosing.calibration.finish.data")

        val channelKey = data.requiredDosingChannelKey("channelKey")
        val measuredMl = data.requiredFiniteDouble("measuredMl").also {
            require(
                it in
                    DeviceDosingRuntimeContract.Limit.MIN_CALIBRATION_MEASURED_ML..
                    DeviceDosingRuntimeContract.Limit.MAX_CALIBRATION_MEASURED_ML
            )
        }
        val durationMs = data.requiredLong("durationMs").also(::requireCalibrationDuration)
        val pendingDoseMsPerMl = data.requiredLong("pendingDoseMsPerMl")
            .also(::requireSafeDoseMsPerMl)
        val channel = parseChannelSnapshot(
            data.requiredObject("channel"),
            "dosing.calibration.finish.data.channel"
        )
        require(channel.channel.key == channelKey)
        require(channel.channel.dosing.doseMsPerMl == pendingDoseMsPerMl)

        return DeviceDosingCalibrationFinishResult(
            operation = data.requiredExactString("operation", "calibrationFinish"),
            channelKey = channelKey,
            measuredMl = measuredMl,
            durationMs = durationMs,
            pendingDoseMsPerMl = pendingDoseMsPerMl,
            pending = data.requiredBoolean("pending").also(::requireTrue),
            saved = data.requiredBoolean("saved").also(::requireFalse),
            runtimeTransport = data.requiredRuntimeTransport(),
            command = data.requiredCommand(DOSING_CALIBRATION_FINISH_COMMAND),
            event = data.requiredStatusEvent(),
            channel = channel
        )
    }

    fun parseCalibrationConfirm(data: JSONObject): DeviceDosingCalibrationConfirmResult {
        data.requireExactKeys(CALIBRATION_CONFIRM_KEYS, "dosing.calibration.confirm.data")

        val channelKey = data.requiredDosingChannelKey("channelKey")
        val doseMsPerMl = data.requiredLong("doseMsPerMl").also(::requireSafeDoseMsPerMl)
        val lastCalibratedAt = data.requiredNonNegativeLong("lastCalibratedAt")
        val channel = parseChannelSnapshot(
            data.requiredObject("channel"),
            "dosing.calibration.confirm.data.channel"
        )
        require(channel.channel.key == channelKey)
        require(channel.channel.dosing.doseMsPerMl == doseMsPerMl)
        require(channel.channel.dosing.lastCalibratedAt == lastCalibratedAt)

        return DeviceDosingCalibrationConfirmResult(
            operation = data.requiredExactString("operation", "calibrationConfirm"),
            channelKey = channelKey,
            doseMsPerMl = doseMsPerMl,
            lastCalibratedAt = lastCalibratedAt,
            saved = data.requiredBoolean("saved").also(::requireTrue),
            runtimeTransport = data.requiredRuntimeTransport(),
            command = data.requiredCommand(DOSING_CALIBRATION_CONFIRM_COMMAND),
            event = data.requiredStatusEvent(),
            channel = channel
        )
    }

    fun parseCalibrationCancel(data: JSONObject): DeviceDosingCalibrationCancelResult {
        data.requireExactKeys(CALIBRATION_CANCEL_KEYS, "dosing.calibration.cancel.data")

        val channelKey = data.requiredDosingChannelKey("channelKey")
        val channel = parseChannelSnapshot(
            data.requiredObject("channel"),
            "dosing.calibration.cancel.data.channel"
        )
        require(channel.channel.key == channelKey)

        return DeviceDosingCalibrationCancelResult(
            operation = data.requiredExactString("operation", "calibrationCancel"),
            channelKey = channelKey,
            restoredPreviousCalibration = data.requiredBoolean("restoredPreviousCalibration"),
            saved = data.requiredBoolean("saved").also(::requireFalse),
            runtimeTransport = data.requiredRuntimeTransport(),
            command = data.requiredCommand(DOSING_CALIBRATION_CANCEL_COMMAND),
            event = data.requiredStatusEvent(),
            channel = channel
        )
    }

    fun parseReservoirRefill(data: JSONObject): DeviceDosingReservoirRefillResult {
        data.requireExactKeys(RESERVOIR_REFILL_KEYS, "dosing.reservoir.refill.data")

        val channelKey = data.requiredDosingChannelKey("channelKey")
        val beforeMl = data.requiredFiniteDouble("reservoirRemainingMlBefore")
        val remainingMl = data.requiredFiniteDouble("reservoirRemainingMl")
        val capacityMl = data.requiredFiniteDouble("reservoirCapacityMl")
        val changed = data.requiredBoolean("changed")
        require(changed == (beforeMl != remainingMl))
        require(capacityMl == remainingMl)

        val channel = parseChannelSnapshot(
            data.requiredObject("channel"),
            "dosing.reservoir.refill.data.channel"
        )
        require(channel.channel.key == channelKey)
        require(channel.channel.dosing.reservoirRemainingMl == remainingMl)
        require(channel.channel.dosing.reservoirCapacityMl == capacityMl)

        return DeviceDosingReservoirRefillResult(
            operation = data.requiredExactString("operation", "reservoirRefill"),
            channelKey = channelKey,
            changed = changed,
            reservoirRemainingMlBefore = beforeMl,
            reservoirRemainingMl = remainingMl,
            reservoirCapacityMl = capacityMl,
            persisted = data.requiredBoolean("persisted"),
            saved = data.requiredBoolean("saved").also(::requireFalse),
            runtimeTransport = data.requiredRuntimeTransport(),
            command = data.requiredCommand(DOSING_RESERVOIR_REFILL_COMMAND),
            event = data.requiredStatusEvent(),
            channel = channel
        )
    }

    private fun parseManualPumpResult(
        data: JSONObject,
        operation: String,
        command: String,
        manualActive: Boolean
    ): DeviceDosingManualPumpResult {
        data.requireExactKeys(MANUAL_PUMP_KEYS, "$command.data")

        val channelKey = data.requiredDosingChannelKey("channelKey")
        val channel = parseChannelSnapshot(data.requiredObject("channel"), "$command.data.channel")
        require(channel.channel.key == channelKey)

        return DeviceDosingManualPumpResult(
            operation = data.requiredExactString("operation", operation),
            channelKey = channelKey,
            manualActive = data.requiredBoolean("manualActive").also {
                require(it == manualActive)
            },
            saved = data.requiredBoolean("saved").also(::requireFalse),
            runtimeTransport = data.requiredRuntimeTransport(),
            command = data.requiredCommand(command),
            event = data.requiredStatusEvent(),
            channel = channel
        )
    }

    private fun parseRuntime(runtime: JSONObject): DeviceDosingRuntimeCapabilities {
        runtime.requireExactKeys(RUNTIME_KEYS, "dosing status runtime")

        return DeviceDosingRuntimeCapabilities(
            module = runtime.requiredExactString("module", DeviceDosingRuntimeContract.MODULE),
            readOnly = runtime.requiredBoolean("readOnly").also(::requireFalse),
            supportsConfigApply = runtime.requiredBoolean("supportsConfigApply")
                .also(::requireTrue),
            supportsSchedules = runtime.requiredBoolean("supportsSchedules").also(::requireTrue),
            supportsChannels = runtime.requiredBoolean("supportsChannels").also(::requireTrue),
            supportsPrime = runtime.requiredBoolean("supportsPrime").also(::requireTrue),
            supportsManualDose = runtime.requiredBoolean("supportsManualDose")
                .also(::requireTrue),
            supportsCalibrationWorkflow = runtime.requiredBoolean("supportsCalibrationWorkflow")
                .also(::requireTrue),
            supportsReservoirRefill = runtime.requiredBoolean("supportsReservoirRefill")
                .also(::requireTrue),
            event = runtime.requiredStatusEvent()
        )
    }

    private fun parseStatusChannels(channels: JSONArray): List<DeviceDosingChannelStatus> =
        List(channels.length()) { index ->
            parseChannel(
                item = channels.requiredObject(index, "dosing status channels"),
                label = "dosing status channels[$index]",
                expectedKeys = CHANNEL_KEYS
            )
        }

    private fun parseChannelSnapshot(
        item: JSONObject,
        label: String
    ): DeviceDosingChannelSnapshot {
        val channel = parseChannel(
            item = item,
            label = label,
            expectedKeys = CHANNEL_SNAPSHOT_KEYS
        )
        return DeviceDosingChannelSnapshot(
            listIndex = item.requiredNonNegativeInt("listIndex"),
            channel = channel
        )
    }

    private fun parseChannel(
        item: JSONObject,
        label: String,
        expectedKeys: Set<String>
    ): DeviceDosingChannelStatus {
        item.requireExactKeys(expectedKeys, label)
        val editable = item.requiredObject("editable")
        editable.requireExactKeys(EDITABLE_KEYS, "$label.editable")
        val dosing = item.requiredObject("dosing")
        dosing.requireExactKeys(DOSING_STATUS_KEYS, "$label.dosing")

        val doseMsPerMl = dosing.requiredLong("doseMsPerMl").also {
            require(it >= -1L)
        }
        val calibrated = dosing.requiredBoolean("calibrated")
        require(calibrated == (doseMsPerMl > 0L))

        return DeviceDosingChannelStatus(
            index = item.requiredNonNegativeInt("index"),
            key = item.requiredDosingChannelKey("key"),
            name = item.requiredStringAllowEmpty("name"),
            displayName = item.requiredStringAllowEmpty("displayName"),
            profileManaged = item.requiredBoolean("profileManaged"),
            regime = DeviceDosingRegime.fromWire(item.requiredNonBlankString("regime")),
            channelKind = item.requiredNonBlankString("channelKind").also {
                require(it in CHANNEL_KINDS)
            },
            gpio = item.requiredInt("gpio"),
            ledcChannel = item.requiredInt("ledcChannel"),
            group = item.requiredInt("group"),
            valueNow = item.requiredFiniteDouble("valueNow"),
            valueAuto = item.requiredFiniteDouble("valueAuto"),
            valueManual = item.requiredFiniteDouble("valueManual"),
            manualTimeoutMs = item.requiredNonNegativeLong("manualTimeoutMs"),
            invert = item.requiredBoolean("invert"),
            pwmResolutionBits = item.requiredNonNegativeInt("pwmResolutionBits"),
            pwmFrequencyHz = item.requiredNonNegativeInt("pwmFrequencyHz"),
            editable = DeviceDosingChannelEditable(
                hardware = editable.requiredBoolean("hardware"),
                displayName = editable.requiredBoolean("displayName"),
                hardwareCalibration = editable.requiredBoolean("hardwareCalibration"),
                dosingCalibration = editable.requiredBoolean("dosingCalibration"),
                reservoir = editable.requiredBoolean("reservoir")
            ),
            dosing = DeviceDosingPumpStatus(
                doseMsPerMl = doseMsPerMl,
                doseUnit = dosing.requiredExactString("unit", DOSING_UNIT),
                lastCalibratedAt = dosing.requiredNonNegativeLong("lastCalibratedAt"),
                calibrated = calibrated,
                reservoirTrackingEnabled = dosing.requiredBoolean("reservoirTrackingEnabled"),
                reservoirCapacityMl = dosing.requiredFiniteDouble("reservoirCapacityMl"),
                reservoirRemainingMl = dosing.requiredFiniteDouble("reservoirRemainingMl"),
                reservoirRemainingPercent =
                    dosing.requiredFiniteDouble("reservoirRemainingPercent")
            )
        )
    }

    private fun parseStatusSchedules(
        schedules: JSONArray
    ): List<DeviceDosingScheduleStatus> = List(schedules.length()) { index ->
        val item = schedules.requiredObject(index, "dosing status schedules")
        item.requireExactKeys(SCHEDULE_KEYS, "dosing status schedules[$index]")

        DeviceDosingScheduleStatus(
            index = item.requiredNonNegativeInt("index"),
            enabled = item.requiredBoolean("enabled"),
            runtimeEnabled = item.requiredBoolean("runtimeEnabled"),
            name = item.requiredStringAllowEmpty("name"),
            channelKey = item.requiredStringAllowEmpty("channelKey").also {
                if (it.isNotEmpty()) requireCanonicalDosingChannelKey(it, "channelKey")
            },
            bound = item.requiredBoolean("bound"),
            group = item.requiredInt("group"),
            weekdays = item.requiredWeekdays("weekdays"),
            startTimeMs = item.requiredLongInDay("startTimeMs"),
            startTime = item.requiredNonBlankString("startTime"),
            intervalOnMs = item.requiredNonNegativeLong("intervalOnMs"),
            intervalOn = item.requiredNonBlankString("intervalOn"),
            intervalOffMs = item.requiredNonNegativeLong("intervalOffMs"),
            intervalOff = item.requiredNonBlankString("intervalOff"),
            repeatCount = item.requiredNonNegativeInt("repeatCount"),
            amountMl = item.requiredFiniteDouble("amountMl"),
            pulseCountRuntime = item.requiredInt("pulseCountRuntime").also {
                require(it >= -1)
            },
            pulseOffPending = item.requiredBoolean("pulseOffPending"),
            pulseRemainingMs = item.requiredNonNegativeLong("pulseRemainingMs")
        )
    }

    private fun parseConfigSnapshot(data: JSONObject): DeviceDosingConfigSnapshot {
        data.requireExactKeys(CONFIG_SNAPSHOT_KEYS, "dosing.config.apply.data.config")

        val channelsJson = data.requiredArray("channels")
        val schedulesJson = data.requiredArray("schedules")
        val channels = List(channelsJson.length()) { index ->
            parseConfigChannel(
                channelsJson.requiredObject(index, "dosing config channels"),
                "dosing config channels[$index]"
            )
        }
        val schedules = List(schedulesJson.length()) { index ->
            parseConfigSchedule(
                schedulesJson.requiredObject(index, "dosing config schedules"),
                "dosing config schedules[$index]"
            )
        }

        require(schedules.size <= DeviceDosingRuntimeContract.Limit.MAX_SCHEDULES)
        requireUnique(
            channels.map(DeviceDosingChannelConfigSnapshot::channelKey),
            "dosing config channel key"
        )

        return DeviceDosingConfigSnapshot(
            channels = channels,
            schedules = schedules
        )
    }

    private fun parseConfigChannel(
        item: JSONObject,
        label: String
    ): DeviceDosingChannelConfigSnapshot {
        item.requireRequiredAndAllowedKeys(
            required = CONFIG_CHANNEL_REQUIRED_KEYS,
            allowed = CONFIG_CHANNEL_ALLOWED_KEYS,
            label = label
        )

        return DeviceDosingChannelConfigSnapshot(
            channelKey = item.requiredDosingChannelKey("channelKey"),
            displayName = item.optionalCanonicalString("displayName"),
            regime = DeviceDosingRegime.fromWire(item.requiredNonBlankString("regime")),
            dosing = if (item.has("dosing")) {
                parseConfigDosing(item.requiredObject("dosing"), "$label.dosing")
            } else {
                null
            }
        )
    }

    private fun parseConfigDosing(
        data: JSONObject,
        label: String
    ): DeviceDosingChannelDosingConfigSnapshot {
        val actual = data.keys().asSequence().toSet()
        require(actual.isNotEmpty())
        require(actual.all { it in DOSING_CONFIG_ALLOWED_KEYS }) {
            "$label contains fields outside the firmware contract."
        }

        val hasCalibration = actual.any { it in DOSING_CALIBRATION_KEYS }
        val hasReservoir = actual.any { it in DOSING_RESERVOIR_KEYS }
        if (hasCalibration) {
            require(actual.containsAll(DOSING_CALIBRATION_KEYS))
        }
        if (hasReservoir) {
            require(actual.containsAll(DOSING_RESERVOIR_KEYS))
        }
        require(hasCalibration || hasReservoir)

        return DeviceDosingChannelDosingConfigSnapshot(
            doseMsPerMl = if (hasCalibration) data.requiredLong("doseMsPerMl") else null,
            lastCalibratedAt = if (hasCalibration) {
                data.requiredNonNegativeLong("lastCalibratedAt")
            } else {
                null
            },
            reservoirTrackingEnabled = if (hasReservoir) {
                data.requiredBoolean("reservoirTrackingEnabled")
            } else {
                null
            },
            reservoirCapacityMl = if (hasReservoir) {
                data.requiredFiniteDouble("reservoirCapacityMl")
            } else {
                null
            }
        )
    }

    private fun parseConfigSchedule(
        item: JSONObject,
        label: String
    ): DeviceDosingScheduleConfigSnapshot {
        item.requireExactKeys(CONFIG_SCHEDULE_KEYS, label)

        return DeviceDosingScheduleConfigSnapshot(
            enabled = item.requiredBoolean("enabled"),
            name = item.requiredStringAllowEmpty("name"),
            channelKey = item.requiredStringAllowEmpty("channelKey").also {
                if (it.isNotEmpty()) requireCanonicalDosingChannelKey(it, "channelKey")
            },
            weekdays = item.requiredWeekdays("weekdays"),
            startTimeMs = item.requiredLongInDay("startTimeMs"),
            intervalOnMs = item.requiredNonNegativeLong("intervalOnMs"),
            intervalOffMs = item.requiredNonNegativeLong("intervalOffMs"),
            repeatCount = item.requiredNonNegativeInt("repeatCount"),
            amountMl = item.requiredFiniteDouble("amountMl")
        )
    }

    private fun JSONObject.requireExactKeys(expected: Set<String>, label: String) {
        val actual = keys().asSequence().toSet()
        require(actual == expected) {
            "$label keys differ from firmware; expected=$expected actual=$actual"
        }
    }

    private fun JSONObject.requireRequiredAndAllowedKeys(
        required: Set<String>,
        allowed: Set<String>,
        label: String
    ) {
        val actual = keys().asSequence().toSet()
        require(actual.containsAll(required) && actual.all { it in allowed }) {
            "$label keys differ from firmware; required=$required allowed=$allowed actual=$actual"
        }
    }

    private fun JSONObject.requiredObject(key: String): JSONObject =
        get(key) as? JSONObject ?: error("$key must be a JSON object.")

    private fun JSONObject.requiredArray(key: String): JSONArray =
        get(key) as? JSONArray ?: error("$key must be a JSON array.")

    private fun JSONArray.requiredObject(index: Int, label: String): JSONObject =
        get(index) as? JSONObject ?: error("$label[$index] must be a JSON object.")

    private fun JSONObject.requiredBoolean(key: String): Boolean =
        get(key) as? Boolean ?: error("$key must be a boolean.")

    private fun JSONObject.requiredInt(key: String): Int {
        val asLong = requiredLong(key)
        require(asLong in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
        return asLong.toInt()
    }

    private fun JSONObject.requiredNonNegativeInt(key: String): Int =
        requiredInt(key).also { require(it >= 0) }

    private fun JSONObject.requiredLong(key: String): Long {
        val number = get(key) as? Number ?: error("$key must be an integer.")
        val asLong = number.toLong()
        require(number.toDouble().isFinite() && number.toDouble() == asLong.toDouble())
        return asLong
    }

    private fun JSONObject.requiredNonNegativeLong(key: String): Long =
        requiredLong(key).also { require(it >= 0L) }

    private fun JSONObject.requiredLongInDay(key: String): Long =
        requiredLong(key).also { require(it in 0L..86_399_999L) }

    private fun JSONObject.requiredFiniteDouble(key: String): Double {
        val number = get(key) as? Number ?: error("$key must be a number.")
        return number.toDouble().also { require(it.isFinite()) }
    }

    private fun JSONObject.requiredNonBlankString(key: String): String {
        val value = get(key) as? String ?: error("$key must be a string.")
        require(value.isNotEmpty())
        requireCanonicalString(value, key)
        return value
    }

    private fun JSONObject.requiredStringAllowEmpty(key: String): String {
        val value = get(key) as? String ?: error("$key must be a string.")
        requireCanonicalString(value, key)
        return value
    }

    private fun JSONObject.requiredExactString(key: String, expected: String): String =
        requiredNonBlankString(key).also { require(it == expected) }

    private fun JSONObject.requiredDosingChannelKey(key: String): String =
        requiredNonBlankString(key).also { requireCanonicalDosingChannelKey(it, key) }

    private fun JSONObject.optionalCanonicalString(key: String): String? =
        if (has(key)) requiredStringAllowEmpty(key) else null

    private fun JSONObject.requiredWeekdays(key: String): List<Boolean> {
        val weekdays = requiredArray(key)
        require(weekdays.length() == 7) {
            "$key must contain exactly 7 booleans."
        }
        return List(7) { index ->
            weekdays.get(index) as? Boolean
                ?: error("$key[$index] must be a boolean.")
        }
    }

    private fun JSONObject.requiredRuntimeTransport(): String =
        requiredExactString("runtimeTransport", RUNTIME_TRANSPORT)

    private fun JSONObject.requiredCommand(expected: String): String =
        requiredExactString("command", expected)

    private fun JSONObject.requiredStatusEvent(): String =
        requiredExactString("event", AqlWsContract.Event.STATUS_CHANGED)

    private fun requireCanonicalString(value: String, key: String) {
        require(value == value.trim()) {
            "$key must not contain surrounding whitespace."
        }
        require(value.none(Char::isISOControl)) {
            "$key must not contain control characters."
        }
    }

    private fun requireCanonicalDosingChannelKey(value: String, key: String) {
        requireCanonicalString(value, key)
        require(value == value.lowercase()) { "$key must use canonical lowercase form." }
        require(value != "-" && value != "none") {
            "$key must target a configured dosing pump."
        }
    }

    private fun requireCalibrationDuration(value: Long) {
        require(
            value in
                DeviceDosingRuntimeContract.Limit.MIN_CALIBRATION_DURATION_MS..
                DeviceDosingRuntimeContract.Limit.MAX_CALIBRATION_DURATION_MS
        )
    }

    private fun requireSafeDoseMsPerMl(value: Long) {
        require(value in 1L..MAX_DOSE_MS_PER_ML)
    }

    private fun <T> requireUnique(values: List<T>, label: String) {
        require(values.toSet().size == values.size) {
            "$label values must be unique."
        }
    }

    private fun requireTrue(value: Boolean) {
        require(value)
    }

    private fun requireFalse(value: Boolean) {
        require(!value)
    }

    private const val DOSING_SCHEMA = "aqualight.dosing.v1"
    private const val DOSING_UNIT = "ml"
    private const val RUNTIME_TRANSPORT = "websocket"
    private const val MIN_MANUAL_DOSE_DURATION_MS = 100L
    private const val MAX_MANUAL_DOSE_DURATION_MS = 3_600_000L
    private const val MAX_DOSE_MS_PER_ML = 3_600_000L

    private const val DOSING_CONFIG_APPLY_COMMAND = "dosing.config.apply"
    private const val DOSING_PRIME_START_COMMAND = "dosing.prime.start"
    private const val DOSING_PRIME_STOP_COMMAND = "dosing.prime.stop"
    private const val DOSING_CALIBRATION_START_COMMAND = "dosing.calibration.start"
    private const val DOSING_CALIBRATION_FINISH_COMMAND = "dosing.calibration.finish"
    private const val DOSING_CALIBRATION_CONFIRM_COMMAND = "dosing.calibration.confirm"
    private const val DOSING_CALIBRATION_CANCEL_COMMAND = "dosing.calibration.cancel"
    private const val DOSING_DOSE_NOW_COMMAND = "dosing.dose.now"
    private const val DOSING_DOSE_STOP_COMMAND = "dosing.dose.stop"
    private const val DOSING_RESERVOIR_REFILL_COMMAND = "dosing.reservoir.refill"

    private val CHANNEL_KINDS = setOf("gpio", "digital", "none")

    private val STATUS_KEYS = setOf(
        "supported", "channelCount", "scheduleCount", "lockLoop", "schema", "rootName",
        "unit", "uptimeMs", "channels", "schedules", "runtime"
    )
    private val RUNTIME_KEYS = setOf(
        "module", "readOnly", "supportsConfigApply", "supportsSchedules", "supportsChannels",
        "supportsPrime", "supportsManualDose", "supportsCalibrationWorkflow",
        "supportsReservoirRefill", "event"
    )
    private val CHANNEL_KEYS = setOf(
        "index", "key", "name", "displayName", "profileManaged", "regime", "channelKind",
        "gpio", "ledcChannel", "group", "valueNow", "valueAuto", "valueManual",
        "manualTimeoutMs", "invert", "pwmResolutionBits", "pwmFrequencyHz", "dosing",
        "editable"
    )
    private val CHANNEL_SNAPSHOT_KEYS = CHANNEL_KEYS + "listIndex"
    private val DOSING_STATUS_KEYS = setOf(
        "unit", "doseMsPerMl", "lastCalibratedAt", "calibrated",
        "reservoirTrackingEnabled", "reservoirCapacityMl", "reservoirRemainingMl",
        "reservoirRemainingPercent"
    )
    private val EDITABLE_KEYS = setOf(
        "hardware", "displayName", "hardwareCalibration", "dosingCalibration", "reservoir"
    )
    private val SCHEDULE_KEYS = setOf(
        "index", "enabled", "runtimeEnabled", "name", "channelKey", "bound", "group",
        "weekdays", "startTimeMs", "startTime", "intervalOnMs", "intervalOn",
        "intervalOffMs", "intervalOff", "repeatCount", "amountMl", "pulseCountRuntime",
        "pulseOffPending", "pulseRemainingMs"
    )

    private val CONFIG_APPLY_KEYS = setOf(
        "operation", "changed", "saved", "saveRequested", "runtimeTransport", "command",
        "event", "appliedChannels", "appliedSchedules", "config"
    )
    private val MANUAL_PUMP_KEYS = setOf(
        "operation", "channelKey", "manualActive", "saved", "runtimeTransport", "command",
        "event", "channel"
    )
    private val DOSE_NOW_KEYS = setOf(
        "operation", "channelKey", "amountMl", "durationMs", "doseMsPerMl",
        "usePendingCalibration", "manualActive", "saved", "runtimeTransport", "command",
        "event", "channel"
    )
    private val CALIBRATION_START_KEYS = setOf(
        "operation", "channelKey", "durationMs", "manualActive", "saved",
        "runtimeTransport", "command", "event"
    )
    private val CALIBRATION_FINISH_KEYS = setOf(
        "operation", "channelKey", "measuredMl", "durationMs", "pendingDoseMsPerMl",
        "pending", "saved", "runtimeTransport", "command", "event", "channel"
    )
    private val CALIBRATION_CONFIRM_KEYS = setOf(
        "operation", "channelKey", "doseMsPerMl", "lastCalibratedAt", "saved",
        "runtimeTransport", "command", "event", "channel"
    )
    private val CALIBRATION_CANCEL_KEYS = setOf(
        "operation", "channelKey", "restoredPreviousCalibration", "saved",
        "runtimeTransport", "command", "event", "channel"
    )
    private val RESERVOIR_REFILL_KEYS = setOf(
        "operation", "channelKey", "changed", "reservoirRemainingMlBefore",
        "reservoirRemainingMl", "reservoirCapacityMl", "persisted", "saved",
        "runtimeTransport", "command", "event", "channel"
    )

    private val CONFIG_SNAPSHOT_KEYS = setOf("channels", "schedules")
    private val CONFIG_CHANNEL_REQUIRED_KEYS = setOf("channelKey", "regime")
    private val CONFIG_CHANNEL_ALLOWED_KEYS =
        CONFIG_CHANNEL_REQUIRED_KEYS + setOf("displayName", "dosing")
    private val DOSING_CALIBRATION_KEYS = setOf("doseMsPerMl", "lastCalibratedAt")
    private val DOSING_RESERVOIR_KEYS =
        setOf("reservoirTrackingEnabled", "reservoirCapacityMl")
    private val DOSING_CONFIG_ALLOWED_KEYS = DOSING_CALIBRATION_KEYS + DOSING_RESERVOIR_KEYS
    private val CONFIG_SCHEDULE_KEYS = setOf(
        "enabled", "name", "channelKey", "weekdays", "startTimeMs", "intervalOnMs",
        "intervalOffMs", "repeatCount", "amountMl"
    )
}

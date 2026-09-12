package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONArray
import org.json.JSONObject

internal object DeviceLightV1JsonParser {
    fun parseScene(data: JSONObject, product: DeviceLightProduct, label: String): DeviceLightScene {
        data.requireLightKeys(product.sceneFields.toSet(), label)
        val values = linkedMapOf<String, Int>()
        product.sceneFields.forEach { field ->
            values[field] = data.requireLightInt(field, 0, 100)
        }
        return DeviceLightScene(product, values)
    }

    fun parseChannels(data: JSONArray, product: DeviceLightProduct): List<DeviceLightChannelDescriptor> {
        require(data.length() == product.channelCount) {
            "Light channel descriptor count differs from the product contract."
        }
        val channels = List(data.length()) { index ->
            val item = data.requireLightObject(index)
            item.requireLightKeys(CHANNEL_KEYS, "light.status.channels[$index]")
            DeviceLightChannelDescriptor(
                key = item.requireLightText("key"),
                percentField = item.requireLightText("percentField"),
                displayName = item.requireLightText("displayName"),
                displayColorRgb = item.requireLightInt("displayColorRgb", 0, 0xFFFFFF),
                order = item.requireLightInt("order", 0, product.channelCount - 1)
            )
        }
        val expected = when (product) {
            DeviceLightProduct.WRGB_PRO_ELITE -> listOf(
                DeviceLightChannelDescriptor("red", "redPercent", "Red", 0xFF0000, 0),
                DeviceLightChannelDescriptor("green", "greenPercent", "Green", 0x00FF00, 1),
                DeviceLightChannelDescriptor("blue", "bluePercent", "Blue", 0x0000FF, 2),
                DeviceLightChannelDescriptor("white", "whitePercent", "White", 0xFFFFFF, 3)
            )
            DeviceLightProduct.RGB_PRO_SLIM -> listOf(
                DeviceLightChannelDescriptor("red", "redPercent", "Red", 0xFF0000, 0),
                DeviceLightChannelDescriptor("green", "greenPercent", "Green", 0x00FF00, 1),
                DeviceLightChannelDescriptor("blue", "bluePercent", "Blue", 0x0000FF, 2)
            )
        }
        require(channels == expected) { "Light channel descriptors differ from firmware." }
        return channels
    }

    fun parseFeatures(data: JSONObject, product: DeviceLightProduct): DeviceLightFeatures {
        data.requireLightKeys(FEATURE_KEYS, "light.status.features")
        val features = DeviceLightFeatures(
            acclimation = data.requireLightBoolean("acclimation"),
            fanControl = data.requireLightBoolean("fanControl"),
            temperatureSensor = data.requireLightBoolean("temperatureSensor"),
            thermal = data.requireLightBoolean("thermal"),
            estimatedPower = data.requireLightBoolean("estimatedPower"),
            estimatedColor = data.requireLightBoolean("estimatedColor")
        )
        val expected = when (product) {
            DeviceLightProduct.WRGB_PRO_ELITE -> DeviceLightFeatures(
                acclimation = true,
                fanControl = true,
                temperatureSensor = true,
                thermal = true,
                estimatedPower = true,
                estimatedColor = true
            )
            DeviceLightProduct.RGB_PRO_SLIM -> DeviceLightFeatures(
                acclimation = false,
                fanControl = false,
                temperatureSensor = false,
                thermal = false,
                estimatedPower = false,
                estimatedColor = false
            )
        }
        require(features == expected) { "Light features differ from the product contract." }
        return features
    }

    fun parseScales(data: JSONObject): DeviceLightScales {
        data.requireLightKeys(SCALE_KEYS, "light.status.scales")
        return DeviceLightScales(
            acclimation = data.requireLightInt("acclimation", 0, 1000),
            thermal = data.requireLightInt("thermal", 0, 1000),
            powerLimit = data.requireLightInt("powerLimit", 0, 1000)
        )
    }

    fun parseElectricalDesign(
        data: JSONObject,
        product: DeviceLightProduct
    ): DeviceLightElectricalDesign {
        data.requireLightKeys(ELECTRICAL_KEYS, "light.status.electricalDesign")
        val result = DeviceLightElectricalDesign(
            available = data.requireLightBoolean("available"),
            contractRevision = data.requireNullableLightInt("contractRevision", 0),
            fixtureLengthMm = data.requireNullableLightInt("fixtureLengthMm", 0),
            maximumFixtureInputPowerW = data.requireNullableLightDouble(
                "maximumFixtureInputPowerW",
                0.0
            ),
            minimumAdapterContinuousPowerW = data.requireNullableLightDouble(
                "minimumAdapterContinuousPowerW",
                0.0
            ),
            maximumLedElectricalPowerW = data.requireNullableLightDouble(
                "maximumLedElectricalPowerW",
                0.0
            ),
            nominalLedElectricalPowerW = data.requireNullableLightDouble(
                "nominalLedElectricalPowerW",
                0.0
            )
        )
        val details = listOfNotNull(
            result.contractRevision,
            result.fixtureLengthMm,
            result.maximumFixtureInputPowerW,
            result.minimumAdapterContinuousPowerW,
            result.maximumLedElectricalPowerW,
            result.nominalLedElectricalPowerW
        )
        require(result.available == (product == DeviceLightProduct.WRGB_PRO_ELITE))
        require(if (result.available) details.size == 6 else details.isEmpty())
        return result
    }

    fun parsePower(data: JSONObject, features: DeviceLightFeatures): DeviceLightPowerStatus {
        data.requireLightKeys(POWER_KEYS, "light.status.power")
        val result = DeviceLightPowerStatus(
            available = data.requireLightBoolean("available"),
            source = DeviceLightModelSource.fromWireExact(data.requireLightText("source")),
            estimatedLedPowerW = data.requireNullableLightDouble("estimatedLedPowerW", 0.0),
            estimatedFixturePowerAvailable = data.requireLightBoolean(
                "estimatedFixturePowerAvailable"
            ),
            estimatedFixturePowerW = data.requireNullableLightDouble(
                "estimatedFixturePowerW",
                0.0
            ),
            hardLedPowerLimitW = data.requireNullableLightDouble("hardLedPowerLimitW", 0.0),
            hardFixturePowerLimitAvailable = data.requireLightBoolean(
                "hardFixturePowerLimitAvailable"
            ),
            hardFixturePowerLimitW = data.requireNullableLightDouble(
                "hardFixturePowerLimitW",
                0.0
            ),
            ratioAvailable = data.requireLightBoolean("ratioAvailable"),
            ratio = data.requireNullableLightDouble("ratio", 0.0),
            limited = data.requireLightBoolean("limited"),
            limitScale = data.requireLightInt("limitScale", 0, 1000),
            limitBasis = DeviceLightPowerLimitBasis.fromWireExact(
                data.requireLightText("limitBasis")
            ),
            modelRevision = data.requireNullableLightInt("modelRevision", 0)
        )
        require(result.available == features.estimatedPower)
        require(result.available == (result.source == DeviceLightModelSource.DESIGN_NOMINAL))
        require((result.estimatedFixturePowerW != null) == result.estimatedFixturePowerAvailable)
        require((result.hardFixturePowerLimitW != null) == result.hardFixturePowerLimitAvailable)
        require((result.ratio != null) == result.ratioAvailable)
        require((result.estimatedLedPowerW != null) == result.available)
        require((result.hardLedPowerLimitW != null) == result.available)
        require((result.modelRevision != null) == result.available)
        return result
    }

    fun parseColor(data: JSONObject, features: DeviceLightFeatures): DeviceLightColorStatus {
        data.requireLightKeys(COLOR_KEYS, "light.status.color")
        val displayRgb = data.requireNullableLightObject("displayRgb")?.let { rgb ->
            rgb.requireLightKeys(DISPLAY_RGB_KEYS, "light.status.color.displayRgb")
            DeviceLightDisplayRgb(
                red = rgb.requireLightInt("red", 0, 255),
                green = rgb.requireLightInt("green", 0, 255),
                blue = rgb.requireLightInt("blue", 0, 255)
            )
        }
        val result = DeviceLightColorStatus(
            available = data.requireLightBoolean("available"),
            source = DeviceLightModelSource.fromWireExact(data.requireLightText("source")),
            cieX = data.requireNullableLightDouble("cieX"),
            cieY = data.requireNullableLightDouble("cieY"),
            cctAvailable = data.requireLightBoolean("cctAvailable"),
            estimatedCctK = data.requireNullableLightInt("estimatedCctK", 0),
            duvAvailable = data.requireLightBoolean("duvAvailable"),
            duv = data.requireNullableLightDouble("duv"),
            displayRgb = displayRgb,
            modelRevision = data.requireNullableLightInt("modelRevision", 0)
        )
        require(result.available == features.estimatedColor)
        require(result.available == (result.source == DeviceLightModelSource.DESIGN_NOMINAL))
        require((result.cieX != null) == result.available)
        require((result.cieY != null) == result.available)
        require((result.displayRgb != null) == result.available)
        require((result.modelRevision != null) == result.available)
        require((result.estimatedCctK != null) == result.cctAvailable)
        require((result.duv != null) == result.duvAvailable)
        return result
    }

    fun parsePolicy(data: JSONObject, product: DeviceLightProduct): DeviceLightPolicy {
        data.requireLightKeys(POLICY_KEYS, "light.status.policy")
        val auto = data.requireLightObject("auto")
        auto.requireLightKeys(AUTO_POLICY_KEYS, "light.status.policy.auto")
        val autoPolicy = DeviceLightAutoPolicy(
            capacity = auto.requireLightInt("capacity", 0),
            timeStepMs = auto.requireLightLong("timeStepMs", 1),
            rampDurationsMs = auto.requireLightArray("rampDurationsMs").toLightLongList(0)
        )
        require(autoPolicy.capacity == DeviceLightRuntimeContract.Limit.AUTO_PROGRAM_CAPACITY)
        require(autoPolicy.timeStepMs == 60_000L)
        require(autoPolicy.rampDurationsMs == EXPECTED_RAMPS)

        val custom = data.requireLightObject("custom")
        custom.requireLightKeys(CUSTOM_POLICY_KEYS, "light.status.policy.custom")
        val customPolicy = DeviceLightCustomPolicy(
            maxPoints = custom.requireLightInt("maxPoints", 0),
            timeStepMs = custom.requireLightLong("timeStepMs", 1)
        )
        require(customPolicy.maxPoints == DeviceLightRuntimeContract.Limit.CUSTOM_POINT_CAPACITY)
        require(customPolicy.timeStepMs == 60_000L)

        val acclimation = parseAcclimationPolicy(data.requireLightObject("acclimation"))
        require(acclimation.supported == product.supportsAcclimation)
        return DeviceLightPolicy(autoPolicy, customPolicy, acclimation)
    }

    private fun parseAcclimationPolicy(data: JSONObject): DeviceLightAcclimationPolicy {
        data.requireLightKeys(ACCLIMATION_POLICY_KEYS, "light.status.policy.acclimation")
        val result = DeviceLightAcclimationPolicy(
            supported = data.requireLightBoolean("supported"),
            startPercentMin = data.requireNullableLightInt("startPercentMin"),
            startPercentMax = data.requireNullableLightInt("startPercentMax"),
            startPercentStep = data.requireNullableLightInt("startPercentStep"),
            defaultStartPercent = data.requireNullableLightInt("defaultStartPercent"),
            durationDaysMin = data.requireNullableLightInt("durationDaysMin"),
            durationDaysMax = data.requireNullableLightInt("durationDaysMax"),
            durationDaysStep = data.requireNullableLightInt("durationDaysStep"),
            defaultDurationDays = data.requireNullableLightInt("defaultDurationDays"),
            targetPercent = data.requireNullableLightInt("targetPercent")
        )
        val actual = listOf(
            result.startPercentMin,
            result.startPercentMax,
            result.startPercentStep,
            result.defaultStartPercent,
            result.durationDaysMin,
            result.durationDaysMax,
            result.durationDaysStep,
            result.defaultDurationDays,
            result.targetPercent
        )
        val expected = listOf(20, 90, 5, 50, 7, 90, 1, 30, 100)
        require(if (result.supported) actual == expected else actual.all { it == null })
        return result
    }

    fun parseScheduler(data: JSONObject): DeviceLightSchedulerStatus {
        data.requireLightKeys(SCHEDULER_KEYS, "light.status.scheduler")
        val result = DeviceLightSchedulerStatus(
            ready = data.requireLightBoolean("ready"),
            reason = data.requireLightText("reason"),
            generation = data.requireNullableLightLong("generation", 0),
            localDate = data.requireNullableLightText("localDate"),
            currentWeekdayMask = data.requireLightInt("currentWeekdayMask", 0, 127),
            currentTimeMs = data.requireNullableLightLong(
                "currentTimeMs",
                0,
                DeviceLightRuntimeContract.Limit.LAST_DAY_MILLISECOND
            )
        )
        require(result.reason == if (result.ready) "OK" else "RTC_NOT_READY")
        require(
            if (result.ready) {
                result.generation != null &&
                    result.localDate?.matches(DATE_PATTERN) == true &&
                    result.currentWeekdayMask in WEEKDAY_BITS &&
                    result.currentTimeMs != null
            } else {
                result.generation == null && result.localDate == null &&
                    result.currentWeekdayMask == 0 && result.currentTimeMs == null
            }
        )
        return result
    }

    fun parseAutoSummary(data: JSONObject): DeviceLightAutoSummary {
        data.requireLightKeys(AUTO_SUMMARY_KEYS, "light.status.auto")
        val result = DeviceLightAutoSummary(
            revision = data.requireLightLong("revision", 0, UINT32_MAX),
            programCount = data.requireLightInt("programCount", 0, 16),
            enabledCount = data.requireLightInt("enabledCount", 0, 16),
            runtimeState = DeviceLightAutoRuntimeState.fromWireExact(
                data.requireLightText("runtimeState")
            ),
            activeProgramId = data.requireNullableLightText("activeProgramId")
        )
        require(result.enabledCount <= result.programCount)
        result.activeProgramId?.let(::requireLightProgramId)
        return result
    }

    fun parseCustomSummary(data: JSONObject): DeviceLightCustomSummary {
        data.requireLightKeys(CUSTOM_SUMMARY_KEYS, "light.status.custom")
        val result = DeviceLightCustomSummary(
            revision = data.requireLightLong("revision", 0, UINT32_MAX),
            installed = data.requireLightBoolean("installed"),
            weekdaysMask = data.requireLightInt("weekdaysMask", 0, 127),
            pointCount = data.requireLightInt("pointCount", 0, 96),
            runtimeState = DeviceLightCustomRuntimeState.fromWireExact(
                data.requireLightText("runtimeState")
            )
        )
        require(result.installed || (result.weekdaysMask == 0 && result.pointCount == 0))
        return result
    }

    fun parseAcclimation(data: JSONObject, product: DeviceLightProduct): DeviceLightAcclimationStatus {
        data.requireLightKeys(ACCLIMATION_KEYS, "light acclimation")
        val result = DeviceLightAcclimationStatus(
            supported = data.requireLightBoolean("supported"),
            revision = data.requireNullableLightLong("revision", 0, UINT32_MAX),
            state = data.requireNullableLightText("state")?.let(
                DeviceLightAcclimationState::fromWireExact
            ),
            clockReady = data.requireNullableLightBoolean("clockReady"),
            startPercent = data.requireNullableLightInt("startPercent", 0, 100),
            currentPermille = data.requireNullableLightInt("currentPermille", 0, 1000),
            targetPercent = data.requireNullableLightInt("targetPercent", 0, 100),
            durationDays = data.requireNullableLightInt("durationDays", 0),
            startedAtEpochSeconds = data.requireNullableLightLong("startedAtEpochSeconds", 0),
            endsAtEpochSeconds = data.requireNullableLightLong("endsAtEpochSeconds", 0),
            remainingSeconds = data.requireNullableLightLong("remainingSeconds", 0)
        )
        require(result.supported == product.supportsAcclimation)
        val detail = listOf(
            result.revision,
            result.state,
            result.clockReady,
            result.startPercent,
            result.currentPermille,
            result.targetPercent,
            result.durationDays,
            result.startedAtEpochSeconds,
            result.endsAtEpochSeconds,
            result.remainingSeconds
        )
        if (!result.supported) {
            require(detail.all { it == null })
        } else {
            require(result.revision != null && result.state != null && result.clockReady != null)
            val startPercent = requireNotNull(result.startPercent)
            val durationDays = requireNotNull(result.durationDays)
            require(startPercent in 20..90 && startPercent % 5 == 0)
            require(result.targetPercent == 100)
            require(durationDays in 7..90)
            require(result.startedAtEpochSeconds != null && result.endsAtEpochSeconds != null)
            if (result.state == DeviceLightAcclimationState.ACTIVE && result.clockReady == false) {
                require(result.currentPermille == null && result.remainingSeconds == null)
            } else {
                require(result.currentPermille != null && result.remainingSeconds != null)
            }
        }
        return result
    }

    fun parseRuntime(data: JSONObject, product: DeviceLightProduct): DeviceLightRuntimeStatus {
        data.requireLightKeys(RUNTIME_KEYS, "light.status.runtime")
        val result = DeviceLightRuntimeStatus(
            rtcReady = data.requireLightBoolean("rtcReady"),
            physicalChannelCount = data.requireLightInt("physicalChannelCount", 0),
            physicalOutputHealthy = data.requireLightBoolean("physicalOutputHealthy"),
            event = data.requireLightText("event")
        )
        require(result.physicalChannelCount == product.channelCount)
        require(result.event == DeviceLightRuntimeContract.Event.STATUS_CHANGED)
        return result
    }

    fun parseProgram(data: JSONObject, product: DeviceLightProduct): DeviceLightAutoProgram {
        data.requireLightKeys(PROGRAM_KEYS, "Light AUTO program")
        return DeviceLightAutoProgram(
            programId = data.requireLightText("programId").also(::requireLightProgramId),
            enabled = data.requireLightBoolean("enabled"),
            weekdaysMask = data.requireLightInt("weekdaysMask", 1, 127),
            startTimeMs = data.requireLightLong(
                "startTimeMs",
                0,
                DeviceLightRuntimeContract.Limit.LAST_DAY_MILLISECOND
            ),
            endTimeMs = data.requireLightLong(
                "endTimeMs",
                0,
                DeviceLightRuntimeContract.Limit.LAST_DAY_MILLISECOND
            ),
            rampDurationMs = data.requireLightLong("rampDurationMs", 0),
            scene = parseScene(data.requireLightObject("scene"), product, "Light AUTO scene")
        ).also { program ->
            require(program.startTimeMs != program.endTimeMs)
            require(program.rampDurationMs in EXPECTED_RAMPS)
        }
    }

    fun parseCustomPoint(tuple: JSONArray, product: DeviceLightProduct): DeviceLightCustomPoint {
        require(tuple.length() == product.channelCount + 1)
        val values = linkedMapOf<String, Int>()
        product.sceneFields.forEachIndexed { index, field ->
            values[field] = tuple.requireLightInt(index + 1, 0, 100)
        }
        return DeviceLightCustomPoint(
            timeMs = tuple.requireLightLong(
                0,
                0,
                DeviceLightRuntimeContract.Limit.LAST_DAY_MILLISECOND
            ),
            scene = DeviceLightScene(product, values)
        )
    }

    private val CHANNEL_KEYS = setOf("key", "percentField", "displayName", "displayColorRgb", "order")
    private val FEATURE_KEYS = setOf(
        "acclimation", "fanControl", "temperatureSensor", "thermal", "estimatedPower", "estimatedColor"
    )
    private val SCALE_KEYS = setOf("acclimation", "thermal", "powerLimit")
    private val ELECTRICAL_KEYS = setOf(
        "available", "contractRevision", "fixtureLengthMm", "maximumFixtureInputPowerW",
        "minimumAdapterContinuousPowerW", "maximumLedElectricalPowerW", "nominalLedElectricalPowerW"
    )
    private val POWER_KEYS = setOf(
        "available", "source", "estimatedLedPowerW", "estimatedFixturePowerAvailable",
        "estimatedFixturePowerW", "hardLedPowerLimitW", "hardFixturePowerLimitAvailable",
        "hardFixturePowerLimitW", "ratioAvailable", "ratio", "limited", "limitScale",
        "limitBasis", "modelRevision"
    )
    private val COLOR_KEYS = setOf(
        "available", "source", "cieX", "cieY", "cctAvailable", "estimatedCctK",
        "duvAvailable", "duv", "displayRgb", "modelRevision"
    )
    private val DISPLAY_RGB_KEYS = setOf("red", "green", "blue")
    private val POLICY_KEYS = setOf("auto", "custom", "acclimation")
    private val AUTO_POLICY_KEYS = setOf("capacity", "timeStepMs", "rampDurationsMs")
    private val CUSTOM_POLICY_KEYS = setOf("maxPoints", "timeStepMs")
    private val ACCLIMATION_POLICY_KEYS = setOf(
        "supported", "startPercentMin", "startPercentMax", "startPercentStep",
        "defaultStartPercent", "durationDaysMin", "durationDaysMax", "durationDaysStep",
        "defaultDurationDays", "targetPercent"
    )
    private val SCHEDULER_KEYS = setOf(
        "ready", "reason", "generation", "localDate", "currentWeekdayMask", "currentTimeMs"
    )
    private val AUTO_SUMMARY_KEYS = setOf(
        "revision", "programCount", "enabledCount", "runtimeState", "activeProgramId"
    )
    private val CUSTOM_SUMMARY_KEYS = setOf(
        "revision", "installed", "weekdaysMask", "pointCount", "runtimeState"
    )
    private val ACCLIMATION_KEYS = setOf(
        "supported", "revision", "state", "clockReady", "startPercent", "currentPermille",
        "targetPercent", "durationDays", "startedAtEpochSeconds", "endsAtEpochSeconds",
        "remainingSeconds"
    )
    private val RUNTIME_KEYS = setOf(
        "rtcReady", "physicalChannelCount", "physicalOutputHealthy", "event"
    )
    private val PROGRAM_KEYS = setOf(
        "programId", "enabled", "weekdaysMask", "startTimeMs", "endTimeMs",
        "rampDurationMs", "scene"
    )
    private val EXPECTED_RAMPS = listOf(
        0L, 1_800_000L, 3_600_000L, 5_400_000L, 7_200_000L, 9_000_000L
    )
    private val WEEKDAY_BITS = setOf(1, 2, 4, 8, 16, 32, 64)
    private val DATE_PATTERN = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    private val PROGRAM_ID = Regex("^ap-[0-9a-f]{8}$")
    private const val UINT32_MAX = 4_294_967_295L

    private fun requireLightProgramId(value: String) =
        require(PROGRAM_ID.matches(value)) { "Invalid Light AUTO programId." }
}

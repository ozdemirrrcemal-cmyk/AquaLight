package com.aqua.aqualight.application.devices.light.smartsetup

import com.aqua.aqualight.application.aquarium.lighting.AquariumObservationSeverity
import com.aqua.aqualight.application.aquarium.lighting.Co2Status
import com.aqua.aqualight.application.aquarium.lighting.PlantDensity
import com.aqua.aqualight.application.aquarium.lighting.PlantLightDemand
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.roundToInt

/** Deterministic, side-effect-free author of bounded firmware-managed Light plans. */
@Suppress(
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "LargeClass",
    "LongMethod",
    "LongParameterList",
    "MagicNumber",
    "ReturnCount",
    "TooManyFunctions"
)
object SmartSetupDecisionEngine {

    fun decide(input: SmartSetupInput): SmartSetupDecision {
        unsupportedDecision(input)?.let { return it }
        val missing = missingFields(input)
        if (missing.isNotEmpty()) return SmartSetupDecision.MissingData(missing)

        val calibration = checkNotNull(input.calibrationProfile)
        installationUnsupported(input, calibration)?.let { return it }
        val generated = generate(input, calibration)
        return SmartSetupDecision.Ready(generated)
    }

    private fun unsupportedDecision(input: SmartSetupInput): SmartSetupDecision.Unsupported? {
        when (input.aquariumEnvironment) {
            SmartSetupAquariumEnvironment.UNKNOWN -> return unsupported(
                SmartSetupUnsupportedReason.UNKNOWN_AQUARIUM_ENVIRONMENT
            )
            SmartSetupAquariumEnvironment.MARINE -> return unsupported(
                SmartSetupUnsupportedReason.MARINE_EVIDENCE_NOT_AVAILABLE
            )
            SmartSetupAquariumEnvironment.FRESHWATER -> Unit
        }
        if (input.deviceProductKey !in SmartSetupCalibrationCatalog.knownProducts) {
            return unsupported(
                SmartSetupUnsupportedReason.UNKNOWN_DEVICE_PRODUCT,
                input.deviceProductKey
            )
        }
        if (input.deviceProductKey == SmartSetupCalibrationCatalog.RGB_PRO_SLIM_PRODUCT) {
            return unsupported(
                SmartSetupUnsupportedReason.CALIBRATION_NOT_AVAILABLE_FOR_PRODUCT,
                input.deviceProductKey
            )
        }
        val reviewedCalibration =
            SmartSetupCalibrationCatalog.reviewedProfileFor(input.deviceProductKey)
                ?: return unsupported(
                    SmartSetupUnsupportedReason.CALIBRATION_NOT_AVAILABLE_FOR_PRODUCT,
                    input.deviceProductKey
                )
        if (input.reportedCalibrationRevision == null) {
            return unsupported(
                SmartSetupUnsupportedReason.DEVICE_CALIBRATION_METADATA_MISSING,
                input.deviceProductKey
            )
        }
        if (input.reportedCalibrationRevision != reviewedCalibration.revision) {
            return unsupported(
                SmartSetupUnsupportedReason.CALIBRATION_PROFILE_MISMATCH,
                "reportedRevision=${input.reportedCalibrationRevision}",
                "expectedRevision=${reviewedCalibration.revision}"
            )
        }
        val reviewedChannelKeys = reviewedCalibration.channels.map { channel -> channel.sceneKey }
        if (input.reportedChannelSceneKeys != reviewedChannelKeys) {
            return unsupported(
                SmartSetupUnsupportedReason.UNSUPPORTED_CHANNEL_SET,
                "actual=${input.reportedChannelSceneKeys.joinToString(",")}",
                "expected=${reviewedChannelKeys.joinToString(",")}"
            )
        }
        val evaluationDay = input.evaluationEpochDay
        val setupDate = input.setupDateEpochDay
        if ((evaluationDay != null && evaluationDay !in MIN_EPOCH_DAY..MAX_EPOCH_DAY) ||
            (setupDate != null && setupDate !in MIN_EPOCH_DAY..MAX_EPOCH_DAY) ||
            (setupDate != null && setupDate > MAX_EPOCH_DAY -
                SmartSetupLifecycleClassifier.ESTABLISHING_END_DAY)
        ) {
            return unsupported(
                SmartSetupUnsupportedReason.CALENDAR_OUTSIDE_FIRMWARE_RANGE,
                "evaluationEpochDay=$evaluationDay",
                "setupDateEpochDay=$setupDate"
            )
        }
        if (evaluationDay != null && setupDate != null) {
            val lifecycle = SmartSetupLifecycleClassifier.classify(setupDate, evaluationDay)
                ?: return unsupported(SmartSetupUnsupportedReason.SETUP_DATE_IN_FUTURE)
            if (input.setupDay != null && input.setupDay != lifecycle.setupDay ||
                input.lifecycleStage != null && input.lifecycleStage != lifecycle.stage
            ) {
                return unsupported(
                    SmartSetupUnsupportedReason.LIFECYCLE_MISMATCH,
                    "expectedDay=${lifecycle.setupDay}",
                    "expectedStage=${lifecycle.stage.name}"
                )
            }
        }
        if (!input.isPlanted &&
            (input.plantDensity != null || input.highestPlantLightDemand != null)
        ) {
            return unsupported(SmartSetupUnsupportedReason.INCONSISTENT_PLANT_FACTS)
        }
        if (input.observationDateEpochDay != null && evaluationDay != null &&
            input.observationDateEpochDay > evaluationDay
        ) {
            return unsupported(SmartSetupUnsupportedReason.OBSERVATION_DATE_IN_FUTURE)
        }
        if (input.observationDateEpochDay != null &&
            input.observationDateEpochDay !in MIN_EPOCH_DAY..MAX_EPOCH_DAY
        ) {
            return unsupported(
                SmartSetupUnsupportedReason.CALENDAR_OUTSIDE_FIRMWARE_RANGE,
                "observationDateEpochDay=${input.observationDateEpochDay}"
            )
        }
        if (input.observationDateEpochDay != null && setupDate != null &&
            input.observationDateEpochDay < setupDate
        ) {
            return unsupported(SmartSetupUnsupportedReason.OBSERVATION_BEFORE_SETUP)
        }
        val calibration = input.calibrationProfile
        if (calibration != null && calibration.productKey != input.deviceProductKey) {
            return unsupported(
                SmartSetupUnsupportedReason.CALIBRATION_PRODUCT_MISMATCH,
                calibration.id,
                input.deviceProductKey
            )
        }
        if (calibration != null && calibration != reviewedCalibration
        ) {
            return unsupported(
                SmartSetupUnsupportedReason.CALIBRATION_PROFILE_MISMATCH,
                "actual=${calibration.id}@${calibration.revision}",
                "expected=${reviewedCalibration.id}"
            )
        }
        return null
    }

    private fun missingFields(input: SmartSetupInput): Set<SmartSetupMissingField> = buildSet {
        if (input.evaluationEpochDay == null) add(SmartSetupMissingField.DEVICE_LOCAL_DATE)
        if (input.setupDateEpochDay == null || input.setupDay == null ||
            input.lifecycleStage == null
        ) {
            add(SmartSetupMissingField.SETUP_DATE)
        }
        if (input.isPlanted && input.plantDensity == null) {
            add(SmartSetupMissingField.PLANT_DENSITY)
        }
        if (input.isPlanted && input.highestPlantLightDemand == null) {
            add(SmartSetupMissingField.HIGHEST_PLANT_LIGHT_DEMAND)
        }
        if (input.co2Status == null) add(SmartSetupMissingField.CO2_STATUS)
        if (input.isActiveSoil == null) add(SmartSetupMissingField.ACTIVE_SOIL)
        if (input.waterDepthCm == null) add(SmartSetupMissingField.WATER_DEPTH)
        if (input.fixtureMountHeightCm == null) {
            add(SmartSetupMissingField.FIXTURE_MOUNT_HEIGHT)
        }
        if (input.preferredViewingStartMinuteOfDay == null ||
            input.preferredViewingEndMinuteOfDay == null
        ) {
            add(SmartSetupMissingField.VIEWING_WINDOW)
        }
        if (input.algaeObservation == null) add(SmartSetupMissingField.ALGAE_OBSERVATION)
        if (input.plantStressObservation == null) {
            add(SmartSetupMissingField.PLANT_STRESS_OBSERVATION)
        }
        if (input.observationDateEpochDay == null) add(SmartSetupMissingField.OBSERVATION_DATE)
        if (input.calibrationProfile == null) add(SmartSetupMissingField.CALIBRATION_PROFILE)

        val evaluation = input.evaluationEpochDay
        val observed = input.observationDateEpochDay
        if (evaluation != null && observed != null &&
            evaluation - observed > MAX_OBSERVATION_AGE_DAYS
        ) {
            add(SmartSetupMissingField.OBSERVATIONS_STALE)
        }
    }

    private fun installationUnsupported(
        input: SmartSetupInput,
        calibration: SmartSetupCalibrationProfile
    ): SmartSetupDecision.Unsupported? {
        val waterDepth = checkNotNull(input.waterDepthCm)
        val mountingHeight = checkNotNull(input.fixtureMountHeightCm)
        if (waterDepth !in calibration.minimumWaterDepthCm..calibration.maximumWaterDepthCm ||
            mountingHeight !in calibration.minimumFixtureMountHeightCm..
            calibration.maximumFixtureMountHeightCm
        ) {
            return unsupported(
                SmartSetupUnsupportedReason.INSTALLATION_OUTSIDE_CALIBRATION,
                "waterDepthCm=$waterDepth",
                "fixtureMountHeightCm=$mountingHeight",
                "calibration=${calibration.id}"
            )
        }
        val start = checkNotNull(input.preferredViewingStartMinuteOfDay)
        val end = checkNotNull(input.preferredViewingEndMinuteOfDay)
        if (start !in 0 until MINUTES_PER_DAY || end !in 0 until MINUTES_PER_DAY ||
            end <= start || end - start < MINIMUM_VIEWING_WINDOW_MINUTES
        ) {
            return unsupported(
                SmartSetupUnsupportedReason.VIEWING_WINDOW_UNSUPPORTED,
                "startMinute=$start",
                "endMinute=$end"
            )
        }
        return null
    }

    private fun generate(
        input: SmartSetupInput,
        calibration: SmartSetupCalibrationProfile
    ): SmartSetupRecommendation {
        val factors = mutableListOf<SmartSetupFactor>()
        val rawIntensity = calculateRawIntensity(input, factors)
        val risk = observationRisk(input, factors)
        val currentStage = checkNotNull(input.lifecycleStage)
        val evaluationDay = checkNotNull(input.evaluationEpochDay)
        val setupDate = checkNotNull(input.setupDateEpochDay)

        factors += factor(
            SmartSetupFactorId.AQUARIUM_STAGE,
            "day=${checkNotNull(input.setupDay)},stage=${currentStage.name}",
            SmartSetupFactorEffect.LIMIT
        )
        factors += factor(
            SmartSetupFactorId.VIEWING_WINDOW,
            "${input.preferredViewingStartMinuteOfDay}-${input.preferredViewingEndMinuteOfDay}",
            SmartSetupFactorEffect.SCHEDULE
        )
        factors += factor(
            SmartSetupFactorId.DEVICE_CALIBRATION,
            calibration.id,
            SmartSetupFactorEffect.LIMIT
        )

        val phases = if (risk.holdUntilReevaluation) {
            listOf(
                createPhase(
                    validFrom = checkNotNull(input.observationDateEpochDay),
                    validUntilExclusive = null,
                    transitionDays = RISK_TRANSITION_DAYS,
                    stageBand = stageBand(currentStage),
                    input = input,
                    calibration = calibration,
                    intensity = stageIntensity(rawIntensity - risk.intensityReduction, currentStage)
                )
            )
        } else {
            createLifecyclePhases(
                input = input,
                calibration = calibration,
                setupDate = setupDate,
                rawIntensity = rawIntensity
            )
        }

        val reevaluationAfter = when {
            risk.significant -> 7L
            risk.present -> 14L
            input.maintenance.overdueRelevantTaskCount > 0 -> 7L
            currentStage == SmartSetupLifecycleStage.STARTUP -> 7L
            currentStage == SmartSetupLifecycleStage.ESTABLISHING -> 14L
            else -> 30L
        }
        val confidence = if (
            checkNotNull(input.observationDateEpochDay) >= evaluationDay - 7L &&
            input.maintenance.overdueRelevantTaskCount == 0 &&
            !risk.present
        ) {
            SmartSetupConfidence.HIGH
        } else {
            SmartSetupConfidence.MODERATE
        }
        val sourceIds = buildSet {
            add(SOURCE_AQUALIGHT_POLICY)
            add(SOURCE_WRGB_CALIBRATION)
            if (input.isPlanted) {
                add(SOURCE_TROPICA_GROWING_IN)
                add(SOURCE_TROPICA_QUICK_GUIDE)
            }
        }
        return SmartSetupRecommendation(
            plan = SmartLightPlanDraft(
                initialStartPercent = initialStartPercent(currentStage, risk),
                phases = phases
            ).also(::requireValidPlan),
            confidence = confidence,
            factors = factors.distinctBy { item -> item.id },
            sourceIds = sourceIds,
            calibrationProfileId = calibration.id,
            profileFingerprint = SmartSetupFingerprint.create(input),
            generatedEpochDay = evaluationDay,
            reevaluationEpochDay = evaluationDay + reevaluationAfter
        )
    }

    private fun calculateRawIntensity(
        input: SmartSetupInput,
        factors: MutableList<SmartSetupFactor>
    ): Int {
        var intensity = if (!input.isPlanted) {
            factors += factor(
                SmartSetupFactorId.UNPLANTED_AQUARIUM,
                "true",
                SmartSetupFactorEffect.LIMIT
            )
            UNPLANTED_BASE_INTENSITY
        } else {
            val demand = checkNotNull(input.highestPlantLightDemand)
            val density = checkNotNull(input.plantDensity)
            var plantedIntensity = when (demand) {
                PlantLightDemand.LOW -> 38
                PlantLightDemand.MEDIUM -> 55
                PlantLightDemand.HIGH -> 68
            }
            factors += factor(
                SmartSetupFactorId.PLANT_LIGHT_DEMAND,
                demand.name,
                SmartSetupFactorEffect.INCREASE
            )
            plantedIntensity += when (density) {
                PlantDensity.LOW -> -3
                PlantDensity.MEDIUM -> 0
                PlantDensity.HIGH -> 5
            }
            factors += factor(
                SmartSetupFactorId.PLANT_DENSITY,
                density.name,
                when (density) {
                    PlantDensity.LOW -> SmartSetupFactorEffect.DECREASE
                    PlantDensity.MEDIUM -> SmartSetupFactorEffect.INFORMATION
                    PlantDensity.HIGH -> SmartSetupFactorEffect.INCREASE
                }
            )
            when (checkNotNull(input.co2Status)) {
                Co2Status.NONE -> plantedIntensity.coerceAtMost(45)
                Co2Status.INSTALLED -> plantedIntensity.coerceAtMost(52)
                Co2Status.ACTIVE -> plantedIntensity + 5
            }
        }
        factors += factor(
            SmartSetupFactorId.CO2_STATE,
            checkNotNull(input.co2Status).name,
            if (input.isPlanted && input.co2Status == Co2Status.ACTIVE) {
                SmartSetupFactorEffect.INCREASE
            } else if (input.isPlanted) {
                SmartSetupFactorEffect.LIMIT
            } else {
                SmartSetupFactorEffect.INFORMATION
            }
        )
        val opticalDistance = checkNotNull(input.waterDepthCm) +
            checkNotNull(input.fixtureMountHeightCm)
        intensity += when (opticalDistance) {
            in 0..35 -> 0
            in 36..55 -> 5
            else -> 10
        }
        factors += factor(
            SmartSetupFactorId.OPTICAL_DISTANCE,
            "${opticalDistance}cm",
            SmartSetupFactorEffect.INCREASE
        )
        factors += factor(
            SmartSetupFactorId.ACTIVE_SOIL,
            checkNotNull(input.isActiveSoil).toString(),
            SmartSetupFactorEffect.INFORMATION
        )
        if (input.isActiveSoil == true &&
            input.lifecycleStage == SmartSetupLifecycleStage.STARTUP
        ) {
            intensity = intensity.coerceAtMost(ACTIVE_SOIL_STARTUP_CAP)
        }
        if (isRecent(input.maintenance.latestAlgaeCleaningEpochDay, input.evaluationEpochDay, 14L)) {
            intensity -= RECENT_ALGAE_MAINTENANCE_REDUCTION
            factors += factor(
                SmartSetupFactorId.RECENT_ALGAE_MAINTENANCE,
                "within14Days",
                SmartSetupFactorEffect.DECREASE
            )
        }
        if (input.maintenance.overdueRelevantTaskCount > 0) {
            intensity -= (input.maintenance.overdueRelevantTaskCount * 2)
                .coerceAtMost(MAX_MAINTENANCE_REDUCTION)
            factors += factor(
                SmartSetupFactorId.OVERDUE_MAINTENANCE,
                input.maintenance.overdueRelevantTaskCount.toString(),
                SmartSetupFactorEffect.DECREASE
            )
        }
        return intensity.coerceIn(MINIMUM_TARGET_INTENSITY, MAXIMUM_TARGET_INTENSITY)
    }

    private fun observationRisk(
        input: SmartSetupInput,
        factors: MutableList<SmartSetupFactor>
    ): ObservationRisk {
        val algae = checkNotNull(input.algaeObservation)
        val stress = checkNotNull(input.plantStressObservation)
        if (algae != AquariumObservationSeverity.NONE) {
            factors += factor(
                SmartSetupFactorId.ALGAE_OBSERVATION,
                algae.name,
                SmartSetupFactorEffect.DECREASE
            )
        }
        if (stress != AquariumObservationSeverity.NONE) {
            factors += factor(
                SmartSetupFactorId.PLANT_STRESS_OBSERVATION,
                stress.name,
                SmartSetupFactorEffect.DECREASE
            )
        }
        val significant = algae == AquariumObservationSeverity.SIGNIFICANT ||
            stress == AquariumObservationSeverity.SIGNIFICANT
        val present = algae != AquariumObservationSeverity.NONE ||
            stress != AquariumObservationSeverity.NONE
        val reduction = listOf(algae, stress).fold(0) { total, severity ->
            total + when (severity) {
                AquariumObservationSeverity.NONE -> 0
                AquariumObservationSeverity.MILD -> 8
                AquariumObservationSeverity.SIGNIFICANT -> 18
            }
        }.coerceAtMost(MAX_OBSERVATION_REDUCTION)
        return ObservationRisk(
            present = present,
            significant = significant,
            holdUntilReevaluation = present,
            intensityReduction = reduction
        )
    }

    private fun createLifecyclePhases(
        input: SmartSetupInput,
        calibration: SmartSetupCalibrationProfile,
        setupDate: Long,
        rawIntensity: Int
    ): List<SmartLightPhaseDraft> {
        val candidates = lifecycleBands(setupDate)
            .map { band ->
                val end = band.validUntilExclusive
                createPhase(
                    validFrom = band.validFrom,
                    validUntilExclusive = end,
                    transitionDays = band.defaultTransitionDays,
                    stageBand = band,
                    input = input,
                    calibration = calibration,
                    intensity = bandIntensity(rawIntensity, band, input.isPlanted)
                )
            }
        require(candidates.isNotEmpty())
        return candidates
    }

    private fun lifecycleBands(setupDate: Long): List<StageBand> = listOf(
        StageBand(
            key = "STARTUP",
            validFrom = setupDate,
            validUntilExclusive = setupDate + SmartSetupLifecycleClassifier.STARTUP_END_DAY,
            photoperiodMinutes = 360,
            intensityCap = 45,
            defaultTransitionDays = 7
        ),
        StageBand(
            key = "ESTABLISHING_EARLY",
            validFrom = setupDate + SmartSetupLifecycleClassifier.STARTUP_END_DAY,
            validUntilExclusive = setupDate + ESTABLISHING_EARLY_END_DAY,
            photoperiodMinutes = 390,
            intensityCap = 52,
            defaultTransitionDays = 14
        ),
        StageBand(
            key = "ESTABLISHING_LATE",
            validFrom = setupDate + ESTABLISHING_EARLY_END_DAY,
            validUntilExclusive = setupDate + SmartSetupLifecycleClassifier.ESTABLISHING_END_DAY,
            photoperiodMinutes = 420,
            intensityCap = 60,
            defaultTransitionDays = 14
        ),
        StageBand(
            key = "MATURE",
            validFrom = setupDate + SmartSetupLifecycleClassifier.ESTABLISHING_END_DAY,
            validUntilExclusive = null,
            photoperiodMinutes = 480,
            intensityCap = 80,
            defaultTransitionDays = 14
        )
    )

    private fun stageBand(stage: SmartSetupLifecycleStage): StageBand = when (stage) {
        SmartSetupLifecycleStage.STARTUP -> StageBand(
            "STARTUP_HOLD", 0, null, 360, 45, RISK_TRANSITION_DAYS
        )
        SmartSetupLifecycleStage.ESTABLISHING -> StageBand(
            "ESTABLISHING_HOLD", 0, null, 390, 52, RISK_TRANSITION_DAYS
        )
        SmartSetupLifecycleStage.MATURE -> StageBand(
            "MATURE_HOLD", 0, null, 420, 65, RISK_TRANSITION_DAYS
        )
    }

    private fun createPhase(
        validFrom: Long,
        validUntilExclusive: Long?,
        transitionDays: Int,
        stageBand: StageBand,
        input: SmartSetupInput,
        calibration: SmartSetupCalibrationProfile,
        intensity: Int
    ): SmartLightPhaseDraft {
        val duration = if (input.isPlanted) {
            stageBand.photoperiodMinutes
        } else {
            minOf(stageBand.photoperiodMinutes, UNPLANTED_PHOTOPERIOD_MINUTES)
        }
        val window = centeredWindow(
            durationMinutes = duration,
            preferredStart = checkNotNull(input.preferredViewingStartMinuteOfDay),
            preferredEnd = checkNotNull(input.preferredViewingEndMinuteOfDay)
        )
        return SmartLightPhaseDraft(
            validFromEpochDay = validFrom,
            validUntilEpochDayExclusive = validUntilExclusive,
            transitionDays = transitionDays,
            weekdaysMask = EVERY_DAY_MASK,
            startTimeMs = window.first * MINUTE_MS,
            endTimeMs = window.second * MINUTE_MS,
            rampDurationMs = RAMP_DURATION_MS,
            scene = createScene(input, calibration, intensity)
        )
    }

    private fun createScene(
        input: SmartSetupInput,
        calibration: SmartSetupCalibrationProfile,
        intensity: Int
    ): SmartLightScene {
        val ratios = when {
            !input.isPlanted -> mapOf(
                SmartSetupChannel.RED to 0.70,
                SmartSetupChannel.GREEN to 0.75,
                SmartSetupChannel.BLUE to 0.85,
                SmartSetupChannel.WHITE to 1.00
            )
            input.highestPlantLightDemand == PlantLightDemand.HIGH -> mapOf(
                SmartSetupChannel.RED to 1.00,
                SmartSetupChannel.GREEN to 0.78,
                SmartSetupChannel.BLUE to 1.00,
                SmartSetupChannel.WHITE to 0.82
            )
            input.highestPlantLightDemand == PlantLightDemand.MEDIUM -> mapOf(
                SmartSetupChannel.RED to 0.95,
                SmartSetupChannel.GREEN to 0.85,
                SmartSetupChannel.BLUE to 1.00,
                SmartSetupChannel.WHITE to 0.95
            )
            else -> mapOf(
                SmartSetupChannel.RED to 0.80,
                SmartSetupChannel.GREEN to 0.85,
                SmartSetupChannel.BLUE to 0.90,
                SmartSetupChannel.WHITE to 1.00
            )
        }
        return SmartLightScene(
            channels = calibration.channels.associateWith { channel ->
                (intensity * checkNotNull(ratios[channel])).roundToInt().coerceIn(0, 100)
            }
        )
    }

    private fun bandIntensity(raw: Int, band: StageBand, planted: Boolean): Int {
        val cap = if (planted) band.intensityCap else minOf(band.intensityCap, 45)
        return raw.coerceAtMost(cap).coerceAtLeast(MINIMUM_TARGET_INTENSITY)
    }

    private fun stageIntensity(raw: Int, stage: SmartSetupLifecycleStage): Int {
        val cap = when (stage) {
            SmartSetupLifecycleStage.STARTUP -> 45
            SmartSetupLifecycleStage.ESTABLISHING -> 52
            SmartSetupLifecycleStage.MATURE -> 65
        }
        return raw.coerceIn(MINIMUM_TARGET_INTENSITY, cap)
    }

    private fun initialStartPercent(
        stage: SmartSetupLifecycleStage,
        risk: ObservationRisk
    ): Int = when {
        risk.significant -> 60
        risk.present -> 70
        stage == SmartSetupLifecycleStage.STARTUP -> 50
        stage == SmartSetupLifecycleStage.ESTABLISHING -> 70
        else -> 85
    }

    private fun centeredWindow(
        durationMinutes: Int,
        preferredStart: Int,
        preferredEnd: Int
    ): Pair<Long, Long> {
        val midpoint = (preferredStart + preferredEnd) / 2
        var start = midpoint - durationMinutes / 2
        var end = start + durationMinutes
        if (start < 0) {
            start = 0
            end = durationMinutes
        }
        if (end >= MINUTES_PER_DAY) {
            end = MINUTES_PER_DAY - 1
            start = end - durationMinutes
        }
        require(start >= 0 && end > start && end < MINUTES_PER_DAY)
        return start.toLong() to end.toLong()
    }

    private fun requireValidPlan(plan: SmartLightPlanDraft) {
        require(plan.initialStartPercent in 20..100 && plan.initialStartPercent % 5 == 0)
        require(plan.phases.size in 1..MAX_PHASES)
        require(plan.phases.last().validUntilEpochDayExclusive == null)
        plan.phases.forEachIndexed { index, phase ->
            require(phase.validFromEpochDay in MIN_EPOCH_DAY..MAX_EPOCH_DAY)
            phase.validUntilEpochDayExclusive?.let { end ->
                require(end in phase.validFromEpochDay + 1..MAX_END_EPOCH_DAY)
                require(phase.transitionDays == 0 ||
                    phase.transitionDays < end - phase.validFromEpochDay
                )
            }
            require(phase.transitionDays in 0..MAX_TRANSITION_DAYS)
            require(phase.weekdaysMask in 1..127)
            require(phase.startTimeMs in 0 until DAY_MS)
            require(phase.endTimeMs in 0 until DAY_MS)
            require(phase.endTimeMs > phase.startTimeMs)
            require(phase.rampDurationMs in ALLOWED_RAMPS)
            require(phase.rampDurationMs * 2L <= phase.endTimeMs - phase.startTimeMs)
            if (index > 0) {
                require(plan.phases[index - 1].validUntilEpochDayExclusive ==
                    phase.validFromEpochDay
                )
            }
        }
    }

    private fun isRecent(value: Long?, evaluation: Long?, maximumAge: Long): Boolean =
        value != null && evaluation != null && value <= evaluation && evaluation - value <= maximumAge

    private fun factor(
        id: SmartSetupFactorId,
        value: String,
        effect: SmartSetupFactorEffect
    ) = SmartSetupFactor(id, value, effect)

    private fun unsupported(
        reason: SmartSetupUnsupportedReason,
        vararg facts: String
    ) = SmartSetupDecision.Unsupported(reason, facts.toSet())

    private data class StageBand(
        val key: String,
        val validFrom: Long,
        val validUntilExclusive: Long?,
        val photoperiodMinutes: Int,
        val intensityCap: Int,
        val defaultTransitionDays: Int
    )

    private data class ObservationRisk(
        val present: Boolean,
        val significant: Boolean,
        val holdUntilReevaluation: Boolean,
        val intensityReduction: Int
    )

    private const val ENGINE_POLICY_ID = "aql.smart-light.policy.2026-09"
    private const val SOURCE_AQUALIGHT_POLICY = ENGINE_POLICY_ID
    private const val SOURCE_WRGB_CALIBRATION =
        "aql.wrgb-pro-elite.design-model-r2"
    private const val SOURCE_TROPICA_GROWING_IN = "tropica_growing_in"
    private const val SOURCE_TROPICA_QUICK_GUIDE = "tropica_quick_guide"
    private const val MAX_OBSERVATION_AGE_DAYS = 30L
    private const val ESTABLISHING_EARLY_END_DAY = 45L
    private const val UNPLANTED_BASE_INTENSITY = 32
    private const val UNPLANTED_PHOTOPERIOD_MINUTES = 420
    private const val ACTIVE_SOIL_STARTUP_CAP = 40
    private const val RECENT_ALGAE_MAINTENANCE_REDUCTION = 5
    private const val MAX_MAINTENANCE_REDUCTION = 10
    private const val MAX_OBSERVATION_REDUCTION = 28
    private const val MINIMUM_TARGET_INTENSITY = 20
    private const val MAXIMUM_TARGET_INTENSITY = 80
    private const val MINIMUM_VIEWING_WINDOW_MINUTES = 60
    private const val RISK_TRANSITION_DAYS = 7
    private const val EVERY_DAY_MASK = 127
    private const val MINUTES_PER_DAY = 24 * 60
    private const val MINUTE_MS = 60_000L
    private const val DAY_MS = 86_400_000L
    private const val RAMP_DURATION_MS = 3_600_000L
    private const val MAX_PHASES = 8
    private const val MAX_TRANSITION_DAYS = 90
    private const val MIN_EPOCH_DAY = 10957L
    private const val MAX_EPOCH_DAY = 47481L
    private const val MAX_END_EPOCH_DAY = 47482L
    private val ALLOWED_RAMPS = setOf(
        0L,
        1_800_000L,
        3_600_000L,
        5_400_000L,
        7_200_000L,
        9_000_000L
    )

    private object SmartSetupFingerprint {
        fun create(input: SmartSetupInput): String {
            val canonical = buildString {
                append("policy=").append(ENGINE_POLICY_ID)
                append("|tankId=").append(input.tankId)
                append("|environment=").append(input.aquariumEnvironment.name)
                append("|evaluation=").append(input.evaluationEpochDay)
                append("|setupDate=").append(input.setupDateEpochDay)
                append("|setupDay=").append(input.setupDay)
                append("|stage=").append(input.lifecycleStage?.name)
                append("|planted=").append(input.isPlanted)
                append("|density=").append(input.plantDensity?.name)
                append("|demand=").append(input.highestPlantLightDemand?.name)
                append("|co2=").append(input.co2Status?.name)
                append("|activeSoil=").append(input.isActiveSoil)
                append("|waterDepth=").append(input.waterDepthCm)
                append("|mountHeight=").append(input.fixtureMountHeightCm)
                append("|product=").append(input.deviceProductKey)
                append("|reportedCalibrationRevision=")
                    .append(input.reportedCalibrationRevision)
                append("|reportedChannelSceneKeys=")
                    .append(input.reportedChannelSceneKeys.joinToString(","))
                append("|calibration=").append(input.calibrationProfile?.id)
                append("|calibrationRevision=").append(input.calibrationProfile?.revision)
                append("|channels=").append(
                    input.calibrationProfile?.channels?.joinToString(",") { it.name }
                )
                append("|viewStart=").append(input.preferredViewingStartMinuteOfDay)
                append("|viewEnd=").append(input.preferredViewingEndMinuteOfDay)
                append("|algae=").append(input.algaeObservation?.name)
                append("|stress=").append(input.plantStressObservation?.name)
                append("|observed=").append(input.observationDateEpochDay)
                append("|waterChange=").append(input.maintenance.latestWaterChangeEpochDay)
                append("|algaeClean=").append(input.maintenance.latestAlgaeCleaningEpochDay)
                append("|plantCheck=").append(input.maintenance.latestPlantHealthCheckEpochDay)
                append("|co2Check=").append(input.maintenance.latestCo2CheckEpochDay)
                append("|overdue=").append(input.maintenance.overdueRelevantTaskCount)
            }
            return MessageDigest.getInstance("SHA-256")
                .digest(canonical.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { byte ->
                    String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff)
                }
        }
    }
}

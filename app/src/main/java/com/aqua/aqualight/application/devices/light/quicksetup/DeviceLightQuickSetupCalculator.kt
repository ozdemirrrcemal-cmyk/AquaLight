@file:Suppress("MagicNumber")

package com.aqua.aqualight.application.devices.light.quicksetup

import com.aqua.aqualight.application.aquarium.AquariumCo2Readiness
import com.aqua.aqualight.application.aquarium.AquariumDaylightExposure
import com.aqua.aqualight.application.aquarium.AquariumPlantCoverage
import com.aqua.aqualight.application.aquarium.AquariumShelterAvailability
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantic
import com.aqua.aqualight.application.aquarium.AquariumSurfaceGrowth
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticScene
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanPhaseDraft
import com.aqua.aqualight.application.devices.light.preset.DeviceLightPresetCatalog
import com.aqua.aqualight.application.devices.light.preset.DeviceLightPresetId
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Evidence-bound recommendation policy.
 *
 * It selects only a reviewed photoperiod and exact channel-drive commands. PPFD/DLI are not
 * calculated until a laboratory calibration exists for the exact hardware and fixture length.
 * Every result contains one open-ended phase so future intensity is never increased without a new
 * tank observation.
 */
object DeviceLightQuickSetupCalculator {
    const val POLICY_VERSION = "aql-smart-light-v1.0.0"

    private const val EVERY_DAY_MASK = 127
    private const val MINUTE_MS = 60_000L
    private const val RAMP_MINUTES = 60
    private const val RAMP_MS = RAMP_MINUTES * MINUTE_MS
    private const val MINUTES_PER_DAY = 1_440
    private const val STARTUP_END_DAY = 21L
    private const val STARTUP_REVIEW_DAYS = 14L
    private const val NORMAL_REVIEW_DAYS = 30L
    private const val GUARDED_REVIEW_DAYS = 7L
    private const val MAX_UNCALIBRATED_PERCENT = 50
    private const val MAX_UNCALIBRATED_STARTUP_PERCENT = 30
    private const val MIN_DEVICE_EPOCH_DAY = 10_957L
    private const val MAX_DEVICE_EPOCH_DAY = 47_481L
    private const val WRGB_PRODUCT_KEY = "LIGHT_WRGB_PRO_ELITE"
    private const val RGB_PRODUCT_KEY = "LIGHT_RGB_PRO_SLIM"

    fun calculate(
        tank: DeviceLightQuickSetupTank,
        input: DeviceLightQuickSetupInput,
        todayEpochDay: Long
    ): DeviceLightQuickSetupPlan {
        validate(tank, input, todayEpochDay)
        val decision = lifecycleDecision(tank, input, todayEpochDay)
        val stage = decision.stage
        val confidence = if (tank.calibrationProfile == null) {
            DeviceLightPlanConfidence.CONSERVATIVE_UNCALIBRATED
        } else {
            DeviceLightPlanConfidence.CALIBRATED
        }
        val scene = constrainedScene(
            tank = tank,
            input = input,
            stage = stage,
            confidence = confidence
        )
        val maximumChannelPercent = scene.channels.values.maxOrNull() ?: 0
        val digest = digest(tank, input, todayEpochDay)
        val phase = phase(stage, input.programEndMinute, todayEpochDay, scene)
        return DeviceLightQuickSetupPlan(
            recommendationId = "slr-" + digest.take(24),
            policyVersion = POLICY_VERSION,
            evidenceSourceIds = evidenceSources(tank, input, confidence),
            initialStartPercent = 100,
            confidence = confidence,
            calibrationProfileId = tank.calibrationProfile?.profileId.orEmpty(),
            calibrationRevision = tank.calibrationProfile?.revision ?: 0,
            maximumChannelPercent = maximumChannelPercent,
            reasons = reasons(decision, tank, input, confidence),
            warnings = warnings(stage, tank, input, todayEpochDay, confidence),
            phases = listOf(phase),
            reevaluationEpochDay = reevaluationEpochDay(
                decision = decision,
                tank = tank,
                input = input,
                todayEpochDay = todayEpochDay
            ),
            profileFingerprint = digest.take(16)
        )
    }

    private fun validate(
        tank: DeviceLightQuickSetupTank,
        input: DeviceLightQuickSetupInput,
        todayEpochDay: Long
    ) {
        require(tank.tankId > 0L)
        require(todayEpochDay == tank.deviceLocalEpochDay) {
            "Recommendation date must come from the verified fixture scheduler."
        }
        require(todayEpochDay in MIN_DEVICE_EPOCH_DAY..MAX_DEVICE_EPOCH_DAY)
        require(tank.productKey == WRGB_PRODUCT_KEY || tank.productKey == RGB_PRODUCT_KEY)
        require(tank.hardwareRevision.isNotBlank())
        require(tank.fixtureLengthMm > 0)
        require(tank.plantedFreshwater && tank.hasPlants) {
            "Commercial smart-light automation requires a planted freshwater aquarium."
        }
        tank.profileUpdatedAtMillis?.let { timestamp -> require(timestamp > 0L) }
        tank.installationUpdatedAtMillis?.let { timestamp -> require(timestamp > 0L) }
        require(tank.plantCatalogIds.isNotEmpty() == tank.hasPlants)
        require(tank.plantCatalogIds.all { value -> value.isNotBlank() && value == value.trim() })
        require(tank.substrateProductIds.all { value ->
            value.isNotBlank() && value == value.trim()
        })
        require(
            tank.substrateProductIds.isEmpty() ==
                (tank.substrateSemantic == AquariumSubstrateSemantic.NOT_APPLICABLE)
        )
        require(input.waterDepthCm in 5..tank.tankHeightCm)
        require(input.fixtureHeightAboveWaterCm in 0..200)
        require(input.plantDemand != DeviceLightPlantDemand.UNKNOWN)
        require(
            input.plantDemand ==
                input.plantDemand.notBelow(tank.reviewedPlantDemandFloor)
        ) {
            "Plant demand cannot be lower than exact reviewed catalog records."
        }
        require(input.plantCoverage != AquariumPlantCoverage.UNKNOWN)
        require(input.co2Readiness != AquariumCo2Readiness.UNKNOWN)
        require(input.daylightExposure != AquariumDaylightExposure.UNKNOWN)
        require(input.surfaceGrowth != AquariumSurfaceGrowth.UNKNOWN)
        require(input.shelterAvailability != AquariumShelterAvailability.UNKNOWN)
        require(input.programEndMinute in 0 until MINUTES_PER_DAY)
        val maximumDuration = DeviceLightLifecycleStage.entries.maxOf { it.durationMinutes }
        require(input.programEndMinute >= maximumDuration) {
            "Firmware V1 schedules cannot cross midnight."
        }
        if (input.daylightExposure == AquariumDaylightExposure.DIRECT) {
            val start = requireNotNull(input.daylightStartMinute)
            val end = requireNotNull(input.daylightEndMinute)
            require(start in 0 until MINUTES_PER_DAY)
            require(end in 1 until MINUTES_PER_DAY)
            require(start < end) { "Direct-daylight window cannot cross midnight." }
        } else {
            require(input.daylightStartMinute == null && input.daylightEndMinute == null)
        }
        if (!tank.co2ComponentPresent) {
            require(input.co2Readiness == AquariumCo2Readiness.NOT_INSTALLED)
        } else {
            require(
                input.co2Readiness == AquariumCo2Readiness.READY_AT_LIGHT_ON ||
                    input.co2Readiness == AquariumCo2Readiness.NOT_READY_AT_LIGHT_ON
            )
        }
        if (tank.hasShrimp) {
            require(
                input.shelterAvailability != AquariumShelterAvailability.UNKNOWN &&
                    input.shelterAvailability != AquariumShelterAvailability.NOT_REQUIRED
            )
        } else {
            require(input.shelterAvailability == AquariumShelterAvailability.NOT_REQUIRED)
            require(input.surfaceGrowth != AquariumSurfaceGrowth.TARGET_BIOFILM)
        }
        val appliedFields = listOf(
            tank.lastAppliedPhotoperiodMinutes,
            tank.lastAppliedMaximumChannelPercent,
            tank.lastAppliedEpochDay,
            tank.lastAppliedWaterDepthCm
        )
        require(
            appliedFields.all { value -> value == null } ||
                appliedFields.all { value -> value != null }
        ) { "Last applied dose and optical baseline must be stored atomically." }
        tank.lastAppliedPhotoperiodMinutes?.let { duration ->
            require(duration in DeviceLightLifecycleStage.entries.map { it.durationMinutes })
        }
        tank.lastAppliedMaximumChannelPercent?.let { percent ->
            require(percent in 0..100)
        }
        tank.lastAppliedEpochDay?.let { day -> require(day in 1..todayEpochDay) }
        tank.lastAppliedWaterDepthCm?.let { depth -> require(depth > 0) }
        tank.calibrationProfile?.let { profile ->
            require(profile.profileId.isNotBlank() && profile.revision > 0)
            require(profile.productKey == tank.productKey)
            require(profile.hardwareRevision == tank.hardwareRevision)
            require(profile.fixtureLengthMm == tank.fixtureLengthMm)
        }
    }

    private fun phase(
        stage: DeviceLightLifecycleStage,
        programEndMinute: Int,
        todayEpochDay: Long,
        scene: DeviceLightAutomaticScene
    ): DeviceLightQuickSetupPhase {
        val startMinute = programEndMinute - stage.durationMinutes
        return DeviceLightQuickSetupPhase(
            lifecycleStage = stage,
            draft = DeviceLightManagedPlanPhaseDraft(
                validFromEpochDay = todayEpochDay,
                validUntilEpochDayExclusive = null,
                transitionDays = 0,
                weekdaysMask = EVERY_DAY_MASK,
                startTimeMs = startMinute * MINUTE_MS,
                endTimeMs = programEndMinute * MINUTE_MS,
                rampDurationMs = RAMP_MS,
                scene = scene
            )
        )
    }

    private data class LifecycleDecision(
        val stage: DeviceLightLifecycleStage,
        val held: Boolean,
        val reviewIntervalBlocked: Boolean,
        val startupWindowBlocked: Boolean,
        val opticalGeometryChanged: Boolean,
        val biologicalProfileReset: Boolean
    )

    private fun lifecycleDecision(
        tank: DeviceLightQuickSetupTank,
        input: DeviceLightQuickSetupInput,
        todayEpochDay: Long
    ): LifecycleDecision {
        val resetDay = tank.lastLightingResetEpochDay
        val resetAgeDays = resetDay
            ?.takeIf { day -> day <= todayEpochDay }
            ?.let { day -> todayEpochDay - day + 1L }
            ?: 1L
        val fixtureHeightChanged = tank.fixtureHeightAboveWaterCm?.let { storedHeight ->
            storedHeight != input.fixtureHeightAboveWaterCm
        } == true
        val previousWaterDepth = tank.waterDepthCm ?: tank.lastAppliedWaterDepthCm
        val waterDepthChanged = previousWaterDepth?.let { previousDepth ->
            previousDepth != input.waterDepthCm
        } == true
        val opticalGeometryChanged = fixtureHeightChanged || waterDepthChanged
        // Plant/substrate edits invalidate the stored coverage in the tank profile. Treat that
        // invalidated value as a new biological baseline even when an untrusted phone clock
        // previously wrote an old lifecycle date. Persistence anchors it to the verified fixture
        // date after this recommendation is accepted.
        val biologicalProfileReset = tank.plantCoverage == AquariumPlantCoverage.UNKNOWN
        val startupWindowBlocked = resetAgeDays <= STARTUP_END_DAY ||
            opticalGeometryChanged ||
            biologicalProfileReset
        val previousStage = tank.lastAppliedPhotoperiodMinutes?.toLifecycleStage()
            ?: DeviceLightLifecycleStage.STARTUP
        val elapsedSinceApplied = tank.lastAppliedEpochDay
            ?.takeIf { day -> day <= todayEpochDay }
            ?.let { day -> todayEpochDay - day }
        val reviewIntervalBlocked = tank.lastAppliedPhotoperiodMinutes != null &&
            (elapsedSinceApplied == null || elapsedSinceApplied < STARTUP_REVIEW_DAYS)
        val guardBlocked = blocksAutomaticIncrease(tank, input) || opticalGeometryChanged
        val hasAppliedBaseline = tank.lastAppliedPhotoperiodMinutes != null
        val canAdvance = hasAppliedBaseline &&
            !startupWindowBlocked &&
            !reviewIntervalBlocked &&
            !guardBlocked
        val stage = when {
            startupWindowBlocked -> DeviceLightLifecycleStage.STARTUP
            !hasAppliedBaseline -> DeviceLightLifecycleStage.STARTUP
            canAdvance -> previousStage.next()
            else -> previousStage
        }
        return LifecycleDecision(
            stage = stage,
            held = hasAppliedBaseline && !canAdvance,
            reviewIntervalBlocked = reviewIntervalBlocked,
            startupWindowBlocked = startupWindowBlocked,
            opticalGeometryChanged = opticalGeometryChanged,
            biologicalProfileReset = biologicalProfileReset
        )
    }

    private fun Int.toLifecycleStage(): DeviceLightLifecycleStage = when {
        this < DeviceLightLifecycleStage.ACCLIMATION.durationMinutes ->
            DeviceLightLifecycleStage.STARTUP
        this < DeviceLightLifecycleStage.ESTABLISHED.durationMinutes ->
            DeviceLightLifecycleStage.ACCLIMATION
        else -> DeviceLightLifecycleStage.ESTABLISHED
    }

    private fun DeviceLightLifecycleStage.next(): DeviceLightLifecycleStage = when (this) {
        DeviceLightLifecycleStage.STARTUP -> DeviceLightLifecycleStage.ACCLIMATION
        DeviceLightLifecycleStage.ACCLIMATION -> DeviceLightLifecycleStage.ESTABLISHED
        DeviceLightLifecycleStage.ESTABLISHED -> DeviceLightLifecycleStage.ESTABLISHED
    }

    private fun blocksAutomaticIncrease(
        tank: DeviceLightQuickSetupTank,
        input: DeviceLightQuickSetupInput
    ): Boolean =
        input.plantCoverage == AquariumPlantCoverage.SPARSE ||
            input.daylightExposure == AquariumDaylightExposure.DIRECT ||
            input.surfaceGrowth == AquariumSurfaceGrowth.STABLE_ALGAE ||
            input.surfaceGrowth == AquariumSurfaceGrowth.WORSENING_ALGAE ||
            (tank.co2ComponentPresent &&
                input.co2Readiness != AquariumCo2Readiness.READY_AT_LIGHT_ON) ||
            (input.plantDemand == DeviceLightPlantDemand.HIGH &&
                input.co2Readiness != AquariumCo2Readiness.READY_AT_LIGHT_ON) ||
            (tank.hasShrimp &&
                input.shelterAvailability != AquariumShelterAvailability.ADEQUATE)

    private fun constrainedScene(
        tank: DeviceLightQuickSetupTank,
        input: DeviceLightQuickSetupInput,
        stage: DeviceLightLifecycleStage,
        confidence: DeviceLightPlanConfidence
    ): DeviceLightAutomaticScene {
        val presetId = when {
            stage == DeviceLightLifecycleStage.STARTUP -> DeviceLightPresetId.NEW_SETUP
            input.plantDemand == DeviceLightPlantDemand.LOW ->
                DeviceLightPresetId.LOW_TECH
            input.co2Readiness != AquariumCo2Readiness.READY_AT_LIGHT_ON ->
                DeviceLightPresetId.LOW_TECH
            else -> DeviceLightPresetId.PLANTED_AQUARIUM
        }
        val preset = requireNotNull(DeviceLightPresetCatalog.find(presetId))
        val requested = linkedMapOf(
            DeviceLightAutomaticChannel.RED to preset.scene.red,
            DeviceLightAutomaticChannel.GREEN to preset.scene.green,
            DeviceLightAutomaticChannel.BLUE to preset.scene.blue
        ).apply {
            if (tank.productKey == WRGB_PRODUCT_KEY) {
                put(DeviceLightAutomaticChannel.WHITE, preset.scene.white)
            }
        }
        val policyCap = outputCap(tank, input, confidence, stage)
        // A later observation may relax a guard, but that alone is not evidence that a higher
        // photon dose is safe. Keep the last applied channel ceiling; progression happens only
        // through the reviewed 6 -> 7 -> 8 hour photoperiod steps.
        val cap = minOf(
            policyCap,
            tank.lastAppliedMaximumChannelPercent ?: policyCap
        )
        val peak = requested.values.maxOrNull()?.coerceAtLeast(1) ?: 1
        val scale = if (peak <= cap) 1.0 else cap.toDouble() / peak
        return DeviceLightAutomaticScene(
            requested.mapValuesTo(linkedMapOf()) { (_, value) ->
                (value * scale).roundToInt().coerceIn(0, cap)
            }
        )
    }

    private fun outputCap(
        tank: DeviceLightQuickSetupTank,
        input: DeviceLightQuickSetupInput,
        confidence: DeviceLightPlanConfidence,
        stage: DeviceLightLifecycleStage
    ): Int {
        var cap = if (confidence == DeviceLightPlanConfidence.CALIBRATED) {
            70
        } else {
            MAX_UNCALIBRATED_PERCENT
        }
        if (confidence == DeviceLightPlanConfidence.CONSERVATIVE_UNCALIBRATED &&
            stage == DeviceLightLifecycleStage.STARTUP
        ) {
            cap = minOf(cap, MAX_UNCALIBRATED_STARTUP_PERCENT)
        }
        if (input.co2Readiness != AquariumCo2Readiness.READY_AT_LIGHT_ON) {
            cap = minOf(cap, 40)
        }
        if (input.plantCoverage == AquariumPlantCoverage.SPARSE) cap = minOf(cap, 40)
        if (input.daylightExposure == AquariumDaylightExposure.DIRECT) cap = minOf(cap, 35)
        cap = when (input.surfaceGrowth) {
            AquariumSurfaceGrowth.STABLE_ALGAE -> minOf(cap, 35)
            AquariumSurfaceGrowth.WORSENING_ALGAE -> minOf(cap, 30)
            AquariumSurfaceGrowth.UNKNOWN,
            AquariumSurfaceGrowth.NONE,
            AquariumSurfaceGrowth.TARGET_BIOFILM -> cap
        }
        cap = when (input.shelterAvailability) {
            AquariumShelterAvailability.LIMITED -> minOf(cap, 35)
            AquariumShelterAvailability.NONE -> minOf(cap, 30)
            AquariumShelterAvailability.NOT_REQUIRED,
            AquariumShelterAvailability.UNKNOWN,
            AquariumShelterAvailability.ADEQUATE -> cap
        }
        val opticalDistanceCm = input.waterDepthCm + input.fixtureHeightAboveWaterCm
        if (opticalDistanceCm <= 25) cap = minOf(cap, 35)
        return cap
    }

    private fun reasons(
        decision: LifecycleDecision,
        tank: DeviceLightQuickSetupTank,
        input: DeviceLightQuickSetupInput,
        confidence: DeviceLightPlanConfidence
    ): Set<DeviceLightPlanReason> = buildSet {
        val stage = decision.stage
        add(
            when (stage) {
                DeviceLightLifecycleStage.STARTUP ->
                    DeviceLightPlanReason.EVIDENCE_SIX_HOUR_START
                DeviceLightLifecycleStage.ACCLIMATION ->
                    DeviceLightPlanReason.CONTROLLED_SEVEN_HOUR_STEP
                DeviceLightLifecycleStage.ESTABLISHED ->
                    DeviceLightPlanReason.EVIDENCE_EIGHT_HOUR_BASELINE
            }
        )
        if (decision.held) add(DeviceLightPlanReason.REVIEW_INTERVAL_HOLD)
        if (decision.opticalGeometryChanged) {
            add(DeviceLightPlanReason.OPTICAL_GEOMETRY_CHANGE_RESET)
        }
        if (decision.biologicalProfileReset) {
            add(DeviceLightPlanReason.BIOLOGICAL_PROFILE_CHANGE_RESET)
        }
        val policyCap = outputCap(tank, input, confidence, stage)
        if (tank.lastAppliedMaximumChannelPercent?.let { last -> last < policyCap } == true) {
            add(DeviceLightPlanReason.LAST_APPLIED_OUTPUT_HOLD)
        }
        add(
            when (input.plantDemand) {
                DeviceLightPlantDemand.UNKNOWN -> error("Plant demand was validated.")
                DeviceLightPlantDemand.LOW -> DeviceLightPlanReason.LOW_LIGHT_PLANTS
                DeviceLightPlantDemand.MEDIUM -> DeviceLightPlanReason.MEDIUM_LIGHT_PLANTS
                DeviceLightPlantDemand.HIGH -> DeviceLightPlanReason.HIGH_LIGHT_PLANTS
            }
        )
        add(
            if (input.co2Readiness == AquariumCo2Readiness.READY_AT_LIGHT_ON) {
                DeviceLightPlanReason.CO2_READY_AT_LIGHT_ON
            } else {
                DeviceLightPlanReason.CO2_NOT_READY_GUARD
            }
        )
        if (stage == DeviceLightLifecycleStage.STARTUP &&
            input.substrateSemantic == AquariumSubstrateSemantic.ACTIVE_SOIL
        ) {
            add(DeviceLightPlanReason.ACTIVE_SOIL_STARTUP)
        }
        if (input.plantCoverage == AquariumPlantCoverage.SPARSE) {
            add(DeviceLightPlanReason.SPARSE_PLANTING_GUARD)
        }
        when (input.daylightExposure) {
            AquariumDaylightExposure.INDIRECT -> add(DeviceLightPlanReason.INDIRECT_DAYLIGHT)
            AquariumDaylightExposure.DIRECT ->
                add(DeviceLightPlanReason.DIRECT_DAYLIGHT_GUARD)
            AquariumDaylightExposure.UNKNOWN,
            AquariumDaylightExposure.LOW -> Unit
        }
        when (input.surfaceGrowth) {
            AquariumSurfaceGrowth.TARGET_BIOFILM ->
                add(DeviceLightPlanReason.TARGET_BIOFILM_PROTECTED)
            AquariumSurfaceGrowth.STABLE_ALGAE ->
                add(DeviceLightPlanReason.STABLE_ALGAE_HOLD)
            AquariumSurfaceGrowth.WORSENING_ALGAE ->
                add(DeviceLightPlanReason.WORSENING_ALGAE_GUARD)
            AquariumSurfaceGrowth.UNKNOWN,
            AquariumSurfaceGrowth.NONE -> Unit
        }
        if (tank.hasShrimp && input.shelterAvailability != AquariumShelterAvailability.ADEQUATE) {
            add(DeviceLightPlanReason.SHRIMP_SHELTER_GUARD)
        }
        if (confidence == DeviceLightPlanConfidence.CONSERVATIVE_UNCALIBRATED) {
            add(DeviceLightPlanReason.UNCALIBRATED_OUTPUT_GUARD)
        }
    }

    private fun warnings(
        stage: DeviceLightLifecycleStage,
        tank: DeviceLightQuickSetupTank,
        input: DeviceLightQuickSetupInput,
        todayEpochDay: Long,
        confidence: DeviceLightPlanConfidence
    ): Set<DeviceLightPlanWarning> = buildSet {
        val setupDay = tank.setupDateEpochDay
        if (setupDay == null) add(DeviceLightPlanWarning.SETUP_DATE_MISSING)
        if (setupDay != null && setupDay > todayEpochDay) {
            add(DeviceLightPlanWarning.SETUP_DATE_IN_FUTURE)
        }
        if (!tank.plantedFreshwater) add(DeviceLightPlanWarning.NOT_PLANTED_FRESHWATER)
        if (!tank.hasPlants) add(DeviceLightPlanWarning.NO_PLANTS)
        if (input.plantDemand == DeviceLightPlantDemand.UNKNOWN) {
            add(DeviceLightPlanWarning.UNKNOWN_PLANT_DEMAND)
        }
        if (input.plantDemand == DeviceLightPlantDemand.HIGH &&
            input.co2Readiness != AquariumCo2Readiness.READY_AT_LIGHT_ON
        ) {
            add(DeviceLightPlanWarning.HIGH_LIGHT_WITHOUT_READY_CO2)
        }
        if (confidence == DeviceLightPlanConfidence.CONSERVATIVE_UNCALIBRATED) {
            add(DeviceLightPlanWarning.CALIBRATION_UNAVAILABLE)
            if (input.waterDepthCm + input.fixtureHeightAboveWaterCm > 60) {
                add(DeviceLightPlanWarning.DEEP_INSTALLATION_UNCALIBRATED)
            }
        }
        if (input.daylightExposure == AquariumDaylightExposure.DIRECT &&
            overlapsArtificialProgram(input, stage)
        ) {
            add(DeviceLightPlanWarning.DIRECT_DAYLIGHT_OVERLAP)
        }
        if (tank.hasShrimp && input.shelterAvailability == AquariumShelterAvailability.NONE) {
            add(DeviceLightPlanWarning.SHRIMP_SHELTER_MISSING)
        }
    }

    private fun overlapsArtificialProgram(
        input: DeviceLightQuickSetupInput,
        stage: DeviceLightLifecycleStage
    ): Boolean {
        val daylightStart = input.daylightStartMinute ?: return false
        val daylightEnd = input.daylightEndMinute ?: return false
        val artificialStart = input.programEndMinute - stage.durationMinutes
        return artificialStart < daylightEnd && daylightStart < input.programEndMinute
    }

    private fun reevaluationEpochDay(
        decision: LifecycleDecision,
        tank: DeviceLightQuickSetupTank,
        input: DeviceLightQuickSetupInput,
        todayEpochDay: Long
    ): Long = when {
        input.surfaceGrowth == AquariumSurfaceGrowth.STABLE_ALGAE ||
            input.surfaceGrowth == AquariumSurfaceGrowth.WORSENING_ALGAE ->
            todayEpochDay + GUARDED_REVIEW_DAYS
        decision.biologicalProfileReset -> todayEpochDay + STARTUP_END_DAY
        decision.opticalGeometryChanged -> todayEpochDay + STARTUP_END_DAY
        decision.reviewIntervalBlocked && tank.lastAppliedEpochDay != null ->
            maxOf(todayEpochDay + 1L, tank.lastAppliedEpochDay + STARTUP_REVIEW_DAYS)
        decision.startupWindowBlocked && tank.lastLightingResetEpochDay != null ->
            maxOf(todayEpochDay + 1L, tank.lastLightingResetEpochDay + STARTUP_END_DAY)
        decision.stage == DeviceLightLifecycleStage.STARTUP ||
            decision.stage == DeviceLightLifecycleStage.ACCLIMATION ->
            todayEpochDay + STARTUP_REVIEW_DAYS
        else -> todayEpochDay + NORMAL_REVIEW_DAYS
    }

    private fun evidenceSources(
        tank: DeviceLightQuickSetupTank,
        input: DeviceLightQuickSetupInput,
        confidence: DeviceLightPlanConfidence
    ): Set<String> = buildSet {
        add("tropica_growing_in")
        add("tropica_quick_guide")
        if (confidence == DeviceLightPlanConfidence.CONSERVATIVE_UNCALIBRATED) {
            add("chihiros_light_intensity_guidance")
        }
        if (tank.plantEvidenceSourceIds.isNotEmpty()) {
            add("tropica_plant_database")
        }
        if (input.surfaceGrowth == AquariumSurfaceGrowth.STABLE_ALGAE ||
            input.surfaceGrowth == AquariumSurfaceGrowth.WORSENING_ALGAE
        ) {
            add("tropica_algae_control")
        }
        addAll(tank.plantEvidenceSourceIds)
        addAll(tank.substrateEvidenceSourceIds)
        if (tank.co2ComponentPresent) {
            add("colombo_co2_profi_manual")
            add("bioscape_co2_generator_manual")
            add("kitaya_2003_co2_light_photosynthesis")
        }
    }

    private fun digest(
        tank: DeviceLightQuickSetupTank,
        input: DeviceLightQuickSetupInput,
        todayEpochDay: Long
    ): String {
        val source = listOf(
            POLICY_VERSION,
            tank.tankId,
            tank.deviceLocalEpochDay,
            tank.setupDateEpochDay,
            tank.tankHeightCm,
            tank.hasPlants,
            tank.plantDemand,
            tank.reviewedPlantDemandFloor,
            tank.plantCoverage,
            tank.co2ComponentPresent,
            tank.co2Readiness,
            tank.substrateSemantic,
            tank.daylightExposure,
            tank.daylightStartMinute,
            tank.daylightEndMinute,
            tank.preferredLightEndMinute,
            tank.surfaceGrowth,
            tank.latestObservationEpochDay,
            tank.hasShrimp,
            tank.shelterAvailability,
            tank.waterDepthCm,
            tank.fixtureHeightAboveWaterCm,
            tank.plantedFreshwater,
            tank.productKey,
            tank.hardwareRevision,
            tank.fixtureLengthMm,
            tank.calibrationProfile?.profileId.orEmpty(),
            tank.calibrationProfile?.revision ?: 0,
            tank.profileUpdatedAtMillis,
            tank.installationUpdatedAtMillis,
            tank.plantCatalogIds.sorted(),
            tank.plantEvidenceSourceIds.sorted(),
            tank.substrateProductIds.sorted(),
            tank.substrateEvidenceSourceIds.sorted(),
            tank.lastLightingResetEpochDay,
            tank.lastAppliedPhotoperiodMinutes,
            tank.lastAppliedMaximumChannelPercent,
            tank.lastAppliedEpochDay,
            tank.nextReevaluationEpochDay,
            tank.lastAppliedWaterDepthCm,
            todayEpochDay,
            input
        ).joinToString("|").lowercase(Locale.ROOT)
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}

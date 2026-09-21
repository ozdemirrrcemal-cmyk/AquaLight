package com.aqua.aqualight.application.devices.light.quicksetup

import com.aqua.aqualight.application.aquarium.AquariumPlantLightDemand

/**
 * Pure, deterministic Quick Setup recommendation engine.
 *
 * It owns biological product policy and authored schedule construction. It does not access Android,
 * storage, networking, firmware runtime state, or presentation.
 */
class DeviceLightQuickSetupRecommendationEngine(
    private val calibration: DeviceLightFixtureCalibration
) {

    fun recommend(
        context: DeviceLightQuickSetupContext,
        input: DeviceLightQuickSetupInput
    ): DeviceLightQuickSetupRecommendationResult {
        val inputFailure = validateInput(context, input)
        if (inputFailure != null) {
            return DeviceLightQuickSetupRecommendationResult.Blocked(inputFailure)
        }
        val plantProfile = DeviceLightQuickSetupPlantProfileResolver.resolve(context)
            ?: return DeviceLightQuickSetupRecommendationResult.Blocked(
                DeviceLightQuickSetupBlockReason.UNKNOWN_PLANT_CATALOG_ID
            )
        val requestedPpfd = requestedTargetPpfd(plantProfile.highestDemand)
        val effectivePpfd = effectiveTargetPpfd(requestedPpfd, input.co2Readiness)
        val calibrationResult = calibration.solve(
            DeviceLightFixtureCalibrationRequest(
                productKey = context.productKey,
                tankWidthCm = context.tankWidthCm,
                tankLengthCm = context.tankLengthCm,
                waterHeightCm = input.waterHeightCm,
                fixtureHeightAboveWaterCm = input.fixtureHeightAboveWaterCm,
                targetPpfd = effectivePpfd,
                channelKeys = context.channelKeys
            )
        ) ?: return DeviceLightQuickSetupRecommendationResult.Blocked(
            DeviceLightQuickSetupBlockReason.MISSING_CALIBRATION
        )
        if (calibrationResult.coverageStatus == DeviceLightFixtureCoverageStatus.INSUFFICIENT) {
            return DeviceLightQuickSetupRecommendationResult.Blocked(
                DeviceLightQuickSetupBlockReason.INSUFFICIENT_FIXTURE_COVERAGE
            )
        }

        val phases = buildPhases(
            setupEpochDay = context.setupDateEpochDay,
            firstLightOnMinuteOfDay = input.firstLightOnMinuteOfDay,
            scene = calibrationResult.channelScenePercent
        ) ?: return DeviceLightQuickSetupRecommendationResult.Blocked(
            DeviceLightQuickSetupBlockReason.INVALID_INPUT
        )

        return DeviceLightQuickSetupRecommendationResult.Available(
            DeviceLightQuickSetupRecommendation(
                contextFingerprint = context.profileFingerprint,
                algorithmRevision = DeviceLightQuickSetupEvidence.ALGORITHM_REVISION,
                plantProfile = plantProfile,
                requestedTargetPpfd = requestedPpfd,
                effectiveTargetPpfd = effectivePpfd,
                co2Limited = effectivePpfd < requestedPpfd,
                calibration = calibrationResult,
                initialStartPercent = calibrationResult.initialStartPercent,
                phases = phases,
                evidenceIds = evidenceIds(context)
            )
        )
    }

    private fun validateInput(
        context: DeviceLightQuickSetupContext,
        input: DeviceLightQuickSetupInput
    ): DeviceLightQuickSetupBlockReason? = when {
        input.waterHeightCm <= 0 || input.waterHeightCm > context.tankHeightCm ->
            DeviceLightQuickSetupBlockReason.INVALID_INPUT
        input.fixtureHeightAboveWaterCm < 0 ->
            DeviceLightQuickSetupBlockReason.INVALID_INPUT
        input.firstLightOnMinuteOfDay !in 0 until MINUTES_PER_DAY ->
            DeviceLightQuickSetupBlockReason.INVALID_INPUT
        input.firstLightOnMinuteOfDay % TIME_STEP_MINUTES != 0 ->
            DeviceLightQuickSetupBlockReason.INVALID_INPUT
        input.firstLightOnMinuteOfDay + MATURE_PHOTOPERIOD_MINUTES >= MINUTES_PER_DAY ->
            DeviceLightQuickSetupBlockReason.INVALID_INPUT
        !context.co2Present && input.co2Readiness != DeviceLightQuickSetupCo2Readiness.NOT_PRESENT ->
            DeviceLightQuickSetupBlockReason.INVALID_INPUT
        context.co2Present && input.co2Readiness == DeviceLightQuickSetupCo2Readiness.NOT_PRESENT ->
            DeviceLightQuickSetupBlockReason.INVALID_INPUT
        else -> null
    }

    private fun requestedTargetPpfd(demand: AquariumPlantLightDemand): Int = when (demand) {
        AquariumPlantLightDemand.LOW -> LOW_TARGET_PPFD
        AquariumPlantLightDemand.MEDIUM -> MEDIUM_TARGET_PPFD
        AquariumPlantLightDemand.HIGH -> HIGH_TARGET_PPFD
    }

    private fun effectiveTargetPpfd(
        requestedPpfd: Int,
        co2Readiness: DeviceLightQuickSetupCo2Readiness
    ): Int = when (co2Readiness) {
        DeviceLightQuickSetupCo2Readiness.PRESENT_PRECHARGED -> requestedPpfd
        DeviceLightQuickSetupCo2Readiness.NOT_PRESENT,
        DeviceLightQuickSetupCo2Readiness.PRESENT_NOT_PRECHARGED ->
            requestedPpfd.coerceAtMost(NO_PRECHARGE_PPFD_CAP)
    }

    private fun buildPhases(
        setupEpochDay: Long,
        firstLightOnMinuteOfDay: Int,
        scene: Map<String, Int>
    ): List<DeviceLightQuickSetupPhase>? {
        val setup = setupEpochDay.toInt().takeIf { it.toLong() == setupEpochDay } ?: return null
        return PHASE_SPECS.mapIndexed { index, spec ->
            DeviceLightQuickSetupPhase(
                validFromEpochDay = setup + spec.startDayOffset,
                validUntilEpochDayExclusive = spec.endDayOffset?.let { setup + it },
                transitionDays = if (index == 0) FIRST_PHASE_TRANSITION_DAYS else 0,
                weekdaysMask = EVERY_DAY_WEEKDAYS_MASK,
                startMinuteOfDay = firstLightOnMinuteOfDay,
                endMinuteOfDay = firstLightOnMinuteOfDay + spec.photoperiodMinutes,
                rampMinutes = DAILY_RAMP_MINUTES,
                channelScenePercent = scene
            )
        }
    }

    private fun evidenceIds(context: DeviceLightQuickSetupContext): Set<String> = buildSet {
        add(DeviceLightQuickSetupEvidence.TROPICA_LIGHT_DEMAND)
        add(DeviceLightQuickSetupEvidence.GREEN_AQUA_PHOTOPERIOD)
        add(DeviceLightQuickSetupEvidence.TWO_HR_PAR_BANDS)
        if (context.co2Present) add(DeviceLightQuickSetupEvidence.TWO_HR_CO2_PRECHARGE)
    }

    private data class PhaseSpec(
        val startDayOffset: Int,
        val endDayOffset: Int?,
        val photoperiodMinutes: Int
    )

    private companion object {
        const val MINUTES_PER_DAY = 1_440
        const val TIME_STEP_MINUTES = 5
        const val MATURE_PHOTOPERIOD_MINUTES = 480
        const val DAILY_RAMP_MINUTES = 60
        const val EVERY_DAY_WEEKDAYS_MASK = 127
        const val FIRST_PHASE_TRANSITION_DAYS = 20

        // Lower/conservative points inside reviewed PAR bands; fixture calibration remains separate.
        const val LOW_TARGET_PPFD = 30
        const val MEDIUM_TARGET_PPFD = 60
        const val HIGH_TARGET_PPFD = 90
        const val NO_PRECHARGE_PPFD_CAP = 60

        val PHASE_SPECS = listOf(
            PhaseSpec(0, 21, 360),
            PhaseSpec(21, 28, 390),
            PhaseSpec(28, 35, 420),
            PhaseSpec(35, 42, 450),
            PhaseSpec(42, null, 480)
        )
    }
}

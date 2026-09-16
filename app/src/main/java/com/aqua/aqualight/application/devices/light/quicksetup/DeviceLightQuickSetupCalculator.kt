@file:Suppress("MagicNumber")

package com.aqua.aqualight.application.devices.light.quicksetup

import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticScene
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanPhaseDraft
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.exp
import kotlin.math.roundToInt

/** Pure, deterministic recommendation engine. Firmware remains the execution authority. */
@Suppress("TooManyFunctions") // Named pure policy steps are kept explicit for auditability.
object DeviceLightQuickSetupCalculator {
    private const val EVERY_DAY_MASK = 127
    private const val MINUTE_MS = 60_000L
    private const val RAMP_MINUTES = 60
    private const val RAMP_MS = RAMP_MINUTES * MINUTE_MS
    private const val MINUTES_PER_DAY = 1_440
    private const val FIRST_STAGE_DAYS = 21L
    private const val REEVALUATION_DAYS = 14L
    private const val ALGAE_REEVALUATION_DAYS = 7L
    private const val PROFILE_VERSION = "aql-planted-balanced-v2"

    fun calculate(
        tank: DeviceLightQuickSetupTank,
        input: DeviceLightQuickSetupInput,
        todayEpochDay: Long
    ): DeviceLightQuickSetupPlan {
        validateInput(input)
        val lifecycle = lifecycle(tank.setupDateEpochDay, todayEpochDay)
        val fullIntensity = targetIntensityPercent(tank.productKey, input)
        val fullProfilePpfd = estimatedFullProfilePpfd(tank.productKey, input)
        val lightProfile = PhaseLightProfile(fullIntensity, fullProfilePpfd)
        val phases = buildPhases(tank, input, lifecycle, lightProfile)
        val current = phases.first()
        return DeviceLightQuickSetupPlan(
            initialStartPercent = initialStartPercent(lifecycle.tankDay, input),
            currentPhaseIndex = 0,
            currentTargetPpfd = current.targetPpfd,
            currentEstimatedDliMolPerM2Day = current.estimatedDliMolPerM2Day,
            confidence = DeviceLightPlanConfidence.ESTIMATED,
            reasons = reasons(lifecycle.tankDay, input),
            warnings = warnings(tank, input, todayEpochDay),
            phases = phases,
            reevaluationEpochDay = todayEpochDay + reevaluationDays(input.algaeLevel),
            profileFingerprint = fingerprint(tank, input, todayEpochDay)
        )
    }

    private fun lifecycle(setupDay: Long?, todayEpochDay: Long): LifecycleContext {
        val safeSetupDay = setupDay?.takeIf { it <= todayEpochDay } ?: todayEpochDay
        val tankDay = (todayEpochDay - safeSetupDay + 1L).coerceAtLeast(1L)
        return LifecycleContext(
            safeSetupDay = safeSetupDay,
            todayEpochDay = todayEpochDay,
            tankDay = tankDay,
            currentStageIndex = lifecycleIndex(tankDay)
        )
    }

    private fun warnings(
        tank: DeviceLightQuickSetupTank,
        input: DeviceLightQuickSetupInput,
        todayEpochDay: Long
    ): Set<DeviceLightPlanWarning> = buildSet {
        val setupDay = tank.setupDateEpochDay
        if (setupDay == null) add(DeviceLightPlanWarning.SETUP_DATE_MISSING)
        if (setupDay != null && setupDay > todayEpochDay) {
            add(DeviceLightPlanWarning.SETUP_DATE_IN_FUTURE)
        }
        if (!tank.plantedFreshwater) add(DeviceLightPlanWarning.NOT_PLANTED_FRESHWATER)
        if (tank.plantCount == 0) add(DeviceLightPlanWarning.NO_PLANTS)
        if (input.plantDemand == DeviceLightPlantDemand.HIGH && !input.co2Installed) {
            add(DeviceLightPlanWarning.HIGH_LIGHT_WITHOUT_CO2)
        }
        add(DeviceLightPlanWarning.PAR_NOT_MEASURED)
    }

    private fun buildPhases(
        tank: DeviceLightQuickSetupTank,
        input: DeviceLightQuickSetupInput,
        lifecycle: LifecycleContext,
        lightProfile: PhaseLightProfile
    ): List<DeviceLightQuickSetupPhase> {
        val context = PhaseBuildContext(
            tank = tank,
            input = input,
            lifecycle = lifecycle,
            lightProfile = lightProfile,
            holdForAlgaeReview = input.algaeLevel != DeviceLightAlgaeLevel.NONE
        )
        return plannedStages(lifecycle, input)
            .mapIndexed { relativeIndex, _ ->
                createPhase(context, relativeIndex)
            }
    }

    private fun plannedStages(
        lifecycle: LifecycleContext,
        input: DeviceLightQuickSetupInput
    ): List<DeviceLightLifecycleStage> = if (input.algaeLevel == DeviceLightAlgaeLevel.NONE) {
        DeviceLightLifecycleStage.entries.drop(lifecycle.currentStageIndex)
    } else {
        listOf(DeviceLightLifecycleStage.entries[lifecycle.currentStageIndex])
    }

    private fun createPhase(
        context: PhaseBuildContext,
        relativeIndex: Int
    ): DeviceLightQuickSetupPhase {
        val tank = context.tank
        val input = context.input
        val lifecycle = context.lifecycle
        val absoluteIndex = lifecycle.currentStageIndex + relativeIndex
        val stage = DeviceLightLifecycleStage.entries[absoluteIndex]
        val fromDay = if (relativeIndex == 0) {
            lifecycle.todayEpochDay
        } else {
            lifecycle.safeSetupDay + stage.dayStart - 1L
        }
        val nextStage = DeviceLightLifecycleStage.entries.getOrNull(absoluteIndex + 1)
        val untilDay = if (context.holdForAlgaeReview) {
            null
        } else {
            nextStage?.let { lifecycle.safeSetupDay + it.dayStart - 1L }
        }
        val stageIntensity = (
            context.lightProfile.fullIntensityPercent * STAGE_INTENSITY_FACTORS[absoluteIndex]
        )
            .roundToInt()
            .coerceIn(MIN_INTENSITY_PERCENT, MAX_INTENSITY_PERCENT)
        val targetPpfd = (context.lightProfile.fullProfilePpfd * stageIntensity / PERCENT_SCALE)
            .roundToInt()
            .coerceAtLeast(MIN_TARGET_PPFD)
        val startMinute = input.programEndMinute - stage.durationMinutes
        val transitionDays = if (relativeIndex == 0) {
            initialTransitionDays(untilDay, fromDay)
        } else {
            STANDARD_TRANSITION_DAYS
        }
        return DeviceLightQuickSetupPhase(
            lifecycleStage = stage,
            targetPpfd = targetPpfd,
            estimatedDliMolPerM2Day = dli(targetPpfd, stage.durationMinutes),
            draft = DeviceLightManagedPlanPhaseDraft(
                validFromEpochDay = fromDay,
                validUntilEpochDayExclusive = untilDay,
                transitionDays = transitionDays,
                weekdaysMask = EVERY_DAY_MASK,
                startTimeMs = startMinute * MINUTE_MS,
                endTimeMs = input.programEndMinute * MINUTE_MS,
                rampDurationMs = RAMP_MS,
                scene = balancedScene(tank.productKey, stageIntensity)
            )
        )
    }

    private fun initialStartPercent(
        tankDay: Long,
        input: DeviceLightQuickSetupInput
    ): Int {
        val base = when {
            tankDay > FIRST_STAGE_DAYS -> MATURE_INITIAL_START_PERCENT
            input.activeSoil -> ACTIVE_SOIL_INITIAL_START_PERCENT
            else -> NEW_TANK_INITIAL_START_PERCENT
        }
        val reduction = when (input.algaeLevel) {
            DeviceLightAlgaeLevel.NONE -> 0
            DeviceLightAlgaeLevel.MILD -> 10
            DeviceLightAlgaeLevel.VISIBLE -> 20
        }
        return (base - reduction).coerceAtLeast(MIN_INITIAL_START_PERCENT)
    }

    private fun reevaluationDays(algaeLevel: DeviceLightAlgaeLevel): Long =
        if (algaeLevel == DeviceLightAlgaeLevel.NONE) {
            REEVALUATION_DAYS
        } else {
            ALGAE_REEVALUATION_DAYS
        }

    private fun validateInput(input: DeviceLightQuickSetupInput) {
        require(input.aquariumHeightCm in 10..100)
        require(input.programEndMinute in 720 until MINUTES_PER_DAY)
        val maximumDuration = DeviceLightLifecycleStage.entries.maxOf { it.durationMinutes }
        require(input.programEndMinute >= maximumDuration)
    }

    private fun lifecycleIndex(tankDay: Long): Int =
        DeviceLightLifecycleStage.entries.indexOfLast { tankDay >= it.dayStart }
            .coerceAtLeast(0)

    private fun targetIntensityPercent(
        productKey: String,
        input: DeviceLightQuickSetupInput
    ): Int {
        var intensity = plantDemandIntensity(input.plantDemand)
        intensity += plantDensityAdjustment(input.plantDensity)
        intensity += ambientLightAdjustment(input.ambientLight)
        intensity += algaeAdjustment(input.algaeLevel)
        val opticalDistance = input.aquariumHeightCm + defaultMountHeightCm(productKey)
        intensity += (opticalDistance - REFERENCE_DISTANCE_CM) / 4
        if (input.activeSoil) intensity -= 3
        return applySafetyCaps(intensity, input)
    }

    private fun plantDemandIntensity(demand: DeviceLightPlantDemand): Int = when (demand) {
        DeviceLightPlantDemand.LOW -> 42
        DeviceLightPlantDemand.MEDIUM -> 56
        DeviceLightPlantDemand.HIGH -> 72
    }

    private fun plantDensityAdjustment(density: DeviceLightPlantDensity): Int = when (density) {
        DeviceLightPlantDensity.SPARSE -> -5
        DeviceLightPlantDensity.MEDIUM -> 0
        DeviceLightPlantDensity.DENSE -> 5
    }

    private fun ambientLightAdjustment(ambientLight: DeviceLightAmbientLight): Int =
        when (ambientLight) {
            DeviceLightAmbientLight.LOW -> 0
            DeviceLightAmbientLight.INDIRECT -> -6
            DeviceLightAmbientLight.DIRECT -> -15
        }

    private fun algaeAdjustment(algaeLevel: DeviceLightAlgaeLevel): Int = when (algaeLevel) {
        DeviceLightAlgaeLevel.NONE -> 0
        DeviceLightAlgaeLevel.MILD -> -14
        DeviceLightAlgaeLevel.VISIBLE -> -24
    }

    private fun applySafetyCaps(
        requestedIntensity: Int,
        input: DeviceLightQuickSetupInput
    ): Int {
        var intensity = requestedIntensity
        if (!input.co2Installed) intensity = intensity.coerceAtMost(NO_CO2_MAX_INTENSITY_PERCENT)
        if (input.ambientLight == DeviceLightAmbientLight.DIRECT) {
            intensity = intensity.coerceAtMost(DIRECT_DAYLIGHT_MAX_INTENSITY_PERCENT)
        }
        if (input.algaeLevel == DeviceLightAlgaeLevel.VISIBLE) {
            intensity = intensity.coerceAtMost(VISIBLE_ALGAE_MAX_INTENSITY_PERCENT)
        }
        return intensity.coerceIn(MIN_INTENSITY_PERCENT, MAX_INTENSITY_PERCENT)
    }

    private fun estimatedFullProfilePpfd(
        productKey: String,
        input: DeviceLightQuickSetupInput
    ): Int {
        val reference = if (productKey == WRGB_PRODUCT_KEY) 105.0 else 78.0
        val distance = input.aquariumHeightCm + defaultMountHeightCm(productKey)
        val attenuation = exp(-0.018 * (distance - REFERENCE_DISTANCE_CM))
        return (reference * attenuation).roundToInt().coerceIn(35, 180)
    }

    private fun defaultMountHeightCm(productKey: String): Int =
        if (productKey == WRGB_PRODUCT_KEY) {
            WRGB_DEFAULT_MOUNT_HEIGHT_CM
        } else {
            RGB_DEFAULT_MOUNT_HEIGHT_CM
        }

    private fun balancedScene(productKey: String, intensity: Int): DeviceLightAutomaticScene {
        fun scaled(ratio: Double) = (intensity * ratio).roundToInt().coerceIn(0, 100)
        val channels = if (productKey == WRGB_PRODUCT_KEY) {
            linkedMapOf(
                DeviceLightAutomaticChannel.RED to scaled(0.86),
                DeviceLightAutomaticChannel.GREEN to scaled(0.68),
                DeviceLightAutomaticChannel.BLUE to scaled(0.74),
                DeviceLightAutomaticChannel.WHITE to intensity
            )
        } else {
            linkedMapOf(
                DeviceLightAutomaticChannel.RED to intensity,
                DeviceLightAutomaticChannel.GREEN to scaled(0.72),
                DeviceLightAutomaticChannel.BLUE to scaled(0.82)
            )
        }
        return DeviceLightAutomaticScene(channels)
    }

    private fun dli(peakPpfd: Int, durationMinutes: Int): Double {
        val equivalentFullMinutes = durationMinutes - RAMP_MINUTES
        return peakPpfd * equivalentFullMinutes * 60.0 / 1_000_000.0
    }

    private fun initialTransitionDays(untilDay: Long?, fromDay: Long): Int {
        val availableDays = untilDay?.minus(fromDay)?.toInt()
        return if (availableDays == null) {
            STANDARD_TRANSITION_DAYS
        } else {
            STANDARD_TRANSITION_DAYS.coerceAtMost((availableDays - 1).coerceAtLeast(0))
        }
    }

    private fun reasons(
        tankDay: Long,
        input: DeviceLightQuickSetupInput
    ): Set<DeviceLightPlanReason> = buildSet {
        add(if (tankDay <= FIRST_STAGE_DAYS) DeviceLightPlanReason.NEW_TANK else DeviceLightPlanReason.ESTABLISHED_TANK)
        add(
            when (input.plantDemand) {
                DeviceLightPlantDemand.LOW -> DeviceLightPlanReason.LOW_LIGHT_PLANTS
                DeviceLightPlantDemand.MEDIUM -> DeviceLightPlanReason.MEDIUM_LIGHT_PLANTS
                DeviceLightPlantDemand.HIGH -> DeviceLightPlanReason.HIGH_LIGHT_PLANTS
            }
        )
        add(
            if (input.co2Installed) {
                DeviceLightPlanReason.CO2_ACTIVE
            } else {
                DeviceLightPlanReason.NO_CO2_SAFETY_CAP
            }
        )
        if (input.activeSoil && tankDay <= 42L) add(DeviceLightPlanReason.ACTIVE_SOIL_STARTUP)
        when (input.ambientLight) {
            DeviceLightAmbientLight.LOW -> Unit
            DeviceLightAmbientLight.INDIRECT -> add(DeviceLightPlanReason.INDIRECT_DAYLIGHT)
            DeviceLightAmbientLight.DIRECT -> add(DeviceLightPlanReason.DIRECT_DAYLIGHT_CAP)
        }
        when (input.algaeLevel) {
            DeviceLightAlgaeLevel.NONE -> Unit
            DeviceLightAlgaeLevel.MILD -> add(DeviceLightPlanReason.MILD_ALGAE_GUARD)
            DeviceLightAlgaeLevel.VISIBLE -> add(DeviceLightPlanReason.VISIBLE_ALGAE_GUARD)
        }
        add(DeviceLightPlanReason.ESTIMATED_PAR)
    }

    private fun fingerprint(
        tank: DeviceLightQuickSetupTank,
        input: DeviceLightQuickSetupInput,
        todayEpochDay: Long
    ): String {
        val source = listOf(PROFILE_VERSION, tank.tankId, tank.productKey, todayEpochDay, input)
            .joinToString("|")
            .lowercase(Locale.ROOT)
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
        return digest.take(8).joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private val STAGE_INTENSITY_FACTORS = listOf(0.75, 0.85, 0.92, 0.96, 1.0)
    private const val STANDARD_TRANSITION_DAYS = 7
    private const val REFERENCE_DISTANCE_CM = 45
    private const val MIN_INTENSITY_PERCENT = 25
    private const val MAX_INTENSITY_PERCENT = 85
    private const val NO_CO2_MAX_INTENSITY_PERCENT = 55
    private const val DIRECT_DAYLIGHT_MAX_INTENSITY_PERCENT = 50
    private const val VISIBLE_ALGAE_MAX_INTENSITY_PERCENT = 45
    private const val MIN_INITIAL_START_PERCENT = 40
    private const val ACTIVE_SOIL_INITIAL_START_PERCENT = 60
    private const val NEW_TANK_INITIAL_START_PERCENT = 70
    private const val MATURE_INITIAL_START_PERCENT = 85
    private const val PERCENT_SCALE = 100.0
    private const val MIN_TARGET_PPFD = 1
    private const val WRGB_PRODUCT_KEY = "LIGHT_WRGB_PRO_ELITE"
    private const val WRGB_DEFAULT_MOUNT_HEIGHT_CM = 10
    private const val RGB_DEFAULT_MOUNT_HEIGHT_CM = 8

    private data class LifecycleContext(
        val safeSetupDay: Long,
        val todayEpochDay: Long,
        val tankDay: Long,
        val currentStageIndex: Int
    )

    private data class PhaseLightProfile(
        val fullIntensityPercent: Int,
        val fullProfilePpfd: Int
    )

    private data class PhaseBuildContext(
        val tank: DeviceLightQuickSetupTank,
        val input: DeviceLightQuickSetupInput,
        val lifecycle: LifecycleContext,
        val lightProfile: PhaseLightProfile,
        val holdForAlgaeReview: Boolean
    )
}

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
    private const val PROFILE_VERSION = "aql-planted-balanced-v1"

    fun calculate(
        tank: DeviceLightQuickSetupTank,
        input: DeviceLightQuickSetupInput,
        todayEpochDay: Long
    ): DeviceLightQuickSetupPlan {
        validateInput(input)
        val lifecycle = lifecycle(tank.setupDateEpochDay, todayEpochDay)
        val fullIntensity = targetIntensityPercent(input)
        val fullProfilePpfd = input.measuredFullProfilePpfd
            ?: estimatedFullProfilePpfd(tank.productKey, input)
        val lightProfile = PhaseLightProfile(fullIntensity, fullProfilePpfd)
        val phases = buildPhases(tank, input, lifecycle, lightProfile)
        val current = phases.first()
        return DeviceLightQuickSetupPlan(
            initialStartPercent = initialStartPercent(lifecycle.tankDay, input.activeSoil),
            currentPhaseIndex = 0,
            currentTargetPpfd = current.targetPpfd,
            currentEstimatedDliMolPerM2Day = current.estimatedDliMolPerM2Day,
            confidence = if (input.measuredFullProfilePpfd == null) {
                DeviceLightPlanConfidence.ESTIMATED
            } else {
                DeviceLightPlanConfidence.CALIBRATED
            },
            reasons = reasons(lifecycle.tankDay, input),
            warnings = warnings(tank, input, todayEpochDay),
            phases = phases,
            reevaluationEpochDay = todayEpochDay + REEVALUATION_DAYS,
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
        if (input.plantDemand == DeviceLightPlantDemand.HIGH && !input.co2Ready) {
            add(DeviceLightPlanWarning.HIGH_LIGHT_WITHOUT_CO2)
        }
        if (input.measuredFullProfilePpfd == null) {
            add(DeviceLightPlanWarning.PAR_NOT_MEASURED)
        }
    }

    private fun buildPhases(
        tank: DeviceLightQuickSetupTank,
        input: DeviceLightQuickSetupInput,
        lifecycle: LifecycleContext,
        lightProfile: PhaseLightProfile
    ): List<DeviceLightQuickSetupPhase> = DeviceLightLifecycleStage.entries
        .drop(lifecycle.currentStageIndex)
        .mapIndexed { relativeIndex, _ ->
            createPhase(
                tank = tank,
                input = input,
                lifecycle = lifecycle,
                relativeIndex = relativeIndex,
                lightProfile = lightProfile
            )
        }

    private fun createPhase(
        tank: DeviceLightQuickSetupTank,
        input: DeviceLightQuickSetupInput,
        lifecycle: LifecycleContext,
        relativeIndex: Int,
        lightProfile: PhaseLightProfile
    ): DeviceLightQuickSetupPhase {
        val absoluteIndex = lifecycle.currentStageIndex + relativeIndex
        val stage = DeviceLightLifecycleStage.entries[absoluteIndex]
        val fromDay = if (relativeIndex == 0) {
            lifecycle.todayEpochDay
        } else {
            lifecycle.safeSetupDay + stage.dayStart - 1L
        }
        val nextStage = DeviceLightLifecycleStage.entries.getOrNull(absoluteIndex + 1)
        val untilDay = nextStage?.let { lifecycle.safeSetupDay + it.dayStart - 1L }
        val stageIntensity = (
            lightProfile.fullIntensityPercent * STAGE_INTENSITY_FACTORS[absoluteIndex]
        )
            .roundToInt()
            .coerceIn(MIN_INTENSITY_PERCENT, MAX_INTENSITY_PERCENT)
        val targetPpfd = (lightProfile.fullProfilePpfd * stageIntensity / PERCENT_SCALE)
            .roundToInt()
            .coerceAtLeast(MIN_TARGET_PPFD)
        val startMinute = input.preferredLightsOffMinute - stage.durationMinutes
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
                endTimeMs = input.preferredLightsOffMinute * MINUTE_MS,
                rampDurationMs = RAMP_MS,
                scene = balancedScene(tank.productKey, stageIntensity)
            )
        )
    }

    private fun initialStartPercent(tankDay: Long, activeSoil: Boolean): Int = when {
        tankDay > FIRST_STAGE_DAYS -> MATURE_INITIAL_START_PERCENT
        activeSoil -> ACTIVE_SOIL_INITIAL_START_PERCENT
        else -> NEW_TANK_INITIAL_START_PERCENT
    }

    private fun validateInput(input: DeviceLightQuickSetupInput) {
        require(input.waterDepthCm in 10..100)
        require(input.fixtureHeightCm in 0..60)
        require(input.preferredLightsOffMinute in 720 until MINUTES_PER_DAY)
        input.measuredFullProfilePpfd?.let { require(it in 20..500) }
        val maximumDuration = DeviceLightLifecycleStage.entries.maxOf { it.durationMinutes }
        require(input.preferredLightsOffMinute >= maximumDuration)
    }

    private fun lifecycleIndex(tankDay: Long): Int =
        DeviceLightLifecycleStage.entries.indexOfLast { tankDay >= it.dayStart }
            .coerceAtLeast(0)

    private fun targetIntensityPercent(input: DeviceLightQuickSetupInput): Int {
        var intensity = when (input.plantDemand) {
            DeviceLightPlantDemand.LOW -> 42
            DeviceLightPlantDemand.MEDIUM -> 56
            DeviceLightPlantDemand.HIGH -> 72
        }
        intensity += when (input.plantDensity) {
            DeviceLightPlantDensity.SPARSE -> -5
            DeviceLightPlantDensity.MEDIUM -> 0
            DeviceLightPlantDensity.DENSE -> 5
        }
        intensity += when (input.ambientLevel) {
            DeviceLightAmbientLevel.LOW -> 0
            DeviceLightAmbientLevel.MEDIUM -> -4
            DeviceLightAmbientLevel.HIGH -> -8
        }
        intensity += ((input.waterDepthCm + input.fixtureHeightCm) - REFERENCE_DISTANCE_CM) / 4
        if (input.activeSoil) intensity -= 3
        if (!input.co2Ready) intensity = intensity.coerceAtMost(NO_CO2_MAX_INTENSITY_PERCENT)
        return intensity.coerceIn(MIN_INTENSITY_PERCENT, MAX_INTENSITY_PERCENT)
    }

    private fun estimatedFullProfilePpfd(
        productKey: String,
        input: DeviceLightQuickSetupInput
    ): Int {
        val reference = if (productKey == WRGB_PRODUCT_KEY) 105.0 else 78.0
        val distance = input.waterDepthCm + input.fixtureHeightCm
        val attenuation = exp(-0.018 * (distance - REFERENCE_DISTANCE_CM))
        return (reference * attenuation).roundToInt().coerceIn(35, 180)
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
        add(if (input.co2Ready) DeviceLightPlanReason.CO2_CONFIRMED else DeviceLightPlanReason.NO_CO2_SAFETY_CAP)
        if (input.activeSoil && tankDay <= 42L) add(DeviceLightPlanReason.ACTIVE_SOIL_STARTUP)
        if (input.ambientLevel != DeviceLightAmbientLevel.LOW) {
            add(DeviceLightPlanReason.AMBIENT_LIGHT_COMPENSATION)
        }
        add(
            if (input.measuredFullProfilePpfd == null) {
                DeviceLightPlanReason.ESTIMATED_PAR
            } else {
                DeviceLightPlanReason.MEASURED_PAR
            }
        )
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
    private const val ACTIVE_SOIL_INITIAL_START_PERCENT = 60
    private const val NEW_TANK_INITIAL_START_PERCENT = 70
    private const val MATURE_INITIAL_START_PERCENT = 85
    private const val PERCENT_SCALE = 100.0
    private const val MIN_TARGET_PPFD = 1
    private const val WRGB_PRODUCT_KEY = "LIGHT_WRGB_PRO_ELITE"

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
}

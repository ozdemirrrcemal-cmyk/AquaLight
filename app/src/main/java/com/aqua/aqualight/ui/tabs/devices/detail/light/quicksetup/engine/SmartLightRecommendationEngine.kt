package com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.engine

import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveChannelValues
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.RepeatMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupAlgaeRisk
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupLightProfile
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupPlantDemand
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupPlantDensity
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupRecommendation
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupRecommendationConfidence
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupSetupPhase
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupTankProfile
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupTankStyle
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupTankType
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupTechLevel
import kotlin.math.roundToInt

object SmartLightRecommendationEngine {

    fun recommend(
        tankProfile: QuickSetupTankProfile
    ): QuickSetupRecommendation {
        val setupPhase = resolveSetupPhase(tankProfile)
        val lightProfile = selectLightProfile(tankProfile)
        val agePolicy = buildAgePolicy(setupPhase)
        val algaePolicy = buildAlgaePolicy(tankProfile)
        val livestockPolicy = buildLivestockPolicy(tankProfile)

        val durationMinutes = calculateDurationMinutes(
            profile = tankProfile,
            lightProfile = lightProfile,
            agePolicy = agePolicy,
            algaePolicy = algaePolicy,
            livestockPolicy = livestockPolicy
        )

        val intensityMultiplier = calculateIntensityMultiplier(
            profile = tankProfile,
            lightProfile = lightProfile,
            agePolicy = agePolicy,
            algaePolicy = algaePolicy,
            livestockPolicy = livestockPolicy
        )

        val channelValues = applyFinalChannelRules(
            profile = tankProfile,
            baseChannels = lightProfile.baseChannels,
            multiplier = intensityMultiplier
        )

        val start = LightCurvePoint.of(10, 0)
        val rampMinutes = calculateRampMinutes(durationMinutes)

        val end = start.plusMinutes(durationMinutes)
        val peakStart = start.plusMinutes(rampMinutes)
        val peakEnd = end.minusMinutes(rampMinutes)

        val draft = LightProgramDraft.default().copy(
            start = start,
            peakStart = peakStart,
            peakEnd = peakEnd,
            end = end,
            channelValues = channelValues,
            repeatMode = RepeatMode.EVERY,
            selectedDays = setOf(1, 2, 3, 4, 5, 6, 7)
        )

        val confidence = calculateConfidence(tankProfile)

        return QuickSetupRecommendation(
            title = buildRecommendationTitle(
                profile = tankProfile,
                lightProfile = lightProfile,
                setupPhase = setupPhase,
                algaePolicy = algaePolicy
            ),
            profileLabel = lightProfile.profileLabel,
            goalLabel = lightProfile.goalLabel,
            setupPhaseLabel = setupPhase.toLabel(),
            techLevelLabel = tankProfile.techLevel.toLabel(),
            durationLabel = formatDuration(durationMinutes),
            intensityLabel = buildIntensityLabel(intensityMultiplier),
            confidenceLabel = confidence.toLabel(),
            start = start,
            peakStart = peakStart,
            peakEnd = peakEnd,
            end = end,
            channelValues = channelValues,
            tankSummary = buildTankSummary(tankProfile, setupPhase),
            reasoningNotes = buildReasoningNotes(
                profile = tankProfile,
                lightProfile = lightProfile,
                setupPhase = setupPhase,
                agePolicy = agePolicy,
                algaePolicy = algaePolicy,
                livestockPolicy = livestockPolicy,
                durationMinutes = durationMinutes,
                intensityMultiplier = intensityMultiplier
            ),
            warnings = buildWarnings(tankProfile, setupPhase),
            draft = draft
        )
    }

    private fun selectLightProfile(
        profile: QuickSetupTankProfile
    ): QuickSetupLightProfile {
        if (
            profile.tankType == QuickSetupTankType.REEF ||
            profile.tankType == QuickSetupTankType.MARINE
        ) {
            return QuickSetupLightProfile(
                title = "Marine Blue Viewing",
                profileLabel = "Marine / reef display",
                goalLabel = "Blue-weighted viewing",
                baseDurationMinutes = 420,
                baseChannels = LightCurveChannelValues(
                    red = 8,
                    green = 18,
                    blue = 95,
                    white = 45
                ),
                baseIntensityMultiplier = 0.70
            )
        }

        if (profile.tankType == QuickSetupTankType.BLACKWATER) {
            return QuickSetupLightProfile(
                title = "Blackwater Soft View",
                profileLabel = "Blackwater aquarium",
                goalLabel = "Warm low-intensity viewing",
                baseDurationMinutes = 390,
                baseChannels = LightCurveChannelValues(
                    red = 55,
                    green = 35,
                    blue = 18,
                    white = 35
                ),
                baseIntensityMultiplier = 0.72
            )
        }

        if (
            profile.tankType == QuickSetupTankType.FISH_ONLY ||
            profile.tankStyle == QuickSetupTankStyle.FISH_ONLY ||
            profile.plantDensity == QuickSetupPlantDensity.NONE
        ) {
            return QuickSetupLightProfile(
                title = "Fish Display",
                profileLabel = "Fish-only aquarium",
                goalLabel = "Natural View + Color Boost",
                baseDurationMinutes = 450,
                baseChannels = LightCurveChannelValues(
                    red = 85,
                    green = 85,
                    blue = 90,
                    white = 45
                ),
                baseIntensityMultiplier = 0.82
            )
        }

        if (
            profile.tankType == QuickSetupTankType.SHRIMP ||
            profile.tankStyle == QuickSetupTankStyle.SHRIMP_TANK ||
            profile.hasShrimp ||
            profile.hasSensitiveLivestock
        ) {
            if (
                profile.hasCo2 &&
                profile.plantDemand == QuickSetupPlantDemand.HIGH &&
                profile.plantDensity >= QuickSetupPlantDensity.MEDIUM
            ) {
                return QuickSetupLightProfile(
                    title = "Planted Shrimp Balance",
                    profileLabel = "Planted shrimp aquarium",
                    goalLabel = "Plant Growth + Gentle Livestock Safety",
                    baseDurationMinutes = 420,
                    baseChannels = LightCurveChannelValues(
                        red = 72,
                        green = 68,
                        blue = 42,
                        white = 66
                    ),
                    baseIntensityMultiplier = 0.78
                )
            }

            return QuickSetupLightProfile(
                title = "Shrimp Safe",
                profileLabel = "Shrimp or sensitive livestock",
                goalLabel = "Gentle Light + Stable Growth",
                baseDurationMinutes = 390,
                baseChannels = LightCurveChannelValues(
                    red = 45,
                    green = 55,
                    blue = 30,
                    white = 45
                ),
                baseIntensityMultiplier = 0.72
            )
        }

        if (
            profile.tankStyle == QuickSetupTankStyle.IWAGUMI ||
            profile.hasGroundCoverPlants
        ) {
            val channels = if (profile.hasCo2) {
                LightCurveChannelValues(
                    red = 88,
                    green = 72,
                    blue = 50,
                    white = 82
                )
            } else {
                LightCurveChannelValues(
                    red = 65,
                    green = 62,
                    blue = 38,
                    white = 62
                )
            }

            return QuickSetupLightProfile(
                title = if (profile.hasCo2) {
                    "Iwagumi Carpet Growth"
                } else {
                    "Low-Tech Carpet Safe Start"
                },
                profileLabel = "Carpet / foreground planted layout",
                goalLabel = "Controlled foreground growth",
                baseDurationMinutes = if (profile.hasCo2) 480 else 390,
                baseChannels = channels,
                baseIntensityMultiplier = if (profile.hasCo2) 0.90 else 0.70
            )
        }

        if (
            profile.techLevel == QuickSetupTechLevel.HIGH_TECH ||
            (
                profile.hasCo2 &&
                    profile.plantDensity >= QuickSetupPlantDensity.MEDIUM &&
                    profile.plantDemand >= QuickSetupPlantDemand.MEDIUM
                )
        ) {
            val channels = if (profile.hasRedPlants) {
                LightCurveChannelValues(
                    red = 95,
                    green = 65,
                    blue = 65,
                    white = 80
                )
            } else {
                LightCurveChannelValues(
                    red = 90,
                    green = 75,
                    blue = 50,
                    white = 85
                )
            }

            return QuickSetupLightProfile(
                title = if (profile.hasRedPlants) {
                    "High-Tech Red Plant"
                } else {
                    "High-Tech Plant Growth"
                },
                profileLabel = "CO₂ planted aquarium",
                goalLabel = if (profile.hasRedPlants) {
                    "Plant Growth + Red Plant Support"
                } else {
                    "Plant Growth"
                },
                baseDurationMinutes = when (profile.plantDensity) {
                    QuickSetupPlantDensity.DENSE -> 510
                    QuickSetupPlantDensity.MEDIUM -> 480
                    else -> 450
                },
                baseChannels = channels,
                baseIntensityMultiplier = 0.95
            )
        }

        if (
            profile.tankStyle == QuickSetupTankStyle.DUTCH ||
            profile.hasStemPlants
        ) {
            return QuickSetupLightProfile(
                title = if (profile.hasRedPlants) {
                    "Dutch Red Plant Balance"
                } else {
                    "Dutch Stem Growth"
                },
                profileLabel = "Stem-heavy planted aquarium",
                goalLabel = "Balanced stem plant growth",
                baseDurationMinutes = 450,
                baseChannels = if (profile.hasRedPlants) {
                    LightCurveChannelValues(
                        red = 82,
                        green = 58,
                        blue = 58,
                        white = 70
                    )
                } else {
                    LightCurveChannelValues(
                        red = 74,
                        green = 68,
                        blue = 44,
                        white = 70
                    )
                },
                baseIntensityMultiplier = 0.82
            )
        }

        if (
            profile.tankStyle == QuickSetupTankStyle.LOW_TECH ||
            profile.techLevel == QuickSetupTechLevel.LOW_TECH
        ) {
            return QuickSetupLightProfile(
                title = "Low-Tech Plant Growth",
                profileLabel = "Low-tech planted aquarium",
                goalLabel = "Plant Growth + Stability",
                baseDurationMinutes = when (profile.plantDensity) {
                    QuickSetupPlantDensity.LOW -> 390
                    QuickSetupPlantDensity.MEDIUM -> 420
                    QuickSetupPlantDensity.DENSE -> 450
                    QuickSetupPlantDensity.NONE -> 390
                },
                baseChannels = LightCurveChannelValues(
                    red = 70,
                    green = 65,
                    blue = 40,
                    white = 65
                ),
                baseIntensityMultiplier = 0.76
            )
        }

        return QuickSetupLightProfile(
            title = "Natural Balanced Light",
            profileLabel = "Balanced freshwater aquarium",
            goalLabel = "Natural View + Stable Growth",
            baseDurationMinutes = 420,
            baseChannels = LightCurveChannelValues(
                red = 72,
                green = 72,
                blue = 48,
                white = 72
            ),
            baseIntensityMultiplier = 0.80
        )
    }

    private fun resolveSetupPhase(
    profile: QuickSetupTankProfile
): QuickSetupSetupPhase {
    if (profile.setupPhase != QuickSetupSetupPhase.UNKNOWN) {
        return profile.setupPhase
    }

    if (profile.setupAgeDays <= 0) {
        return QuickSetupSetupPhase.UNKNOWN
    }

    return when (profile.setupAgeDays) {
        in 1..7 -> QuickSetupSetupPhase.FIRST_WEEK
        in 8..14 -> QuickSetupSetupPhase.EARLY_START
        in 15..30 -> QuickSetupSetupPhase.STABILIZING
        in 31..60 -> QuickSetupSetupPhase.BALANCED_RAMP_UP
        else -> QuickSetupSetupPhase.MATURE
    }
}

    private fun buildAgePolicy(
        phase: QuickSetupSetupPhase
    ): Policy {
        return when (phase) {
            QuickSetupSetupPhase.FIRST_WEEK -> {
                Policy(
                    maxDurationMinutes = 240,
                    multiplier = 0.55,
                    note = "The tank is in its first week, so light is limited to around 4 hours to reduce early algae pressure."
                )
            }

            QuickSetupSetupPhase.EARLY_START -> {
                Policy(
                    maxDurationMinutes = 300,
                    multiplier = 0.65,
                    note = "The tank is still in the early start phase, so light is increased slowly while the biological balance develops."
                )
            }

            QuickSetupSetupPhase.STABILIZING -> {
                Policy(
                    maxDurationMinutes = 360,
                    multiplier = 0.75,
                    note = "The tank is stabilizing, so the program uses moderate light without rushing intensity."
                )
            }

            QuickSetupSetupPhase.BALANCED_RAMP_UP -> {
                Policy(
                    maxDurationMinutes = 420,
                    multiplier = 0.90,
                    note = "The tank is past the most sensitive startup period, so light can be gradually increased."
                )
            }

            QuickSetupSetupPhase.MATURE -> {
                Policy(
                    maxDurationMinutes = Int.MAX_VALUE,
                    multiplier = 1.0,
                    note = "The tank is mature enough for a full recommendation based on plants, CO₂ and livestock."
                )
            }

            QuickSetupSetupPhase.UNKNOWN -> {
                Policy(
                    maxDurationMinutes = 360,
                    multiplier = 0.75,
                    note = "Tank age is unknown, so a conservative light profile is recommended."
                )
            }
        }
    }

    private fun buildAlgaePolicy(
        profile: QuickSetupTankProfile
    ): Policy {
        return when (profile.algaeRisk) {
            QuickSetupAlgaeRisk.HIGH -> {
                Policy(
                    maxDurationMinutes = 360,
                    multiplier = 0.75,
                    note = "High algae risk detected, so duration and intensity are reduced."
                )
            }

            QuickSetupAlgaeRisk.NORMAL -> {
                Policy(
                    maxDurationMinutes = Int.MAX_VALUE,
                    multiplier = 1.0,
                    note = "Algae risk is normal, so the recommendation follows the tank profile."
                )
            }

            QuickSetupAlgaeRisk.LOW -> {
                Policy(
                    maxDurationMinutes = Int.MAX_VALUE,
                    multiplier = 1.0,
                    note = "Algae risk is low, so no additional light restriction is applied."
                )
            }
        }
    }

    private fun buildLivestockPolicy(
        profile: QuickSetupTankProfile
    ): Policy {
        val hasSensitiveLivestock =
            profile.hasShrimp ||
                profile.hasSensitiveLivestock

        return if (hasSensitiveLivestock) {
            Policy(
                maxDurationMinutes = 420,
                multiplier = 0.88,
                note = "Shrimp or sensitive livestock detected, so sudden high intensity is avoided."
            )
        } else {
            Policy(
                maxDurationMinutes = Int.MAX_VALUE,
                multiplier = 1.0,
                note = "No sensitive livestock restriction is required."
            )
        }
    }

    private fun calculateDurationMinutes(
        profile: QuickSetupTankProfile,
        lightProfile: QuickSetupLightProfile,
        agePolicy: Policy,
        algaePolicy: Policy,
        livestockPolicy: Policy
    ): Int {
        var duration = lightProfile.baseDurationMinutes

        duration = minOf(
            duration,
            agePolicy.maxDurationMinutes,
            algaePolicy.maxDurationMinutes,
            livestockPolicy.maxDurationMinutes
        )

        if (!profile.hasCo2 && profile.plantDemand != QuickSetupPlantDemand.HIGH) {
            duration = minOf(duration, 420)
        }

        if (
            profile.hasFloatingPlants &&
            profile.plantDensity <= QuickSetupPlantDensity.MEDIUM
        ) {
            duration = minOf(duration, 390)
        }

        if (
            profile.tankType == QuickSetupTankType.BLACKWATER ||
            profile.tankStyle == QuickSetupTankStyle.BIOTOPE
        ) {
            duration = minOf(duration, 390)
        }

        return duration.coerceIn(
            minimumValue = 240,
            maximumValue = 540
        )
    }

    private fun calculateIntensityMultiplier(
        profile: QuickSetupTankProfile,
        lightProfile: QuickSetupLightProfile,
        agePolicy: Policy,
        algaePolicy: Policy,
        livestockPolicy: Policy
    ): Double {
        var multiplier =
            lightProfile.baseIntensityMultiplier *
                agePolicy.multiplier *
                algaePolicy.multiplier *
                livestockPolicy.multiplier

        if (!profile.hasCo2) {
            multiplier *= 0.92
        }

        if (!profile.hasFertilizer && profile.plantDensity >= QuickSetupPlantDensity.MEDIUM) {
            multiplier *= 0.92
        }

        if (profile.hasFloatingPlants) {
            multiplier *= 0.90
        }

        if (
            profile.hasCo2 &&
            profile.hasFertilizer &&
            profile.hasNutrientSubstrate &&
            profile.plantDensity == QuickSetupPlantDensity.DENSE &&
            profile.plantDemand == QuickSetupPlantDemand.HIGH &&
            profile.algaeRisk != QuickSetupAlgaeRisk.HIGH &&
            resolveSetupPhase(profile) == QuickSetupSetupPhase.MATURE
        ) {
            multiplier *= 1.08
        }

        return multiplier.coerceIn(0.42, 1.0)
    }

    private fun applyFinalChannelRules(
        profile: QuickSetupTankProfile,
        baseChannels: LightCurveChannelValues,
        multiplier: Double
    ): LightCurveChannelValues {
        var red = scaleChannel(baseChannels.red, multiplier)
        var green = scaleChannel(baseChannels.green, multiplier)
        var blue = scaleChannel(baseChannels.blue, multiplier)
        var white = scaleChannel(baseChannels.white, multiplier)

        if (profile.hasRedPlants) {
            red = (red + 6).coerceAtMost(100)
            blue = (blue + 4).coerceAtMost(100)
        }

        if (profile.hasShrimp || profile.hasSensitiveLivestock) {
            white = (white * 0.92).roundToInt().coerceIn(0, 100)
            blue = (blue * 0.94).roundToInt().coerceIn(0, 100)
        }

        if (profile.hasFloatingPlants) {
            white = (white * 0.88).roundToInt().coerceIn(0, 100)
            green = (green * 0.92).roundToInt().coerceIn(0, 100)
        }

        if (
            profile.tankType == QuickSetupTankType.BLACKWATER ||
            profile.tankStyle == QuickSetupTankStyle.BIOTOPE
        ) {
            blue = (blue * 0.75).roundToInt().coerceIn(0, 100)
            white = (white * 0.80).roundToInt().coerceIn(0, 100)
            red = (red + 8).coerceAtMost(100)
        }

        return LightCurveChannelValues(
            red = red,
            green = green,
            blue = blue,
            white = white
        )
    }

    private fun scaleChannel(
        value: Int,
        multiplier: Double
    ): Int {
        return (value * multiplier)
            .roundToInt()
            .coerceIn(0, 100)
    }

    private fun calculateRampMinutes(
        durationMinutes: Int
    ): Int {
        return when {
            durationMinutes <= 300 -> 30
            durationMinutes <= 390 -> 45
            else -> 60
        }
    }

    private fun buildRecommendationTitle(
        profile: QuickSetupTankProfile,
        lightProfile: QuickSetupLightProfile,
        setupPhase: QuickSetupSetupPhase,
        algaePolicy: Policy
    ): String {
        if (profile.algaeRisk == QuickSetupAlgaeRisk.HIGH) {
            return "Safe Low-Algae Setup"
        }

        return when (setupPhase) {
            QuickSetupSetupPhase.FIRST_WEEK -> "First Week Safe Start"
            QuickSetupSetupPhase.EARLY_START -> "Early Tank Setup"
            QuickSetupSetupPhase.STABILIZING -> "Stabilizing Tank Setup"
            else -> lightProfile.title
        }
    }

    private fun buildTankSummary(
        profile: QuickSetupTankProfile,
        setupPhase: QuickSetupSetupPhase
    ): List<String> {
        val summary = mutableListOf<String>()

        summary.add("${profile.tankName} · ${profile.volumeLiters}L")
        summary.add("Setup phase: ${setupPhase.toLabel()}")
        summary.add("Tech level: ${profile.techLevel.toLabel()}")

        if (profile.plantCount > 0) {
            summary.add("Plants: ${profile.plantCount} · ${profile.plantDensity.toLabel()} density")
        } else {
            summary.add("Plants: none detected")
        }

        val livestock = mutableListOf<String>()

        if (profile.hasFish) livestock.add("Fish")
        if (profile.hasShrimp) livestock.add("Shrimp")
        if (profile.hasSnails) livestock.add("Snails")

        summary.add(
            if (livestock.isEmpty()) {
                "Livestock: none detected"
            } else {
                "Livestock: ${livestock.joinToString(" + ")}"
            }
        )

        return summary
    }

    private fun buildReasoningNotes(
        profile: QuickSetupTankProfile,
        lightProfile: QuickSetupLightProfile,
        setupPhase: QuickSetupSetupPhase,
        agePolicy: Policy,
        algaePolicy: Policy,
        livestockPolicy: Policy,
        durationMinutes: Int,
        intensityMultiplier: Double
    ): List<String> {
        val notes = mutableListOf<String>()

        notes.add(agePolicy.note)

        if (profile.algaeRisk == QuickSetupAlgaeRisk.HIGH) {
            notes.add(algaePolicy.note)
        }

        if (profile.hasCo2) {
            notes.add("CO₂ is available, so the program can support stronger planted-tank output.")
        } else {
            notes.add("No CO₂ is detected, so intensity is kept controlled for a safer low-tech setup.")
        }

        if (profile.hasFertilizer) {
            notes.add("Fertilizer is detected, so plant growth support is considered in the light profile.")
        } else if (profile.plantDensity >= QuickSetupPlantDensity.MEDIUM) {
            notes.add("No fertilizer is detected for a planted tank, so the recommendation avoids excessive intensity.")
        }

        if (profile.hasNutrientSubstrate) {
            notes.add("Nutrient substrate is detected, supporting stronger planted-tank stability.")
        }

        when (profile.plantDensity) {
            QuickSetupPlantDensity.NONE -> {
                notes.add("No planted layout is detected, so the recommendation focuses on natural viewing and livestock display.")
            }

            QuickSetupPlantDensity.LOW -> {
                notes.add("Low plant density detected, so the program avoids excessive intensity.")
            }

            QuickSetupPlantDensity.MEDIUM -> {
                notes.add("Medium plant density detected, suitable for balanced plant growth.")
            }

            QuickSetupPlantDensity.DENSE -> {
                notes.add("Dense planting detected, so the spectrum supports stronger plant growth.")
            }
        }

        when (profile.plantDemand) {
            QuickSetupPlantDemand.LOW -> {
                notes.add("Low-demand plants do not require aggressive lighting.")
            }

            QuickSetupPlantDemand.MEDIUM -> {
                notes.add("Medium-demand plants receive a balanced growth profile.")
            }

            QuickSetupPlantDemand.HIGH -> {
                notes.add("High-demand plants are considered, but final intensity is limited by tank age, CO₂ and algae risk.")
            }
        }

        if (profile.hasGroundCoverPlants) {
            notes.add("Ground cover plants detected, so ramped lighting helps avoid sudden high-intensity exposure.")
        }

        if (profile.hasEpiphytePlants) {
            notes.add("Epiphyte plants are detected, so the program avoids overly aggressive lighting.")
        }

        if (profile.hasFloatingPlants) {
            notes.add("Floating plants are detected, so output is slightly reduced because surface shade is expected.")
        }

        if (profile.hasRedPlants) {
            notes.add("Red plants detected, so red and blue channels are supported without overexposing the tank.")
        }

        if (profile.hasShrimp || profile.hasSensitiveLivestock) {
            notes.add(livestockPolicy.note)
        }

        notes.add("Recommended duration: ${formatDuration(durationMinutes)}.")
        notes.add("Recommended intensity: ${buildIntensityLabel(intensityMultiplier)}.")
        notes.add("Suggested profile: ${lightProfile.title}.")

        return notes
    }

    private fun buildWarnings(
        profile: QuickSetupTankProfile,
        setupPhase: QuickSetupSetupPhase
    ): List<String> {
        val warnings = mutableListOf<String>()

        warnings.addAll(profile.profileWarnings)

        if (
            setupPhase == QuickSetupSetupPhase.FIRST_WEEK ||
            setupPhase == QuickSetupSetupPhase.EARLY_START
        ) {
            warnings.add("Avoid increasing duration too quickly during the first two weeks.")
        }

        if (
            profile.plantDemand == QuickSetupPlantDemand.HIGH &&
            !profile.hasCo2
        ) {
            warnings.add("High-demand plants are detected without CO₂. Consider keeping intensity conservative.")
        }

        if (
            profile.plantDensity == QuickSetupPlantDensity.NONE &&
            profile.tankType != QuickSetupTankType.FISH_ONLY
        ) {
            warnings.add("No plants are detected. If this is incorrect, update the tank profile for a better recommendation.")
        }

        if (
            profile.tankType == QuickSetupTankType.REEF ||
            profile.tankType == QuickSetupTankType.MARINE
        ) {
            warnings.add("Marine and reef tanks may require specialized spectrum support. Verify device compatibility before applying.")
        }

        return warnings.distinct()
    }

    private fun calculateConfidence(
        profile: QuickSetupTankProfile
    ): QuickSetupRecommendationConfidence {
        var score = 0

        if (profile.tankId != null) score += 1
        if (profile.volumeLiters > 0) score += 1
        if (profile.setupAgeDays > 0) score += 1
        if (profile.tankType != QuickSetupTankType.UNKNOWN) score += 1
        if (profile.tankStyle != QuickSetupTankStyle.UNKNOWN) score += 1
        if (profile.plantCount > 0 || profile.hasFish || profile.hasShrimp) score += 1

        return when {
            score >= 5 -> QuickSetupRecommendationConfidence.HIGH
            score >= 3 -> QuickSetupRecommendationConfidence.MEDIUM
            else -> QuickSetupRecommendationConfidence.LOW
        }
    }

    private fun buildIntensityLabel(
        multiplier: Double
    ): String {
        return when {
            multiplier < 0.55 -> "Very Gentle"
            multiplier < 0.72 -> "Gentle"
            multiplier < 0.88 -> "Balanced"
            else -> "Strong"
        }
    }

    private fun formatDuration(
        minutes: Int
    ): String {
        val hours = minutes / 60
        val remainingMinutes = minutes % 60

        return if (remainingMinutes == 0) {
            "${hours}h"
        } else {
            "${hours}h ${remainingMinutes}m"
        }
    }

    private fun QuickSetupSetupPhase.toLabel(): String {
        return when (this) {
            QuickSetupSetupPhase.UNKNOWN -> "Unknown"
            QuickSetupSetupPhase.FIRST_WEEK -> "First week"
            QuickSetupSetupPhase.EARLY_START -> "Early start"
            QuickSetupSetupPhase.STABILIZING -> "Stabilizing"
            QuickSetupSetupPhase.BALANCED_RAMP_UP -> "Balanced ramp-up"
            QuickSetupSetupPhase.MATURE -> "Mature"
        }
    }

    private fun QuickSetupTechLevel.toLabel(): String {
        return when (this) {
            QuickSetupTechLevel.LOW_TECH -> "Low-tech"
            QuickSetupTechLevel.MID_TECH -> "Mid-tech"
            QuickSetupTechLevel.HIGH_TECH -> "High-tech"
        }
    }

    private fun QuickSetupPlantDensity.toLabel(): String {
        return when (this) {
            QuickSetupPlantDensity.NONE -> "No"
            QuickSetupPlantDensity.LOW -> "Low"
            QuickSetupPlantDensity.MEDIUM -> "Medium"
            QuickSetupPlantDensity.DENSE -> "Dense"
        }
    }

    private fun QuickSetupRecommendationConfidence.toLabel(): String {
        return when (this) {
            QuickSetupRecommendationConfidence.LOW -> "Low confidence"
            QuickSetupRecommendationConfidence.MEDIUM -> "Medium confidence"
            QuickSetupRecommendationConfidence.HIGH -> "High confidence"
        }
    }

    private fun LightCurvePoint.plusMinutes(
        minutesToAdd: Int
    ): LightCurvePoint {
        val total = (hour * 60 + minute + minutesToAdd)
            .coerceIn(0, 23 * 60 + 59)

        return LightCurvePoint.of(
            hour = total / 60,
            minute = total % 60
        )
    }

    private fun LightCurvePoint.minusMinutes(
        minutesToSubtract: Int
    ): LightCurvePoint {
        val total = (hour * 60 + minute - minutesToSubtract)
            .coerceIn(0, 23 * 60 + 59)

        return LightCurvePoint.of(
            hour = total / 60,
            minute = total % 60
        )
    }

    private data class Policy(
        val maxDurationMinutes: Int,
        val multiplier: Double,
        val note: String
    )
}
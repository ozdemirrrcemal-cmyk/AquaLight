package com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.mapper

import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupAlgaeRisk
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupPlantDemand
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupPlantDensity
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupRecommendationConfidence
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupSetupPhase
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupTankProfile
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupTankStyle
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupTankType
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupTechLevel
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

object QuickSetupTankProfileMapper {

    fun map(
        tank: SavedAquariumTank,
        nowMillis: Long = System.currentTimeMillis()
    ): QuickSetupTankProfile {
        val volumeLiters = calculateVolumeLiters(tank)

        val setupAgeDays = calculateSetupAgeDays(
            setupDateMillis = tank.setupDateMillis,
            nowMillis = nowMillis
        )

        val setupPhase = resolveSetupPhase(
            setupDateMillis = tank.setupDateMillis,
            setupAgeDays = setupAgeDays
        )

        val tankType = mapTankType(tank.tankType)
        val tankStyle = mapTankStyle(tank.tankStyle)

        val hasCo2 = tank.materials.any { material ->
            isCo2Material(
                categoryKey = material.categoryKey,
                categoryTitle = material.categoryTitle,
                name = material.name
            )
        }

        val hasFertilizer = tank.materials.any { material ->
            isFertilizerMaterial(
                categoryKey = material.categoryKey,
                categoryTitle = material.categoryTitle,
                name = material.name
            )
        }

        val hasNutrientSubstrate = tank.materials.any { material ->
            isNutrientSubstrateMaterial(
                categoryKey = material.categoryKey,
                categoryTitle = material.categoryTitle,
                name = material.name
            )
        }

        val plantCount = tank.plants.size

        val plantDensity = resolvePlantDensity(
            plantCount = plantCount,
            volumeLiters = volumeLiters
        )

        val hasGroundCoverPlants = tank.plants.any {
            isGroundCoverPlant(
                category = it.category,
                name = it.plantName
            )
        }

        val hasStemPlants = tank.plants.any {
            isStemPlant(
                category = it.category,
                name = it.plantName
            )
        }

        val hasEpiphytePlants = tank.plants.any {
            isEpiphytePlant(
                category = it.category,
                name = it.plantName
            )
        }

        val hasFloatingPlants = tank.plants.any {
            isFloatingPlant(
                category = it.category,
                name = it.plantName
            )
        }

        val hasRedPlants = tank.plants.any {
            isRedPlant(it.plantName)
        }

        val plantDemand = resolvePlantDemand(
            tank = tank,
            hasGroundCoverPlants = hasGroundCoverPlants,
            hasStemPlants = hasStemPlants,
            hasRedPlants = hasRedPlants
        )

        val hasFish = tank.livestock.any {
            isFishLivestock(
                category = it.category,
                name = it.name
            )
        }

        val hasShrimp = tank.livestock.any {
            isShrimpLivestock(
                category = it.category,
                name = it.name
            )
        }

        val hasSnails = tank.livestock.any {
            isSnailLivestock(
                category = it.category,
                name = it.name
            )
        }

        val hasSensitiveLivestock = tank.livestock.any {
            isSensitiveLivestock(
                category = it.category,
                name = it.name
            )
        }

        val techLevel = resolveTechLevel(
            hasCo2 = hasCo2,
            hasFertilizer = hasFertilizer,
            hasNutrientSubstrate = hasNutrientSubstrate,
            plantDensity = plantDensity,
            plantDemand = plantDemand
        )

        val algaeRisk = resolveAlgaeRisk(
            setupPhase = setupPhase,
            hasCo2 = hasCo2,
            hasFertilizer = hasFertilizer,
            plantDensity = plantDensity,
            plantDemand = plantDemand,
            hasFloatingPlants = hasFloatingPlants,
            tankType = tankType
        )

        val warnings = buildWarnings(
            volumeLiters = volumeLiters,
            setupPhase = setupPhase,
            plantDensity = plantDensity,
            plantDemand = plantDemand,
            hasCo2 = hasCo2,
            tankType = tankType
        )

        val confidence = resolveConfidence(
            tank = tank,
            volumeLiters = volumeLiters,
            setupPhase = setupPhase,
            plantCount = plantCount,
            hasFish = hasFish,
            hasShrimp = hasShrimp
        )

        return QuickSetupTankProfile(
            tankId = tank.id,
            tankName = tank.name.ifBlank {
                "Selected Tank"
            },
            volumeLiters = volumeLiters,
            setupAgeDays = setupAgeDays,
            tankType = tankType,
            tankStyle = tankStyle,
            setupPhase = setupPhase,
            techLevel = techLevel,
            hasCo2 = hasCo2,
            hasFertilizer = hasFertilizer,
            hasNutrientSubstrate = hasNutrientSubstrate,
            plantCount = plantCount,
            plantDensity = plantDensity,
            plantDemand = plantDemand,
            hasGroundCoverPlants = hasGroundCoverPlants,
            hasStemPlants = hasStemPlants,
            hasEpiphytePlants = hasEpiphytePlants,
            hasFloatingPlants = hasFloatingPlants,
            hasRedPlants = hasRedPlants,
            hasFish = hasFish,
            hasShrimp = hasShrimp,
            hasSnails = hasSnails,
            hasSensitiveLivestock = hasSensitiveLivestock,
            algaeRisk = algaeRisk,
            recommendationConfidence = confidence,
            profileWarnings = warnings
        )
    }

    private fun calculateVolumeLiters(
        tank: SavedAquariumTank
    ): Int {
        val calculated =
            tank.widthCm * tank.lengthCm * tank.heightCm / 1000.0

        return calculated
            .roundToInt()
            .coerceAtLeast(0)
    }

    private fun calculateSetupAgeDays(
        setupDateMillis: Long?,
        nowMillis: Long
    ): Int {
        if (setupDateMillis == null || setupDateMillis <= 0L) {
            return 0
        }

        val diffMillis = nowMillis - setupDateMillis

        return TimeUnit.MILLISECONDS
            .toDays(diffMillis.coerceAtLeast(0L))
            .toInt()
            .coerceAtLeast(1)
    }

    private fun resolveSetupPhase(
        setupDateMillis: Long?,
        setupAgeDays: Int
    ): QuickSetupSetupPhase {
        if (setupDateMillis == null || setupDateMillis <= 0L) {
            return QuickSetupSetupPhase.UNKNOWN
        }

        return when (setupAgeDays) {
            in 1..7 -> QuickSetupSetupPhase.FIRST_WEEK
            in 8..14 -> QuickSetupSetupPhase.EARLY_START
            in 15..30 -> QuickSetupSetupPhase.STABILIZING
            in 31..60 -> QuickSetupSetupPhase.BALANCED_RAMP_UP
            else -> QuickSetupSetupPhase.MATURE
        }
    }

    private fun mapTankType(
        rawType: String
    ): QuickSetupTankType {
        val value = rawType.cleanKey()

        return when {
            value.contains("blackwater") -> QuickSetupTankType.BLACKWATER
            value.contains("reef") -> QuickSetupTankType.REEF
            value.contains("softies") -> QuickSetupTankType.REEF
            value.contains("sps") -> QuickSetupTankType.REEF
            value.contains("coral") -> QuickSetupTankType.REEF
            value.contains("marine") -> QuickSetupTankType.MARINE
            value.contains("brackish") -> QuickSetupTankType.BRACKISH
            value.contains("planted") -> QuickSetupTankType.PLANTED
            value.contains("shrimp") -> QuickSetupTankType.SHRIMP
            value.contains("fish") -> QuickSetupTankType.FISH_ONLY
            value.contains("freshwater") -> QuickSetupTankType.FRESHWATER
            value.contains("tatli su") -> QuickSetupTankType.FRESHWATER
            value.contains("tatlı su") -> QuickSetupTankType.FRESHWATER
            else -> QuickSetupTankType.UNKNOWN
        }
    }

    private fun mapTankStyle(
        rawStyle: String
    ): QuickSetupTankStyle {
        val value = rawStyle.cleanKey()

        return when {
            value.contains("nature") -> QuickSetupTankStyle.NATURE
            value.contains("iwagumi") -> QuickSetupTankStyle.IWAGUMI
            value.contains("dutch") -> QuickSetupTankStyle.DUTCH
            value.contains("jungle") -> QuickSetupTankStyle.JUNGLE
            value.contains("biotope") -> QuickSetupTankStyle.BIOTOPE
            value.contains("blackwater") -> QuickSetupTankStyle.BIOTOPE
            value.contains("forest") -> QuickSetupTankStyle.NATURE
            value.contains("mountain") -> QuickSetupTankStyle.NATURE
            value.contains("island") -> QuickSetupTankStyle.NATURE
            value.contains("lowtech") || value.contains("low tech") -> QuickSetupTankStyle.LOW_TECH
            value.contains("hightech") || value.contains("high tech") -> QuickSetupTankStyle.HIGH_TECH
            value.contains("fish") -> QuickSetupTankStyle.FISH_ONLY
            value.contains("shrimp") -> QuickSetupTankStyle.SHRIMP_TANK
            else -> QuickSetupTankStyle.UNKNOWN
        }
    }

    private fun resolvePlantDensity(
        plantCount: Int,
        volumeLiters: Int
    ): QuickSetupPlantDensity {
        if (plantCount <= 0) {
            return QuickSetupPlantDensity.NONE
        }

        return when {
            volumeLiters <= 40 -> {
                when {
                    plantCount <= 2 -> QuickSetupPlantDensity.LOW
                    plantCount <= 5 -> QuickSetupPlantDensity.MEDIUM
                    else -> QuickSetupPlantDensity.DENSE
                }
            }

            volumeLiters <= 100 -> {
                when {
                    plantCount <= 3 -> QuickSetupPlantDensity.LOW
                    plantCount <= 8 -> QuickSetupPlantDensity.MEDIUM
                    else -> QuickSetupPlantDensity.DENSE
                }
            }

            volumeLiters <= 200 -> {
                when {
                    plantCount <= 4 -> QuickSetupPlantDensity.LOW
                    plantCount <= 11 -> QuickSetupPlantDensity.MEDIUM
                    else -> QuickSetupPlantDensity.DENSE
                }
            }

            else -> {
                when {
                    plantCount <= 6 -> QuickSetupPlantDensity.LOW
                    plantCount <= 15 -> QuickSetupPlantDensity.MEDIUM
                    else -> QuickSetupPlantDensity.DENSE
                }
            }
        }
    }

    private fun resolvePlantDemand(
        tank: SavedAquariumTank,
        hasGroundCoverPlants: Boolean,
        hasStemPlants: Boolean,
        hasRedPlants: Boolean
    ): QuickSetupPlantDemand {
        val highDemandCount = tank.plants.count {
            isHighDemandPlant(
                category = it.category,
                name = it.plantName
            )
        }

        val mediumDemandCount = tank.plants.count {
            isMediumDemandPlant(
                category = it.category,
                name = it.plantName
            )
        }

        return when {
            highDemandCount >= 2 -> QuickSetupPlantDemand.HIGH
            highDemandCount >= 1 && (hasGroundCoverPlants || hasRedPlants) -> QuickSetupPlantDemand.HIGH
            hasGroundCoverPlants && hasStemPlants -> QuickSetupPlantDemand.MEDIUM
            hasRedPlants -> QuickSetupPlantDemand.MEDIUM
            mediumDemandCount >= 2 -> QuickSetupPlantDemand.MEDIUM
            else -> QuickSetupPlantDemand.LOW
        }
    }

    private fun resolveTechLevel(
        hasCo2: Boolean,
        hasFertilizer: Boolean,
        hasNutrientSubstrate: Boolean,
        plantDensity: QuickSetupPlantDensity,
        plantDemand: QuickSetupPlantDemand
    ): QuickSetupTechLevel {
        if (
            hasCo2 &&
            hasFertilizer &&
            plantDensity >= QuickSetupPlantDensity.MEDIUM &&
            plantDemand >= QuickSetupPlantDemand.MEDIUM
        ) {
            return QuickSetupTechLevel.HIGH_TECH
        }

        if (
            hasCo2 ||
            hasFertilizer ||
            hasNutrientSubstrate ||
            plantDemand == QuickSetupPlantDemand.MEDIUM ||
            plantDemand == QuickSetupPlantDemand.HIGH
        ) {
            return QuickSetupTechLevel.MID_TECH
        }

        return QuickSetupTechLevel.LOW_TECH
    }

    private fun resolveAlgaeRisk(
        setupPhase: QuickSetupSetupPhase,
        hasCo2: Boolean,
        hasFertilizer: Boolean,
        plantDensity: QuickSetupPlantDensity,
        plantDemand: QuickSetupPlantDemand,
        hasFloatingPlants: Boolean,
        tankType: QuickSetupTankType
    ): QuickSetupAlgaeRisk {
        if (
            tankType == QuickSetupTankType.MARINE ||
            tankType == QuickSetupTankType.REEF
        ) {
            return QuickSetupAlgaeRisk.NORMAL
        }

        if (
            setupPhase == QuickSetupSetupPhase.FIRST_WEEK ||
            setupPhase == QuickSetupSetupPhase.EARLY_START
        ) {
            return QuickSetupAlgaeRisk.HIGH
        }

        if (
            setupPhase == QuickSetupSetupPhase.STABILIZING &&
            plantDensity <= QuickSetupPlantDensity.LOW
        ) {
            return QuickSetupAlgaeRisk.HIGH
        }

        if (
            plantDemand == QuickSetupPlantDemand.HIGH &&
            !hasCo2
        ) {
            return QuickSetupAlgaeRisk.HIGH
        }

        if (
            plantDensity >= QuickSetupPlantDensity.MEDIUM &&
            !hasFertilizer &&
            !hasCo2
        ) {
            return QuickSetupAlgaeRisk.HIGH
        }

        if (
            setupPhase == QuickSetupSetupPhase.MATURE &&
            plantDensity == QuickSetupPlantDensity.DENSE &&
            (hasCo2 || hasFloatingPlants)
        ) {
            return QuickSetupAlgaeRisk.LOW
        }

        return QuickSetupAlgaeRisk.NORMAL
    }

    private fun resolveConfidence(
        tank: SavedAquariumTank,
        volumeLiters: Int,
        setupPhase: QuickSetupSetupPhase,
        plantCount: Int,
        hasFish: Boolean,
        hasShrimp: Boolean
    ): QuickSetupRecommendationConfidence {
        var score = 0

        if (tank.name.isNotBlank()) score += 1
        if (volumeLiters > 0) score += 1
        if (setupPhase != QuickSetupSetupPhase.UNKNOWN) score += 1
        if (tank.tankType.isNotBlank()) score += 1
        if (tank.tankStyle.isNotBlank()) score += 1
        if (plantCount > 0 || hasFish || hasShrimp) score += 1
        if (tank.materials.isNotEmpty()) score += 1

        return when {
            score >= 6 -> QuickSetupRecommendationConfidence.HIGH
            score >= 4 -> QuickSetupRecommendationConfidence.MEDIUM
            else -> QuickSetupRecommendationConfidence.LOW
        }
    }

    private fun buildWarnings(
        volumeLiters: Int,
        setupPhase: QuickSetupSetupPhase,
        plantDensity: QuickSetupPlantDensity,
        plantDemand: QuickSetupPlantDemand,
        hasCo2: Boolean,
        tankType: QuickSetupTankType
    ): List<String> {
        val warnings = mutableListOf<String>()

        if (volumeLiters <= 0) {
            warnings.add("Tank volume is missing, so the recommendation uses conservative assumptions.")
        }

        if (setupPhase == QuickSetupSetupPhase.UNKNOWN) {
            warnings.add("Setup date is missing. Add setup date for a more accurate startup photoperiod.")
        }

        if (
            plantDensity == QuickSetupPlantDensity.NONE &&
            tankType != QuickSetupTankType.FISH_ONLY &&
            tankType != QuickSetupTankType.MARINE &&
            tankType != QuickSetupTankType.REEF
        ) {
            warnings.add("No plants are saved in this tank. Add plants for a better planted-tank recommendation.")
        }

        if (
            plantDemand == QuickSetupPlantDemand.HIGH &&
            !hasCo2
        ) {
            warnings.add("High-demand plants are detected without CO₂. The recommendation will keep intensity conservative.")
        }

        if (
            tankType == QuickSetupTankType.MARINE ||
            tankType == QuickSetupTankType.REEF
        ) {
            warnings.add("Marine and reef lighting may require specialized spectrum support. Verify controller compatibility before applying.")
        }

        return warnings.distinct()
    }

    private fun isGroundCoverPlant(
        category: String,
        name: String
    ): Boolean {
        val cleanCategory = category.cleanKey()
        val cleanName = name.cleanKey()

        return cleanCategory.contains("groundcover") ||
            cleanCategory.contains("ground cover") ||
            cleanName.contains("monte carlo") ||
            cleanName.contains("hemianthus") ||
            cleanName.contains("glossostigma") ||
            cleanName.contains("eleocharis") ||
            cleanName.contains("utricularia") ||
            cleanName.contains("hairgrass")
    }

    private fun isStemPlant(
        category: String,
        name: String
    ): Boolean {
        val cleanCategory = category.cleanKey()
        val cleanName = name.cleanKey()

        return cleanCategory.contains("background") ||
            cleanCategory.contains("middle ground") ||
            cleanName.contains("rotala") ||
            cleanName.contains("ludwigia") ||
            cleanName.contains("hygrophila") ||
            cleanName.contains("bacopa") ||
            cleanName.contains("pogostemon") ||
            cleanName.contains("limnophila")
    }

    private fun isEpiphytePlant(
        category: String,
        name: String
    ): Boolean {
        val cleanCategory = category.cleanKey()
        val cleanName = name.cleanKey()

        return cleanCategory.contains("epiphyte") ||
            cleanName.contains("anubias") ||
            cleanName.contains("bucephalandra") ||
            cleanName.contains("microsorum") ||
            cleanName.contains("bolbitis")
    }

    private fun isFloatingPlant(
        category: String,
        name: String
    ): Boolean {
        val cleanCategory = category.cleanKey()
        val cleanName = name.cleanKey()

        return cleanCategory.contains("floating") ||
            cleanName.contains("salvinia") ||
            cleanName.contains("frogbit") ||
            cleanName.contains("pistia") ||
            cleanName.contains("duckweed") ||
            cleanName.contains("limnobium")
    }

    private fun isRedPlant(
        name: String
    ): Boolean {
        val cleanName = name.cleanKey()

        return cleanName.contains("red") ||
            cleanName.contains("super red") ||
            cleanName.contains("alternanthera") ||
            cleanName.contains("ludwigia") ||
            cleanName.contains("rotala hra") ||
            cleanName.contains("rotala h ra") ||
            cleanName.contains("rotala macrandra") ||
            cleanName.contains("ar mini") ||
            cleanName.contains("reineckii")
    }

    private fun isHighDemandPlant(
        category: String,
        name: String
    ): Boolean {
        val cleanCategory = category.cleanKey()
        val cleanName = name.cleanKey()

        return cleanCategory.contains("rare") ||
            cleanName.contains("hemianthus") ||
            cleanName.contains("cuba") ||
            cleanName.contains("glossostigma") ||
            cleanName.contains("utricularia") ||
            cleanName.contains("eriocaulon") ||
            cleanName.contains("tonina") ||
            cleanName.contains("rotala macrandra") ||
            cleanName.contains("wallichii") ||
            cleanName.contains("alternanthera")
    }

    private fun isMediumDemandPlant(
        category: String,
        name: String
    ): Boolean {
        return isGroundCoverPlant(category, name) ||
            isStemPlant(category, name) ||
            isRedPlant(name) ||
            category.cleanKey().contains("foreground")
    }

    private fun isSensitiveLivestock(
        category: String,
        name: String
    ): Boolean {
        val value = "$category $name".cleanKey()

        return isShrimpLivestock(category, name) ||
            value.contains("caridina") ||
            value.contains("crystal") ||
            value.contains("bee shrimp") ||
            value.contains("discus") ||
            value.contains("ram") ||
            value.contains("apistogramma") ||
            value.contains("oto") ||
            value.contains("otocinclus")
    }

    private fun isCo2Material(
        categoryKey: String,
        categoryTitle: String,
        name: String
    ): Boolean {
        val value = "$categoryKey $categoryTitle $name".cleanKey()

        return value.contains("co2") ||
            value.contains("co 2") ||
            value.contains("carbon dioxide") ||
            value.contains("karbondioksit")
    }

    private fun isFertilizerMaterial(
        categoryKey: String,
        categoryTitle: String,
        name: String
    ): Boolean {
        val value = "$categoryKey $categoryTitle $name".cleanKey()

        return value.contains("fertilizer") ||
            value.contains("fertiliser") ||
            value.contains("fert") ||
            value.contains("plant food") ||
            value.contains("liquid nutrient") ||
            value.contains("macro") ||
            value.contains("micro") ||
            value.contains("gubre") ||
            value.contains("gübre")
    }

    private fun isNutrientSubstrateMaterial(
        categoryKey: String,
        categoryTitle: String,
        name: String
    ): Boolean {
        val value = "$categoryKey $categoryTitle $name".cleanKey()

        return value.contains("substrate") ||
            value.contains("aqua soil") ||
            value.contains("aquasoil") ||
            value.contains("soil") ||
            value.contains("nutrient substrate") ||
            value.contains("active soil") ||
            value.contains("substrat") ||
            value.contains("taban")
    }

    private fun isFishLivestock(
        category: String,
        name: String
    ): Boolean {
        val value = "$category $name".cleanKey()

        return value.contains("fish") ||
            value.contains("balik") ||
            value.contains("balık") ||
            value.contains("tetra") ||
            value.contains("guppy") ||
            value.contains("rasbora") ||
            value.contains("cichlid") ||
            value.contains("betta") ||
            value.contains("discus") ||
            value.contains("ram") ||
            value.contains("apistogramma") ||
            value.contains("otocinclus") ||
            value.contains("oto") ||
            value.contains("molly") ||
            value.contains("platy")
    }

    private fun isShrimpLivestock(
        category: String,
        name: String
    ): Boolean {
        val value = "$category $name".cleanKey()

        return value.contains("shrimp") ||
            value.contains("karides") ||
            value.contains("neocaridina") ||
            value.contains("caridina") ||
            value.contains("cherry") ||
            value.contains("amano") ||
            value.contains("crystal") ||
            value.contains("bee shrimp")
    }

    private fun isSnailLivestock(
        category: String,
        name: String
    ): Boolean {
        val value = "$category $name".cleanKey()

        return value.contains("snail") ||
            value.contains("salyangoz") ||
            value.contains("nerite") ||
            value.contains("ramshorn") ||
            value.contains("mystery snail")
    }

    private fun String.cleanKey(): String {
        return trim()
            .lowercase()
            .replace("-", " ")
            .replace("_", " ")
            .replace("'", "")
            .replace(".", " ")
            .replace(Regex("\\s+"), " ")
    }
}
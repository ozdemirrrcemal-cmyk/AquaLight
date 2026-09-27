package com.aqua.aqualight.application.aquarium.health.context

import com.aqua.aqualight.application.aquarium.AquariumPlantCare
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.AquariumVolumeCalculator
import com.aqua.aqualight.application.aquarium.LivestockWaterRequirements
import java.util.Collections

fun interface AquariumHealthContextProvider {
    suspend fun capture(tankId: Long): AquariumHealthContextResult
}

sealed interface AquariumHealthContextResult {
    data class Available(val context: AquariumHealthContext) : AquariumHealthContextResult
    data object TankMissing : AquariumHealthContextResult
    data class Unavailable(val reason: HealthContextFailure) : AquariumHealthContextResult
}

enum class HealthContextFailure { TANK_READ_FAILED, INVALID_TANK, CATALOG_UNAVAILABLE }
enum class HealthEntityResolution { RESOLVED, PARTIAL, CUSTOM_UNVERIFIED, CATALOG_MISSING, CATALOG_UNAVAILABLE }
enum class HealthContextTimeBasis { CURRENT_AT_ENTRY }

data class HealthPlantContext(
    val plantId: Long,
    val catalogId: String,
    val displayName: String,
    val resolution: HealthEntityResolution,
    val care: AquariumPlantCare?
)

data class HealthLivestockContext(
    val livestockId: Long,
    val catalogId: String,
    val displayName: String,
    val quantity: Int,
    val resolution: HealthEntityResolution,
    val waterGroup: String?,
    val requirements: LivestockWaterRequirements?
)

/** The selected product is known; its operating state, dose and output are not inferred. */
data class HealthMaterialContext(val selectionId: Long, val productId: String, val categoryKey: String)

data class HealthTankFacts(
    val tankId: Long,
    val tankType: String,
    val waterEnvironment: String?,
    val setupDateEpochDay: Long?,
    val geometricVolumeLitres: Double?,
    val dimensionsCm: HealthTankDimensions? = null
)

data class HealthTankDimensions(val width: Int, val length: Int, val height: Int)

data class HealthCatalogRevisions(val plant: String?, val livestock: String?)
data class HealthContextCapture(val capturedAtMillis: Long, val revision: String)

/** Frozen facts from a single owner-scoped tank read; never a claim about a past sample's habitat. */
class AquariumHealthContext(
    val capture: HealthContextCapture,
    val tank: HealthTankFacts,
    val catalogs: HealthCatalogRevisions,
    plants: List<HealthPlantContext>,
    livestock: List<HealthLivestockContext>,
    materials: List<HealthMaterialContext>
) {
    val capturedAtMillis get() = capture.capturedAtMillis
    val revision get() = capture.revision
    val tankId get() = tank.tankId
    val tankType get() = tank.tankType
    val waterEnvironment get() = tank.waterEnvironment
    val setupDateEpochDay get() = tank.setupDateEpochDay
    val geometricVolumeLitres get() = tank.geometricVolumeLitres
    val plantCatalogRevision get() = catalogs.plant
    val livestockCatalogRevision get() = catalogs.livestock
    val timeBasis: HealthContextTimeBasis = HealthContextTimeBasis.CURRENT_AT_ENTRY
    val plants: List<HealthPlantContext> = frozen(plants.map { plant ->
        plant.copy(care = plant.care?.let { care ->
            care.copy(verifiedCareFields = Collections.unmodifiableSet(LinkedHashSet(care.verifiedCareFields)))
        })
    })
    val livestock: List<HealthLivestockContext> = frozen(livestock.map { animal ->
        animal.copy(requirements = animal.requirements?.let { requirements ->
            requirements.copy(evidence = requirements.evidence?.let { evidence ->
                evidence.copy(parameters = Collections.unmodifiableMap(LinkedHashMap(evidence.parameters)))
            })
        })
    })
    val materials: List<HealthMaterialContext> = frozen(materials)

    init {
        require(tankId > 0 && capturedAtMillis > 0 && revision.isNotBlank())
        require(tank.geometricVolumeLitres == null ||
            tank.geometricVolumeLitres.isFinite() && tank.geometricVolumeLitres > 0)
        require(plants.map { it.plantId }.distinct().size == plants.size && plants.all { it.plantId > 0 })
        require(livestock.map { it.livestockId }.distinct().size == livestock.size)
        require(livestock.all { it.livestockId > 0 && it.quantity > 0 })
    }
}

internal fun AquariumTankSnapshot.geometricVolumeLitres(): Double? =
    AquariumVolumeCalculator.grossLiters(widthCm, lengthCm, heightCm).takeIf { it > 0 }

private fun <T> frozen(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))

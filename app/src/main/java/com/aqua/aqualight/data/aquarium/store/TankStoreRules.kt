package com.aqua.aqualight.data.aquarium.store

import com.aqua.aqualight.application.aquarium.AquariumAutomationProfile
import com.aqua.aqualight.application.aquarium.AquariumLivestockCategory
import com.aqua.aqualight.application.aquarium.AquariumMaterialCategory
import com.aqua.aqualight.application.aquarium.AquariumPlantLightCatalog
import com.aqua.aqualight.application.aquarium.AquariumPlantLightDemand
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantic
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantics
import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy
import com.aqua.aqualight.data.store.CommercialStoreSchema
import com.aqua.aqualight.data.store.StoreInvariantViolation
import java.time.LocalDate

/** Authoritative invariant rules for the commercial tank store. */
object TankStoreRules {

    const val MIN_DIMENSION_CM = 1
    const val MAX_DIMENSION_CM = 5_000

    const val MAX_NAME_CHARS = 80
    const val MAX_DESCRIPTION_CHARS = 2_000
    const val MAX_STYLE_CHARS = 80
    const val MAX_URI_CHARS = 2_048
    const val MAX_ENTITY_NAME_CHARS = 120
    const val MAX_CATEGORY_CHARS = 80
    const val MAX_NOTE_CHARS = 1_000
    const val MAX_PRODUCT_ID_CHARS = 160

    private const val MIN_TIMESTAMP_MILLIS = 946_684_800_000L // 2000-01-01 UTC
    private const val MAX_TIMESTAMP_MILLIS = 4_102_444_800_000L // 2100-01-01 UTC
    private val minDateEpochDay = LocalDate.of(2000, 1, 1).toEpochDay()
    private val maxDateEpochDay = LocalDate.of(2100, 12, 31).toEpochDay()

    private val allowedSizeUnits = setOf("cm", "in")
    private val allowedVolumeUnits = setOf("L", "gal")
    private val allowedTankTypes = AquariumTankTaxonomy.tankTypeCodes

    fun defaultStore(): AquariumTanksStore = AquariumTanksStore.newBuilder()
        .setSchemaVersion(CommercialStoreSchema.AQUARIUM_TANKS_VERSION)
        .build()

    fun validateStore(store: AquariumTanksStore): AquariumTanksStore {
        CommercialStoreSchema.requireCurrent(
            storeName = "AquariumTanksStore",
            actualVersion = store.schemaVersion,
            expectedVersion = CommercialStoreSchema.AQUARIUM_TANKS_VERSION
        )

        val ownerScopedIds = mutableSetOf<Pair<String, Long>>()

        store.tanksList.forEach { tank ->
            validateTank(tank)

            val ownerKey = canonicalOwnerUid(tank.ownerUid)
            if (!ownerScopedIds.add(ownerKey to tank.id)) {
                violation("Duplicate tank id ${tank.id} for owner $ownerKey.")
            }
        }

        return store
    }

    fun validateTank(tank: StoredTank): StoredTank {
        requirePositiveId("tank.id", tank.id)
        canonicalOwnerUid(tank.ownerUid)
        requireCanonicalRequiredText("tank.name", tank.name, MAX_NAME_CHARS)
        requireCanonicalOptionalText("tank.description", tank.description, MAX_DESCRIPTION_CHARS)
        requireCanonicalOptionalText("tank.photoUri", tank.photoUri, MAX_URI_CHARS)
        requireOptionalEpochDay("tank.setupDateEpochDay", tank.setupDateEpochDay)
        requireTimestamp("tank.createdAtMillis", tank.createdAtMillis)
        requireDimension("tank.widthCm", tank.widthCm)
        requireDimension("tank.lengthCm", tank.lengthCm)
        requireDimension("tank.heightCm", tank.heightCm)

        if (tank.sizeUnit !in allowedSizeUnits) {
            violation("tank.sizeUnit must be one of $allowedSizeUnits.")
        }
        if (tank.volumeUnit !in allowedVolumeUnits) {
            violation("tank.volumeUnit must be one of $allowedVolumeUnits.")
        }
        if (tank.tankType !in allowedTankTypes) {
            violation("tank.tankType is not a supported commercial value.")
        }
        requireCanonicalOptionalText("tank.tankStyle", tank.tankStyle, MAX_STYLE_CHARS)

        validateAutomationProfile(tank)

        validatePlants(tank)
        validateMaterials(tank)
        validateLivestock(tank)

        return tank
    }

    fun requireValidTankId(tankId: Long) {
        requirePositiveId("tankId", tankId)
    }

    private fun validatePlants(tank: StoredTank) {
        val ids = mutableSetOf<Long>()
        tank.plantsList.forEach { plant ->
            requirePositiveId("plant.id", plant.id)
            if (!ids.add(plant.id)) {
                violation("Duplicate plant id ${plant.id} in tank ${tank.id}.")
            }
            requireCanonicalRequiredText(
                "plant.catalogId",
                plant.catalogId,
                MAX_PRODUCT_ID_CHARS
            )
            requireCanonicalRequiredText(
                "plant.plantName",
                plant.plantName,
                MAX_ENTITY_NAME_CHARS
            )
            requireCanonicalRequiredText(
                "plant.category",
                plant.category,
                MAX_CATEGORY_CHARS
            )
            requireNormalizedMarker("plant.markerX", plant.markerX)
            requireNormalizedMarker("plant.markerY", plant.markerY)
            requireOptionalEpochDay("plant.plantedAtEpochDay", plant.plantedAtEpochDay)
            if (plant.lightDemand == StoredPlantLightDemand.UNRECOGNIZED) {
                violation("plant.lightDemand is not recognized.")
            }
            val expectedDemand = AquariumPlantLightCatalog.resolve(plant.catalogId).toStored()
            if (plant.lightDemand != expectedDemand) {
                violation(
                    "plant.lightDemand must match the reviewed catalog record or remain unknown."
                )
            }
        }
    }

    private fun validateAutomationProfile(tank: StoredTank) {
        if (!tank.hasAutomationProfile()) {
            violation("tank.automationProfile is required by the final commercial V1 contract.")
        }
        val profile = tank.automationProfile
        if (profile.contractRevision != AquariumAutomationProfile.CONTRACT_REVISION) {
            violation("tank.automationProfile contract revision is unsupported.")
        }
        val hasUnrecognizedEnum = profile.plantDemandOverride ==
            StoredPlantLightDemand.UNRECOGNIZED ||
            profile.plantCoverage == StoredPlantCoverage.UNRECOGNIZED ||
            profile.canopyDensity == StoredCanopyDensity.UNRECOGNIZED ||
            profile.co2Readiness == StoredCo2Readiness.UNRECOGNIZED ||
            profile.daylightExposure == StoredDaylightExposure.UNRECOGNIZED ||
            profile.latestSurfaceGrowth == StoredSurfaceGrowth.UNRECOGNIZED ||
            profile.shelterAvailability == StoredShelterAvailability.UNRECOGNIZED
        if (hasUnrecognizedEnum) {
            violation("tank.automationProfile contains an unrecognized enum value.")
        }
        if (profile.hasWaterDepthCm()) {
            val depth = profile.waterDepthCm
            if (depth !in MIN_DIMENSION_CM..tank.heightCm) {
                violation("tank.automationProfile water depth must fit inside tank height.")
            }
        }
        if (profile.hasSubstrateDepthCm()) {
            val depth = profile.substrateDepthCm
            if (depth !in MIN_DIMENSION_CM until tank.heightCm) {
                violation("tank.automationProfile substrate depth must fit inside tank height.")
            }
        }
        if (profile.hasWaterDepthCm() && profile.hasSubstrateDepthCm() &&
            profile.waterDepthCm + profile.substrateDepthCm > tank.heightCm
        ) {
            violation("tank automation water and substrate depths exceed tank height.")
        }
        requireOptionalMinute(
            "automation.daylightStartMinute",
            profile.hasDaylightStartMinute(),
            profile.daylightStartMinute
        )
        requireOptionalMinute(
            "automation.daylightEndMinute",
            profile.hasDaylightEndMinute(),
            profile.daylightEndMinute
        )
        requireOptionalMinute(
            "automation.preferredLightEndMinute",
            profile.hasPreferredLightEndMinute(),
            profile.preferredLightEndMinute
        )
        requireOptionalEpochDay(
            "automation.lastMajorPlantingEpochDay",
            profile.lastMajorPlantingEpochDay.takeIf {
                profile.hasLastMajorPlantingEpochDay()
            } ?: 0L
        )
        requireOptionalEpochDay(
            "automation.latestObservationEpochDay",
            profile.latestObservationEpochDay.takeIf {
                profile.hasLatestObservationEpochDay()
            } ?: 0L
        )
        val directDaylight = profile.daylightExposure ==
            StoredDaylightExposure.STORED_DAYLIGHT_EXPOSURE_DIRECT
        if (directDaylight &&
            (!profile.hasDaylightStartMinute() || !profile.hasDaylightEndMinute() ||
                profile.daylightStartMinute >= profile.daylightEndMinute)
        ) {
            violation("direct daylight requires a valid same-day observation window.")
        }
        if (!directDaylight &&
            (profile.hasDaylightStartMinute() || profile.hasDaylightEndMinute())
        ) {
            violation("only direct daylight can carry an observation window.")
        }
        val hasSurfaceObservation = profile.latestSurfaceGrowth !=
            StoredSurfaceGrowth.STORED_SURFACE_GROWTH_UNKNOWN
        if (hasSurfaceObservation != profile.hasLatestObservationEpochDay()) {
            violation("surface observation value and observation date must be stored together.")
        }
        if (profile.hasObservedAreaPercent() && profile.observedAreaPercent !in 0..100) {
            violation("automation.observedAreaPercent must be between 0 and 100.")
        }
        requireCanonicalOptionalText(
            "automation.observationLocation",
            profile.observationLocation,
            MAX_ENTITY_NAME_CHARS
        )
        if (profile.hasUpdatedAtMillis()) {
            requireTimestamp("automation.updatedAtMillis", profile.updatedAtMillis)
        }
        validateCo2Inventory(tank, profile)
        validateLivestockAutomation(tank, profile)
    }

    private fun validateCo2Inventory(
        tank: StoredTank,
        profile: StoredTankAutomationProfile
    ) {
        val hasCo2Component = tank.materialsList.any { material ->
            material.categoryKey == MATERIAL_CATEGORY_CO2
        }
        if (!hasCo2Component && profile.co2Readiness !=
            StoredCo2Readiness.STORED_CO2_READINESS_NOT_INSTALLED
        ) {
            violation("CO2 readiness must be not-installed when no CO2 component is recorded.")
        }
        if (hasCo2Component && profile.co2Readiness ==
            StoredCo2Readiness.STORED_CO2_READINESS_NOT_INSTALLED
        ) {
            violation("A recorded CO2 component cannot use the not-installed readiness state.")
        }
    }

    private fun validateLivestockAutomation(
        tank: StoredTank,
        profile: StoredTankAutomationProfile
    ) {
        val hasShrimp = tank.livestockList.any { item ->
            item.category == AquariumLivestockCategory.SHRIMP
        }
        if (hasShrimp && profile.shelterAvailability ==
            StoredShelterAvailability.STORED_SHELTER_AVAILABILITY_NOT_REQUIRED
        ) {
            violation("Shrimp tanks require an unknown or assessed shelter state.")
        }
        if (!hasShrimp && profile.shelterAvailability !=
            StoredShelterAvailability.STORED_SHELTER_AVAILABILITY_NOT_REQUIRED
        ) {
            violation("Shelter state must be not-required when no shrimp is recorded.")
        }
        if (!hasShrimp && profile.latestSurfaceGrowth ==
            StoredSurfaceGrowth.STORED_SURFACE_GROWTH_TARGET_BIOFILM
        ) {
            violation("Target biofilm requires a recorded shrimp profile.")
        }
    }

    private fun validateMaterials(tank: StoredTank) {
        val ids = mutableSetOf<Long>()
        tank.materialsList.forEach { material ->
            requirePositiveId("material.id", material.id)
            if (!ids.add(material.id)) {
                violation("Duplicate material id ${material.id} in tank ${tank.id}.")
            }
            requireCanonicalRequiredText(
                "material.productId",
                material.productId,
                MAX_PRODUCT_ID_CHARS
            )
            requireCanonicalRequiredText(
                "material.categoryKey",
                material.categoryKey,
                MAX_CATEGORY_CHARS
            )
            if (!AquariumMaterialCategory.isSupported(material.categoryKey)) {
                violation("material.categoryKey must be a supported stable category code.")
            }
            requireCanonicalRequiredText(
                "material.categoryTitle",
                material.categoryTitle,
                MAX_ENTITY_NAME_CHARS
            )
            requireCanonicalRequiredText(
                "material.name",
                material.name,
                MAX_ENTITY_NAME_CHARS
            )
            requireCanonicalOptionalText(
                "material.brand",
                material.brand,
                MAX_ENTITY_NAME_CHARS
            )
            requireCanonicalOptionalText("material.note", material.note, MAX_NOTE_CHARS)
            if (material.substrateSemantic == StoredSubstrateSemantic.UNRECOGNIZED) {
                violation("material.substrateSemantic is not recognized.")
            }
            if (material.categoryKey == AquariumMaterialCategory.SUBSTRATE &&
                material.substrateSemantic ==
                StoredSubstrateSemantic.STORED_SUBSTRATE_SEMANTIC_NOT_APPLICABLE
            ) {
                violation("substrate materials require an explicit semantic class.")
            }
            if (material.categoryKey !in setOf(
                    AquariumMaterialCategory.SUBSTRATE,
                    AquariumMaterialCategory.GRAVEL
                ) &&
                material.substrateSemantic !=
                StoredSubstrateSemantic.STORED_SUBSTRATE_SEMANTIC_NOT_APPLICABLE
            ) {
                violation("non-substrate materials cannot carry substrate semantics.")
            }
            if (
                !AquariumSubstrateSemantics.matchesCatalogCategory(
                    productId = material.productId,
                    categoryKey = material.categoryKey
                )
            ) {
                violation("material category must match the reviewed catalog identity.")
            }
            val expectedSemantic = AquariumSubstrateSemantics.resolve(
                productId = material.productId,
                categoryKey = material.categoryKey
            ).toStored()
            if (material.substrateSemantic != expectedSemantic) {
                violation("material.substrateSemantic must match the reviewed catalog record.")
            }
        }
    }

    private fun validateLivestock(tank: StoredTank) {
        val ids = mutableSetOf<Long>()
        tank.livestockList.forEach { livestock ->
            requirePositiveId("livestock.id", livestock.id)
            if (!ids.add(livestock.id)) {
                violation("Duplicate livestock id ${livestock.id} in tank ${tank.id}.")
            }
            requireCanonicalRequiredText(
                "livestock.name",
                livestock.name,
                MAX_ENTITY_NAME_CHARS
            )
            requireCanonicalRequiredText(
                "livestock.category",
                livestock.category,
                MAX_CATEGORY_CHARS
            )
            if (!AquariumLivestockCategory.isSupported(livestock.category)) {
                violation("livestock.category must be a supported stable category code.")
            }
            if (livestock.quantity !in 1..100_000) {
                violation("livestock.quantity must be between 1 and 100000.")
            }
            requireOptionalEpochDay(
                "livestock.addedDateEpochDay",
                livestock.addedDateEpochDay
            )
            requireCanonicalOptionalText("livestock.note", livestock.note, MAX_NOTE_CHARS)
        }
    }

    private fun canonicalOwnerUid(value: String): String {
        val canonical = value.trim()
        if (canonical.isBlank() || canonical != value) {
            violation("ownerUid must be non-blank and canonical.")
        }
        if (canonical.length > 128) {
            violation("ownerUid exceeds 128 characters.")
        }
        return canonical
    }

    private fun requirePositiveId(field: String, value: Long) {
        if (value <= 0L) {
            violation("$field must be positive.")
        }
    }

    private fun AquariumPlantLightDemand.toStored(): StoredPlantLightDemand = when (this) {
        AquariumPlantLightDemand.UNKNOWN ->
            StoredPlantLightDemand.STORED_PLANT_LIGHT_DEMAND_UNKNOWN
        AquariumPlantLightDemand.LOW -> StoredPlantLightDemand.STORED_PLANT_LIGHT_DEMAND_LOW
        AquariumPlantLightDemand.MEDIUM -> StoredPlantLightDemand.STORED_PLANT_LIGHT_DEMAND_MEDIUM
        AquariumPlantLightDemand.HIGH -> StoredPlantLightDemand.STORED_PLANT_LIGHT_DEMAND_HIGH
    }

    private fun AquariumSubstrateSemantic.toStored(): StoredSubstrateSemantic = when (this) {
        AquariumSubstrateSemantic.NOT_APPLICABLE ->
            StoredSubstrateSemantic.STORED_SUBSTRATE_SEMANTIC_NOT_APPLICABLE
        AquariumSubstrateSemantic.UNKNOWN ->
            StoredSubstrateSemantic.STORED_SUBSTRATE_SEMANTIC_UNKNOWN
        AquariumSubstrateSemantic.INERT ->
            StoredSubstrateSemantic.STORED_SUBSTRATE_SEMANTIC_INERT
        AquariumSubstrateSemantic.NUTRIENT_BASE ->
            StoredSubstrateSemantic.STORED_SUBSTRATE_SEMANTIC_NUTRIENT_BASE
        AquariumSubstrateSemantic.ACTIVE_SOIL ->
            StoredSubstrateSemantic.STORED_SUBSTRATE_SEMANTIC_ACTIVE_SOIL
        AquariumSubstrateSemantic.ADDITIVE ->
            StoredSubstrateSemantic.STORED_SUBSTRATE_SEMANTIC_ADDITIVE
    }

    private fun requireDimension(field: String, value: Int) {
        if (value !in MIN_DIMENSION_CM..MAX_DIMENSION_CM) {
            violation("$field must be between $MIN_DIMENSION_CM and $MAX_DIMENSION_CM cm.")
        }
    }

    private fun requireCanonicalRequiredText(
        field: String,
        value: String,
        maxChars: Int
    ) {
        val canonical = value.trim()
        if (canonical.isBlank() || canonical != value) {
            violation("$field must be non-blank and canonical.")
        }
        requireTextLength(field, canonical, maxChars)
    }

    private fun requireCanonicalOptionalText(
        field: String,
        value: String,
        maxChars: Int
    ) {
        if (value != value.trim()) {
            violation("$field must be canonical.")
        }
        requireTextLength(field, value, maxChars)
    }

    private fun requireTextLength(field: String, value: String, maxChars: Int) {
        if (value.length > maxChars) {
            violation("$field exceeds $maxChars characters.")
        }
    }

    private fun requireNormalizedMarker(field: String, value: Float) {
        if (!value.isFinite() || value !in 0f..1f) {
            violation("$field must be finite and between 0 and 1.")
        }
    }

    private fun requireOptionalEpochDay(field: String, value: Long) {
        if (value != 0L && value !in minDateEpochDay..maxDateEpochDay) {
            violation("$field is outside the supported commercial calendar-date range.")
        }
    }

    private fun requireOptionalMinute(field: String, present: Boolean, value: Int) {
        if (present && value !in 0 until 24 * 60) {
            violation("$field must be a minute of day.")
        }
    }

    private fun requireTimestamp(field: String, value: Long) {
        if (value !in MIN_TIMESTAMP_MILLIS..MAX_TIMESTAMP_MILLIS) {
            violation("$field is outside the supported commercial timestamp range.")
        }
    }

    private fun violation(message: String): Nothing {
        throw StoreInvariantViolation(message)
    }

    private const val MATERIAL_CATEGORY_CO2 = AquariumMaterialCategory.CO2
}

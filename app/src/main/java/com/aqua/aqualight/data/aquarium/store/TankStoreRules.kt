package com.aqua.aqualight.data.aquarium.store

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
    private val allowedTankTypes = setOf(
        "Fish",
        "Shrimp",
        "Planted",
        "Marine",
        "Softies",
        "Mixed Reef",
        "SPS",
        "Coral",
        "Other"
    )

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

    private fun requireTimestamp(field: String, value: Long) {
        if (value !in MIN_TIMESTAMP_MILLIS..MAX_TIMESTAMP_MILLIS) {
            violation("$field is outside the supported commercial timestamp range.")
        }
    }

    private fun violation(message: String): Nothing {
        throw StoreInvariantViolation(message)
    }
}

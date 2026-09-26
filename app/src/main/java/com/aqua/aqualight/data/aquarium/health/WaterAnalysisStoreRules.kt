package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisPolicy
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementCatalog
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementSelection
import com.aqua.aqualight.data.store.CommercialStoreSchema
import com.aqua.aqualight.data.store.StoreInvariantViolation

internal object WaterAnalysisStoreRules {

    fun defaultStore(): WaterAnalysesStore = WaterAnalysesStore.newBuilder()
        .setSchemaVersion(CommercialStoreSchema.WATER_ANALYSES_VERSION)
        .build()

    fun validateStore(store: WaterAnalysesStore): WaterAnalysesStore {
        CommercialStoreSchema.requireCurrent(
            storeName = "WaterAnalysesStore",
            actualVersion = store.schemaVersion,
            expectedVersion = CommercialStoreSchema.WATER_ANALYSES_VERSION
        )

        val ownerScopedIds = mutableSetOf<Pair<String, Long>>()
        store.analysesList.forEach { stored ->
            validateStoredAnalysis(stored)
            val ownerUid = canonicalOwnerUid(stored.ownerUid)
            if (!ownerScopedIds.add(ownerUid to stored.id)) {
                violation("Duplicate water-analysis id ${stored.id} for owner $ownerUid.")
            }
        }
        return store
    }

    fun validateRecord(
        record: WaterAnalysisRecord,
        expectedOwnerUid: String? = null
    ): WaterAnalysisRecord {
        requirePositive("analysis.id", record.id)
        val ownerUid = canonicalOwnerUid(record.ownerUid)
        requireExpectedOwner(ownerUid, expectedOwnerUid)
        requirePositive("analysis.tankId", record.tankId)
        requireDate("analysis.measuredAtMillis", record.measuredAtMillis)
        requireDate("analysis.createdAtMillis", record.createdAtMillis)

        if (record.temperatureCelsius == null) {
            if (record.temperatureSource != null) {
                violation("Temperature source requires a temperature value.")
            }
        } else {
            if (!record.temperatureCelsius.isFinite() ||
                record.temperatureCelsius !in
                WaterAnalysisPolicy.MIN_TEMPERATURE_C..WaterAnalysisPolicy.MAX_TEMPERATURE_C
            ) {
                violation("analysis.temperatureCelsius is outside the supported range.")
            }
            if (record.temperatureSource == null) {
                violation("Temperature value requires a temperature source.")
            }
        }

        if (record.measurements.isEmpty()) {
            violation("Water analysis must contain at least one measurement.")
        }
        if (record.measurements.size > WaterAnalysisPolicy.MAX_MEASUREMENTS) {
            violation("Water analysis contains too many measurements.")
        }
        val parameters = mutableSetOf<String>()
        record.measurements.forEach { measurement ->
            if (!parameters.add(measurement.parameter.name)) {
                violation("Water analysis contains a duplicate measurement parameter.")
            }
            if (!measurement.value.isFinite() || measurement.value < 0.0) {
                violation("Measurement value must be finite and non-negative.")
            }
            val selection = WaterMeasurementSelection(
                method = measurement.method,
                testKitId = measurement.testKitId,
                basis = measurement.basis,
                unit = measurement.unit
            )
            if (!WaterMeasurementCatalog.isStoredSelectionValid(measurement.parameter, selection)) {
                violation("Measurement selection is not valid for ${measurement.parameter}.")
            }
        }

        return record
    }

    fun validateStoredAnalysis(stored: StoredWaterAnalysis): StoredWaterAnalysis {
        if (!stored.hasTemperature) {
            if (stored.temperatureCelsius != 0.0 || stored.temperatureSource.isNotBlank()) {
                violation("Absent temperature must use canonical empty storage values.")
            }
        } else if (stored.temperatureSource.isBlank()) {
            violation("Stored temperature requires a source.")
        }
        validateRecord(stored.toRecordStrict())
        return stored
    }

    fun nextUniqueId(
        current: List<StoredWaterAnalysis>,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val maxExistingId = current.maxOfOrNull { stored -> stored.id } ?: 0L
        val next = maxOf(nowMillis, maxExistingId + 1L)
        requirePositive("generated analysis id", next)
        return next
    }

    private fun requireDate(field: String, value: Long) {
        if (value !in WaterAnalysisPolicy.MIN_DATE_MILLIS..WaterAnalysisPolicy.MAX_DATE_MILLIS) {
            violation("$field is outside the supported range.")
        }
    }

    private fun canonicalOwnerUid(value: String): String {
        val canonical = value.trim()
        if (
            canonical.isBlank() ||
            canonical != value ||
            canonical.length > MAX_OWNER_UID_CHARS
        ) {
            violation(
                "ownerUid must be non-blank, canonical and at most $MAX_OWNER_UID_CHARS characters."
            )
        }
        return canonical
    }

    private fun requireExpectedOwner(actualOwnerUid: String, expectedOwnerUid: String?) {
        val expected = expectedOwnerUid?.trim().orEmpty()
        if (expected.isNotEmpty() && actualOwnerUid != expected) {
            violation("Water-analysis owner does not match the active owner.")
        }
    }

    fun requirePositive(field: String, value: Long) {
        if (value <= 0L) violation("$field must be positive.")
    }

    private fun violation(message: String): Nothing {
        throw StoreInvariantViolation(message)
    }

    private const val MAX_OWNER_UID_CHARS = 128
}

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

    fun validateStore(store: WaterAnalysesStore): WaterAnalysesStore =
        validateStoreVersion(store, CommercialStoreSchema.WATER_ANALYSES_VERSION)

    fun upgradeLegacyStore(store: WaterAnalysesStore): WaterAnalysesStore {
        validateStoreVersion(store, LEGACY_VERSION)
        return store.toBuilder()
            .setSchemaVersion(CommercialStoreSchema.WATER_ANALYSES_VERSION)
            .build()
            .let(::validateStore)
    }

    private fun validateStoreVersion(store: WaterAnalysesStore, expectedVersion: Int): WaterAnalysesStore {
        CommercialStoreSchema.requireCurrent(
            storeName = "WaterAnalysesStore",
            actualVersion = store.schemaVersion,
            expectedVersion = expectedVersion
        )

        val ownerScopedIds = mutableSetOf<Pair<String, Long>>()
        val ownerScopedRequests = mutableSetOf<Pair<String, String>>()
        store.analysesList.forEach { stored ->
            validateStoredAnalysis(stored)
            val ownerUid = canonicalOwnerUid(stored.ownerUid)
            if (!ownerScopedIds.add(ownerUid to stored.id)) {
                violation("Duplicate water-analysis id ${stored.id} for owner $ownerUid.")
            }
            if (stored.requestId.isNotBlank() &&
                !ownerScopedRequests.add(ownerUid to stored.requestId)
            ) {
                violation("Duplicate water-analysis request ID for owner $ownerUid.")
            }
        }
        return store
    }

    fun validateRecord(
        record: WaterAnalysisRecord,
        expectedOwnerUid: String? = null
    ): WaterAnalysisRecord {
        WaterAnalysisIdentityRules.requirePositive("analysis.id", record.id)
        val ownerUid = canonicalOwnerUid(record.ownerUid)
        requireExpectedOwner(ownerUid, expectedOwnerUid)
        WaterAnalysisIdentityRules.requirePositive("analysis.tankId", record.tankId)
        requireDate("analysis.measuredAtMillis", record.measuredAtMillis)
        requireDate("analysis.createdAtMillis", record.createdAtMillis)
        if (record.requestId.isNotBlank() && !WaterAnalysisPolicy.isValidRequestId(record.requestId)) {
            violation("analysis.requestId must be a canonical UUID when present.")
        }

        WaterAnalysisValueRules.validateTemperature(record)

        if (record.measurements.isEmpty()) {
            violation("Water analysis must contain at least one measurement.")
        }
        if (record.measurements.size > WaterAnalysisPolicy.MAX_MEASUREMENTS) {
            violation("Water analysis contains too many measurements.")
        }
        WaterAnalysisValueRules.validateMeasurements(record.measurements)

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

    private fun violation(message: String): Nothing {
        throw StoreInvariantViolation(message)
    }

    private const val MAX_OWNER_UID_CHARS = 128
    private const val LEGACY_VERSION = 1
}

internal object WaterAnalysisIdentityRules {
    fun newestFirst(records: List<WaterAnalysisRecord>): List<WaterAnalysisRecord> =
        records.sortedWith(
            compareByDescending<WaterAnalysisRecord>(WaterAnalysisRecord::measuredAtMillis)
                .thenByDescending(WaterAnalysisRecord::createdAtMillis)
                .thenByDescending(WaterAnalysisRecord::id)
        )

    fun replayId(store: WaterAnalysesStore, ownerUid: String, draft: WaterAnalysisDraftRecord): Long? {
        val previous = store.analysesList.firstOrNull { stored ->
            stored.ownerUid == ownerUid && stored.requestId == draft.requestId
        }?.toRecordStrict() ?: return null
        val sameSample = previous.tankId == draft.tankId && previous.measuredAtMillis == draft.measuredAtMillis
        val sameTemperature = previous.temperatureCelsius == draft.temperatureCelsius &&
            previous.temperatureSource == draft.temperatureSource
        if (!sameSample || !sameTemperature || previous.measurements != draft.measurements) {
            throw StoreInvariantViolation("A water-analysis request ID cannot be reused with different input.")
        }
        return previous.id
    }

    fun nextUniqueId(current: List<StoredWaterAnalysis>, nowMillis: Long = System.currentTimeMillis()): Long {
        val maxExistingId = current.maxOfOrNull { stored -> stored.id } ?: 0L
        val next = maxOf(nowMillis, maxExistingId + 1L)
        requirePositive("generated analysis id", next)
        return next
    }

    fun requirePositive(field: String, value: Long) {
        if (value <= 0L) throw StoreInvariantViolation("$field must be positive.")
    }
}

internal object WaterAnalysisValueRules {
    fun validateTemperature(record: WaterAnalysisRecord) {
        if (record.temperatureCelsius == null) {
            if (record.temperatureSource != null) invalidValue("Temperature source requires a temperature value.")
        } else {
            if (!record.temperatureCelsius.isFinite() ||
                record.temperatureCelsius !in
                WaterAnalysisPolicy.MIN_TEMPERATURE_C..WaterAnalysisPolicy.MAX_TEMPERATURE_C
            ) {
                invalidValue("analysis.temperatureCelsius is outside the supported range.")
            }
            if (record.temperatureSource == null) invalidValue("Temperature value requires a temperature source.")
        }
    }

    fun validateMeasurements(measurements: List<WaterMeasurementRecord>) {
        val parameters = mutableSetOf<String>()
        measurements.forEach { measurement ->
            if (!parameters.add(measurement.parameter.name)) {
                invalidValue("Water analysis contains a duplicate measurement parameter.")
            }
            if (!measurement.value.isFinite() || measurement.value < 0.0) {
                invalidValue("Measurement value must be finite and non-negative.")
            }
            val selection = WaterMeasurementSelection(
                method = measurement.method,
                testKitId = measurement.testKitId,
                basis = measurement.basis,
                unit = measurement.unit
            )
            if (!WaterMeasurementCatalog.isStoredSelectionValid(measurement.parameter, selection)) {
                invalidValue("Measurement selection is not valid for ${measurement.parameter}.")
            }
        }
    }

    private fun invalidValue(message: String): Nothing = throw StoreInvariantViolation(message)
}

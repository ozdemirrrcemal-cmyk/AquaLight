package com.aqua.aqualight.data.care.integrity

import com.aqua.aqualight.data.store.StoreInvariantViolation
import java.util.UUID

internal data class TankCareIntegrityEntryParts(val fields: List<String>, val waterTransaction: String?)

/** Bounded structural reader; v1 has no external water snapshot reference. */
internal object TankCareIntegrityFormat {
    const val VERSION = "v2"
    private const val LEGACY_FIELDS = 5
    private const val CURRENT_FIELDS = 6

    fun parse(encoded: String): TankCareIntegrityEntryParts {
        val fields = encoded.split('|', limit = CURRENT_FIELDS)
        val legacy = fields.size == LEGACY_FIELDS && fields[0] == "v1"
        if (!legacy && (fields.size != CURRENT_FIELDS || fields[0] != VERSION)) {
            throw StoreInvariantViolation("Tank-care integrity journal contains an unsupported entry.")
        }
        val transaction = if (legacy) null else fields.last().takeIf { it.isNotEmpty() }
        transaction?.let(::requireWaterReference)
        return TankCareIntegrityEntryParts(fields, transaction)
    }

    fun requireWaterReference(value: String) {
        if (!runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)) {
            throw StoreInvariantViolation("Water deletion transaction must be a canonical UUID.")
        }
    }
}

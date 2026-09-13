package com.aqua.aqualight.data.devices.light.library

import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryNamePolicy
import com.aqua.aqualight.data.store.CommercialStoreSchema
import com.aqua.aqualight.data.store.StoreInvariantViolation

internal object DeviceLightLibraryStoreRules {
    const val MAX_CUSTOM_POINTS = 96
    const val MIN_WEEKDAYS_MASK = 1
    const val MAX_WEEKDAYS_MASK = 127
    const val LAST_DAY_MILLISECOND = 86_399_999L

    fun defaultStore(): DeviceLightLibraryStoreData = DeviceLightLibraryStoreData
        .newBuilder()
        .setSchemaVersion(CommercialStoreSchema.LIGHT_LIBRARY_VERSION)
        .build()

    fun validateStore(store: DeviceLightLibraryStoreData): DeviceLightLibraryStoreData {
        CommercialStoreSchema.requireCurrent(
            storeName = STORE_NAME,
            actualVersion = store.schemaVersion,
            expectedVersion = CommercialStoreSchema.LIGHT_LIBRARY_VERSION
        )
        val ids = mutableSetOf<String>()
        val ownerNames = mutableSetOf<String>()
        store.entriesList.forEach { entry ->
            validateEntry(entry)
            if (!ids.add(entry.id)) violation("Duplicate light-library id ${entry.id}.")
            val ownerNameKey = listOf(
                entry.ownerUid,
                entry.kind.name,
                entry.normalizedName
            ).joinToString(NAME_KEY_SEPARATOR)
            if (!ownerNames.add(ownerNameKey)) {
                violation("Duplicate light-library name for one owner and type.")
            }
        }
        return store
    }

    fun validateEntry(
        entry: StoredDeviceLightLibraryEntry
    ): StoredDeviceLightLibraryEntry {
        requireCanonicalText("entry.id", entry.id, MAX_ID_LENGTH)
        requireCanonicalText("entry.ownerUid", entry.ownerUid, MAX_OWNER_UID_LENGTH)

        val validatedName = when (
            val validation = DeviceLightLibraryNamePolicy.validate(entry.displayName)
        ) {
            is DeviceLightLibraryNamePolicy.Validation.Valid -> validation.name
            is DeviceLightLibraryNamePolicy.Validation.Invalid ->
                violation("entry.displayName is invalid: ${validation.reason}.")
        }
        if (entry.displayName != validatedName.display) {
            violation("entry.displayName must be canonical.")
        }
        if (entry.normalizedName != validatedName.normalized) {
            violation("entry.normalizedName does not match displayName.")
        }

        val requiredChannelKeys = PRODUCT_CHANNELS[entry.productKey]
            ?: violation("Unsupported light-library productKey ${entry.productKey}.")
        if (entry.channelKeysList != requiredChannelKeys) {
            violation("entry.channelKeys must exactly match ${entry.productKey}.")
        }
        requireTimestamp("entry.createdAtMillis", entry.createdAtMillis)
        requireTimestamp("entry.updatedAtMillis", entry.updatedAtMillis)
        if (entry.updatedAtMillis < entry.createdAtMillis) {
            violation("entry.updatedAtMillis precedes createdAtMillis.")
        }

        when (entry.kind) {
            StoredDeviceLightLibraryKind.STORED_DEVICE_LIGHT_LIBRARY_KIND_MANUAL -> {
                if (entry.payloadCase != StoredDeviceLightLibraryEntry.PayloadCase.MANUAL) {
                    violation("Manual light-library entry must contain only a manual payload.")
                }
                validateChannelValues(entry.manual.channelsList, requiredChannelKeys)
            }
            StoredDeviceLightLibraryKind.STORED_DEVICE_LIGHT_LIBRARY_KIND_CUSTOM -> {
                if (entry.payloadCase != StoredDeviceLightLibraryEntry.PayloadCase.CUSTOM) {
                    violation("Custom light-library entry must contain only a custom payload.")
                }
                validateCustom(entry.custom, requiredChannelKeys)
            }
            StoredDeviceLightLibraryKind.STORED_DEVICE_LIGHT_LIBRARY_KIND_UNSPECIFIED,
            StoredDeviceLightLibraryKind.UNRECOGNIZED ->
                violation("entry.kind must be a supported explicit type.")
        }
        return entry
    }

    fun canonicalOwnerUid(rawOwnerUid: String): String = rawOwnerUid.trim().also { ownerUid ->
        requireCanonicalText("ownerUid", ownerUid, MAX_OWNER_UID_LENGTH)
        if (ownerUid != rawOwnerUid) violation("ownerUid must be canonical.")
    }

    private fun validateCustom(
        custom: StoredDeviceLightCustomCurve,
        requiredChannelKeys: List<String>
    ) {
        if (custom.weekdaysMask !in MIN_WEEKDAYS_MASK..MAX_WEEKDAYS_MASK) {
            violation("custom.weekdaysMask must be between 1 and 127.")
        }
        if (custom.pointsCount !in 1..MAX_CUSTOM_POINTS) {
            violation("custom.points must contain between 1 and 96 actual time points.")
        }
        var priorTime = -1L
        custom.pointsList.forEach { point ->
            if (point.timeMs !in 0..LAST_DAY_MILLISECOND) {
                violation("custom point timeMs is outside one day.")
            }
            if (point.timeMs <= priorTime) {
                violation("custom point times must be strictly increasing.")
            }
            priorTime = point.timeMs
            validateChannelValues(point.channelsList, requiredChannelKeys)
        }
    }

    private fun validateChannelValues(
        values: List<StoredDeviceLightChannelValue>,
        requiredChannelKeys: List<String>
    ) {
        if (values.map { value -> value.channelKey } != requiredChannelKeys) {
            violation("Stored channel values must exactly match the product channel order.")
        }
        if (values.any { value -> value.percent !in 0..100 }) {
            violation("Stored channel percentage must be between 0 and 100.")
        }
    }

    private fun requireCanonicalText(field: String, value: String, maxLength: Int) {
        if (value.isBlank() || value != value.trim()) {
            violation("$field must be non-blank and canonical.")
        }
        if (value.length > maxLength) violation("$field exceeds $maxLength characters.")
        if (value.any(Char::isISOControl)) violation("$field contains a control character.")
    }

    private fun requireTimestamp(field: String, value: Long) {
        if (value !in MIN_TIMESTAMP_MILLIS..MAX_TIMESTAMP_MILLIS) {
            violation("$field is outside the supported commercial timestamp range.")
        }
    }

    private fun violation(message: String): Nothing = throw StoreInvariantViolation(message)

    private const val STORE_NAME = "light_library"
    private const val NAME_KEY_SEPARATOR = "\u0000"
    private const val MAX_ID_LENGTH = 128
    private const val MAX_OWNER_UID_LENGTH = 128
    private const val MIN_TIMESTAMP_MILLIS = 1_577_836_800_000L
    private const val MAX_TIMESTAMP_MILLIS = 4_102_444_800_000L
    private const val WRGB_PRODUCT = "LIGHT_WRGB_PRO_ELITE"
    private const val RGB_PRODUCT = "LIGHT_RGB_PRO_SLIM"
    private val PRODUCT_CHANNELS = mapOf(
        WRGB_PRODUCT to listOf("redPercent", "greenPercent", "bluePercent", "whitePercent"),
        RGB_PRODUCT to listOf("redPercent", "greenPercent", "bluePercent")
    )
}

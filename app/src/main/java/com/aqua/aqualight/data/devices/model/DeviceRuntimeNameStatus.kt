package com.aqua.aqualight.data.devices.model

import com.aqua.aqualight.data.devices.runtime.core.utf8ByteCount

/** Firmware-owned product name plus the owner-editable device label. */
data class DeviceRuntimeNameStatus(
    val productDisplayName: String,
    val customName: String,
    val effectiveDisplayName: String,
    val nameEditable: Boolean,
    val customNameMaxBytes: Int
) {
    init {
        requireWireText(productDisplayName, "productDisplayName", allowEmpty = false)
        requireWireText(customName, "customName", allowEmpty = true)
        requireWireText(effectiveDisplayName, "effectiveDisplayName", allowEmpty = false)
        require(nameEditable) { "Runtime device name must be owner-editable." }
        require(customNameMaxBytes == DEVICE_CUSTOM_NAME_MAX_BYTES) {
            "customNameMaxBytes differs from the Android firmware contract."
        }
        require(customName.utf8ByteCount() <= customNameMaxBytes) {
            "customName exceeds the firmware UTF-8 byte limit."
        }
        require(effectiveDisplayName == customName.ifBlank { productDisplayName }) {
            "effectiveDisplayName differs from the firmware name resolution rule."
        }
    }

    companion object {
        const val DEVICE_CUSTOM_NAME_MAX_BYTES = 64

        fun defaultFor(productDisplayName: String): DeviceRuntimeNameStatus =
            DeviceRuntimeNameStatus(
                productDisplayName = productDisplayName,
                customName = "",
                effectiveDisplayName = productDisplayName,
                nameEditable = true,
                customNameMaxBytes = DEVICE_CUSTOM_NAME_MAX_BYTES
            )
    }
}

private fun requireWireText(value: String, field: String, allowEmpty: Boolean) {
    require(allowEmpty || value.isNotEmpty()) { "$field must not be empty." }
    require(value.none(Char::isISOControl)) { "$field must not contain control characters." }
    require(value.isEmpty() || (!value.first().isWhitespace() && !value.last().isWhitespace())) {
        "$field must not contain surrounding whitespace."
    }
}

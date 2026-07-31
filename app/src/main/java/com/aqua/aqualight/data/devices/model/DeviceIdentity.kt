package com.aqua.aqualight.data.devices.model

data class DeviceIdentity(
    val uid: DeviceUid,
    val shortId: String = "",
    val chipId: String = "",
    val espChipId: String = "",
    val efuseMac: String = "",
    val macAddress: String = "",
    val serialNumber: String = "",
    val firmwareSerial: String = "",
    /** Immutable commercial product display name. */
    val displayName: String = "",
    /** Owner-editable firmware name override; empty means cleared. */
    val customName: String = "",
    /** Authenticated firmware resolution of customName or displayName. */
    val effectiveDisplayName: String = "",
    val nameEditable: Boolean = false,
    val customNameMaxBytes: Int = 0,
    val setupCode: String = "",
    val setupSsid: String = ""
) {
    val title: String
        get() = effectiveDisplayName.ifBlank {
            customName.ifBlank { displayName.ifBlank { uid.value } }
        }
}

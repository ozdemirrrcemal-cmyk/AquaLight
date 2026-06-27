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
    val displayName: String = "",
    val customName: String = "",
    val setupCode: String = "",
    val setupSsid: String = ""
) {
    val title: String
        get() = customName.ifBlank { displayName.ifBlank { uid.value } }
}

package com.aqua.aqualight.data.devices.model

data class DeviceProduct(
    val brand: String = "",
    val productId: String = "",
    val productKey: String = "",
    val family: DeviceFamily = DeviceFamily.UNKNOWN,
    val familyRaw: String = "",
    val line: String = "",
    val model: String = "",
    val displayName: String = "",
    val skuId: String = "",
    val skuCode: String = "",
    val setupCode: String = "",
    val hardwareRevision: String = ""
)

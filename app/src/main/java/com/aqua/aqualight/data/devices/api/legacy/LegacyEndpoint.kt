package com.aqua.aqualight.data.devices.api.legacy

enum class LegacyEndpoint(
    val path: String
) {
    GET("/get"),
    SET("/set")
}

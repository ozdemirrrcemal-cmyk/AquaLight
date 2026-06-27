package com.aqua.aqualight.data.devices.model

@JvmInline
value class DeviceUid(val value: String) {
    init {
        require(value.isNotBlank()) { "deviceUid must not be blank" }
    }

    override fun toString(): String = value
}

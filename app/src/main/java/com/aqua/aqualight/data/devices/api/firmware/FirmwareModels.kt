package com.aqua.aqualight.data.devices.api.firmware

data class FirmwareStatus(
    val available: Boolean,
    val otaSupported: Boolean,
    val firmwareVersion: String,
    val firmwareBuild: String,
    val hardwareRevision: String,
    val productKey: String,
    val productId: String,
    val updateInProgress: Boolean,
    val otaPhase: String,
    val otaProgressPercent: Double,
    val restartRequired: Boolean,
    val lastError: String
)

data class FirmwareOtaRequest(
    val url: String,
    val version: String,
    val sha256: String,
    val size: Int = 0,
    val productKey: String = "",
    val productId: String = "",
    val hardwareRevision: String = "",
    val startDownload: Boolean = true,
    val applyNow: Boolean = true,
    val allowInsecureHttp: Boolean = false
)

data class FirmwareOtaResult(
    val accepted: Boolean,
    val operation: String,
    val pendingRequest: Boolean,
    val downloadStarted: Boolean,
    val flashWriteStarted: Boolean,
    val restartRequired: Boolean,
    val message: String
)

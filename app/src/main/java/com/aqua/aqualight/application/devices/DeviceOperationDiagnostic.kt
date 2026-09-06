package com.aqua.aqualight.application.devices

/** Structured, presentation-safe evidence for a failed user-initiated device operation. */
data class DeviceOperationDiagnostic(
    val stage: String,
    val outcome: String,
    val command: DeviceOperationCommandDiagnostic? = null,
    val response: DeviceOperationResponseDiagnostic? = null,
    val runtimeState: DeviceOperationRuntimeStateDiagnostic? = null,
    val detail: String? = null
)

data class DeviceOperationCommandDiagnostic(
    val deviceUid: String,
    val module: String,
    val action: String,
    val messageId: String? = null,
    val connectionGeneration: Long? = null,
    val timeoutMillis: Long? = null
)

data class DeviceOperationResponseDiagnostic(
    val statusCode: Int? = null,
    val firmwareCode: String? = null,
    val firmwareField: String? = null,
    val firmwareMessage: String? = null
)

data class DeviceOperationRuntimeStateDiagnostic(
    val connectionGeneration: Long? = null,
    val authoritative: Boolean? = null
)

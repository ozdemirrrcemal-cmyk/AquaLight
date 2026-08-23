package com.aqua.aqualight.data.devices.runtime.core

internal enum class DeviceRuntimeDiagnosticCancellationReason {
    CONNECTION_REPLACED,
    NETWORK_ROUTE_CHANGED,
    LOCAL_NETWORK_LOSS,
    DEVICE_RETIRED,
    DEVICE_CLOSED,
    CREDENTIAL_REVOKED,
    REPOSITORY_CLOSED,
    REPOSITORY_SHUTDOWN,
    METADATA_FAILURE,
    TRANSPORT_UNAVAILABLE,
    SOCKET_CLOSED,
    SOCKET_FAILURE,
    OTHER
}

internal fun String.toDeviceRuntimeDiagnosticCancellationReason():
    DeviceRuntimeDiagnosticCancellationReason = when (this) {
        "runtime connection replaced" -> DeviceRuntimeDiagnosticCancellationReason.CONNECTION_REPLACED
        "local network route changed" ->
            DeviceRuntimeDiagnosticCancellationReason.NETWORK_ROUTE_CHANGED
        "local network unavailable" -> DeviceRuntimeDiagnosticCancellationReason.LOCAL_NETWORK_LOSS
        "device runtime retired" -> DeviceRuntimeDiagnosticCancellationReason.DEVICE_RETIRED
        "device runtime closed" -> DeviceRuntimeDiagnosticCancellationReason.DEVICE_CLOSED
        "runtime credential revoked" -> DeviceRuntimeDiagnosticCancellationReason.CREDENTIAL_REVOKED
        "runtime repository closed" -> DeviceRuntimeDiagnosticCancellationReason.REPOSITORY_CLOSED
        "runtime repository shutdown" -> DeviceRuntimeDiagnosticCancellationReason.REPOSITORY_SHUTDOWN
        "metadata bootstrap failed" -> DeviceRuntimeDiagnosticCancellationReason.METADATA_FAILURE
        "runtime transport unavailable" ->
            DeviceRuntimeDiagnosticCancellationReason.TRANSPORT_UNAVAILABLE
        "runtime socket closed" -> DeviceRuntimeDiagnosticCancellationReason.SOCKET_CLOSED
        "runtime socket failure" -> DeviceRuntimeDiagnosticCancellationReason.SOCKET_FAILURE
        else -> DeviceRuntimeDiagnosticCancellationReason.OTHER
    }

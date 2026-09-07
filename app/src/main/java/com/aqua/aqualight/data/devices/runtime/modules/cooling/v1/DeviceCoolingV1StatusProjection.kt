package com.aqua.aqualight.data.devices.runtime.modules.cooling.v1

/** Status is parsed once at the protocol boundary; central state consumes typed firmware truth. */
internal fun DeviceCoolingV1StatusDocument.toConfigSnapshot(): DeviceCoolingV1ConfigSnapshot =
    config

/**
 * The embedded telemetry keeps the firmware's evaluated program revision. It is never rebuilt from
 * the persisted status revision, so a newly saved program cannot masquerade as already actuated.
 */
internal fun DeviceCoolingV1StatusDocument.toTelemetrySnapshot(): DeviceCoolingV1Telemetry =
    telemetry

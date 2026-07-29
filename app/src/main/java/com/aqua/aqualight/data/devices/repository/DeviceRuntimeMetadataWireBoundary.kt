package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage

/**
 * Direct wire-to-snapshot reduction is forbidden.
 *
 * The repository still reaches this boundary until authenticated bootstrap dispatch supplies
 * generation-tagged typed fragments. Returning no snapshot is intentionally fail-closed: no partial
 * identity/capability response may mutate or refresh durable device metadata.
 */
@Suppress("UNUSED_PARAMETER")
internal fun DeviceRuntimeMetadataReducer.reduce(
    snapshot: DeviceSnapshot,
    response: AqlWsIncomingMessage.Response
): DeviceSnapshot? = null

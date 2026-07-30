package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage

/**
 * Direct wire-to-snapshot reduction is forbidden.
 *
 * The repository still reaches this boundary until authenticated bootstrap dispatch supplies
 * generation-tagged typed fragments. Throwing is intentionally fail-closed: the caller catches the
 * boundary failure and no partial identity/capability response mutates durable device metadata.
 */
@Suppress("UNUSED_PARAMETER")
internal fun DeviceRuntimeMetadataReducer.reduce(
    snapshot: DeviceSnapshot,
    response: AqlWsIncomingMessage.Response
): DeviceSnapshot? {
    error("Wire metadata must be parsed into generation-tagged typed fragments before reduction.")
}

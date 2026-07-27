package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid

/**
 * Invalidates stale authenticated evidence after a bounded control probe definitively fails.
 *
 * Menu access and device cards share the same registry, so an unavailable dialog must never be
 * emitted while the canonical card remains Online on an older runtime proof. A failed proof also
 * replaces only that device's reusable runtime session, preventing a ghost Authenticated socket
 * from being selected again by the next background probe.
 */
internal fun DevicesRepository.recordControlFailure(deviceUid: DeviceUid): DeviceSnapshot? {
    val localNetworkAvailable = isLocalNetworkAvailable()
    val visibleState = if (localNetworkAvailable) {
        DeviceOnlineState.OFFLINE
    } else {
        DeviceOnlineState.LOCAL_NETWORK_OFFLINE
    }

    val updated = updateConnectionState(deviceUid) { previous ->
        previous.copy(
            onlineState = visibleState,
            lastWsConnectedAtMillis = null,
            lastWsConnectedElapsedMillis = null,
            lastAuthenticatedAtMillis = null,
            lastAuthenticatedElapsedMillis = null,
            lastRuntimeMessageAtMillis = null,
            lastRuntimeMessageElapsedMillis = null,
            lastControlProofAtMillis = null,
            lastControlProofElapsedMillis = null,
            lastErrorMessage = CONTROL_PROOF_FAILURE_MESSAGE
        )
    }

    if (localNetworkAvailable) {
        replaceRuntimeAfterControlFailure(deviceUid)
    }
    return updated
}

private const val CONTROL_PROOF_FAILURE_MESSAGE = "Device did not answer the control probe."

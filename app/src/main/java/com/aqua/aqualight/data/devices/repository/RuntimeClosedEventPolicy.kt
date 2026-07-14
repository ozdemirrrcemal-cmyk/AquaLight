package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState

/**
 * Protects the live session from a delayed close callback belonging to an older WebSocket.
 *
 * AqlWsClient updates its current state before publishing the lifecycle event. Therefore a close
 * event belongs to the still-current session only while that device session remains disconnected.
 */
internal object RuntimeClosedEventPolicy {
    fun shouldClearRuntimeProof(currentState: AqlWsConnectionState?): Boolean =
        currentState == AqlWsConnectionState.Disconnected
}

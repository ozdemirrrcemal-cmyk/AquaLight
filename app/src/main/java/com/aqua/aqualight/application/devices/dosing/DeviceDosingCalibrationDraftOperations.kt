package com.aqua.aqualight.application.devices.dosing

/**
 * Durable owner-scoped boundary for the uncommitted calibration channel name.
 *
 * Operations are synchronous by design: the draft must be committed before presentation advances
 * beyond the name step, otherwise an immediate Android process stop can leave a recoverable
 * firmware calibration session without the name required for final confirmation.
 */
interface DeviceDosingCalibrationDraftOperations {
    fun loadDisplayName(deviceUid: String, slotId: String): String?

    fun saveDisplayName(deviceUid: String, slotId: String, displayName: String)

    fun clearDisplayName(deviceUid: String, slotId: String)
}

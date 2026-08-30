package com.aqua.aqualight.application.devices.dosing

/**
 * A persisted Dosing mutation was durably acknowledged by firmware at [revision], but the
 * authoritative global/channel/progress readback could not be completed yet.
 *
 * This result deliberately carries no snapshot. Consumers may acknowledge the user's completed
 * save and advance an editor's base revision, while authoritative device state remains owned only
 * by the central Dosing state owner and stays fail-closed until a later successful refresh.
 */
data class DeviceDosingChannelCommittedResult(
    val revision: Long
) : DeviceDosingChannelOperationResult {
    init {
        require(revision >= 0L)
    }
}

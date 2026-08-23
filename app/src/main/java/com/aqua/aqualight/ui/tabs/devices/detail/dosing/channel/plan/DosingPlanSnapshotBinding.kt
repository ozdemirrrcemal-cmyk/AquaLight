package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class DosingPlanChannelBinding(
    val deviceUid: String,
    val slotId: String
) {
    companion object {
        fun from(deviceUidText: String, slotIdText: String): DosingPlanChannelBinding? {
            val deviceUid = deviceUidText.trim()
            val slotId = slotIdText.trim()
            return if (deviceUid.isBlank() || slotId.isBlank()) {
                null
            } else {
                DosingPlanChannelBinding(deviceUid, slotId)
            }
        }
    }
}

/** Owns the observation/refresh jobs for exactly one normalized channel identity. */
internal class DosingPlanSnapshotBinding(
    private val operations: DeviceDosingChannelOperations,
    private val scope: CoroutineScope
) {
    private var observeJob: Job? = null
    private var refreshJob: Job? = null

    fun replace(
        binding: DosingPlanChannelBinding,
        onSnapshot: (DeviceDosingChannelSnapshot) -> Unit
    ) {
        clear()
        observeJob = scope.launch {
            operations.observe(binding.deviceUid, binding.slotId).collect { snapshot ->
                snapshot?.let(onSnapshot)
            }
        }
        refreshJob = scope.launch {
            val result = operations.refresh(binding.deviceUid, binding.slotId)
            if (result is DeviceDosingChannelOperationResult.Success) {
                onSnapshot(result.snapshot)
            }
        }
    }

    fun clear() {
        observeJob?.cancel()
        refreshJob?.cancel()
        observeJob = null
        refreshJob = null
    }
}

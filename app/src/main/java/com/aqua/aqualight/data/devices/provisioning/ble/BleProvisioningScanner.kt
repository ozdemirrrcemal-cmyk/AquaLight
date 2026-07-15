package com.aqua.aqualight.data.devices.provisioning.ble

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

interface BleProvisioningScanner {
    val candidates: StateFlow<List<AqlBleProvisioningCandidate>>

    fun startScan(): AqlBleProvisioningScanner.StartResult

    fun stopScan()

    fun clearCandidates()
}

class DefaultBleProvisioningScanner(
    context: Context
) : BleProvisioningScanner {

    private val delegate = AqlBleProvisioningScanner(context.applicationContext)

    override val candidates: StateFlow<List<AqlBleProvisioningCandidate>>
        get() = delegate.candidates

    override fun startScan(): AqlBleProvisioningScanner.StartResult =
        delegate.startScan()

    override fun stopScan() {
        delegate.stopScan()
    }

    override fun clearCandidates() {
        delegate.clearCandidates()
    }
}

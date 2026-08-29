@file:Suppress("ComplexCondition")

package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import com.aqua.aqualight.data.devices.model.DeviceUid
import java.util.concurrent.ConcurrentHashMap

/** Durable evidence required to recover one OTA attempt after Android process recreation. */
internal data class DeviceOtaTransaction(
    val plan: PreparedDeviceFirmwareUpdate,
    val startedAtEpochMillis: Long,
    val recoveryDeadlineEpochMillis: Long = 0L,
    val awaitingVersionVerification: Boolean = false
) {
    init {
        require(plan.deviceUid.isNotBlank())
        require(startedAtEpochMillis > 0L)
        require(recoveryDeadlineEpochMillis >= 0L)
    }

    val isWaitingForPostRestartVerification: Boolean
        get() = awaitingVersionVerification
}

/** Exact rejected release identity; a different signed artifact remains eligible. */
internal data class DeviceOtaQuarantine(
    val deviceUid: String,
    val previousVersion: String,
    val rejectedVersion: String,
    val productKey: String,
    val hardwareRevision: String,
    val manifestTag: String,
    val sha256: String,
    val recordedAtEpochMillis: Long
) {
    fun matches(plan: PreparedDeviceFirmwareUpdate): Boolean =
        deviceUid == plan.deviceUid &&
            rejectedVersion == plan.targetVersion &&
            productKey == plan.productKey &&
            hardwareRevision == plan.hardwareRevision &&
            manifestTag == plan.manifestTag &&
            sha256.equals(plan.sha256, ignoreCase = true)
}

/** Synchronous by design: the attempt is committed before the OTA command can reboot the device. */
internal interface DeviceOtaTransactionStore {
    fun activeTransactions(): List<DeviceOtaTransaction>
    fun active(deviceUid: DeviceUid): DeviceOtaTransaction?
    fun saveActive(transaction: DeviceOtaTransaction)
    fun clearActive(deviceUid: DeviceUid)
    fun quarantine(deviceUid: DeviceUid): DeviceOtaQuarantine?
    fun saveQuarantine(quarantine: DeviceOtaQuarantine)
}

internal class InMemoryDeviceOtaTransactionStore : DeviceOtaTransactionStore {
    private val active = ConcurrentHashMap<DeviceUid, DeviceOtaTransaction>()
    private val quarantines = ConcurrentHashMap<DeviceUid, DeviceOtaQuarantine>()

    override fun activeTransactions(): List<DeviceOtaTransaction> = active.values.toList()

    override fun active(deviceUid: DeviceUid): DeviceOtaTransaction? = active[deviceUid]

    override fun saveActive(transaction: DeviceOtaTransaction) {
        active[DeviceUid(transaction.plan.deviceUid)] = transaction
    }

    override fun clearActive(deviceUid: DeviceUid) {
        active.remove(deviceUid)
    }

    override fun quarantine(deviceUid: DeviceUid): DeviceOtaQuarantine? = quarantines[deviceUid]

    override fun saveQuarantine(quarantine: DeviceOtaQuarantine) {
        quarantines[DeviceUid(quarantine.deviceUid)] = quarantine
    }
}

@file:Suppress("TooManyFunctions")

package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.DeviceDosingCalibrationOperations
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationResult
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationSessionPhase
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import java.util.concurrent.TimeUnit

/**
 * Debug-only mutable Dosing state used by installable test devices.
 *
 * It never talks to a physical runtime and never changes the production Dosing boundary. Odd fixture
 * channels start uncalibrated so the calibration screen is reachable; even fixture channels start
 * calibrated so the detail screen is reachable from the same test device.
 */
internal class DebugFixtureDosingStateStore(
    fixtures: DebugDeviceFixtureCatalog
) {
    private val states = linkedMapOf<String, MutableStateFlow<DeviceDosingCalibrationSnapshot>>()

    init {
        fixtures.snapshots.forEach { snapshot ->
            val root = fixtures.rootSnapshot(snapshot.deviceUid.value) ?: return@forEach
            root.channelSlots.dosingChannels.forEach { slot ->
                val calibrated = slot.index.position % 2 == 0
                val state = DeviceDosingCalibrationSnapshot(
                    deviceUid = root.deviceUid,
                    slotId = slot.id.value,
                    pumpCount = root.channelSlots.dosingChannels.size,
                    channelNumber = slot.index.position,
                    channelTitle = slot.defaultDisplayName,
                    deviceUptimeMs = FIXTURE_UPTIME_MS,
                    calibrated = calibrated,
                    lastCalibratedAt = if (calibrated) currentEpochSeconds() else 0L,
                    sessionPhase = DeviceDosingCalibrationSessionPhase.IDLE,
                    startedAtUptimeMs = 0L,
                    durationMs = 0L,
                    measuredMl = 0.0,
                    pendingDoseMsPerMl = 0L,
                    verificationDoseStarted = false,
                    verificationDoseComplete = false,
                    verificationDoseRemainingMs = 0L,
                    manualActive = false
                )
                states[key(root.deviceUid, slot.id.value)] = MutableStateFlow(state)
            }
        }
    }

    fun observe(deviceUid: String, slotId: String): Flow<DeviceDosingCalibrationSnapshot?> =
        states[key(deviceUid, slotId)]?.asStateFlow() ?: flowOf(null)

    fun current(deviceUid: String, slotId: String): DeviceDosingCalibrationSnapshot? =
        states[key(deviceUid, slotId)]?.value

    fun isCalibrated(deviceUid: String, slotId: String): Boolean =
        current(deviceUid, slotId)?.calibrated == true

    fun refresh(deviceUid: String, slotId: String): DeviceDosingCalibrationResult {
        val state = current(deviceUid, slotId) ?: return DeviceDosingCalibrationResult.Unavailable
        val refreshed = if (
            state.sessionPhase == DeviceDosingCalibrationSessionPhase.PENDING_VERIFICATION &&
            state.verificationDoseStarted &&
            !state.verificationDoseComplete
        ) {
            update(deviceUid, slotId) { current ->
                current.copy(
                    verificationDoseComplete = true,
                    verificationDoseRemainingMs = 0L,
                    manualActive = false
                )
            }
        } else {
            state
        }
        return refreshed.toResult()
    }

    fun saveDisplayName(
        deviceUid: String,
        slotId: String,
        displayName: String
    ): DeviceDosingCalibrationResult = update(deviceUid, slotId) { state ->
        state.copy(channelTitle = displayName.trim())
    }.toResult()

    fun primeStart(deviceUid: String, slotId: String): DeviceDosingCalibrationResult =
        update(deviceUid, slotId) { state -> state.copy(manualActive = true) }.toResult()

    fun primeStop(deviceUid: String, slotId: String): DeviceDosingCalibrationResult =
        update(deviceUid, slotId) { state -> state.copy(manualActive = false) }.toResult()

    fun start(deviceUid: String, slotId: String): DeviceDosingCalibrationResult =
        update(deviceUid, slotId) { state ->
            state.copy(
                deviceUptimeMs = FIXTURE_UPTIME_MS,
                sessionPhase = DeviceDosingCalibrationSessionPhase.RUNNING,
                startedAtUptimeMs = FIXTURE_UPTIME_MS,
                durationMs = CALIBRATION_DURATION_MS,
                measuredMl = 0.0,
                pendingDoseMsPerMl = 0L,
                verificationDoseStarted = false,
                verificationDoseComplete = false,
                verificationDoseRemainingMs = 0L,
                manualActive = true
            )
        }.toResult(operationDurationMs = CALIBRATION_DURATION_MS)

    fun finish(
        deviceUid: String,
        slotId: String,
        measuredMl: Double
    ): DeviceDosingCalibrationResult = update(deviceUid, slotId) { state ->
        state.copy(
            sessionPhase = DeviceDosingCalibrationSessionPhase.PENDING_VERIFICATION,
            durationMs = CALIBRATION_DURATION_MS,
            measuredMl = measuredMl,
            pendingDoseMsPerMl = (CALIBRATION_DURATION_MS / measuredMl)
                .toLong()
                .coerceAtLeast(1L),
            verificationDoseStarted = false,
            verificationDoseComplete = false,
            verificationDoseRemainingMs = 0L,
            manualActive = false
        )
    }.toResult()

    fun startVerificationDose(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = update(deviceUid, slotId) { state ->
        state.copy(
            sessionPhase = DeviceDosingCalibrationSessionPhase.PENDING_VERIFICATION,
            verificationDoseStarted = true,
            verificationDoseComplete = false,
            verificationDoseRemainingMs = VERIFICATION_DURATION_MS,
            manualActive = true
        )
    }.toResult(operationDurationMs = VERIFICATION_DURATION_MS)

    fun stopVerificationDose(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = update(deviceUid, slotId) { state ->
        state.copy(
            verificationDoseStarted = false,
            verificationDoseComplete = false,
            verificationDoseRemainingMs = 0L,
            manualActive = false
        )
    }.toResult()

    fun confirm(deviceUid: String, slotId: String): DeviceDosingCalibrationResult =
        update(deviceUid, slotId) { state ->
            state.copy(
                calibrated = true,
                lastCalibratedAt = currentEpochSeconds(),
                sessionPhase = DeviceDosingCalibrationSessionPhase.IDLE,
                startedAtUptimeMs = 0L,
                durationMs = 0L,
                verificationDoseStarted = false,
                verificationDoseComplete = false,
                verificationDoseRemainingMs = 0L,
                manualActive = false
            )
        }.toResult()

    fun cancel(deviceUid: String, slotId: String): DeviceDosingCalibrationResult =
        update(deviceUid, slotId) { state ->
            state.copy(
                sessionPhase = DeviceDosingCalibrationSessionPhase.IDLE,
                startedAtUptimeMs = 0L,
                durationMs = 0L,
                measuredMl = 0.0,
                pendingDoseMsPerMl = 0L,
                verificationDoseStarted = false,
                verificationDoseComplete = false,
                verificationDoseRemainingMs = 0L,
                manualActive = false
            )
        }.toResult()

    private fun update(
        deviceUid: String,
        slotId: String,
        transform: (DeviceDosingCalibrationSnapshot) -> DeviceDosingCalibrationSnapshot
    ): DeviceDosingCalibrationSnapshot? {
        val flow = states[key(deviceUid, slotId)] ?: return null
        return transform(flow.value).also { updated -> flow.value = updated }
    }

    private fun DeviceDosingCalibrationSnapshot?.toResult(
        operationDurationMs: Long? = null
    ): DeviceDosingCalibrationResult = this?.let { snapshot ->
        DeviceDosingCalibrationResult.Success(snapshot, operationDurationMs)
    } ?: DeviceDosingCalibrationResult.Unavailable

    private fun key(deviceUid: String, slotId: String): String =
        "${deviceUid.trim()}|${slotId.trim()}"

    private fun currentEpochSeconds(): Long =
        TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())

    private companion object {
        const val FIXTURE_UPTIME_MS = 60_000L
        const val CALIBRATION_DURATION_MS = 5_000L
        const val VERIFICATION_DURATION_MS = 1_000L
    }
}

/** Routes fixture devices to the debug state store and real devices to the production boundary. */
internal class DebugFixtureDosingCalibrationOperations(
    private val delegate: DeviceDosingCalibrationOperations,
    private val fixtures: DebugDeviceFixtureCatalog,
    private val stateStore: DebugFixtureDosingStateStore
) : DeviceDosingCalibrationOperations {

    override fun observe(
        deviceUid: String,
        slotId: String
    ): Flow<DeviceDosingCalibrationSnapshot?> = if (fixtures.contains(deviceUid)) {
        stateStore.observe(deviceUid, slotId)
    } else {
        delegate.observe(deviceUid, slotId)
    }

    override suspend fun refresh(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = if (fixtures.contains(deviceUid)) {
        stateStore.refresh(deviceUid, slotId)
    } else {
        delegate.refresh(deviceUid, slotId)
    }

    override suspend fun saveDisplayName(
        deviceUid: String,
        slotId: String,
        displayName: String
    ): DeviceDosingCalibrationResult = if (fixtures.contains(deviceUid)) {
        stateStore.saveDisplayName(deviceUid, slotId, displayName)
    } else {
        delegate.saveDisplayName(deviceUid, slotId, displayName)
    }

    override suspend fun primeStart(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = if (fixtures.contains(deviceUid)) {
        stateStore.primeStart(deviceUid, slotId)
    } else {
        delegate.primeStart(deviceUid, slotId)
    }

    override suspend fun primeStop(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = if (fixtures.contains(deviceUid)) {
        stateStore.primeStop(deviceUid, slotId)
    } else {
        delegate.primeStop(deviceUid, slotId)
    }

    override suspend fun start(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = if (fixtures.contains(deviceUid)) {
        stateStore.start(deviceUid, slotId)
    } else {
        delegate.start(deviceUid, slotId)
    }

    override suspend fun finish(
        deviceUid: String,
        slotId: String,
        measuredMl: Double
    ): DeviceDosingCalibrationResult = if (fixtures.contains(deviceUid)) {
        stateStore.finish(deviceUid, slotId, measuredMl)
    } else {
        delegate.finish(deviceUid, slotId, measuredMl)
    }

    override suspend fun startVerificationDose(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = if (fixtures.contains(deviceUid)) {
        stateStore.startVerificationDose(deviceUid, slotId)
    } else {
        delegate.startVerificationDose(deviceUid, slotId)
    }

    override suspend fun stopVerificationDose(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = if (fixtures.contains(deviceUid)) {
        stateStore.stopVerificationDose(deviceUid, slotId)
    } else {
        delegate.stopVerificationDose(deviceUid, slotId)
    }

    override suspend fun confirm(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = if (fixtures.contains(deviceUid)) {
        stateStore.confirm(deviceUid, slotId)
    } else {
        delegate.confirm(deviceUid, slotId)
    }

    override suspend fun cancel(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = if (fixtures.contains(deviceUid)) {
        stateStore.cancel(deviceUid, slotId)
    } else {
        delegate.cancel(deviceUid, slotId)
    }
}

package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.DeviceDosingCalibrationCandidate
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationChannelSnapshot
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationOperations
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationRun
import com.aqua.aqualight.application.devices.DeviceDosingVerificationRun
import com.aqua.aqualight.application.devices.DeviceRootMenuFeature
import com.aqua.aqualight.data.devices.runtime.modules.dosing.DeviceDosingRuntimeContract

/**
 * Keeps the installable Debug APK calibration wizard usable without physical hardware.
 *
 * Fixture UIDs stay catalog-backed and simulate only the firmware command results required by the
 * calibration UI. Real device UIDs continue through the production calibration operations.
 */
internal class DebugFixtureDosingCalibrationOperations(
    private val delegate: DeviceDosingCalibrationOperations,
    private val fixtures: DebugDeviceFixtureCatalog
) : DeviceDosingCalibrationOperations {

    private val channelStates = mutableMapOf<FixtureChannelIdentity, FixtureCalibrationState>()

    override suspend fun loadChannel(
        deviceUid: String,
        channelKey: String
    ): Result<DeviceDosingCalibrationChannelSnapshot> = fixtureResult(deviceUid, channelKey) {
        snapshot(deviceUid, channelKey)
    } ?: delegate.loadChannel(deviceUid, channelKey)

    override suspend fun updateDisplayName(
        deviceUid: String,
        channelKey: String,
        displayName: String
    ): Result<DeviceDosingCalibrationChannelSnapshot> = fixtureResult(deviceUid, channelKey) {
        val normalizedName = displayName.trim()
        require(normalizedName.isNotEmpty()) { "Dosing liquid name must not be blank." }
        state(deviceUid, channelKey).displayName = normalizedName
        snapshot(deviceUid, channelKey)
    } ?: delegate.updateDisplayName(deviceUid, channelKey, displayName)

    override suspend fun startPrime(deviceUid: String, channelKey: String): Result<Unit> =
        fixtureResult(deviceUid, channelKey) {
            state(deviceUid, channelKey).primeActive = true
        } ?: delegate.startPrime(deviceUid, channelKey)

    override suspend fun stopPrime(deviceUid: String, channelKey: String): Result<Unit> =
        fixtureResult(deviceUid, channelKey) {
            state(deviceUid, channelKey).primeActive = false
        } ?: delegate.stopPrime(deviceUid, channelKey)

    override suspend fun startCalibrationDose(
        deviceUid: String,
        channelKey: String
    ): Result<DeviceDosingCalibrationRun> = fixtureResult(deviceUid, channelKey) {
        val fixtureState = state(deviceUid, channelKey)
        fixtureState.calibrationDurationMs =
            DeviceDosingRuntimeContract.Limit.DEFAULT_CALIBRATION_DURATION_MS
        fixtureState.pendingDoseMsPerMl = null
        DeviceDosingCalibrationRun(durationMs = fixtureState.calibrationDurationMs)
    } ?: delegate.startCalibrationDose(deviceUid, channelKey)

    override suspend fun finishCalibrationDose(
        deviceUid: String,
        channelKey: String,
        measuredMl: Double
    ): Result<DeviceDosingCalibrationCandidate> = fixtureResult(deviceUid, channelKey) {
        require(
            measuredMl in DeviceDosingRuntimeContract.Limit.MIN_CALIBRATION_MEASURED_ML..
                DeviceDosingRuntimeContract.Limit.MAX_CALIBRATION_MEASURED_ML
        ) { "Measured calibration volume is outside the firmware contract." }
        val fixtureState = state(deviceUid, channelKey)
        val durationMs = fixtureState.calibrationDurationMs
        require(durationMs > 0L) { "Calibration dose must run before measurement." }
        val rawDoseMsPerMl = durationMs.toDouble() / measuredMl
        require(rawDoseMsPerMl in 1.0..DeviceDosingRuntimeContract.Limit.MAX_DOSE_MS_PER_ML.toDouble()) {
            "Calculated calibration is outside the firmware contract."
        }
        val pendingDoseMsPerMl = (rawDoseMsPerMl + HALF_UP_ROUNDING).toLong()
        fixtureState.pendingDoseMsPerMl = pendingDoseMsPerMl
        DeviceDosingCalibrationCandidate(
            measuredMl = measuredMl,
            durationMs = durationMs,
            pendingDoseMsPerMl = pendingDoseMsPerMl
        )
    } ?: delegate.finishCalibrationDose(deviceUid, channelKey, measuredMl)

    override suspend fun startVerificationDose(
        deviceUid: String,
        channelKey: String,
        amountMl: Double
    ): Result<DeviceDosingVerificationRun> = fixtureResult(deviceUid, channelKey) {
        require(amountMl > 0.0 && amountMl <= DeviceDosingRuntimeContract.Limit.MAX_MANUAL_DOSE_ML) {
            "Verification dose is outside the firmware contract."
        }
        val fixtureState = state(deviceUid, channelKey)
        val pendingDoseMsPerMl = requireNotNull(fixtureState.pendingDoseMsPerMl) {
            "Calibration measurement must be saved before verification."
        }
        val durationMs = (amountMl * pendingDoseMsPerMl + HALF_UP_ROUNDING).toLong()
        require(
            durationMs in DeviceDosingRuntimeContract.Limit.MIN_MANUAL_DOSE_DURATION_MS..
                DeviceDosingRuntimeContract.Limit.MAX_MANUAL_DOSE_DURATION_MS
        ) { "Verification duration is outside the firmware contract." }
        fixtureState.verificationActive = true
        DeviceDosingVerificationRun(amountMl = amountMl, durationMs = durationMs)
    } ?: delegate.startVerificationDose(deviceUid, channelKey, amountMl)

    override suspend fun stopVerificationDose(
        deviceUid: String,
        channelKey: String
    ): Result<Unit> = fixtureResult(deviceUid, channelKey) {
        state(deviceUid, channelKey).verificationActive = false
    } ?: delegate.stopVerificationDose(deviceUid, channelKey)

    override suspend fun confirmCalibration(
        deviceUid: String,
        channelKey: String
    ): Result<DeviceDosingCalibrationChannelSnapshot> = fixtureResult(deviceUid, channelKey) {
        val fixtureState = state(deviceUid, channelKey)
        require(fixtureState.pendingDoseMsPerMl != null) {
            "Pending calibration is required before confirmation."
        }
        fixtureState.calibrated = true
        fixtureState.pendingDoseMsPerMl = null
        fixtureState.verificationActive = false
        snapshot(deviceUid, channelKey)
    } ?: delegate.confirmCalibration(deviceUid, channelKey)

    override suspend fun cancelCalibration(
        deviceUid: String,
        channelKey: String
    ): Result<DeviceDosingCalibrationChannelSnapshot> = fixtureResult(deviceUid, channelKey) {
        val fixtureState = state(deviceUid, channelKey)
        fixtureState.pendingDoseMsPerMl = null
        fixtureState.primeActive = false
        fixtureState.verificationActive = false
        fixtureState.calibrationDurationMs = 0L
        snapshot(deviceUid, channelKey)
    } ?: delegate.cancelCalibration(deviceUid, channelKey)

    private fun snapshot(
        deviceUid: String,
        channelKey: String
    ): DeviceDosingCalibrationChannelSnapshot {
        val root = requireNotNull(fixtures.rootSnapshot(deviceUid)) { "Debug fixture device is missing." }
        val normalizedChannelKey = channelKey.trim().lowercase()
        val slot = requireNotNull(
            root.channelSlots.dosingChannels.singleOrNull { channel ->
                channel.wireKey.value == normalizedChannelKey
            }
        ) { "Debug fixture Dosing channel is missing." }
        val fixtureState = state(deviceUid, normalizedChannelKey)
        val calibrationAvailable = DeviceRootMenuFeature.DOSING_CALIBRATION in root.menuFeatures
        return DeviceDosingCalibrationChannelSnapshot(
            pumpCount = root.channelSlots.dosingChannels.size,
            channelNumber = slot.index.position,
            channelKey = slot.wireKey.value,
            displayName = fixtureState.displayName,
            calibrated = fixtureState.calibrated,
            calibrationEditable = calibrationAvailable,
            supportsPrime = calibrationAvailable,
            supportsManualDose = calibrationAvailable,
            minimumMeasuredMl = DeviceDosingRuntimeContract.Limit.MIN_CALIBRATION_MEASURED_ML,
            maximumMeasuredMl = DeviceDosingRuntimeContract.Limit.MAX_CALIBRATION_MEASURED_ML,
            maximumVerificationDoseMl = DeviceDosingRuntimeContract.Limit.MAX_MANUAL_DOSE_ML
        )
    }

    private fun state(deviceUid: String, channelKey: String): FixtureCalibrationState {
        val normalizedChannelKey = channelKey.trim().lowercase()
        val identity = FixtureChannelIdentity(deviceUid.trim(), normalizedChannelKey)
        return channelStates.getOrPut(identity) {
            val root = requireNotNull(fixtures.rootSnapshot(identity.deviceUid)) {
                "Debug fixture device is missing."
            }
            val slot = requireNotNull(
                root.channelSlots.dosingChannels.singleOrNull { channel ->
                    channel.wireKey.value == identity.channelKey
                }
            ) { "Debug fixture Dosing channel is missing." }
            FixtureCalibrationState(displayName = slot.defaultDisplayName)
        }
    }

    private fun <T> fixtureResult(
        deviceUid: String,
        channelKey: String,
        block: () -> T
    ): Result<T>? {
        if (!fixtures.contains(deviceUid)) return null
        return runCatching {
            require(channelKey.isNotBlank()) { "Dosing channel key is missing." }
            block()
        }
    }
}

private data class FixtureChannelIdentity(
    val deviceUid: String,
    val channelKey: String
)

private data class FixtureCalibrationState(
    var displayName: String,
    var calibrated: Boolean = false,
    var primeActive: Boolean = false,
    var verificationActive: Boolean = false,
    var calibrationDurationMs: Long = 0L,
    var pendingDoseMsPerMl: Long? = null
)

private const val HALF_UP_ROUNDING = 0.5

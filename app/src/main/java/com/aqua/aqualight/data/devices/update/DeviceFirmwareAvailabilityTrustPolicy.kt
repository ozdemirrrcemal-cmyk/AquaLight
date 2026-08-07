package com.aqua.aqualight.data.devices.update

import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal class DeviceFirmwareAvailabilityTrustPolicy(
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS,
    private val maxFutureSkewMillis: Long = DEFAULT_MAX_FUTURE_SKEW_MILLIS
) {

    init {
        require(maxAgeMillis > 0L) { "maxAgeMillis must be positive" }
        require(maxFutureSkewMillis >= 0L) {
            "maxFutureSkewMillis must not be negative"
        }
    }

    fun recordFor(
        snapshot: DeviceSnapshot
    ): DeviceFirmwareAvailabilityTrustRecord? {
        val proofAtMillis = snapshot.connectionState.latestRuntimeProofAtMillis
            ?.takeIf { snapshot.isTrustedLiveFirmwareSnapshot() }
            ?.takeIf(::isFreshTimestamp)
        return proofAtMillis?.let { timestamp ->
            DeviceFirmwareAvailabilityTrustRecord(
                deviceUid = snapshot.deviceUid.value,
                snapshotFingerprint = fingerprint(snapshot),
                validatedAtMillis = timestamp
            )
        }
    }

    fun matches(
        snapshot: DeviceSnapshot,
        record: DeviceFirmwareAvailabilityTrustRecord
    ): Boolean {
        return snapshot.capabilities.ota &&
            snapshot.deviceUid.value == record.deviceUid &&
            fingerprint(snapshot) == record.snapshotFingerprint &&
            isFreshTimestamp(record.validatedAtMillis)
    }

    fun fingerprint(snapshot: DeviceSnapshot): String {
        val canonical = buildList {
            add(snapshot.deviceUid.value.normalized())
            addAll(snapshot.product.firmwareIdentityValues())
            add(snapshot.firmwareVersion.normalized())
            add(snapshot.firmwareBuild.normalized())
            add(snapshot.apiVersion.normalized())
            add(snapshot.protocolVersion.normalized())
            addAll(snapshot.capabilities.canonicalValues())
            addAll(snapshot.limits.canonicalValues())
            addAll(snapshot.supportedFeatures.normalizedValues())
            addAll(snapshot.supportedScreens.normalizedValues())
            addAll(snapshot.modules.normalizedValues())
        }.joinToString(separator = "") { value -> "${value.length}:$value;" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            val value = byte.toInt() and 0xff
            "${HEX_DIGITS[value ushr 4]}${HEX_DIGITS[value and 0x0f]}"
        }
    }

    private fun isFreshTimestamp(timestampMillis: Long): Boolean {
        if (timestampMillis <= 0L) return false
        val ageMillis = nowMillis() - timestampMillis
        return ageMillis in -maxFutureSkewMillis..maxAgeMillis
    }

    private companion object {
        const val DEFAULT_MAX_AGE_MILLIS = 15L * 60L * 1_000L
        const val DEFAULT_MAX_FUTURE_SKEW_MILLIS = 60L * 1_000L
        const val HEX_DIGITS = "0123456789abcdef"
    }
}

private fun DeviceSnapshot.isTrustedLiveFirmwareSnapshot(): Boolean {
    return hasValidatedRuntimeMetadata &&
        capabilities.ota &&
        connectionState.onlineState == DeviceOnlineState.AUTHENTICATED &&
        hasCompleteFirmwareIdentity()
}

private fun DeviceSnapshot.hasCompleteFirmwareIdentity(): Boolean {
    val product = product
    return product.brand.isNotBlank() &&
        product.productId.isNotBlank() &&
        product.productKey.isNotBlank() &&
        product.family != DeviceFamily.UNKNOWN &&
        product.line.isNotBlank() &&
        product.model.isNotBlank() &&
        product.displayName.isNotBlank() &&
        product.skuCode.isNotBlank() &&
        product.hardwareRevision.isNotBlank() &&
        firmwareVersion.isNotBlank()
}

private val DeviceConnectionState.latestRuntimeProofAtMillis: Long?
    get() = listOfNotNull(
        lastControlProofAtMillis,
        lastRuntimeMessageAtMillis
    ).maxOrNull()

private fun DeviceProduct.firmwareIdentityValues(): List<String> = listOf(
    brand,
    productId,
    productKey,
    family.wireValue,
    line,
    model,
    displayName,
    skuCode,
    hardwareRevision
).map(String::normalized)

private fun DeviceCapabilities.canonicalValues(): List<String> = listOf(
    light,
    manualLight,
    lightProgram,
    lightPresets,
    lightSimulation,
    fan,
    cooling,
    temperature,
    standaloneTimer,
    dosing,
    timeSync,
    ota
).map(Boolean::canonicalValue)

private fun DeviceLimits.canonicalValues(): List<String> = listOf(
    lightChannelCount,
    fanOutputCount,
    temperatureSensorCount,
    timerChannelCount,
    dosingChannelCount
).map(Int::toString)

private fun List<String>.normalizedValues(): List<String> {
    return asSequence()
        .map(String::normalized)
        .filter(String::isNotBlank)
        .distinct()
        .sorted()
        .toList()
}

private fun String.normalized(): String = trim()

private fun Boolean.canonicalValue(): String = if (this) "1" else "0"

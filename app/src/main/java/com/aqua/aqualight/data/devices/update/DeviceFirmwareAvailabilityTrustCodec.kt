package com.aqua.aqualight.data.devices.update

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.aqua.aqualight.data.user.UserDataScope
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import java.util.UUID

internal data class DeviceFirmwareAvailabilityTrustRecord(
    val deviceUid: String,
    val snapshotFingerprint: String,
    val validatedAtMillis: Long
)

internal object DeviceFirmwareAvailabilityTrustCodec {

    fun ownerKey(ownerUid: String): Preferences.Key<Set<String>> {
        val owner = normalizeOwnerUid(ownerUid)
        val ownerId = UUID.nameUUIDFromBytes(owner.toByteArray(StandardCharsets.UTF_8))
        return stringSetPreferencesKey(OWNER_KEY_PREFIX + ownerId)
    }

    fun encode(record: DeviceFirmwareAvailabilityTrustRecord): String {
        val deviceUid = normalizeDeviceUid(record.deviceUid)
        val fingerprint = normalizeFingerprint(record.snapshotFingerprint)
        require(record.validatedAtMillis > 0L) {
            "validatedAtMillis must be positive"
        }
        return listOf(
            encodePart(deviceUid),
            fingerprint,
            record.validatedAtMillis.toString()
        ).joinToString(RECORD_SEPARATOR)
    }

    fun decode(value: String): DeviceFirmwareAvailabilityTrustRecord? {
        val parts = value.split(RECORD_SEPARATOR, limit = RECORD_FIELD_COUNT)
        if (parts.size != RECORD_FIELD_COUNT || parts.any(String::isBlank)) {
            return null
        }
        return runCatching {
            DeviceFirmwareAvailabilityTrustRecord(
                deviceUid = normalizeDeviceUid(decodePart(parts[0])),
                snapshotFingerprint = normalizeFingerprint(parts[1]),
                validatedAtMillis = parts[2].toLong().also { timestamp ->
                    require(timestamp > 0L)
                }
            )
        }.getOrNull()
    }

    fun normalizeOwnerUid(ownerUid: String): String {
        return UserDataScope.normalizeOwnerUid(ownerUid).also { normalized ->
            require(normalized.isNotBlank()) { "ownerUid must not be blank" }
        }
    }

    fun normalizeDeviceUid(deviceUid: String): String {
        return deviceUid.trim().also { normalized ->
            require(normalized.isNotBlank()) { "deviceUid must not be blank" }
        }
    }

    private fun normalizeFingerprint(fingerprint: String): String {
        return fingerprint.trim().lowercase(Locale.ROOT).also { normalized ->
            require(FINGERPRINT_PATTERN.matches(normalized)) {
                "snapshotFingerprint must be a SHA-256 hex value"
            }
        }
    }

    private fun encodePart(value: String): String {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun decodePart(value: String): String {
        return String(
            Base64.getUrlDecoder().decode(value),
            StandardCharsets.UTF_8
        )
    }

    private const val OWNER_KEY_PREFIX = "owner_"
    private const val RECORD_SEPARATOR = "\u001F"
    private const val RECORD_FIELD_COUNT = 3
    private val FINGERPRINT_PATTERN = Regex("[0-9a-f]{64}")
}

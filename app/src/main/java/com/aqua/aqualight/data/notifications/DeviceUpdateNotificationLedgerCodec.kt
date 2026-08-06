package com.aqua.aqualight.data.notifications

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.aqua.aqualight.data.user.UserDataScope
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

internal data class DeviceUpdateNotificationRecord(
    val deviceUid: String,
    val targetVersion: String
)

internal object DeviceUpdateNotificationLedgerCodec {

    fun ownerKey(ownerUid: String): Preferences.Key<Set<String>> {
        val owner = normalizeOwnerUid(ownerUid)
        val ownerId = UUID.nameUUIDFromBytes(owner.toByteArray(StandardCharsets.UTF_8))
        return stringSetPreferencesKey(OWNER_KEY_PREFIX + ownerId)
    }

    fun encode(record: DeviceUpdateNotificationRecord): String {
        val deviceUid = normalizeDeviceUid(record.deviceUid)
        val targetVersion = normalizeTargetVersion(record.targetVersion)
        val encodedDevice = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(deviceUid.toByteArray(StandardCharsets.UTF_8))
        return encodedDevice + RECORD_SEPARATOR + targetVersion
    }

    fun decode(value: String): DeviceUpdateNotificationRecord? {
        val parts = value.split(RECORD_SEPARATOR, limit = 2)
        if (parts.size != 2 || parts.any(String::isBlank)) return null
        return runCatching {
            val deviceUid = String(
                Base64.getUrlDecoder().decode(parts[0]),
                StandardCharsets.UTF_8
            )
            DeviceUpdateNotificationRecord(
                deviceUid = normalizeDeviceUid(deviceUid),
                targetVersion = normalizeTargetVersion(parts[1])
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

    fun normalizeTargetVersion(targetVersion: String): String {
        return targetVersion.trim().also { normalized ->
            require(normalized.isNotBlank()) { "targetVersion must not be blank" }
        }
    }

    private const val OWNER_KEY_PREFIX = "owner_"
    private const val RECORD_SEPARATOR = "\u001F"
}

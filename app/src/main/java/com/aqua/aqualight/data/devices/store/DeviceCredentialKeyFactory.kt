package com.aqua.aqualight.data.devices.store

import com.aqua.aqualight.data.devices.model.DeviceUid
import java.security.MessageDigest
import java.util.Locale

object DeviceCredentialKeyFactory {

    fun tokenKey(
        ownerUid: String,
        deviceUid: DeviceUid
    ): String {
        return buildString {
            append(ownerPrefix(ownerUid))
            append(TOKEN_SEGMENT)
            append(sha256(deviceUid.normalizedValue()))
        }
    }

    fun pendingTokenKey(
        ownerUid: String,
        deviceUid: DeviceUid
    ): String {
        return buildString {
            append(pendingTokenPrefix(ownerUid))
            append(sha256(deviceUid.normalizedValue()))
        }
    }

    fun pendingTokenPrefix(
        ownerUid: String
    ): String {
        return ownerPrefix(ownerUid) + PENDING_TOKEN_SEGMENT
    }

    fun ownerPrefix(
        ownerUid: String
    ): String {
        val normalizedOwnerUid = ownerUid.trim()
        require(normalizedOwnerUid.isNotBlank()) {
            "ownerUid must not be blank"
        }

        return "$OWNER_SEGMENT${sha256(normalizedOwnerUid)}_"
    }

    private fun DeviceUid.normalizedValue(): String {
        return value.trim()
            .uppercase(Locale.US)
            .also { normalized ->
                require(normalized.isNotBlank()) {
                    "deviceUid must not be blank"
                }
            }
    }

    private fun sha256(
        value: String
    ): String {
        val digest = MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))

        return digest.joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }

    private const val OWNER_SEGMENT = "owner_"
    private const val TOKEN_SEGMENT = "ws_token_"
    private const val PENDING_TOKEN_SEGMENT = "ws_token_pending_"
}

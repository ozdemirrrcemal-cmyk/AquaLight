package com.aqua.aqualight.data.devices.store

import java.security.MessageDigest
import java.util.Locale

internal object DeviceCredentialKeyFactory {

    fun key(
        ownerUid: String,
        deviceUid: String
    ): String {
        return buildString {
            append(ownerPrefix(ownerUid))
            append(sha256(deviceUid.trim().uppercase(Locale.US)))
        }
    }

    fun ownerPrefix(
        ownerUid: String
    ): String {
        return buildString {
            append(KEY_PREFIX)
            append(sha256(ownerUid.trim()))
            append(KEY_PART_SEPARATOR)
        }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))

        return digest.joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }

    private const val KEY_PREFIX = "ws_token_"
    private const val KEY_PART_SEPARATOR = "_"
}

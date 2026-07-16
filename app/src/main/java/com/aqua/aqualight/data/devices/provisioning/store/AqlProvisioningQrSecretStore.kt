package com.aqua.aqualight.data.devices.provisioning.store

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aqua.aqualight.data.user.UserDataScope
import java.util.UUID
import org.json.JSONObject

/** Short-lived encrypted owner-scoped storage for QR claim material. */
class AqlProvisioningQrSecretStore(
    context: Context,
    private val ownerUidProvider: () -> String = UserDataScope::requireCurrentUid,
    private val clock: () -> Long = System::currentTimeMillis
) : ProvisioningQrSecretStorage {

    private val preferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        FILE_NAME,
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun create(
        claimCode: String,
        rawPayload: String,
        createdAtMillis: Long
    ): String {
        val ownerUid = requireOwnerUid()
        val reference = UUID.randomUUID().toString()
        val payload = JSONObject()
            .put(FIELD_OWNER_UID, ownerUid)
            .put(FIELD_CLAIM_CODE, claimCode)
            .put(FIELD_RAW_PAYLOAD, rawPayload)
            .put(FIELD_CREATED_AT, createdAtMillis)
            .put(FIELD_EXPIRES_AT, createdAtMillis + SECRET_TTL_MILLIS)
            .toString()

        synchronized(LOCK) {
            preferences.edit()
                .putString(key(reference), payload)
                .commitOrThrow()
            trimLocked(ownerUid)
        }
        return reference
    }

    override fun get(reference: String): ProvisioningQrSecret? {
        if (reference.isBlank()) return null
        val ownerUid = requireOwnerUid()
        return synchronized(LOCK) {
            val storedKey = key(reference)
            val payload = preferences.getString(storedKey, null) ?: return@synchronized null
            val decoded = runCatching { decode(payload) }.getOrNull()
            when {
                decoded == null -> {
                    preferences.edit().remove(storedKey).commitOrThrow()
                    null
                }
                decoded.expiresAtMillis <= clock() -> {
                    preferences.edit().remove(storedKey).commitOrThrow()
                    null
                }
                decoded.ownerUid != ownerUid -> null
                else -> ProvisioningQrSecret(
                    claimCode = decoded.claimCode,
                    rawPayload = decoded.rawPayload
                )
            }
        }
    }

    override fun remove(reference: String) {
        if (reference.isBlank()) return
        val ownerUid = requireOwnerUid()
        synchronized(LOCK) {
            val storedKey = key(reference)
            val payload = preferences.getString(storedKey, null) ?: return
            val decoded = runCatching { decode(payload) }.getOrNull()
            if (decoded == null || decoded.ownerUid == ownerUid) {
                preferences.edit().remove(storedKey).commitOrThrow()
            }
        }
    }

    override fun clearOwner() {
        val ownerUid = requireOwnerUid()
        synchronized(LOCK) {
            val editor = preferences.edit()
            entriesLocked().forEach { (storedKey, payload) ->
                val decoded = runCatching { decode(payload) }.getOrNull()
                if (decoded == null || decoded.ownerUid == ownerUid) {
                    editor.remove(storedKey)
                }
            }
            editor.commitOrThrow()
        }
    }

    private fun trimLocked(ownerUid: String) {
        val records = entriesLocked().mapNotNull { (storedKey, payload) ->
            val decoded = runCatching { decode(payload) }.getOrNull()
            if (decoded == null || decoded.expiresAtMillis <= clock()) {
                preferences.edit().remove(storedKey).commitOrThrow()
                null
            } else {
                Triple(storedKey, decoded.ownerUid, decoded.createdAtMillis)
            }
        }
        records
            .filter { (_, recordOwnerUid, _) -> recordOwnerUid == ownerUid }
            .sortedBy { (_, _, createdAtMillis) -> createdAtMillis }
            .dropLast(MAX_SECRET_COUNT)
            .forEach { (storedKey, _, _) ->
                preferences.edit().remove(storedKey).commitOrThrow()
            }
    }

    private fun entriesLocked(): List<Pair<String, String>> =
        preferences.all.mapNotNull { (storedKey, value) ->
            if (!storedKey.startsWith(KEY_PREFIX)) return@mapNotNull null
            val payload = value as? String ?: return@mapNotNull null
            storedKey to payload
        }

    private fun decode(payload: String): StoredSecret {
        val json = JSONObject(payload)
        return StoredSecret(
            ownerUid = json.getString(FIELD_OWNER_UID),
            claimCode = json.optString(FIELD_CLAIM_CODE),
            rawPayload = json.optString(FIELD_RAW_PAYLOAD),
            createdAtMillis = json.getLong(FIELD_CREATED_AT),
            expiresAtMillis = json.getLong(FIELD_EXPIRES_AT)
        )
    }

    private fun requireOwnerUid(): String = ownerUidProvider().trim().also { ownerUid ->
        require(ownerUid.isNotBlank()) { "Provisioning QR secret owner is unavailable." }
    }

    private fun key(reference: String): String = "$KEY_PREFIX$reference"

    private fun android.content.SharedPreferences.Editor.commitOrThrow() {
        check(commit()) { "Encrypted provisioning QR secret storage write failed." }
    }

    private data class StoredSecret(
        val ownerUid: String,
        val claimCode: String,
        val rawPayload: String,
        val createdAtMillis: Long,
        val expiresAtMillis: Long
    )

    private companion object {
        val LOCK = Any()
        const val FILE_NAME = "aql_provisioning_qr_secrets"
        const val KEY_PREFIX = "secret."
        const val MAX_SECRET_COUNT = 8
        const val SECRET_TTL_MILLIS = 15 * 60 * 1000L
        const val FIELD_OWNER_UID = "owner_uid"
        const val FIELD_CLAIM_CODE = "claim_code"
        const val FIELD_RAW_PAYLOAD = "raw_payload"
        const val FIELD_CREATED_AT = "created_at"
        const val FIELD_EXPIRES_AT = "expires_at"
    }
}

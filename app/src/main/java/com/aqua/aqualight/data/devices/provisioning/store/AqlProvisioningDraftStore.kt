package com.aqua.aqualight.data.devices.provisioning.store

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningDraft
import com.aqua.aqualight.data.devices.provisioning.model.AqlWifiCredentials
import com.aqua.aqualight.data.user.UserDataScope
import java.util.UUID
import org.json.JSONObject

/**
 * Short-lived encrypted provisioning session storage.
 *
 * Credentials must survive Android process recreation, but must never be stored
 * as plaintext or become visible to another authenticated owner. Every record
 * is encrypted with an Android Keystore-backed master key, owner-scoped and
 * automatically expires.
 */
class AqlProvisioningDraftStore(
    context: Context,
    private val ownerUidProvider: () -> String = UserDataScope::requireCurrentUid,
    private val clock: () -> Long = System::currentTimeMillis
) : ProvisioningDraftStorage {

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
        candidateId: String,
        bleAddress: String,
        bleName: String,
        claimCode: String,
        rawQrPayload: String,
        deviceTitle: String,
        deviceSerial: String,
        deviceModel: String,
        wifiCredentials: AqlWifiCredentials,
        createdAtMillis: Long
    ): AqlProvisioningDraft {
        val ownerUid = requireOwnerUid()
        val draft = AqlProvisioningDraft(
            sessionId = UUID.randomUUID().toString(),
            candidateId = candidateId,
            bleAddress = bleAddress,
            bleName = bleName,
            claimCode = claimCode,
            rawQrPayload = rawQrPayload,
            deviceTitle = deviceTitle,
            deviceSerial = deviceSerial,
            deviceModel = deviceModel,
            wifiCredentials = wifiCredentials,
            createdAtMillis = createdAtMillis
        )

        synchronized(LOCK) {
            preferences.edit()
                .putString(
                    key(draft.sessionId),
                    encode(
                        ownerUid = ownerUid,
                        draft = draft,
                        expiresAtMillis = createdAtMillis + SESSION_TTL_MILLIS
                    )
                )
                .commitOrThrow()
            trimLocked()
        }
        return draft
    }

    override fun get(sessionId: String): AqlProvisioningDraft? {
        if (sessionId.isBlank()) return null
        val ownerUid = requireOwnerUid()
        return synchronized(LOCK) {
            val storedKey = key(sessionId)
            val record = preferences.getString(storedKey, null) ?: return@synchronized null
            val decoded = runCatching { decode(record) }.getOrNull()
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
                else -> decoded.draft
            }
        }
    }

    override fun remove(sessionId: String) {
        if (sessionId.isBlank()) return
        val ownerUid = requireOwnerUid()
        synchronized(LOCK) {
            val storedKey = key(sessionId)
            val stored = preferences.getString(storedKey, null) ?: return
            val decoded = runCatching { decode(stored) }.getOrNull()
            if (decoded == null || decoded.ownerUid == ownerUid) {
                preferences.edit().remove(storedKey).commitOrThrow()
            }
        }
    }

    override fun clearOwner() {
        val ownerUid = requireOwnerUid()
        synchronized(LOCK) {
            val editor = preferences.edit()
            draftEntriesLocked().forEach { (storedKey, record) ->
                val decoded = runCatching { decode(record) }.getOrNull()
                if (decoded == null || decoded.ownerUid == ownerUid) {
                    editor.remove(storedKey)
                }
            }
            editor.commitOrThrow()
        }
    }

    private fun trimLocked() {
        val records = draftEntriesLocked().mapNotNull { (storedKey, record) ->
            val decoded = runCatching { decode(record) }.getOrNull()
            if (decoded == null || decoded.expiresAtMillis <= clock()) {
                preferences.edit().remove(storedKey).commitOrThrow()
                null
            } else {
                Triple(storedKey, decoded.ownerUid, decoded.draft.createdAtMillis)
            }
        }
        val ownerUid = requireOwnerUid()
        records
            .filter { (_, recordOwnerUid, _) -> recordOwnerUid == ownerUid }
            .sortedBy { (_, _, createdAtMillis) -> createdAtMillis }
            .dropLast(MAX_DRAFT_COUNT)
            .forEach { (storedKey, _, _) ->
                preferences.edit().remove(storedKey).commitOrThrow()
            }
    }

    private fun draftEntriesLocked(): List<Pair<String, String>> =
        preferences.all.mapNotNull { (storedKey, value) ->
            if (!storedKey.startsWith(KEY_PREFIX)) return@mapNotNull null
            val record = value as? String ?: return@mapNotNull null
            storedKey to record
        }

    private fun encode(
        ownerUid: String,
        draft: AqlProvisioningDraft,
        expiresAtMillis: Long
    ): String = JSONObject()
        .put(FIELD_OWNER_UID, ownerUid)
        .put(FIELD_EXPIRES_AT, expiresAtMillis)
        .put(FIELD_SESSION_ID, draft.sessionId)
        .put(FIELD_CANDIDATE_ID, draft.candidateId)
        .put(FIELD_BLE_ADDRESS, draft.bleAddress)
        .put(FIELD_BLE_NAME, draft.bleName)
        .put(FIELD_CLAIM_CODE, draft.claimCode)
        .put(FIELD_RAW_QR, draft.rawQrPayload)
        .put(FIELD_DEVICE_TITLE, draft.deviceTitle)
        .put(FIELD_DEVICE_SERIAL, draft.deviceSerial)
        .put(FIELD_DEVICE_MODEL, draft.deviceModel)
        .put(FIELD_WIFI_SSID, draft.wifiCredentials.ssid)
        .put(FIELD_WIFI_PASSWORD, draft.wifiCredentials.password)
        .put(FIELD_TIMEZONE, draft.wifiCredentials.timezone)
        .put(FIELD_UTC_OFFSET, draft.wifiCredentials.utcOffsetMinutes)
        .put(FIELD_CREATED_AT, draft.createdAtMillis)
        .toString()

    private fun decode(record: String): StoredDraft {
        val json = JSONObject(record)
        return StoredDraft(
            ownerUid = json.getString(FIELD_OWNER_UID),
            expiresAtMillis = json.getLong(FIELD_EXPIRES_AT),
            draft = AqlProvisioningDraft(
                sessionId = json.getString(FIELD_SESSION_ID),
                candidateId = json.getString(FIELD_CANDIDATE_ID),
                bleAddress = json.optString(FIELD_BLE_ADDRESS),
                bleName = json.optString(FIELD_BLE_NAME),
                claimCode = json.optString(FIELD_CLAIM_CODE),
                rawQrPayload = json.optString(FIELD_RAW_QR),
                deviceTitle = json.optString(FIELD_DEVICE_TITLE),
                deviceSerial = json.optString(FIELD_DEVICE_SERIAL),
                deviceModel = json.optString(FIELD_DEVICE_MODEL),
                wifiCredentials = AqlWifiCredentials(
                    ssid = json.getString(FIELD_WIFI_SSID),
                    password = json.getString(FIELD_WIFI_PASSWORD),
                    timezone = json.optString(FIELD_TIMEZONE),
                    utcOffsetMinutes = json.optInt(FIELD_UTC_OFFSET)
                ),
                createdAtMillis = json.getLong(FIELD_CREATED_AT)
            )
        )
    }

    private fun requireOwnerUid(): String = ownerUidProvider().trim().also { ownerUid ->
        require(ownerUid.isNotBlank()) { "Provisioning session owner is unavailable." }
    }

    private fun key(sessionId: String): String = "$KEY_PREFIX$sessionId"

    private fun android.content.SharedPreferences.Editor.commitOrThrow() {
        check(commit()) { "Encrypted provisioning session storage write failed." }
    }

    private data class StoredDraft(
        val ownerUid: String,
        val expiresAtMillis: Long,
        val draft: AqlProvisioningDraft
    )

    private companion object {
        val LOCK = Any()
        const val FILE_NAME = "aql_provisioning_sessions"
        const val KEY_PREFIX = "draft."
        const val MAX_DRAFT_COUNT = 8
        const val SESSION_TTL_MILLIS = 15 * 60 * 1000L

        const val FIELD_OWNER_UID = "owner_uid"
        const val FIELD_EXPIRES_AT = "expires_at"
        const val FIELD_SESSION_ID = "session_id"
        const val FIELD_CANDIDATE_ID = "candidate_id"
        const val FIELD_BLE_ADDRESS = "ble_address"
        const val FIELD_BLE_NAME = "ble_name"
        const val FIELD_CLAIM_CODE = "claim_code"
        const val FIELD_RAW_QR = "raw_qr"
        const val FIELD_DEVICE_TITLE = "device_title"
        const val FIELD_DEVICE_SERIAL = "device_serial"
        const val FIELD_DEVICE_MODEL = "device_model"
        const val FIELD_WIFI_SSID = "wifi_ssid"
        const val FIELD_WIFI_PASSWORD = "wifi_password"
        const val FIELD_TIMEZONE = "timezone"
        const val FIELD_UTC_OFFSET = "utc_offset"
        const val FIELD_CREATED_AT = "created_at"
    }
}

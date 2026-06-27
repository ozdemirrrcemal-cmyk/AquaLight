package com.aqua.aqualight.data.devices.provisioning.store

import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningDraft
import com.aqua.aqualight.data.devices.provisioning.model.AqlWifiCredentials
import java.util.UUID

object AqlProvisioningDraftStore {

    private const val MAX_DRAFT_COUNT = 8

    private val lock = Any()
    private val drafts = linkedMapOf<String, AqlProvisioningDraft>()

    fun create(
        candidateId: String,
        bleAddress: String,
        bleName: String = "",
        claimCode: String = "",
        rawQrPayload: String = "",
        deviceTitle: String,
        deviceSerial: String,
        deviceModel: String,
        wifiCredentials: AqlWifiCredentials,
        createdAtMillis: Long = System.currentTimeMillis()
    ): AqlProvisioningDraft {
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

        synchronized(lock) {
            drafts[draft.sessionId] = draft
            trimLocked()
        }

        return draft
    }

    fun get(sessionId: String): AqlProvisioningDraft? {
        return synchronized(lock) {
            drafts[sessionId]
        }
    }

    fun remove(sessionId: String) {
        synchronized(lock) {
            drafts.remove(sessionId)
        }
    }

    fun clear() {
        synchronized(lock) {
            drafts.clear()
        }
    }

    private fun trimLocked() {
        while (drafts.size > MAX_DRAFT_COUNT) {
            val firstKey = drafts.keys.firstOrNull() ?: return
            drafts.remove(firstKey)
        }
    }
}

package com.aqua.aqualight.data.devices.provisioning.store

interface ProvisioningQrSecretStorage {
    fun create(
        claimCode: String,
        rawPayload: String,
        createdAtMillis: Long = System.currentTimeMillis()
    ): String

    fun get(reference: String): ProvisioningQrSecret?

    fun remove(reference: String)

    fun clearOwner()
}

data class ProvisioningQrSecret(
    val claimCode: String,
    val rawPayload: String
)

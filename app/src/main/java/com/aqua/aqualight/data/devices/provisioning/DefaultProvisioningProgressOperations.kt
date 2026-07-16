package com.aqua.aqualight.data.devices.provisioning

import android.content.Context
import com.aqua.aqualight.application.devices.provisioning.PreparedProvisioningRegistration
import com.aqua.aqualight.application.devices.provisioning.ProvisionedDevice
import com.aqua.aqualight.application.devices.provisioning.ProvisioningProgressOperations
import com.aqua.aqualight.application.devices.provisioning.ProvisioningRuntimeHandoff
import com.aqua.aqualight.application.devices.provisioning.ProvisioningSessionSnapshot
import com.aqua.aqualight.application.devices.provisioning.ProvisioningTransportEvent
import com.aqua.aqualight.application.devices.provisioning.ProvisioningVerifiedDeviceInfo
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningAddressResolver
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningGattClient
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningGattEvent
import com.aqua.aqualight.data.devices.provisioning.repository.AqlProvisioningHandoffSaver
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningDraftStore
import com.aqua.aqualight.data.devices.provisioning.store.ProvisioningCommitRecoveryStore
import com.aqua.aqualight.data.devices.provisioning.store.ProvisioningDraftStorage
import com.aqua.aqualight.data.devices.toOwnerDeviceFamily
import com.aqua.aqualight.data.user.UserDataScope
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultProvisioningProgressOperations internal constructor(
    context: Context,
    override val ownerUid: String,
    private val draftStore: ProvisioningDraftStorage
) : ProvisioningProgressOperations {

    private val appContext = context.applicationContext
    private val addressResolver = AqlBleProvisioningAddressResolver(appContext)
    private val gattClient = AqlBleProvisioningGattClient(appContext)
    private val handoffSaver = AqlProvisioningHandoffSaver(appContext)
    private val commitRecoveryStore = ProvisioningCommitRecoveryStore(appContext)
    private val preparedSnapshots = ConcurrentHashMap<String, DeviceSnapshot>()
    private val preparedRuntimeTokens = ConcurrentHashMap<String, String>()

    private constructor(
        context: Context,
        ownerScope: OwnerProvisioningScope
    ) : this(
        context = context.applicationContext,
        ownerUid = ownerScope.ownerUid,
        draftStore = ownerScope.draftStore
    )

    constructor(context: Context) : this(
        context = context.applicationContext,
        ownerScope = OwnerProvisioningScope.create(context.applicationContext)
    )

    override val events: Flow<ProvisioningTransportEvent> =
        gattClient.events.map(AqlBleProvisioningGattEvent::toApplicationEvent)

    override fun getSession(sessionId: String): ProvisioningSessionSnapshot? =
        draftStore.get(sessionId)?.toApplicationSession()

    override fun removeSession(sessionId: String) {
        draftStore.remove(sessionId)
    }

    override suspend fun resolveBleAddress(sessionId: String): Result<String> {
        val draft = draftStore.get(sessionId)
            ?: return Result.failure(IllegalStateException("Provisioning session is unavailable."))
        return addressResolver.resolveQrAddress(draft)
    }

    override fun startTransport(
        sessionId: String,
        bleAddress: String
    ): Result<Unit> = runCatching {
        val draft = requireNotNull(draftStore.get(sessionId)) {
            "Provisioning session is unavailable."
        }
        val address = bleAddress.trim()
        require(address.isNotBlank()) { "Provisioning BLE address is unavailable." }
        gattClient.start(draft.copy(bleAddress = address))
    }

    override fun finalizeSetup(
        handoff: ProvisioningRuntimeHandoff
    ): Result<Unit> = runCatching {
        gattClient.finalizeSetup(handoff.toDataHandoff())
    }

    override fun closeTransport() {
        gattClient.close()
    }

    override suspend fun prepareRegistration(
        sessionId: String,
        verifiedDeviceInfo: ProvisioningVerifiedDeviceInfo?,
        handoff: ProvisioningRuntimeHandoff
    ): Result<PreparedProvisioningRegistration> {
        val draft = draftStore.get(sessionId)
            ?: return Result.failure(IllegalStateException("Provisioning session is unavailable."))
        return UserDataScope.withOwnerUid(ownerUid) {
            handoffSaver.prepareAndConnect(
                draft = draft.withVerifiedInfo(verifiedDeviceInfo),
                handoff = handoff.toDataHandoff()
            )
        }.map { snapshot ->
            val registrationId = UUID.randomUUID().toString()
            preparedSnapshots[registrationId] = snapshot
            preparedRuntimeTokens[registrationId] = handoff.webSocketToken
            PreparedProvisioningRegistration(
                registrationId = registrationId,
                device = ProvisionedDevice(
                    deviceUid = snapshot.deviceUid.value,
                    title = snapshot.title,
                    family = snapshot.product.family.toOwnerDeviceFamily()
                )
            )
        }
    }

    override suspend fun commitPreparedRegistration(
        registration: PreparedProvisioningRegistration
    ): Result<Unit> {
        val snapshot = preparedSnapshots[registration.registrationId]
            ?: return Result.failure(
                IllegalStateException("Prepared provisioning registration is unavailable.")
            )
        val runtimeToken = preparedRuntimeTokens[registration.registrationId]
            ?: return Result.failure(
                IllegalStateException("Prepared provisioning runtime token is unavailable.")
            )
        if (snapshot.deviceUid.value != registration.device.deviceUid) {
            return Result.failure(
                IllegalStateException("Prepared provisioning registration identity changed.")
            )
        }

        try {
            commitRecoveryStore.record(
                ownerUid = ownerUid,
                snapshot = snapshot,
                runtimeToken = runtimeToken
            )
        } catch (error: Throwable) {
            return Result.failure(error)
        }

        return UserDataScope.withOwnerUid(ownerUid) {
            handoffSaver.commitPreparedRegistration(snapshot)
        }.map { Unit }
            .onSuccess {
                preparedSnapshots.remove(registration.registrationId)
                preparedRuntimeTokens.remove(registration.registrationId)
                clearCommitRecoveryRecord(snapshot.deviceUid)
            }
    }

    override suspend fun rollbackProvisioningRegistration(
        deviceUid: String
    ): Result<Unit> {
        val normalizedDeviceUid = DeviceUid(deviceUid)
        removePreparedSnapshot(deviceUid)
        return UserDataScope.withOwnerUid(ownerUid) {
            handoffSaver.rollbackProvisioningRegistration(normalizedDeviceUid)
        }.onSuccess {
            clearCommitRecoveryRecord(normalizedDeviceUid)
        }
    }

    override suspend fun rollbackProvisioningRegistrationForOwner(
        ownerUid: String,
        deviceUid: String
    ): Result<Unit> {
        require(ownerUid == this.ownerUid) {
            "Provisioning rollback owner does not match the captured transaction owner."
        }
        val normalizedDeviceUid = DeviceUid(deviceUid)
        removePreparedSnapshot(deviceUid)
        return UserDataScope.withOwnerUid(this.ownerUid) {
            handoffSaver.rollbackProvisioningRegistrationForOwner(
                ownerUid = this@DefaultProvisioningProgressOperations.ownerUid,
                deviceUid = normalizedDeviceUid
            )
        }.onSuccess {
            clearCommitRecoveryRecord(normalizedDeviceUid)
        }
    }

    private fun removePreparedSnapshot(deviceUid: String) {
        preparedSnapshots.entries
            .filter { (_, snapshot) -> snapshot.deviceUid.value == deviceUid }
            .forEach { (registrationId, _) ->
                preparedSnapshots.remove(registrationId)
                preparedRuntimeTokens.remove(registrationId)
            }
    }

    private suspend fun clearCommitRecoveryRecord(deviceUid: DeviceUid) {
        try {
            commitRecoveryStore.clear(ownerUid, deviceUid)
        } catch (error: Throwable) {
            error.printStackTrace()
        }
    }

    private data class OwnerProvisioningScope(
        val ownerUid: String,
        val draftStore: ProvisioningDraftStorage
    ) {
        companion object {
            fun create(context: Context): OwnerProvisioningScope {
                val ownerUid = UserDataScope.requireCurrentUid()
                return OwnerProvisioningScope(
                    ownerUid = ownerUid,
                    draftStore = AqlProvisioningDraftStore(
                        context = context.applicationContext,
                        ownerUidProvider = { ownerUid }
                    )
                )
            }
        }
    }
}

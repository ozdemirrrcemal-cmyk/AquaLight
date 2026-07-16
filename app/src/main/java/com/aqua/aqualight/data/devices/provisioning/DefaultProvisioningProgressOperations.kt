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
import com.aqua.aqualight.data.devices.toOwnerDeviceFamily
import com.aqua.aqualight.data.user.UserDataScope
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultProvisioningProgressOperations(
    context: Context
) : ProvisioningProgressOperations {

    private val appContext = context.applicationContext
    private val addressResolver = AqlBleProvisioningAddressResolver(appContext)
    private val gattClient = AqlBleProvisioningGattClient(appContext)
    private val handoffSaver = AqlProvisioningHandoffSaver(appContext)
    private val preparedSnapshots = ConcurrentHashMap<String, DeviceSnapshot>()

    override val ownerUid: String = UserDataScope.requireCurrentUid()
    override val events: Flow<ProvisioningTransportEvent> =
        gattClient.events.map(AqlBleProvisioningGattEvent::toApplicationEvent)

    override fun getSession(sessionId: String): ProvisioningSessionSnapshot? =
        AqlProvisioningDraftStore.get(sessionId)?.toApplicationSession()

    override fun removeSession(sessionId: String) {
        AqlProvisioningDraftStore.remove(sessionId)
    }

    override suspend fun resolveBleAddress(sessionId: String): Result<String> {
        val draft = AqlProvisioningDraftStore.get(sessionId)
            ?: return Result.failure(IllegalStateException("Provisioning session is unavailable."))
        return addressResolver.resolveQrAddress(draft)
    }

    override fun startTransport(
        sessionId: String,
        bleAddress: String
    ): Result<Unit> = runCatching {
        val draft = requireNotNull(AqlProvisioningDraftStore.get(sessionId)) {
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
        val draft = AqlProvisioningDraftStore.get(sessionId)
            ?: return Result.failure(IllegalStateException("Provisioning session is unavailable."))
        return handoffSaver.prepareAndConnect(
            draft = draft.withVerifiedInfo(verifiedDeviceInfo),
            handoff = handoff.toDataHandoff()
        ).map { snapshot ->
            val registrationId = UUID.randomUUID().toString()
            preparedSnapshots[registrationId] = snapshot
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
        if (snapshot.deviceUid.value != registration.device.deviceUid) {
            return Result.failure(
                IllegalStateException("Prepared provisioning registration identity changed.")
            )
        }
        return handoffSaver.commitPreparedRegistration(snapshot)
            .map { Unit }
            .onSuccess { preparedSnapshots.remove(registration.registrationId) }
    }

    override suspend fun rollbackProvisioningRegistration(
        deviceUid: String
    ): Result<Unit> {
        removePreparedSnapshot(deviceUid)
        return handoffSaver.rollbackProvisioningRegistration(DeviceUid(deviceUid))
    }

    override suspend fun rollbackProvisioningRegistrationForOwner(
        ownerUid: String,
        deviceUid: String
    ): Result<Unit> {
        removePreparedSnapshot(deviceUid)
        return handoffSaver.rollbackProvisioningRegistrationForOwner(
            ownerUid = ownerUid,
            deviceUid = DeviceUid(deviceUid)
        )
    }

    private fun removePreparedSnapshot(deviceUid: String) {
        preparedSnapshots.entries.removeIf { (_, snapshot) ->
            snapshot.deviceUid.value == deviceUid
        }
    }
}

package com.aqua.aqualight.composition

import android.content.Context
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningAddressResolver
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningGattClient
import com.aqua.aqualight.data.devices.provisioning.ble.AqlBleProvisioningGattEvent
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningDraft
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningRuntimeHandoff
import com.aqua.aqualight.data.devices.provisioning.repository.AqlProvisioningHandoffSaver
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningDraftStore
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.ui.tabs.devices.add.DeviceProvisioningProgressOperations
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRoute
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRouteResolver
import kotlinx.coroutines.flow.Flow

internal class DefaultDeviceProvisioningProgressOperations(
    context: Context
) : DeviceProvisioningProgressOperations {

    private val appContext = context.applicationContext
    private val addressResolver = AqlBleProvisioningAddressResolver(appContext)
    private val gattClient = AqlBleProvisioningGattClient(appContext)
    private val handoffSaver = AqlProvisioningHandoffSaver(appContext)
    private val routeResolver = DeviceRouteResolver()

    override val ownerUid: String = UserDataScope.requireCurrentUid()
    override val gattEvents: Flow<AqlBleProvisioningGattEvent> = gattClient.events

    override fun getDraft(sessionId: String): AqlProvisioningDraft? =
        AqlProvisioningDraftStore.get(sessionId)

    override fun removeDraft(sessionId: String) {
        AqlProvisioningDraftStore.remove(sessionId)
    }

    override suspend fun resolveQrAddress(
        draft: AqlProvisioningDraft
    ): Result<String> = addressResolver.resolveQrAddress(draft)

    override fun startGatt(draft: AqlProvisioningDraft) {
        gattClient.start(draft)
    }

    override fun finalizeSetup(handoff: AqlProvisioningRuntimeHandoff) {
        gattClient.finalizeSetup(handoff)
    }

    override fun closeGatt() {
        gattClient.close()
    }

    override suspend fun prepareAndConnect(
        draft: AqlProvisioningDraft,
        handoff: AqlProvisioningRuntimeHandoff
    ): Result<DeviceSnapshot> = handoffSaver.prepareAndConnect(draft, handoff)

    override suspend fun commitPreparedRegistration(
        snapshot: DeviceSnapshot
    ): Result<DeviceSnapshot> = handoffSaver.commitPreparedRegistration(snapshot)

    override suspend fun rollbackProvisioningRegistration(
        deviceUid: DeviceUid
    ): Result<Unit> = handoffSaver.rollbackProvisioningRegistration(deviceUid)

    override suspend fun rollbackProvisioningRegistrationForOwner(
        ownerUid: String,
        deviceUid: DeviceUid
    ): Result<Unit> = handoffSaver.rollbackProvisioningRegistrationForOwner(
        ownerUid = ownerUid,
        deviceUid = deviceUid
    )

    override fun resolveRoute(
        snapshot: DeviceSnapshot,
        requestedDeviceUid: String
    ): DeviceRoute = routeResolver.resolve(
        snapshot = snapshot,
        requestedDeviceUid = requestedDeviceUid
    )
}

package com.aqua.aqualight.data.devices.dosing

import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceMenuAccessOperations
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelDestinationPolicy
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationTarget
import com.aqua.aqualight.data.devices.menu.DefaultDeviceMenuAccessOperations
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingChannelStatus
import com.aqua.aqualight.data.devices.toDeviceRootSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/** Projects central channel-scoped Dosing runtime state into authorized navigation targets. */
internal class DefaultDeviceDosingChannelNavigationOperations(
    private val runtimePort: DeviceDosingChannelNavigationRuntimePort
) : DeviceDosingChannelNavigationOperations {

    constructor(devicesRepository: DevicesRepository) : this(
        RepositoryDeviceDosingChannelNavigationRuntimePort(
            devicesRepository = devicesRepository,
            menuAccessOperations = DefaultDeviceMenuAccessOperations.create(devicesRepository)
        )
    )

    override suspend fun resolve(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelNavigationTarget? = navigationRequest(deviceUid, slotId)
        ?.let(::navigationContext)
        ?.let { context -> requestTarget(context) }

    override fun observeTargets(deviceUid: String): Flow<List<DeviceDosingChannelNavigationTarget>> {
        val uid = deviceUid.trim().takeIf(String::isNotBlank)?.let(::DeviceUid)
            ?: return flowOf(emptyList())
        return runtimePort.observeStatuses(uid)
            .map { statuses -> statuses.toNavigationTargets(uid) }
            .distinctUntilChanged()
    }

    override suspend fun refreshTargets(deviceUid: String): Boolean {
        val uid = deviceUid.trim().takeIf(String::isNotBlank)?.let(::DeviceUid) ?: return false
        val root = runtimePort.currentRootSnapshot(uid)
            ?.takeIf { it.catalogState == DeviceRootCatalogState.VALID && it.family == OwnerDeviceFamily.DOSING }
            ?: return false
        return root.channelSlots.dosingChannels.all { slot ->
            refreshOne(uid, slot.wireKey.value)
        }
    }

    override suspend fun resolveCurrent(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelNavigationTarget? = navigationRequest(deviceUid, slotId)
        ?.let(::navigationContext)
        ?.let { context ->
            runtimePort.currentStatus(context.uid, context.slot.wireKey.value)
                ?.toNavigationTarget(context)
                ?: requestTarget(context)
        }

    private fun navigationContext(request: DosingChannelNavigationRequest): DosingChannelNavigationContext? {
        val root = runtimePort.currentRootSnapshot(request.uid)
        val slot = root?.authorizedDosingSlot(request.slotId)
        return slot?.let { DosingChannelNavigationContext(request.uid, root, it) }
    }

    private suspend fun requestTarget(
        context: DosingChannelNavigationContext
    ): DeviceDosingChannelNavigationTarget? = when (
        val outcome = runtimePort.requestStatus(context.uid, context.slot.wireKey.value)
    ) {
        is DeviceRuntimeCommandOutcome.Success -> outcome.value.toNavigationTarget(context)
        is DeviceRuntimeCommandOutcome.NotConnected,
        is DeviceRuntimeCommandOutcome.NotAuthenticated,
        is DeviceRuntimeCommandOutcome.UnsupportedByDevice,
        is DeviceRuntimeCommandOutcome.SendFailed,
        is DeviceRuntimeCommandOutcome.Timeout,
        is DeviceRuntimeCommandOutcome.Cancelled -> retryAfterRuntimeRecovery(context)
        else -> null
    }

    private suspend fun retryAfterRuntimeRecovery(
        context: DosingChannelNavigationContext
    ): DeviceDosingChannelNavigationTarget? = if (runtimePort.prepareRuntime(context.uid)) {
        when (val retry = runtimePort.requestStatus(context.uid, context.slot.wireKey.value)) {
            is DeviceRuntimeCommandOutcome.Success -> retry.value.toNavigationTarget(context)
            else -> null
        }
    } else {
        null
    }

    private suspend fun refreshOne(uid: DeviceUid, channelKey: String): Boolean = when (
        runtimePort.requestStatus(uid, channelKey)
    ) {
        is DeviceRuntimeCommandOutcome.Success -> true
        is DeviceRuntimeCommandOutcome.NotConnected,
        is DeviceRuntimeCommandOutcome.NotAuthenticated,
        is DeviceRuntimeCommandOutcome.UnsupportedByDevice,
        is DeviceRuntimeCommandOutcome.SendFailed,
        is DeviceRuntimeCommandOutcome.Timeout,
        is DeviceRuntimeCommandOutcome.Cancelled -> runtimePort.prepareRuntime(uid) &&
            runtimePort.requestStatus(uid, channelKey) is DeviceRuntimeCommandOutcome.Success
        else -> false
    }

    private fun DeviceRootSnapshot.authorizedDosingSlot(slotId: String) =
        takeIf { snapshot ->
            snapshot.catalogState == DeviceRootCatalogState.VALID && snapshot.family == OwnerDeviceFamily.DOSING
        }
            ?.channelSlots
            ?.dosingChannels
            ?.singleOrNull { slot -> slot.id.value == slotId }

    private fun DeviceDosingChannelStatus.toNavigationTarget(
        context: DosingChannelNavigationContext
    ): DeviceDosingChannelNavigationTarget? = channel
        .takeIf { detail ->
            detail.index == context.slot.index.zeroBased &&
                detail.channelKey == context.slot.wireKey.value &&
                detail.editable.displayName == context.slot.displayNameEditable
        }
        ?.let { detail ->
            DeviceDosingChannelDestinationPolicy.resolve(
                calibrated = detail.calibration.confirmed,
                allowedRoutes = context.root.allowedRoutes
            )?.let { destination ->
                DeviceDosingChannelNavigationTarget(
                    deviceUid = context.uid.value,
                    slotId = context.slot.id.value,
                    pumpCount = context.root.channelSlots.dosingChannels.size,
                    channelNumber = context.slot.index.position,
                    channelTitle = detail.effectiveName.ifBlank { context.slot.defaultDisplayName },
                    lastCalibratedAtEpochSeconds = detail.calibration.lastCalibratedAt,
                    destination = destination
                )
            }
        }

    private fun Map<String, DeviceDosingChannelStatus>.toNavigationTargets(
        uid: DeviceUid
    ): List<DeviceDosingChannelNavigationTarget> {
        val root = runtimePort.currentRootSnapshot(uid)
            ?.takeIf { snapshot ->
                snapshot.catalogState == DeviceRootCatalogState.VALID && snapshot.family == OwnerDeviceFamily.DOSING
            }
            ?: return emptyList()
        return root.channelSlots.dosingChannels.mapNotNull { slot ->
            get(slot.wireKey.value)?.toNavigationTarget(DosingChannelNavigationContext(uid, root, slot))
        }
    }

    private data class DosingChannelNavigationContext(
        val uid: DeviceUid,
        val root: DeviceRootSnapshot,
        val slot: DeviceDosingChannelSlot
    )
}

private fun navigationRequest(deviceUid: String, slotId: String): DosingChannelNavigationRequest? {
    val normalizedDeviceUid = deviceUid.trim()
    val normalizedSlotId = slotId.trim()
    return normalizedDeviceUid
        .takeIf { uid -> uid.isNotBlank() && normalizedSlotId.isNotBlank() }
        ?.let(::DeviceUid)
        ?.let { uid -> DosingChannelNavigationRequest(uid, normalizedSlotId) }
}

private data class DosingChannelNavigationRequest(val uid: DeviceUid, val slotId: String)

internal interface DeviceDosingChannelNavigationRuntimePort {
    suspend fun prepareRuntime(deviceUid: DeviceUid): Boolean
    fun currentRootSnapshot(deviceUid: DeviceUid): DeviceRootSnapshot?
    fun observeStatuses(deviceUid: DeviceUid): Flow<Map<String, DeviceDosingChannelStatus>> = flowOf(emptyMap())
    fun currentStatus(deviceUid: DeviceUid, channelKey: String): DeviceDosingChannelStatus? = null
    suspend fun requestStatus(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceRuntimeCommandOutcome<DeviceDosingChannelStatus>?
}

private class RepositoryDeviceDosingChannelNavigationRuntimePort(
    private val devicesRepository: DevicesRepository,
    private val menuAccessOperations: DeviceMenuAccessOperations
) : DeviceDosingChannelNavigationRuntimePort {

    override suspend fun prepareRuntime(deviceUid: DeviceUid): Boolean {
        if (devicesRepository.replaceRuntimeAfterControlFailure(deviceUid).isFailure) return false
        val accessAvailable = when (val access = menuAccessOperations.resolve(deviceUid.value)) {
            is DeviceMenuAccessResult.Available ->
                access.deviceUid == deviceUid.value && access.family == OwnerDeviceFamily.DOSING
            is DeviceMenuAccessResult.Unavailable -> false
        }
        return accessAvailable && awaitValidatedRuntimeMetadata(deviceUid, RECOVERY_METADATA_WAIT_MILLIS)
    }

    override fun currentRootSnapshot(deviceUid: DeviceUid): DeviceRootSnapshot? =
        devicesRepository.currentDevice(deviceUid)?.toDeviceRootSnapshot()

    override fun observeStatuses(deviceUid: DeviceUid): Flow<Map<String, DeviceDosingChannelStatus>> =
        devicesRepository.runtimeModules()?.dosing?.states
            ?.map { states -> states[deviceUid]?.channels.orEmpty() }
            ?.distinctUntilChanged()
            ?: flowOf(emptyMap())

    override fun currentStatus(deviceUid: DeviceUid, channelKey: String): DeviceDosingChannelStatus? =
        devicesRepository.runtimeModules()?.dosing?.states?.value?.get(deviceUid)?.channels?.get(channelKey)

    override suspend fun requestStatus(
        deviceUid: DeviceUid,
        channelKey: String
    ): DeviceRuntimeCommandOutcome<DeviceDosingChannelStatus>? {
        if (devicesRepository.runtimeModules()?.dosing == null) return null
        awaitValidatedRuntimeMetadata(deviceUid, CURRENT_METADATA_WAIT_MILLIS)
        return devicesRepository.runtimeModules()?.dosing?.requestChannelStatus(deviceUid, channelKey)
    }

    private suspend fun awaitValidatedRuntimeMetadata(deviceUid: DeviceUid, timeoutMillis: Long): Boolean {
        if (devicesRepository.currentDevice(deviceUid)?.hasValidatedRuntimeMetadata == true) return true
        return withTimeoutOrNull(timeoutMillis) {
            devicesRepository.observeDevice(deviceUid)
                .filterNotNull()
                .first { snapshot -> snapshot.hasValidatedRuntimeMetadata }
        } != null
    }

    private companion object {
        const val CURRENT_METADATA_WAIT_MILLIS = 2_000L
        const val RECOVERY_METADATA_WAIT_MILLIS = 10_000L
    }
}

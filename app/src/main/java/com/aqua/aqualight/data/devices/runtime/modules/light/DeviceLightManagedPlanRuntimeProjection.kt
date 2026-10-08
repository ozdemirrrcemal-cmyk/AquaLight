package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration

internal enum class DeviceLightManagedPlanReadAuthority {
    AUTHORITATIVE,
    PRESENTATION
}

/** One current-generation firmware-managed AUTO plan document; never an app-owned cache. */
internal class DeviceLightManagedPlanRuntimeProjection(
    private val lock: Any,
    private val authorityCoordinator: DeviceLightRuntimeAuthorityCoordinator,
    private val statuses: () -> Map<DeviceUid, DeviceLightStatus>,
    private val publishChange: () -> Unit
) {
    private var documents: Map<DeviceUid, DeviceLightManagedAutoPlan> = emptyMap()

    fun current(
        deviceUid: DeviceUid,
        authority: DeviceLightManagedPlanReadAuthority
    ): DeviceLightManagedAutoPlan? = synchronized(lock) {
        val document = documents[deviceUid] ?: return@synchronized null
        when (authority) {
            DeviceLightManagedPlanReadAuthority.PRESENTATION -> document
            DeviceLightManagedPlanReadAuthority.AUTHORITATIVE -> {
                val status = statuses()[deviceUid]
                document.takeIf {
                    status != null &&
                        document.isCoherentWith(status) &&
                        authorityCoordinator.isCurrentlyAuthoritative(
                            DeviceLightRuntimeProjection.STATUS,
                            deviceUid
                        ) &&
                        authorityCoordinator.isCurrentlyAuthoritative(
                            DeviceLightRuntimeProjection.AUTO_PLAN,
                            deviceUid
                        )
                }
            }
        }
    }

    fun record(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        document: DeviceLightManagedAutoPlan
    ): Boolean = synchronized(lock) {
        if (!authorityCoordinator.isAuthoritative(
                DeviceLightRuntimeProjection.STATUS,
                deviceUid,
                generation
            )) {
            return@synchronized false
        }
        val status = statuses()[deviceUid] ?: return@synchronized false
        if (!document.isCoherentWith(status)) return@synchronized false
        if (!authorityCoordinator.acceptAuthoritativeSnapshot(
                DeviceLightRuntimeProjection.AUTO_PLAN,
                deviceUid,
                generation
            )) {
            return@synchronized false
        }
        if (documents[deviceUid] != document) {
            documents = documents + (deviceUid to document)
            publishChange()
        }
        true
    }

    fun reconcileStatus(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        status: DeviceLightStatus
    ) = synchronized(lock) {
        val document = documents[deviceUid] ?: return@synchronized
        if (!document.isCoherentWith(status)) {
            documents = documents.withoutManagedPlan(deviceUid)
            authorityCoordinator.invalidateProjection(
                DeviceLightRuntimeProjection.AUTO_PLAN,
                deviceUid,
                generation
            )
            publishChange()
        }
    }

    fun clear(deviceUid: DeviceUid) = synchronized(lock) {
        if (deviceUid in documents) {
            documents = documents.withoutManagedPlan(deviceUid)
            publishChange()
        }
    }
}

private fun DeviceLightManagedAutoPlan.isCoherentWith(status: DeviceLightStatus): Boolean =
    storageGeneration == status.storageGeneration &&
        revision == status.auto.planRevision &&
        installed == status.auto.planInstalled &&
        planId == status.auto.planId &&
        runtime.clockReady == status.scheduler.ready &&
        runtime.state == status.auto.planRuntimeState &&
        runtime.activePhaseIndex == status.auto.activePlanPhaseIndex &&
        runtime.transitionPermille == status.auto.planTransitionPermille &&
        runtime.nextTransitionEpochDay == status.auto.nextPlanTransitionEpochDay

private fun <T> Map<DeviceUid, T>.withoutManagedPlan(
    deviceUid: DeviceUid
): Map<DeviceUid, T> = if (deviceUid !in this) {
    this
} else {
    toMutableMap().apply { remove(deviceUid) }.toMap()
}

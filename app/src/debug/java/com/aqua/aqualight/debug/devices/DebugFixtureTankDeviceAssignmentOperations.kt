package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.AssignDeviceToTankResult
import com.aqua.aqualight.application.devices.AvailableTankDevicesSnapshot
import com.aqua.aqualight.application.devices.RemoveDeviceFromTankResult
import com.aqua.aqualight.application.devices.TankDeviceAssignmentOperations
import com.aqua.aqualight.application.devices.TankDeviceListItem
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.toTankDeviceListItem
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

/** Shared in-process assignment authority for installable-debug fixture devices. */
internal class DebugFixtureTankAssignmentRuntime {
    private val lock = Any()
    private val _assignments = MutableStateFlow<Map<String, Long>>(emptyMap())
    val assignments: StateFlow<Map<String, Long>> = _assignments.asStateFlow()

    fun tankIdForDevice(deviceUid: String): Long? = assignments.value[deviceUid.trim()]

    fun assign(deviceUid: String, tankId: Long): DebugFixtureAssignDecision = synchronized(lock) {
        val normalizedUid = deviceUid.trim()
        val existingTankId = _assignments.value[normalizedUid]
        when {
            existingTankId == tankId -> DebugFixtureAssignDecision.AlreadyAssigned
            existingTankId != null -> DebugFixtureAssignDecision.Conflict(existingTankId)
            else -> {
                _assignments.value = _assignments.value + (normalizedUid to tankId)
                DebugFixtureAssignDecision.Assigned
            }
        }
    }

    fun remove(deviceUid: String, tankId: Long): Boolean = synchronized(lock) {
        val normalizedUid = deviceUid.trim()
        if (_assignments.value[normalizedUid] != tankId) {
            false
        } else {
            _assignments.value = _assignments.value - normalizedUid
            true
        }
    }
}

internal sealed interface DebugFixtureAssignDecision {
    data object Assigned : DebugFixtureAssignDecision
    data object AlreadyAssigned : DebugFixtureAssignDecision
    data class Conflict(val existingTankId: Long) : DebugFixtureAssignDecision
}

/** Adds debug fixtures to tank assignment without registering them in the production runtime. */
internal class DebugFixtureTankDeviceAssignmentOperations(
    private val delegate: TankDeviceAssignmentOperations,
    private val fixtures: DebugDeviceFixtureCatalog,
    private val runtime: DebugFixtureTankAssignmentRuntime,
    private val tankExists: suspend (Long) -> Boolean
) : TankDeviceAssignmentOperations {

    override fun start(scope: CoroutineScope): Job = delegate.start(scope)

    override fun assignedDevices(tankId: Long): Flow<List<TankDeviceListItem>> = combine(
        delegate.assignedDevices(tankId),
        runtime.assignments
    ) { registeredDevices, fixtureAssignments ->
        mergeDevices(
            registeredDevices = registeredDevices,
            fixtureDevices = fixtures.snapshots
                .filter { snapshot -> fixtureAssignments[snapshot.deviceUid.value] == tankId }
                .map(DeviceSnapshot::toDebugTankDeviceListItem)
        )
    }

    override fun availableDevices(tankId: Long): Flow<AvailableTankDevicesSnapshot> = combine(
        delegate.availableDevices(tankId),
        runtime.assignments
    ) { registeredSnapshot, fixtureAssignments ->
        val availableFixtures = fixtures.snapshots
            .filterNot { snapshot -> snapshot.deviceUid.value in fixtureAssignments }
            .map(DeviceSnapshot::toDebugTankDeviceListItem)
        AvailableTankDevicesSnapshot(
            devices = mergeDevices(
                registeredDevices = registeredSnapshot.devices,
                fixtureDevices = availableFixtures
            ),
            hasRegisteredDevices = registeredSnapshot.hasRegisteredDevices ||
                fixtures.snapshots.isNotEmpty()
        )
    }

    override suspend fun assignDevice(
        tankId: Long,
        deviceUid: String
    ): AssignDeviceToTankResult {
        val normalizedUid = deviceUid.trim()
        if (!fixtures.contains(normalizedUid)) {
            return delegate.assignDevice(tankId, deviceUid)
        }
        if (tankId <= 0L || normalizedUid.isBlank()) {
            return AssignDeviceToTankResult.InvalidRequest
        }
        return try {
            if (!tankExists(tankId)) {
                AssignDeviceToTankResult.TankNotFound
            } else {
                when (val decision = runtime.assign(normalizedUid, tankId)) {
                    DebugFixtureAssignDecision.Assigned -> AssignDeviceToTankResult.Assigned
                    DebugFixtureAssignDecision.AlreadyAssigned ->
                        AssignDeviceToTankResult.AlreadyAssigned
                    is DebugFixtureAssignDecision.Conflict ->
                        AssignDeviceToTankResult.Conflict(decision.existingTankId)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            AssignDeviceToTankResult.Failure
        }
    }

    override suspend fun removeDevice(
        tankId: Long,
        deviceUid: String
    ): RemoveDeviceFromTankResult {
        val normalizedUid = deviceUid.trim()
        if (!fixtures.contains(normalizedUid)) {
            return delegate.removeDevice(tankId, deviceUid)
        }
        if (tankId <= 0L || normalizedUid.isBlank()) {
            return RemoveDeviceFromTankResult.INVALID_REQUEST
        }
        return if (runtime.remove(normalizedUid, tankId)) {
            RemoveDeviceFromTankResult.REMOVED
        } else {
            RemoveDeviceFromTankResult.NOT_ASSIGNED
        }
    }
}

private fun DeviceSnapshot.toDebugTankDeviceListItem(): TankDeviceListItem =
    toTankDeviceListItem().copy(displayName = "$title [TEST]")

private fun mergeDevices(
    registeredDevices: List<TankDeviceListItem>,
    fixtureDevices: List<TankDeviceListItem>
): List<TankDeviceListItem> {
    val fixtureUids = fixtureDevices.mapTo(mutableSetOf(), TankDeviceListItem::deviceUid)
    return (fixtureDevices + registeredDevices.filterNot { it.deviceUid in fixtureUids })
        .sortedWith(
            compareBy<TankDeviceListItem> { item -> item.displayName.lowercase() }
                .thenBy(TankDeviceListItem::deviceUid)
        )
}

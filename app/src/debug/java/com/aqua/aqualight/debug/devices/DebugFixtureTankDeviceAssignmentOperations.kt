package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.AssignDeviceToTankResult
import com.aqua.aqualight.application.devices.AvailableTankDevicesSnapshot
import com.aqua.aqualight.application.devices.RemoveDeviceFromTankResult
import com.aqua.aqualight.application.devices.TankDeviceAssignmentOperations
import com.aqua.aqualight.application.devices.TankDeviceListItem
import com.aqua.aqualight.data.devices.toTankDeviceListItem
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

/** Owner-graph-scoped fixture assignments. They intentionally never enter production storage. */
internal class DebugFixtureTankAssignments {
    private val lock = Any()
    private val mutableAssignments = MutableStateFlow<Map<String, Long>>(emptyMap())

    val assignments: StateFlow<Map<String, Long>> = mutableAssignments.asStateFlow()

    fun tankIdFor(deviceUid: String): Long? = mutableAssignments.value[deviceUid.trim()]

    fun assign(
        deviceUid: String,
        tankId: Long,
        validTankIds: Set<Long>
    ): AssignDeviceToTankResult = synchronized(lock) {
        val normalizedDeviceUid = deviceUid.trim()
        val activeAssignments = mutableAssignments.value.filterValues(validTankIds::contains)
        val result = when {
            normalizedDeviceUid.isBlank() || tankId <= 0L ->
                AssignDeviceToTankResult.InvalidRequest
            tankId !in validTankIds -> AssignDeviceToTankResult.TankNotFound
            activeAssignments[normalizedDeviceUid] == null -> AssignDeviceToTankResult.Assigned
            activeAssignments[normalizedDeviceUid] == tankId ->
                AssignDeviceToTankResult.AlreadyAssigned
            else -> AssignDeviceToTankResult.Conflict(
                checkNotNull(activeAssignments[normalizedDeviceUid])
            )
        }
        mutableAssignments.value = when (result) {
            AssignDeviceToTankResult.Assigned -> activeAssignments + (normalizedDeviceUid to tankId)
            else -> activeAssignments
        }
        result
    }

    fun remove(tankId: Long, deviceUid: String): RemoveDeviceFromTankResult = synchronized(lock) {
        val normalizedDeviceUid = deviceUid.trim()
        if (tankId <= 0L || normalizedDeviceUid.isBlank()) {
            return@synchronized RemoveDeviceFromTankResult.INVALID_REQUEST
        }
        if (mutableAssignments.value[normalizedDeviceUid] != tankId) {
            return@synchronized RemoveDeviceFromTankResult.NOT_ASSIGNED
        }
        mutableAssignments.value = mutableAssignments.value - normalizedDeviceUid
        RemoveDeviceFromTankResult.REMOVED
    }
}

/** Adds test fixtures to tank assignment while preserving the exact real-device delegate. */
internal class DebugFixtureTankDeviceAssignmentOperations(
    private val delegate: TankDeviceAssignmentOperations,
    private val fixtures: DebugDeviceFixtureCatalog,
    private val fixtureAssignments: DebugFixtureTankAssignments,
    private val validTankIds: Flow<Set<Long>>,
    private val validTankIdsSnapshot: suspend () -> Set<Long>
) : TankDeviceAssignmentOperations {

    private val fixtureItems = fixtures.snapshots.map { snapshot ->
        snapshot.toTankDeviceListItem().copy(displayName = "${snapshot.title} [TEST]")
    }

    override fun start(scope: CoroutineScope): Job = delegate.start(scope)

    override fun assignedDevices(tankId: Long): Flow<List<TankDeviceListItem>> = combine(
        delegate.assignedDevices(tankId),
        fixtureAssignments.assignments,
        validTankIds
    ) { realDevices, assignments, tankIds ->
        val fixtureDevices = if (tankId > 0L && tankId in tankIds) {
            fixtureItems.filter { item -> assignments[item.deviceUid] == tankId }
        } else {
            emptyList()
        }
        mergeDevices(fixtureDevices, realDevices)
    }

    override fun availableDevices(tankId: Long): Flow<AvailableTankDevicesSnapshot> = combine(
        delegate.availableDevices(tankId),
        fixtureAssignments.assignments,
        validTankIds
    ) { realSnapshot, assignments, tankIds ->
        val activeAssignments = assignments.filterValues(tankIds::contains)
        val fixtureDevices = if (tankId > 0L && tankId in tankIds) {
            fixtureItems.filterNot { item -> item.deviceUid in activeAssignments }
        } else {
            emptyList()
        }
        AvailableTankDevicesSnapshot(
            devices = mergeDevices(fixtureDevices, realSnapshot.devices),
            hasRegisteredDevices = fixtureItems.isNotEmpty() || realSnapshot.hasRegisteredDevices
        )
    }

    override suspend fun assignDevice(
        tankId: Long,
        deviceUid: String
    ): AssignDeviceToTankResult {
        val normalizedDeviceUid = deviceUid.trim()
        if (!fixtures.contains(normalizedDeviceUid)) {
            return delegate.assignDevice(tankId, normalizedDeviceUid)
        }
        if (tankId <= 0L || normalizedDeviceUid.isBlank()) {
            return AssignDeviceToTankResult.InvalidRequest
        }
        return try {
            val tankIds = validTankIdsSnapshot()
            if (tankId !in tankIds) {
                AssignDeviceToTankResult.TankNotFound
            } else {
                fixtureAssignments.assign(normalizedDeviceUid, tankId, tankIds)
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            AssignDeviceToTankResult.Failure
        }
    }

    override suspend fun removeDevice(
        tankId: Long,
        deviceUid: String
    ): RemoveDeviceFromTankResult {
        val normalizedDeviceUid = deviceUid.trim()
        if (!fixtures.contains(normalizedDeviceUid)) {
            return delegate.removeDevice(tankId, normalizedDeviceUid)
        }
        if (tankId <= 0L || normalizedDeviceUid.isBlank()) {
            return RemoveDeviceFromTankResult.INVALID_REQUEST
        }
        return fixtureAssignments.remove(tankId, normalizedDeviceUid)
    }
}

private fun mergeDevices(
    fixtures: List<TankDeviceListItem>,
    realDevices: List<TankDeviceListItem>
): List<TankDeviceListItem> = (fixtures + realDevices.filterNot { real ->
    fixtures.any { fixture -> fixture.deviceUid == real.deviceUid }
}).sortedBy { item -> item.displayName.lowercase(Locale.ROOT) }

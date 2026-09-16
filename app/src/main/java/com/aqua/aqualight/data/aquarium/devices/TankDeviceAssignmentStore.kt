package com.aqua.aqualight.data.aquarium.devices

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
import com.aqua.aqualight.data.store.CommercialStoreSchema
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.tankDeviceAssignmentsDataStore: DataStore<TankDeviceAssignmentsStore> by dataStore(
    fileName = "tank_device_assignments.pb",
    serializer = TankDeviceAssignmentsSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler {
        LocalDataRecoveryTracker.markRecovered(
            LocalDataRecoveryTracker.Area.TANK_DEVICE_ASSIGNMENTS
        )
        TankDeviceAssignmentsStore.newBuilder()
            .setSchemaVersion(CommercialStoreSchema.TANK_DEVICE_ASSIGNMENTS_VERSION)
            .build()
    }
)

class TankDeviceAssignmentStore private constructor(
    context: Context
) {

    private val dataStore = context.applicationContext.tankDeviceAssignmentsDataStore
    private val mutationMutex = Mutex()

    fun assignmentsForOwner(
        ownerUid: String
    ): Flow<List<TankDeviceAssignment>> {
        val normalizedOwnerUid = ownerUid.requireOwnerUid()

        return dataStore.data.map { store ->
            store.getAssignmentsList()
                .asSequence()
                .filter { assignment ->
                    assignment.ownerUid == normalizedOwnerUid
                }
                .map { assignment ->
                    assignment.toDomain()
                }
                .sortedWith(
                    compareBy<TankDeviceAssignment> { assignment ->
                        assignment.assignedAtMillis
                    }.thenBy { assignment ->
                        assignment.deviceUid.value
                    }
                )
                .toList()
        }
    }

    internal suspend fun assignDeviceToTank(
        ownerUid: String,
        tankId: Long,
        deviceUid: DeviceUid,
        assignedAtMillis: Long = System.currentTimeMillis()
    ): TankDeviceStoreAssignDecision {
        return mutationMutex.withLock {
            var decision: TankDeviceStoreAssignDecision? = null

            dataStore.updateData { currentStore ->
                val mutation = TankDeviceAssignmentRules.assign(
                    store = currentStore,
                    ownerUid = ownerUid,
                    tankId = tankId,
                    deviceUid = deviceUid.value,
                    assignedAtMillis = assignedAtMillis
                )

                decision = mutation.decision
                mutation.store
            }

            checkNotNull(decision) {
                "Tank assignment mutation completed without a decision."
            }
        }
    }

    internal suspend fun updateLightAutomation(
        ownerUid: String,
        deviceUid: DeviceUid,
        installation: TankLightInstallationProfile,
        recommendations: List<TankLightRecommendationSnapshot>
    ): TankDeviceAssignment {
        return mutationMutex.withLock {
            var updated: TankDeviceAssignment? = null
            dataStore.updateData { currentStore ->
                val normalizedOwner = ownerUid.requireOwnerUid()
                val index = currentStore.assignmentsList.indexOfFirst { assignment ->
                    assignment.ownerUid == normalizedOwner &&
                        assignment.deviceUid == deviceUid.value
                }
                require(index >= 0) { "Tank assignment was not found." }
                val replacement = currentStore.getAssignments(index).toDomain().copy(
                    lightInstallation = installation,
                    lightRecommendations = recommendations.takeLast(MAX_RECOMMENDATION_HISTORY)
                )
                updated = replacement
                currentStore.toBuilder()
                    .setAssignments(index, replacement.toStored())
                    .build()
                    .also(TankDeviceAssignmentRules::validate)
            }
            checkNotNull(updated)
        }
    }

    suspend fun removeDeviceFromTank(
        ownerUid: String,
        tankId: Long,
        deviceUid: DeviceUid
    ): Boolean {
        return mutationMutex.withLock {
            var removed = false

            dataStore.updateData { currentStore ->
                val mutation = TankDeviceAssignmentRules.removeFromTank(
                    store = currentStore,
                    ownerUid = ownerUid,
                    tankId = tankId,
                    deviceUid = deviceUid.value
                )

                removed = mutation.second
                mutation.first
            }

            removed
        }
    }

    suspend fun removeDeviceFromAnyTank(
        ownerUid: String,
        deviceUid: DeviceUid
    ): Boolean {
        return mutationMutex.withLock {
            var removed = false

            dataStore.updateData { currentStore ->
                val mutation = TankDeviceAssignmentRules.removeDevice(
                    store = currentStore,
                    ownerUid = ownerUid,
                    deviceUid = deviceUid.value
                )

                removed = mutation.second
                mutation.first
            }

            removed
        }
    }

    suspend fun removeAssignmentsForTank(
        ownerUid: String,
        tankId: Long
    ): Int {
        return mutationMutex.withLock {
            var removedCount = 0

            dataStore.updateData { currentStore ->
                val mutation = TankDeviceAssignmentRules.removeTank(
                    store = currentStore,
                    ownerUid = ownerUid,
                    tankId = tankId
                )

                removedCount = mutation.second
                mutation.first
            }

            removedCount
        }
    }

    suspend fun clearOwnerAssignments(
        ownerUid: String
    ): Int {
        return mutationMutex.withLock {
            var removedCount = 0

            dataStore.updateData { currentStore ->
                val mutation = TankDeviceAssignmentRules.clearOwner(
                    store = currentStore,
                    ownerUid = ownerUid
                )

                removedCount = mutation.second
                mutation.first
            }

            removedCount
        }
    }

    suspend fun repairOwnerAssignments(
        ownerUid: String,
        validTankIds: Set<Long>,
        validDeviceUids: Set<String>
    ): List<TankDeviceAssignment> {
        return mutationMutex.withLock {
            var removedAssignments: List<TankDeviceAssignment> = emptyList()

            dataStore.updateData { currentStore ->
                val mutation = TankDeviceAssignmentRules.repairOwner(
                    store = currentStore,
                    ownerUid = ownerUid,
                    validTankIds = validTankIds,
                    validDeviceUids = validDeviceUids
                )

                removedAssignments = mutation.removedAssignments
                mutation.store
            }

            removedAssignments
        }
    }

    private fun String.requireOwnerUid(): String {
        val normalized = trim()
        require(normalized.isNotBlank()) {
            "ownerUid must not be blank"
        }
        return normalized
    }

    companion object {
        private const val MAX_RECOMMENDATION_HISTORY = 1_000
        @Volatile
        private var instance: TankDeviceAssignmentStore? = null

        fun get(
            context: Context
        ): TankDeviceAssignmentStore {
            return instance ?: synchronized(this) {
                instance ?: TankDeviceAssignmentStore(
                    context = context.applicationContext
                ).also { store ->
                    instance = store
                }
            }
        }
    }
}

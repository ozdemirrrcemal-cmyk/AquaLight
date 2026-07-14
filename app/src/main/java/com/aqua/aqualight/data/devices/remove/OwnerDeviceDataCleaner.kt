package com.aqua.aqualight.data.devices.remove

import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignment
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepository
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentResult
import com.aqua.aqualight.data.aquarium.devices.TankDeviceRemovalResult
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import java.util.concurrent.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OwnerDeviceDataCleaner internal constructor(
    private val assignmentForDevice: suspend (DeviceUid) -> TankDeviceAssignment?,
    private val removeAssignment: suspend (DeviceUid) -> TankDeviceRemovalResult,
    private val restoreAssignment: suspend (TankDeviceAssignment) -> TankDeviceAssignmentResult,
    private val forgetDevice: suspend (DeviceUid) -> Unit
) {

    enum class FailureStage {
        READ_ASSIGNMENT,
        REMOVE_ASSIGNMENT,
        FORGET_DEVICE
    }

    data class Failure(
        val deviceUid: DeviceUid,
        val stage: FailureStage,
        val error: Throwable,
        val rollbackError: Throwable? = null
    )

    data class Result(
        val succeededDeviceUids: Set<DeviceUid>,
        val failures: List<Failure>
    ) {
        val requestedCount: Int
            get() = succeededDeviceUids.size + failures.size

        val succeededCount: Int
            get() = succeededDeviceUids.size

        val failedCount: Int
            get() = failures.size

        val isCompleteSuccess: Boolean
            get() = failures.isEmpty()

        val isCompleteFailure: Boolean
            get() = succeededDeviceUids.isEmpty() && failures.isNotEmpty()
    }

    private val operationMutex = Mutex()

    suspend fun deleteDevices(
        deviceUids: Iterable<DeviceUid>
    ): Result {
        val uniqueDeviceUids = deviceUids
            .distinctBy { deviceUid -> deviceUid.value }

        if (uniqueDeviceUids.isEmpty()) {
            return Result(
                succeededDeviceUids = emptySet(),
                failures = emptyList()
            )
        }

        return operationMutex.withLock {
            val succeeded = linkedSetOf<DeviceUid>()
            val failures = mutableListOf<Failure>()

            uniqueDeviceUids.forEach { deviceUid ->
                val assignment = try {
                    assignmentForDevice(deviceUid)
                } catch (error: Throwable) {
                    error.throwIfCancellation()
                    failures += Failure(
                        deviceUid = deviceUid,
                        stage = FailureStage.READ_ASSIGNMENT,
                        error = error
                    )
                    return@forEach
                }

                if (assignment != null) {
                    when (
                        val removalResult = removeAssignment(deviceUid)
                    ) {
                        TankDeviceRemovalResult.Removed,
                        TankDeviceRemovalResult.NotAssigned -> Unit

                        TankDeviceRemovalResult.InvalidRequest -> {
                            failures += Failure(
                                deviceUid = deviceUid,
                                stage = FailureStage.REMOVE_ASSIGNMENT,
                                error = IllegalArgumentException(
                                    "Assignment removal received an invalid device UID."
                                )
                            )
                            return@forEach
                        }

                        is TankDeviceRemovalResult.Failure -> {
                            failures += Failure(
                                deviceUid = deviceUid,
                                stage = FailureStage.REMOVE_ASSIGNMENT,
                                error = removalResult.error
                            )
                            return@forEach
                        }
                    }
                }

                try {
                    forgetDevice(deviceUid)
                    succeeded += deviceUid
                } catch (error: Throwable) {
                    error.throwIfCancellation()
                    val rollbackError = assignment?.let { previousAssignment ->
                        rollbackAssignment(previousAssignment)
                    }

                    failures += Failure(
                        deviceUid = deviceUid,
                        stage = FailureStage.FORGET_DEVICE,
                        error = error,
                        rollbackError = rollbackError
                    )
                }
            }

            Result(
                succeededDeviceUids = succeeded.toSet(),
                failures = failures.toList()
            )
        }
    }

    private suspend fun rollbackAssignment(
        assignment: TankDeviceAssignment
    ): Throwable? {
        return try {
            when (
                val result = restoreAssignment(assignment)
            ) {
                is TankDeviceAssignmentResult.Assigned,
                is TankDeviceAssignmentResult.AlreadyAssigned -> null

                is TankDeviceAssignmentResult.Conflict -> {
                    IllegalStateException(
                        "Assignment rollback conflicted with tank ${result.existingAssignment.tankId}."
                    )
                }

                TankDeviceAssignmentResult.TankNotFound -> {
                    IllegalStateException("Assignment rollback tank no longer exists.")
                }

                TankDeviceAssignmentResult.DeviceNotFound -> {
                    IllegalStateException("Assignment rollback device is no longer registered.")
                }

                TankDeviceAssignmentResult.InvalidRequest -> {
                    IllegalArgumentException("Assignment rollback request was invalid.")
                }

                is TankDeviceAssignmentResult.Failure -> result.error
            }
        } catch (error: Throwable) {
            error.throwIfCancellation()
            error
        }
    }

    private fun Throwable.throwIfCancellation() {
        if (this is CancellationException) {
            throw this
        }
    }

    companion object {
        fun create(
            devicesRepository: DevicesRepository,
            assignmentRepository: TankDeviceAssignmentRepository
        ): OwnerDeviceDataCleaner {
            return OwnerDeviceDataCleaner(
                assignmentForDevice = assignmentRepository::assignmentForDevice,
                removeAssignment = assignmentRepository::removeDeviceFromAnyTank,
                restoreAssignment = { assignment ->
                    assignmentRepository.assignDeviceToTank(
                        tankId = assignment.tankId,
                        deviceUid = assignment.deviceUid
                    )
                },
                forgetDevice = { deviceUid ->
                    devicesRepository.forgetDevice(deviceUid)
                    Unit
                }
            )
        }
    }
}

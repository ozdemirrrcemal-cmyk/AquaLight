package com.aqua.aqualight.data.aquarium.devices

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.aqua.aqualight.data.user.UserDataScope
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object TankDeviceAssignmentsSerializer : Serializer<TankDeviceAssignmentsStore> {

    override val defaultValue: TankDeviceAssignmentsStore =
        TankDeviceAssignmentsStore.getDefaultInstance()

    override suspend fun readFrom(
        input: InputStream
    ): TankDeviceAssignmentsStore {
        val store = try {
            TankDeviceAssignmentsStore.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException(
                "Cannot read tank-device assignments.",
                exception
            )
        }

        return try {
            validate(store)
            store
        } catch (exception: IllegalArgumentException) {
            throw CorruptionException(
                "Tank-device assignments contain invalid records.",
                exception
            )
        }
    }

    override suspend fun writeTo(
        t: TankDeviceAssignmentsStore,
        output: OutputStream
    ) {
        validate(t)
        t.writeTo(output)
    }

    private fun validate(
        store: TankDeviceAssignmentsStore
    ) {
        store.assignmentsList.forEach { assignment ->
            val ownerUid = UserDataScope.normalizeOwnerUid(assignment.ownerUid)
            val deviceUid = assignment.deviceUid.trim()

            require(ownerUid.isNotBlank()) {
                "Assignment owner UID must not be blank."
            }
            require(assignment.ownerUid == ownerUid) {
                "Assignment owner UID must be normalized."
            }
            require(deviceUid.isNotBlank()) {
                "Assignment device UID must not be blank."
            }
            require(assignment.deviceUid == deviceUid) {
                "Assignment device UID must be normalized."
            }
            require(assignment.tankId > 0L) {
                "Assignment tank ID must be positive."
            }
            require(assignment.assignedAtMillis > 0L) {
                "Assignment creation timestamp must be positive."
            }
            require(assignment.updatedAtMillis > 0L) {
                "Assignment update timestamp must be positive."
            }
        }
    }
}

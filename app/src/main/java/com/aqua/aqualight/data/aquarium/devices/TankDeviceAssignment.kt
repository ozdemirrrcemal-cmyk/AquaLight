package com.aqua.aqualight.data.aquarium.devices

import com.aqua.aqualight.data.devices.model.DeviceUid

data class TankDeviceAssignment(
    val ownerUid: String,
    val tankId: Long,
    val deviceUid: DeviceUid,
    val assignedAtMillis: Long
)

internal fun StoredTankDeviceAssignment.toDomain(): TankDeviceAssignment {
    return TankDeviceAssignment(
        ownerUid = ownerUid,
        tankId = tankId,
        deviceUid = DeviceUid(deviceUid),
        assignedAtMillis = assignedAtMillis
    )
}

internal fun TankDeviceAssignment.toStored(): StoredTankDeviceAssignment {
    return StoredTankDeviceAssignment.newBuilder()
        .setOwnerUid(ownerUid)
        .setTankId(tankId)
        .setDeviceUid(deviceUid.value)
        .setAssignedAtMillis(assignedAtMillis)
        .build()
}

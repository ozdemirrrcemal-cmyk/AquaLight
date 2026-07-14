package com.aqua.aqualight.data.devices.store

import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid

internal class KnownDevicesValidationException(
    message: String
) : IllegalStateException(message)

internal object KnownDevicesStoreReducer {

    fun validate(
        store: KnownDevicesStore
    ) {
        val knownKeys = mutableSetOf<String>()
        val ignoredKeys = mutableSetOf<String>()

        store.getDevicesList().forEachIndexed { index, device ->
            val ownerUid = device.ownerUid
            val deviceUid = device.identity.deviceUid

            validateOwnerUid(ownerUid, "known device", index)
            validateDeviceUid(deviceUid, "known device", index)
            validatePort(device.endpoint.wsPort, "ws_port", index)
            validatePort(device.endpoint.discoveryPort, "discovery_port", index)
            validateNonNegative(device.endpoint.wsProtocolVersion, "ws_protocol_version", index)
            validateNonNegative(device.limits.lightChannelCount, "light_channel_count", index)
            validateNonNegative(device.limits.fanOutputCount, "fan_output_count", index)
            validateNonNegative(
                device.limits.temperatureSensorCount,
                "temperature_sensor_count",
                index
            )
            validateNonNegative(device.limits.timerChannelCount, "timer_channel_count", index)
            validateNonNegative(device.limits.dosingChannelCount, "dosing_channel_count", index)
            validateStringList(device.getSupportedFeaturesList(), "supported_features", index)
            validateStringList(device.getSupportedScreensList(), "supported_screens", index)
            validateStringList(device.getModulesList(), "modules", index)

            if (device.lastSeenAtMillis < 0L) {
                invalid("known device", index, "last_seen_at_millis must not be negative")
            }

            val key = ownerDeviceKey(ownerUid, deviceUid)
            if (!knownKeys.add(key)) {
                invalid("known device", index, "duplicate owner/device record")
            }
        }

        store.getIgnoredDevicesList().forEachIndexed { index, ignored ->
            validateOwnerUid(ignored.ownerUid, "ignored device", index)
            validateDeviceUid(ignored.deviceUid, "ignored device", index)

            val key = ownerDeviceKey(ignored.ownerUid, ignored.deviceUid)
            if (!ignoredKeys.add(key)) {
                invalid("ignored device", index, "duplicate owner/device record")
            }
        }

        val overlap = knownKeys.intersect(ignoredKeys)
        if (overlap.isNotEmpty()) {
            throw KnownDevicesValidationException(
                "A device cannot be both known and ignored for the same owner."
            )
        }
    }

    fun saveSnapshots(
        store: KnownDevicesStore,
        ownerUid: String,
        snapshots: Iterable<DeviceSnapshot>
    ): KnownDevicesStore {
        val normalizedOwnerUid = ownerUid.requireOwnerUid()
        validate(store)

        val incoming = linkedMapOf<String, StoredKnownDevice>()
        snapshots.forEach { snapshot ->
            val stored = KnownDeviceProtoMapper.toStored(
                ownerUid = normalizedOwnerUid,
                snapshot = snapshot
            )
            incoming[stored.identity.deviceUid] = stored
        }

        if (incoming.isEmpty()) {
            return store
        }

        val incomingKeys = incoming.keys
        val keptDevices = store.getDevicesList().filterNot { stored ->
            stored.ownerUid == normalizedOwnerUid &&
                stored.identity.deviceUid in incomingKeys
        }
        val keptIgnored = store.getIgnoredDevicesList().filterNot { ignored ->
            ignored.ownerUid == normalizedOwnerUid &&
                ignored.deviceUid in incomingKeys
        }

        val updated = KnownDevicesStore.newBuilder()
            .addAllDevices(
                (keptDevices + incoming.values)
                    .sortedWith(deviceComparator)
            )
            .addAllIgnoredDevices(
                keptIgnored.sortedWith(ignoredComparator)
            )
            .build()

        validate(updated)
        return updated
    }

    fun removeKnownDevice(
        store: KnownDevicesStore,
        ownerUid: String,
        deviceUid: DeviceUid
    ): KnownDevicesStore {
        val normalizedOwnerUid = ownerUid.requireOwnerUid()
        val normalizedDeviceUid = deviceUid.value.requireDeviceUid()
        validate(store)

        val remaining = store.getDevicesList().filterNot { stored ->
            stored.ownerUid == normalizedOwnerUid &&
                stored.identity.deviceUid == normalizedDeviceUid
        }

        if (remaining.size == store.devicesCount) {
            return store
        }

        return rebuild(
            devices = remaining,
            ignored = store.getIgnoredDevicesList()
        )
    }

    fun forgetDevice(
        store: KnownDevicesStore,
        ownerUid: String,
        deviceUid: DeviceUid
    ): KnownDevicesStore {
        val normalizedOwnerUid = ownerUid.requireOwnerUid()
        val normalizedDeviceUid = deviceUid.value.requireDeviceUid()
        validate(store)

        val remainingDevices = store.getDevicesList().filterNot { stored ->
            stored.ownerUid == normalizedOwnerUid &&
                stored.identity.deviceUid == normalizedDeviceUid
        }
        val remainingIgnored = store.getIgnoredDevicesList().filterNot { ignored ->
            ignored.ownerUid == normalizedOwnerUid &&
                ignored.deviceUid == normalizedDeviceUid
        }
        val ignoredRecord = StoredIgnoredDevice.newBuilder()
            .setOwnerUid(normalizedOwnerUid)
            .setDeviceUid(normalizedDeviceUid)
            .build()

        return rebuild(
            devices = remainingDevices,
            ignored = remainingIgnored + ignoredRecord
        )
    }

    fun allowDevice(
        store: KnownDevicesStore,
        ownerUid: String,
        deviceUid: DeviceUid
    ): KnownDevicesStore {
        val normalizedOwnerUid = ownerUid.requireOwnerUid()
        val normalizedDeviceUid = deviceUid.value.requireDeviceUid()
        validate(store)

        val remainingIgnored = store.getIgnoredDevicesList().filterNot { ignored ->
            ignored.ownerUid == normalizedOwnerUid &&
                ignored.deviceUid == normalizedDeviceUid
        }

        if (remainingIgnored.size == store.ignoredDevicesCount) {
            return store
        }

        return rebuild(
            devices = store.getDevicesList(),
            ignored = remainingIgnored
        )
    }

    fun clearKnownDevices(
        store: KnownDevicesStore,
        ownerUid: String
    ): KnownDevicesStore {
        val normalizedOwnerUid = ownerUid.requireOwnerUid()
        validate(store)

        return rebuild(
            devices = store.getDevicesList().filterNot { stored ->
                stored.ownerUid == normalizedOwnerUid
            },
            ignored = store.getIgnoredDevicesList()
        )
    }

    fun clearIgnoredDevices(
        store: KnownDevicesStore,
        ownerUid: String
    ): KnownDevicesStore {
        val normalizedOwnerUid = ownerUid.requireOwnerUid()
        validate(store)

        return rebuild(
            devices = store.getDevicesList(),
            ignored = store.getIgnoredDevicesList().filterNot { ignored ->
                ignored.ownerUid == normalizedOwnerUid
            }
        )
    }

    fun clearOwner(
        store: KnownDevicesStore,
        ownerUid: String
    ): KnownDevicesStore {
        val normalizedOwnerUid = ownerUid.requireOwnerUid()
        validate(store)

        return rebuild(
            devices = store.getDevicesList().filterNot { stored ->
                stored.ownerUid == normalizedOwnerUid
            },
            ignored = store.getIgnoredDevicesList().filterNot { ignored ->
                ignored.ownerUid == normalizedOwnerUid
            }
        )
    }

    private fun rebuild(
        devices: Iterable<StoredKnownDevice>,
        ignored: Iterable<StoredIgnoredDevice>
    ): KnownDevicesStore {
        val updated = KnownDevicesStore.newBuilder()
            .addAllDevices(devices.sortedWith(deviceComparator))
            .addAllIgnoredDevices(ignored.sortedWith(ignoredComparator))
            .build()

        validate(updated)
        return updated
    }

    private fun validateOwnerUid(
        value: String,
        recordType: String,
        index: Int
    ) {
        if (value.isBlank() || value != value.trim()) {
            invalid(recordType, index, "owner_uid must be non-blank and trimmed")
        }
    }

    private fun validateDeviceUid(
        value: String,
        recordType: String,
        index: Int
    ) {
        if (value.isBlank() || value != value.trim()) {
            invalid(recordType, index, "device_uid must be non-blank and trimmed")
        }
    }

    private fun validatePort(
        value: Int,
        field: String,
        index: Int
    ) {
        if (value !in 0..MAX_PORT) {
            invalid("known device", index, "$field is outside the valid port range")
        }
    }

    private fun validateNonNegative(
        value: Int,
        field: String,
        index: Int
    ) {
        if (value < 0) {
            invalid("known device", index, "$field must not be negative")
        }
    }

    private fun validateStringList(
        values: List<String>,
        field: String,
        index: Int
    ) {
        if (values.any { value -> value.isBlank() || value != value.trim() }) {
            invalid("known device", index, "$field contains a blank or untrimmed value")
        }

        if (values.distinct().size != values.size) {
            invalid("known device", index, "$field contains duplicate values")
        }
    }

    private fun String.requireOwnerUid(): String {
        val normalized = trim()
        require(normalized.isNotBlank()) {
            "ownerUid must not be blank"
        }
        return normalized
    }

    private fun String.requireDeviceUid(): String {
        val normalized = trim()
        require(normalized.isNotBlank()) {
            "deviceUid must not be blank"
        }
        return normalized
    }

    private fun ownerDeviceKey(
        ownerUid: String,
        deviceUid: String
    ): String {
        return "$ownerUid\u0000$deviceUid"
    }

    private fun invalid(
        recordType: String,
        index: Int,
        reason: String
    ): Nothing {
        throw KnownDevicesValidationException(
            "Invalid $recordType at index $index: $reason."
        )
    }

    private val deviceComparator =
        compareBy<StoredKnownDevice> { stored -> stored.ownerUid }
            .thenBy { stored -> stored.identity.deviceUid }

    private val ignoredComparator =
        compareBy<StoredIgnoredDevice> { ignored -> ignored.ownerUid }
            .thenBy { ignored -> ignored.deviceUid }

    private const val MAX_PORT = 65_535
}

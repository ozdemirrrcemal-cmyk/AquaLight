package com.aqua.aqualight.data.devices.store

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.aqua.aqualight.data.user.UserDataScope
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object KnownDevicesSerializer : Serializer<KnownDevicesStore> {

    override val defaultValue: KnownDevicesStore =
        KnownDevicesStore.getDefaultInstance()

    override suspend fun readFrom(
        input: InputStream
    ): KnownDevicesStore {
        val store = try {
            KnownDevicesStore.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException(
                "Cannot read known-device metadata.",
                exception
            )
        }

        return try {
            KnownDevicesStoreValidator.validate(store)
            store
        } catch (exception: IllegalArgumentException) {
            throw CorruptionException(
                "Known-device metadata is invalid.",
                exception
            )
        }
    }

    override suspend fun writeTo(
        t: KnownDevicesStore,
        output: OutputStream
    ) {
        KnownDevicesStoreValidator.validate(t)
        t.writeTo(output)
    }
}

internal object KnownDevicesStoreValidator {

    fun validate(
        store: KnownDevicesStore
    ) {
        val deviceKeys = mutableSetOf<Pair<String, String>>()
        store.devicesList.forEach { record ->
            val ownerUid = UserDataScope.normalizeOwnerUid(record.ownerUid)
            val deviceUid = record.snapshot.identity.uid.trim()

            require(ownerUid.isNotBlank()) {
                "Known-device owner UID must not be blank."
            }
            require(record.ownerUid == ownerUid) {
                "Known-device owner UID must be normalized."
            }
            require(deviceUid.isNotBlank()) {
                "Known-device UID must not be blank."
            }
            require(record.snapshot.identity.uid == deviceUid) {
                "Known-device UID must be normalized."
            }
            require(deviceKeys.add(ownerUid to deviceUid)) {
                "Known-device records must be unique per owner and device."
            }
        }

        val ignoredKeys = mutableSetOf<Pair<String, String>>()
        store.ignoredDevicesList.forEach { record ->
            val ownerUid = UserDataScope.normalizeOwnerUid(record.ownerUid)
            val deviceUid = record.deviceUid.trim()

            require(ownerUid.isNotBlank()) {
                "Ignored-device owner UID must not be blank."
            }
            require(record.ownerUid == ownerUid) {
                "Ignored-device owner UID must be normalized."
            }
            require(deviceUid.isNotBlank()) {
                "Ignored-device UID must not be blank."
            }
            require(record.deviceUid == deviceUid) {
                "Ignored-device UID must be normalized."
            }
            require(ignoredKeys.add(ownerUid to deviceUid)) {
                "Ignored-device records must be unique per owner and device."
            }
        }
    }
}

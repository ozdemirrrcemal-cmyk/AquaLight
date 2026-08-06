package com.aqua.aqualight.data.notifications

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

internal object DeviceUpdateNotificationStateSerializer :
    Serializer<DeviceUpdateNotificationStateStore> {

    override val defaultValue: DeviceUpdateNotificationStateStore =
        DeviceUpdateNotificationStateRules.defaultStore()

    override suspend fun readFrom(input: InputStream): DeviceUpdateNotificationStateStore {
        return try {
            DeviceUpdateNotificationStateRules.validateStore(
                DeviceUpdateNotificationStateStore.parseFrom(input)
            )
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException(
                "Device update notification state could not be parsed.",
                exception
            )
        }
    }

    override suspend fun writeTo(
        t: DeviceUpdateNotificationStateStore,
        output: OutputStream
    ) {
        DeviceUpdateNotificationStateRules.validateStore(t).writeTo(output)
    }
}

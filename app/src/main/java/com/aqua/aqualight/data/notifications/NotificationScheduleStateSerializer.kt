package com.aqua.aqualight.data.notifications

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

internal object NotificationScheduleStateSerializer :
    Serializer<NotificationScheduleStateStore> {

    override val defaultValue: NotificationScheduleStateStore =
        NotificationScheduleStateRules.defaultStore()

    override suspend fun readFrom(input: InputStream): NotificationScheduleStateStore {
        return try {
            NotificationScheduleStateRules.validateStore(
                NotificationScheduleStateStore.parseFrom(input)
            )
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException(
                "Notification schedule state could not be parsed.",
                exception
            )
        }
    }

    override suspend fun writeTo(
        t: NotificationScheduleStateStore,
        output: OutputStream
    ) {
        NotificationScheduleStateRules.validateStore(t).writeTo(output)
    }
}

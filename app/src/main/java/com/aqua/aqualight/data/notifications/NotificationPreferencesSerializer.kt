package com.aqua.aqualight.data.notifications

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

/** Strict serializer for owner-scoped notification preferences. */
object NotificationPreferencesSerializer : Serializer<NotificationPreferencesStore> {

    override val defaultValue: NotificationPreferencesStore =
        NotificationPreferenceStoreRules.defaultStore()

    override suspend fun readFrom(input: InputStream): NotificationPreferencesStore {
        val parsed = try {
            NotificationPreferencesStore.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException(
                "Cannot read notification preferences proto.",
                exception
            )
        }

        return try {
            NotificationPreferenceStoreRules.validateStore(parsed)
        } catch (exception: StoreInvariantViolation) {
            throw CorruptionException(
                "Notification preferences violate the commercial store contract.",
                exception
            )
        }
    }

    override suspend fun writeTo(
        t: NotificationPreferencesStore,
        output: OutputStream
    ) {
        NotificationPreferenceStoreRules.validateStore(t).writeTo(output)
    }
}

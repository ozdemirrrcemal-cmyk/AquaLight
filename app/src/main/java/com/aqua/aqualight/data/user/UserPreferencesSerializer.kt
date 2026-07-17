package com.aqua.aqualight.data.user

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object UserPreferencesSerializer : Serializer<UserPreferences> {

    override val defaultValue: UserPreferences =
        UserPreferencesStoreRules.defaultPreferences()

    override suspend fun readFrom(input: InputStream): UserPreferences {
        val parsed = try {
            UserPreferences.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException(
                "Cannot read user preferences proto.",
                exception
            )
        }

        return try {
            UserPreferencesStoreRules.validate(parsed)
        } catch (exception: StoreInvariantViolation) {
            throw CorruptionException(
                "User preferences violate the commercial store contract.",
                exception
            )
        }
    }

    override suspend fun writeTo(
        t: UserPreferences,
        output: OutputStream
    ) {
        UserPreferencesStoreRules.validate(t).writeTo(output)
    }
}

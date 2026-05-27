package com.aqua.aqualight.data.devices.dosing

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.aqua.aqualight.data.devices.dosing.proto.DosingChannelSettingsPreferences
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object DosingChannelSettingsPreferencesSerializer :
    Serializer<DosingChannelSettingsPreferences> {

    override val defaultValue: DosingChannelSettingsPreferences =
        DosingChannelSettingsPreferences.getDefaultInstance()

    override suspend fun readFrom(
        input: InputStream
    ): DosingChannelSettingsPreferences {
        try {
            return DosingChannelSettingsPreferences.parseFrom(
                input
            )
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException(
                message = "Cannot read dosing channel settings preferences.",
                cause = exception
            )
        }
    }

    override suspend fun writeTo(
        t: DosingChannelSettingsPreferences,
        output: OutputStream
    ) {
        t.writeTo(
            output
        )
    }
}
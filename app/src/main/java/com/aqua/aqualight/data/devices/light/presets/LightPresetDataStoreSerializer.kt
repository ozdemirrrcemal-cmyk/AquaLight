package com.aqua.aqualight.data.devices.light.presets

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object LightPresetDataStoreSerializer : Serializer<LightPresetsPreferences> {

    override val defaultValue: LightPresetsPreferences =
        LightPresetsPreferences.getDefaultInstance()

    override suspend fun readFrom(
        input: InputStream
    ): LightPresetsPreferences {
        return try {
            LightPresetsPreferences.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException(
                message = "Cannot read light presets preferences.",
                cause = exception
            )
        }
    }

    override suspend fun writeTo(
        t: LightPresetsPreferences,
        output: OutputStream
    ) {
        t.writeTo(output)
    }
}
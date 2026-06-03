package com.aqua.aqualight.data.devices.light.programs

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object LightProgramsSerializer : Serializer<LightProgramsPreferences> {

    override val defaultValue: LightProgramsPreferences =
        LightProgramsPreferences.getDefaultInstance()

    override suspend fun readFrom(
        input: InputStream
    ): LightProgramsPreferences {
        return try {
            LightProgramsPreferences.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException(
                message = "Cannot read light programs proto.",
                cause = exception
            )
        }
    }

    override suspend fun writeTo(
        t: LightProgramsPreferences,
        output: OutputStream
    ) {
        t.writeTo(output)
    }
}
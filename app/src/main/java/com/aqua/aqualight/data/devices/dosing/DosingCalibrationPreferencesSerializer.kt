package com.aqua.aqualight.data.devices.dosing

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.aqua.aqualight.data.devices.dosing.proto.DosingCalibrationPreferences
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object DosingCalibrationPreferencesSerializer :
    Serializer<DosingCalibrationPreferences> {

    override val defaultValue: DosingCalibrationPreferences =
        DosingCalibrationPreferences.getDefaultInstance()

    override suspend fun readFrom(
        input: InputStream
    ): DosingCalibrationPreferences {
        try {
            return DosingCalibrationPreferences.parseFrom(
                input
            )
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException(
                message = "Cannot read dosing calibration preferences.",
                cause = exception
            )
        }
    }

    override suspend fun writeTo(
        t: DosingCalibrationPreferences,
        output: OutputStream
    ) {
        t.writeTo(
            output
        )
    }
}
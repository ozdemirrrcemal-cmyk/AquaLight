package com.aqua.aqualight.data.devices

import androidx.datastore.core.Serializer
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object DevicesPreferencesSerializer : Serializer<DevicesPreferences> {

    override val defaultValue: DevicesPreferences =
        DevicesPreferences.getDefaultInstance()

    override suspend fun readFrom(
        input: InputStream
    ): DevicesPreferences {
        return try {
            DevicesPreferences.parseFrom(input)
        } catch (exception: IOException) {
            exception.printStackTrace()
            defaultValue
        } catch (exception: Exception) {
            exception.printStackTrace()
            defaultValue
        }
    }

    override suspend fun writeTo(
        t: DevicesPreferences,
        output: OutputStream
    ) {
        t.writeTo(output)
    }
}
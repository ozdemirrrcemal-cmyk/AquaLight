package com.aqua.aqualight.data.devices.light.automation

import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object LightAutomationSerializer : Serializer<LightAutomationPreferences> {
    override val defaultValue: LightAutomationPreferences = LightAutomationPreferences.getDefaultInstance()
    override suspend fun readFrom(input: InputStream): LightAutomationPreferences = try { LightAutomationPreferences.parseFrom(input) } catch (e: InvalidProtocolBufferException) { Log.w("LightAutomationStore", "Cannot read light automation proto.", e); throw CorruptionException("Cannot read light automation proto.", e) }
    override suspend fun writeTo(t: LightAutomationPreferences, output: OutputStream) { t.writeTo(output) }
}

package com.aqua.aqualight.data.aquarium.health

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/** Versioned, length-framed input only. Catalog changes do not change a saved request's identity. */
internal object WaterAnalysisRequestFingerprint {
    fun of(draft: WaterAnalysisDraftRecord): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeUTF("AquaLight.WaterRequest.v1")
            output.writeLong(draft.tankId)
            output.writeLong(draft.measuredAtMillis)
            output.writeBoolean(draft.temperatureCelsius != null)
            draft.temperatureCelsius?.let { output.writeDouble(it.canonicalZero()) }
            output.writeUTF(draft.temperatureSource?.name.orEmpty())
            output.writeInt(draft.measurements.size)
            draft.measurements.sortedBy { it.parameter.name }.forEach { value ->
                output.writeUTF(value.parameter.name)
                output.writeDouble(value.value.canonicalZero())
                output.writeUTF(value.method.name)
                output.writeUTF(value.testKitId.orEmpty())
                output.writeUTF(value.basis.name)
                output.writeUTF(value.unit.name)
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun of(record: StoredWaterAnalysis): String = of(record.toRecordStrict().let {
        WaterAnalysisDraftRecord(it.tankId, it.measuredAtMillis, it.temperatureCelsius,
            it.temperatureSource, it.measurements, it.requestId)
    })

    private fun Double.canonicalZero(): Double = if (this == 0.0) 0.0 else this
}

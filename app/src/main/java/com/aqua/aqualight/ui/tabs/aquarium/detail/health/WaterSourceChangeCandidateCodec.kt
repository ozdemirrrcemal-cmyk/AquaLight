package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import java.util.Base64

/** Keeps a pending source change in the shared confirmation dialog's saved arguments. */
internal object WaterSourceChangeCandidateCodec {
    private const val VERSION = "v1"

    fun encode(
        parameterId: WaterTestParameterId,
        selection: WaterMeasurementSelectionUi
    ): String = listOf(
        VERSION,
        parameterId.name,
        selection.method.name,
        selection.testKitId,
        selection.basisId,
        selection.unitId
    ).joinToString(".") { value ->
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(Charsets.UTF_8))
    }

    fun decode(raw: String): Pair<WaterTestParameterId, WaterMeasurementSelectionUi>? {
        val parts = raw.split('.')
        if (parts.size != 6) return null
        val values = runCatching {
            parts.map { part ->
                Base64.getUrlDecoder().decode(part).toString(Charsets.UTF_8)
            }
        }.getOrNull() ?: return null
        if (values[0] != VERSION) return null
        val parameterId = runCatching { WaterTestParameterId.valueOf(values[1]) }.getOrNull()
            ?: return null
        val method = runCatching { WaterMeasurementMethodUi.valueOf(values[2]) }.getOrNull()
            ?: return null
        val selection = WaterMeasurementSelectionUi(method, values[3], values[4], values[5])
        if (!WaterMeasurementUiCatalog.isSelectionValid(parameterId, selection)) return null
        return parameterId to selection
    }
}

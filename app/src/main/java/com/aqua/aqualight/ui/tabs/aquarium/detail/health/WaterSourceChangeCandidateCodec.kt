package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import java.util.Base64

/** Keeps a pending source change in the shared confirmation dialog's saved arguments. */
internal object WaterSourceChangeCandidateCodec {
    private const val VERSION = "v1"
    private const val PART_COUNT = 6
    private const val VERSION_INDEX = 0
    private const val PARAMETER_INDEX = 1
    private const val METHOD_INDEX = 2
    private const val KIT_INDEX = 3
    private const val BASIS_INDEX = 4
    private const val UNIT_INDEX = 5

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

    fun decode(raw: String): Pair<WaterTestParameterId, WaterMeasurementSelectionUi>? = runCatching {
        val parts = raw.split('.')
        if (parts.size == PART_COUNT) {
            parts.map { part ->
                Base64.getUrlDecoder().decode(part).toString(Charsets.UTF_8)
            }.takeIf { values -> values[VERSION_INDEX] == VERSION }
                ?.let { values ->
                    val parameterId = WaterTestParameterId.valueOf(values[PARAMETER_INDEX])
                    val method = WaterMeasurementMethodUi.valueOf(values[METHOD_INDEX])
                    val selection = WaterMeasurementSelectionUi(
                        method, values[KIT_INDEX], values[BASIS_INDEX], values[UNIT_INDEX]
                    )
                    (parameterId to selection).takeIf { (id, candidate) ->
                        WaterMeasurementUiCatalog.isSelectionValid(id, candidate)
                    }
                }
        } else {
            null
        }
    }.getOrNull()
}

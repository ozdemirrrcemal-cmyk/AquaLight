package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementCatalog
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementNormalizer
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementSelection
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementUnit
import com.aqua.aqualight.application.aquarium.health.WaterParameterDefinitions

internal enum class WaterMeasurementMethodUi {
    MANUAL,
    TEST_KIT,
    DIGITAL,
    SENSOR
}

internal data class WaterMeasurementSelectionUi(
    val method: WaterMeasurementMethodUi,
    val testKitId: String,
    val basisId: String,
    val unitId: String
)

internal data class WaterMeasurementOptionUi(
    val id: String,
    @StringRes val labelRes: Int
)

internal object WaterMeasurementUiCatalog {

    fun defaultSelection(parameterId: WaterTestParameterId): WaterMeasurementSelectionUi =
        WaterMeasurementUiMapper.toUiSelection(
            WaterMeasurementCatalog.defaultSelection(parameterId.toDomainParameter())
        )

    fun normalizeSelection(
        parameterId: WaterTestParameterId,
        selection: WaterMeasurementSelectionUi
    ): WaterMeasurementSelectionUi {
        val parameter = parameterId.toDomainParameter()
        val candidate = WaterMeasurementUiMapper.toDomainSelectionOrNull(selection)
            ?: WaterMeasurementCatalog.defaultSelection(parameter)
        return WaterMeasurementUiMapper.toUiSelection(
            WaterMeasurementCatalog.normalizeSelection(parameter, candidate)
        )
    }

    fun isSelectionValid(
        parameterId: WaterTestParameterId,
        selection: WaterMeasurementSelectionUi
    ): Boolean {
        val candidate = WaterMeasurementUiMapper.toDomainSelectionOrNull(selection)
        return candidate != null &&
            WaterMeasurementCatalog.isSelectionValid(parameterId.toDomainParameter(), candidate)
    }

    fun testKitOptions(parameterId: WaterTestParameterId): List<WaterMeasurementOptionUi> =
        buildList {
            add(WaterMeasurementOptionUi(OPTION_NONE, R.string.water_measurement_not_selected))
            WaterMeasurementCatalog
                .builtInTestKitsFor(parameterId.toDomainParameter())
                .forEach { definition ->
                    add(
                        WaterMeasurementOptionUi(
                            id = definition.id,
                            labelRes = WaterMeasurementUiMapper.testKitLabelRes(definition.id)
                        )
                    )
                }
            add(WaterMeasurementOptionUi(KIT_OTHER, R.string.water_measurement_kit_other))
        }

    fun basisOptions(parameterId: WaterTestParameterId): List<WaterMeasurementOptionUi> =
        WaterParameterDefinitions
            .basisOptions(parameterId.toDomainParameter())
            .map(WaterMeasurementUiMapper::basisOption)

    fun unitOptions(parameterId: WaterTestParameterId): List<WaterMeasurementOptionUi> =
        WaterParameterDefinitions
            .unitOptions(parameterId.toDomainParameter())
            .map(WaterMeasurementUiMapper::unitOption)

    fun selectableBasisOptions(
        parameterId: WaterTestParameterId,
        selection: WaterMeasurementSelectionUi
    ): List<WaterMeasurementOptionUi> {
        val parameter = parameterId.toDomainParameter()
        val candidate = WaterMeasurementUiMapper.toDomainSelectionOrNull(selection)
            ?: WaterMeasurementCatalog.defaultSelection(parameter)
        return WaterMeasurementCatalog
            .selectableBasisOptions(parameter, candidate)
            .map(WaterMeasurementUiMapper::basisOption)
    }

    fun selectableUnitOptions(
        parameterId: WaterTestParameterId,
        selection: WaterMeasurementSelectionUi
    ): List<WaterMeasurementOptionUi> {
        val parameter = parameterId.toDomainParameter()
        val candidate = WaterMeasurementUiMapper.toDomainSelectionOrNull(selection)
            ?: WaterMeasurementCatalog.defaultSelection(parameter)
        return WaterMeasurementCatalog
            .selectableUnitOptions(parameter, candidate)
            .map(WaterMeasurementUiMapper::unitOption)
    }

    const val OPTION_NONE = "none"
    const val KIT_SALIFERT_NITRATE = WaterMeasurementCatalog.SALIFERT_NITRATE_TEST_KIT_ID
    const val KIT_OTHER = WaterMeasurementCatalog.OTHER_TEST_KIT_ID

    const val BASIS_PH = "ph"
    const val BASIS_NO3 = "no3"
    const val BASIS_NO3_N = "no3_n"
    const val BASIS_NO2 = "no2"
    const val BASIS_NH3_NH4 = "nh3_nh4"
    const val BASIS_TAN = "tan"
    const val BASIS_TAN_N = "tan_n"
    const val BASIS_FREE_NH3 = "free_nh3"
    const val BASIS_GH = "gh"
    const val BASIS_KH = "kh"
    const val BASIS_TOTAL_ALKALINITY = "total_alkalinity"
    const val BASIS_PO4 = "po4"
    const val BASIS_P = "p"
    const val BASIS_TDS = "tds"
    const val BASIS_EC = "ec"
    const val BASIS_CO2 = "co2"
    const val BASIS_FE = "fe"
    const val BASIS_K = "k"
    const val BASIS_SALINITY = "salinity"
    const val BASIS_SG = "sg"
    const val BASIS_CA = "ca"
    const val BASIS_MG = "mg"
    const val BASIS_CU = "cu"
    const val BASIS_O2 = "o2"

    const val UNIT_NONE = "none"
    const val UNIT_MG_L = "mg_l"
    const val UNIT_DGH = "dgh"
    const val UNIT_DKH = "dkh"
    const val UNIT_PPM = "ppm"
    const val UNIT_US_CM = "us_cm"
    const val UNIT_PPT = "ppt"
    const val UNIT_MEQ_L = "meq_l"
    const val UNIT_PPM_CACO3 = "ppm_caco3"
}

internal object WaterMeasurementCanonicalUi {
    fun hasCanonicalSemantics(parameterId: WaterTestParameterId): Boolean =
        WaterMeasurementNormalizer.hasCanonicalSemantics(parameterId.toDomainParameter())

    fun hasCanonicalPreview(
        parameterId: WaterTestParameterId,
        selection: WaterMeasurementSelectionUi
    ): Boolean = selection.toValidDomainSelectionOrNull(parameterId)?.let { source ->
        WaterMeasurementNormalizer.supportsCanonicalSource(parameterId.toDomainParameter(), source)
    } ?: false

    fun canonicalBasis(parameterId: WaterTestParameterId): WaterMeasurementOptionUi =
        WaterMeasurementUiMapper.basisOption(
            WaterParameterDefinitions.canonicalBasis(parameterId.toDomainParameter())
        )

    fun canonicalUnit(parameterId: WaterTestParameterId): WaterMeasurementOptionUi? =
        WaterParameterDefinitions
            .canonicalUnit(parameterId.toDomainParameter())
            .takeUnless { unit -> unit == WaterMeasurementUnit.NONE }
            ?.let(WaterMeasurementUiMapper::unitOption)
}

internal fun WaterMeasurementSelectionUi.toValidDomainSelectionOrNull(
    parameterId: WaterTestParameterId
): WaterMeasurementSelection? {
    val candidate = WaterMeasurementUiMapper.toDomainSelectionOrNull(this)
    return candidate?.takeIf { selection ->
        WaterMeasurementCatalog.isSelectionValid(parameterId.toDomainParameter(), selection)
    }
}

internal object WaterMeasurementUiStateCodec {
    private const val STATE_KEY = "water_measurement_ui_selections"
    private const val DELIMITER = "|"
    private const val PART_COUNT = 5

    fun save(
        outState: Bundle,
        selections: Map<WaterTestParameterId, WaterMeasurementSelectionUi>
    ) {
        outState.putStringArrayList(
            STATE_KEY,
            ArrayList(
                selections.map { (parameterId, selection) ->
                    listOf(
                        parameterId.name,
                        selection.method.name,
                        selection.testKitId,
                        selection.basisId,
                        selection.unitId
                    ).joinToString(DELIMITER)
                }
            )
        )
    }

    fun restore(
        savedInstanceState: Bundle?,
        destination: MutableMap<WaterTestParameterId, WaterMeasurementSelectionUi>
    ) {
        savedInstanceState
            ?.getStringArrayList(STATE_KEY)
            .orEmpty()
            .forEach { encoded ->
                val parts = encoded.split(DELIMITER, limit = PART_COUNT)
                if (parts.size == PART_COUNT) {
                    val parameterId = runCatching {
                        WaterTestParameterId.valueOf(parts[0])
                    }.getOrNull()
                    val method = runCatching {
                        WaterMeasurementMethodUi.valueOf(parts[1])
                    }.getOrNull()
                    if (parameterId != null && method != null) {
                        destination[parameterId] = WaterMeasurementUiCatalog.normalizeSelection(
                            parameterId = parameterId,
                            selection = WaterMeasurementSelectionUi(
                                method = method,
                                testKitId = parts[2],
                                basisId = parts[3],
                                unitId = parts[4]
                            )
                        )
                    }
                }
            }
    }
}
